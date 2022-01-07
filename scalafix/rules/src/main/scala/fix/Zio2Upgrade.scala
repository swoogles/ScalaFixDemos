package fix

import scalafix.v1._

import scala.annotation.tailrec
import scala.meta._

class Zio2Upgrade extends SemanticRule("Zio2Upgrade") {

  val renames =
    Map(
      "accessM"                -> "environmentWithZIO",
      "accessZIO"              -> "environmentWithZIO",
      "whenM"                  -> "whenZIO",
      "serviceWith"               -> "serviceWithZIO"
    )
  
  lazy val scopes = List(
    "zio.test.package",
    "zio.test.Gen",
    "zio.test.DefaultRunnableSpec",
    "zio.Exit",
    "zio.ZIO",
  )

  case class GenericRename(scopes: List[String], oldName: String, newName: String) {
    val companions = scopes.map(_ + ".")
    val traits     = scopes.map(_ + "#")
    val allPaths   = companions ++ traits

    val list    = allPaths.map(path => s"$path$oldName")
    val matcher = SymbolMatcher.normalized(list: _*)

    def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
      tree match {
        case matcher(name @ Name(_)) =>
          Some(Patch.renameSymbol(name.symbol, newName))
        case _ => None
      }
  }

  case class Renames(scopes: List[String], renames: Map[String, String]) {
    val normalizedRenames = renames.map { case (k, v) =>
      GenericRename(scopes, k, v)
    }

    object Matcher {
      def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
        normalizedRenames.flatMap(_.unapply(tree)).headOption
    }
  }

  val UniversalRenames = Renames(scopes, renames)

  val ZIORenames = Renames(
    List("zio.ZIO"),
    Map(
      "run" -> "exit"
    )
  )

  val FiberId_Old = SymbolMatcher.normalized("zio/Fiber.Id#")

  val Blocking_Old_Exact = SymbolMatcher.exact("zio/blocking/package.Blocking#")

  val FiberId_Old_Exact = SymbolMatcher.exact("zio/Fiber.Id#")

  val hasNormalized = SymbolMatcher.normalized("zio/Has#")

  val newFiberId = Symbol("zio/FiberId#")

  def replaceSymbols(implicit doc: SemanticDocument) = Patch.replaceSymbols(
    // System
    "zio.system.env"              -> "zio.System.env",
    "zio.system.envOrElse"        -> "zio.System.envOrElse",
    // Console
    "zio.console.putStrLn"    -> "zio.Console.printLine",
    "zio.random.shuffle"           -> "zio.Random.shuffle",
    // Blocking
    "zio.blocking.effectBlockingIO"         -> "zio.ZIO.attemptBlockingIO",
    // Gen
    "zio.test.Gen.anyInt"                     -> "zio.test.Gen.int",
    "zio.test.Gen.anyString"                  -> "zio.test.Gen.string",
    // App
    "zio.App"           -> "zio.ZIOAppDefault",
    "zio.Executor.asEC" -> "zio.Executor.asExecutionContext"
  )

  val foreachParN             = ParNRenamer("foreachPar", 3)
  val collectAllParN          = ParNRenamer("collectAllPar", 2)

  object BuiltInServiceFixer { // TODO Handle all built-in services?

    object ImporteeRenamer {
        
      def importeeRenames(implicit sdoc: SemanticDocument): PartialFunction[Tree, Option[Patch]] = {
        val pf: SymbolMatcher => PartialFunction[Tree, Patch] =
          (symbolMatcher: SymbolMatcher) => {
            case t @ ImporteeNameOrRename(symbolMatcher(_)) =>
              Patch.removeImportee(t)
          }

        val pf1:PartialFunction[Tree, Option[Patch]] = { case (_: Tree) => None }
        val pf2: Function2[PartialFunction[Tree, Option[Patch]], PartialFunction[Tree, Patch], PartialFunction[Tree, Option[Patch]]] = {
          case (totalPatch, nextPatch) => {
            case (tree: Tree) => nextPatch.lift(tree).orElse(totalPatch(tree))
          }
        }

        List(
          randomMigrator,
          systemMigrator,
          consoleMigrator,
        ).foldLeft(List[SymbolMatcher](hasNormalized)) { case (serviceMatchers, serviceMigrator) =>
          serviceMatchers ++ List(serviceMigrator.normalizedOld, serviceMigrator.normalizedOldService)
        }.map(pf).foldLeft {pf1} {pf2}
      }

      def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
        importeeRenames.apply(tree)
    }

    private val consoleMigrator =
      ServiceMigrator(name = "Console", oldPath = "zio/console/package.", newPath = "zio/")

    case class ServiceMigrator(
      oldExact: SymbolMatcher,
      oldService: SymbolMatcher,
      newSymbol: Symbol,
      plainName: String,
      normalizedOld: SymbolMatcher,
      normalizedOldService: SymbolMatcher
    ) {
      def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] = {
        val partial: PartialFunction[Tree, Patch] = {
          case t @ oldService(Name(_)) =>
            Patch.replaceTree(unwindSelect(t), plainName) +
              Patch.addGlobalImport(newSymbol)

          case t @ oldExact(Name(_)) =>
            Patch.addGlobalImport(newSymbol) +
              Patch.replaceTree(unwindSelect(t), plainName)
        }
        partial.lift(tree)
      }
    }
    object ServiceMigrator {

      def apply(name: String, oldPath: String, newPath: String): ServiceMigrator =
        ServiceMigrator(
          SymbolMatcher.exact(oldPath + name + "#"),
          SymbolMatcher.exact(oldPath + name + ".Service#"),
          Symbol(newPath + name + "#"),
          name,
          SymbolMatcher.normalized(oldPath + name + "#"),
          SymbolMatcher.normalized(oldPath + name + ".Service#")
        )
    }

    private val randomMigrator =
      ServiceMigrator(name = "Random", oldPath = "zio/random/package.", newPath = "zio/")

    private val systemMigrator =
      ServiceMigrator(name = "System", oldPath = "zio/system/package.", newPath = "zio/")

    def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] = {
      val partial: PartialFunction[Tree, Patch] = {
        case t @ Type.Apply(tpe: Type, args: List[Type]) if hasNormalized.matches(tpe.symbol) =>
          val builtInServices: Seq[SymbolMatcher] =
            List(
              randomMigrator,
              systemMigrator,
              consoleMigrator,
            ).foldLeft(List.empty[SymbolMatcher]) { case (serviceMatchers, serviceMigrator) =>
              serviceMatchers ++ List(serviceMigrator.oldService, serviceMigrator.oldExact)
            }

          if (builtInServices.exists(_.matches(args.head)))
            Patch.replaceTree(t, "")
          else
            Patch.replaceTree(t, args.head.toString)

        case randomMigrator(patch)          => patch
        case systemMigrator(patch)          => patch
        case consoleMigrator(patch)         => patch

        case t @ q"zio.random.Random" =>
          Patch.replaceTree(t, "zio.Random")

        case t @ q"import zio.duration._" =>
          Patch.replaceTree(t, "") +
            Patch.addGlobalImport(wildcardImport(q"zio"))

        case t @ q"import zio.system" =>
          Patch.replaceTree(t, "") + Patch.addGlobalImport(systemMigrator.newSymbol)

      }
      partial.lift(tree)
    }

  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    Zio2ZIOSpec.fix +
    doc.tree.collect {
      case BuiltInServiceFixer.ImporteeRenamer(patch) => patch

      case ZIORenames.Matcher(patch)       => patch
      case UniversalRenames.Matcher(patch) => patch

      case BuiltInServiceFixer(patch) => patch

      // Replace >>= with flatMap. For some reason, this doesn't work with the
      // technique used above.
      case t @ q"$lhs >>= $rhs" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs flatMap $rhs")
      case t @ q"$lhs.>>=($rhs)" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs.flatMap($rhs)")

      case t @ q"$lhs.collectAllParN($n)($as)" =>
        Patch.replaceTree(t, s"$lhs.collectAllPar($as).withParallelism($n)")

      case t @ q"$lhs.collectAllParN_($n)($as)" =>
        Patch.replaceTree(t, s"$lhs.collectAllParDiscard($as).withParallelism($n)")
      case t @ q"$lhs.collectAllParNDiscard($n)($as)" =>
        Patch.replaceTree(t, s"$lhs.collectAllParDiscard($as).withParallelism($n)")

      case foreachParN.Matcher(patch)             => patch
      case collectAllParN.Matcher(patch)          => patch

      case t @ q"import zio.blocking._" =>
        Patch.removeTokens(t.tokens)

      case t @ q"import zio.blocking.Blocking" =>
        Patch.removeTokens(t.tokens)

      case t @ Blocking_Old_Exact(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), s"Any")

      case t @ FiberId_Old_Exact(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), "FiberId") +
          Patch.addGlobalImport(newFiberId)

      case t @ q"import zio.console._" =>
        Patch.replaceTree(t, "") +
          Patch.addGlobalImport(wildcardImport(q"zio.Console"))

      case t @ q"import zio.test.environment._" =>
        Patch.removeTokens(t.tokens)

      case t @ q"Fiber.Id" =>
        Patch.replaceTree(t, "FiberId") +
          Patch.addGlobalImport(Symbol("zio/FiberId#"))

      // TODO Safe to do for many similar types?
      case t @ q"import zio.duration.Duration" =>
        Patch.replaceTree(t, "import zio.Duration")

      case t @ q"zio.duration.Duration" =>
        Patch.replaceTree(t, "zio.Duration")

      case t @ q"import zio.internal.Tracing" =>
        Patch.replaceTree(t, "import zio.internal.tracing.Tracing")

      case t @ ImporteeNameOrRename(FiberId_Old(_)) => Patch.removeImportee(t)

    }.asPatch + replaceSymbols
  }

  private def wildcardImport(ref: Term.Ref): Importer =
    Importer(ref, List(Importee.Wildcard()))

  @tailrec
  private def unwindSelect(t: Tree): Tree = t.parent match {
    case Some(t: Type.Select) => unwindSelect(t)
    case Some(t: Term.Select) => unwindSelect(t)
    case _                    => t
  }
  
  object Zio2ZIOSpec extends SemanticRule("ZIOSpecMigration"){
    val zio2UpgradeRule = new Zio2Upgrade()
    val AbstractRunnableSpecRenames = zio2UpgradeRule.Renames(
      List("zio.test.DefaultRunnableSpec" /* TODO What other types here? */),
      Map(
        "Failure"            -> "Any",
      )
    )

    override def fix(implicit doc: SemanticDocument): Patch =
      doc.tree.collect {
        case AbstractRunnableSpecRenames.Matcher(patch) => patch

        // TODO Check if we really want to do this, or if we want to keep it now that we might have a
        //    meaningful Failure type
        case t @ q"override def spec: $tpe = $body" if tpe.toString().contains("ZSpec[Environment, Failure]") =>
          Patch.replaceTree(t, s"override def spec = $body")
      }.asPatch + replaceSymbols

    def replaceSymbols(implicit doc: SemanticDocument) = Patch.replaceSymbols(
      "zio.test.DefaultRunnableSpec" -> "zio.test.ZIOSpecDefault"
    )

  }
}

private object ImporteeNameOrRename {
  def unapply(importee: Importee): Option[Name] =
    importee match {
      case Importee.Name(x)      => Some(x)
      case Importee.Rename(x, _) => Some(x)
      case _                     => None
    }
}

final case class ParNRenamer(methodName: String, paramCount: Int) {
  object Matcher {
    def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
      tree match {
        case t @ q"$lhs.$method(...$params)"
            if method.value.startsWith(methodName + "N") && paramCount == params.length =>
          val generatedName =
            if (method.value.endsWith("_") || method.value.endsWith("Discard"))
              s"${methodName}Discard"
            else methodName
          val n          = params.head.head
          val paramLists = params.drop(1).map(_.mkString("(", ", ", ")")).mkString("")
          Some(Patch.replaceTree(t, s"$lhs.$generatedName$paramLists.withParallelism($n)"))

        case t @ q"$lhs.$method[..$types](...$params)"
            if method.value.startsWith(methodName + "N") && paramCount == params.length =>
          val generatedName =
            if (method.value.endsWith("_") || method.value.endsWith("Discard"))
              s"${methodName}Discard"
            else methodName
          val n          = params.head.head
          val paramLists = params.drop(1).map(_.mkString("(", ", ", ")")).mkString("")
          Some(Patch.replaceTree(t, s"$lhs.$generatedName[${types.mkString(", ")}]$paramLists.withParallelism($n)"))

        case _ =>
          None
      }
    //        normalizedRenames.flatMap(_.unapply(tree)).headOption
  }
}
