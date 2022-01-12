package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class RewriteClockUsage extends SemanticRule("RewriteClockUsage") {
    def replaceNow(tree: Tree)(implicit doc: SemanticDocument): Patch = 
        tree match {
                case badClockUsage @ Term.Apply(Term.Select(Term.Name("Instant"), Term.Name("now")), List()) => 
                    Patch.replaceTree(badClockUsage, "now")
                case other => tree.children.map(child=>replaceNow(child)(doc)).asPatch
            }

    def containsAClock(tree: Tree)(implicit doc: SemanticDocument): Boolean = 
        tree match {
                case badClockUsage @ Term.Apply(Term.Select(Term.Name("Instant"), Term.Name("now")), List()) => 
                    true
                case other =>
                    tree.children.exists(containsAClock)
            }

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

