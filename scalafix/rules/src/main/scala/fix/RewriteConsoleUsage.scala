package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class RewriteConsoleUsage extends SemanticRule("RewriteConsoleUsage") {
    private val newClockNowDeclaration = "input <- zio.Console.readLine"
    def replaceNow(tree: Tree, containingBlock: Term.Block)(implicit doc: SemanticDocument): Patch =
        tree match {

                case sideEffectingCall @ 
                          Defn.Val(
                            List(),
                            List(Pat.Var(Term.Name(variableName))),
                            None,
                            Term.Apply(Term.Name("readLine"), List())
                        ) => 

                    Patch.addLeft(containingBlock, s"""$variableName <- zio.Console.readLine""") +
                    Patch.replaceTree(sideEffectingCall, "")

                case sideEffectingCall @ 
                          Defn.Val(
                            List(),
                            List(Pat.Var(Term.Name(variableName))),
                            None,
                            Term.Apply(rhs)
                        ) => 
                    Patch.replaceTree(sideEffectingCall, s"$variableName <- ZIO(rhs)")
                case _ => tree.children.map { child =>
                    child match {
                        case sideEffectingCall @ 
                                Defn.Val(
                                    List(),
                                    List(Pat.Var(Term.Name("name"))),
                                    None,
                                    Term.Apply(Term.Name("readLine"), List())
                                ) => 
                            Patch.replaceTree(sideEffectingCall, "now")
                        case other => 
                            if (containsAClock(other))
                                replaceNow(child, containingBlock)(doc)
                            else
                                Patch.empty
                    }
                }.asPatch
            }

    /*
    def consoleProgram() = for {
        _ <- zio.Console.printLine("Please enter your name")
        name <- zio.Console.readLine
        _ <- zio.Console.printLine("Hello " + name)
        } yield ()


    */

    def containsAClock(tree: Tree)(implicit doc: SemanticDocument): Boolean = 
        tree match {
                case sideEffectingCall @ 
                          Defn.Val(
                            List(),
                            List(Pat.Var(Term.Name("name"))),
                            None,
                            Term.Apply(Term.Name("readLine"), List())
                        ) => 
                    true
                case _ => 
                    tree.children.exists{child => 
                        println("Child when searching for readLine: " + child)
                        child match {
                            case sideEffectingCall @ 
                                    Defn.Val(
                                        List(),
                                        List(Pat.Var(Term.Name("name"))),
                                        None,
                                        Term.Name("readLine")
                                    ) => 
                                ???
                                true
                            case other =>
                                other.children.exists(containsAClock)
                        }
                    }

        }

    def identifyClockInABlock(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case t @ Term.Block(args) =>
            if (args.exists(containsAClock) )
                Patch.addLeft(t, s"""for {\n      """) + 
                    args.map(replaceNow(_, t)).asPatch + 
                    Patch.addLeft(t, s"""\n    } yield """) 
            else  args.collect(identifyClockInABlock).asPatch
    }

  override def fix(implicit doc: SemanticDocument): Patch = {
      println(q"""object ConsoleUsage {
            def consoleProgram() = {
                println("Please enter your name")
                val name = readLine()
                println(s"Hello " + name)
            }
            }
      """.structure)

    doc.tree.collect {
        identifyClockInABlock
    }.asPatch
  }
  
}

