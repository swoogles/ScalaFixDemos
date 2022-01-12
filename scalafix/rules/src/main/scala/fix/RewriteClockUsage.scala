package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class RewriteClockUsage extends SemanticRule("RewriteClockUsage") {
    def replaceNow(tree: Tree)(implicit doc: SemanticDocument): Patch = 
        tree.children.map { child =>
            child match {
                case badClockUsage @ Term.Apply(Term.Select(Term.Name("Instant"), Term.Name("now")), List()) => 
                    Patch.replaceTree(badClockUsage, "now")
                case other => replaceNow(child)(doc)
            }
    }.asPatch

    def containsAClock(tree: Tree)(implicit doc: SemanticDocument): Boolean = 
        tree.children.exists(child => 
            child match {
                case badClockUsage @ Term.Apply(Term.Select(Term.Name("Instant"), Term.Name("now")), List()) => 
                    true
                case other =>
                    other.children.exists(containsAClock)
            }
            )

    private val newClockNowDeclaration = "now <- zio.Clock.instant"
    def identifyClockInABlock(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case t @ Term.Block(args) =>
            if (args.exists(containsAClock) )
                Patch.addLeft(t, s"""for {\n      ${newClockNowDeclaration}\n    } yield """) + 
                args.map(replaceNow).asPatch
            else  args.collect(identifyClockInABlock).asPatch
    }

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
        identifyClockInABlock
    }.asPatch
  
}

