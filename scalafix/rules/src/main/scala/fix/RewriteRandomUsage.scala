package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class RewriteRandomUsage extends SemanticRule("RewriteRandomUsage") {
    def replaceRandInt(tree: Tree)(implicit doc: SemanticDocument): Patch = 
        tree match {
                case badRandUsage @ Term.Apply(Term.Select(Term.Name("Random"), Term.Name("nextInt")), List()) => 
                // case badRandUsage @ Term.Select(Term.Name("Random"), Term.Name("nextInt")) => 

                    Patch.replaceTree(badRandUsage, "randInt")
                case other => tree.children.map(child => replaceRandInt(child)(doc)).asPatch
    }

    def containsARandomNextInt(tree: Tree)(implicit doc: SemanticDocument): Boolean = 
        tree match {
                // case badClockUsage @ Term.Select(Term.Name("Random"), Term.Name("nextInt")) =>
                case badClockUsage @ Term.Apply(Term.Select(Term.Name("Random"), Term.Name("nextInt")), List()) =>   // TODO Doesn't work for some reason :(
                    true
                case other =>
                    other.children.exists(containsARandomNextInt)
        }
        
    
    private val newClockNowDeclaration = "randInt <- zio.Random.int"
    def convertRandomUsageInBlocks(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case t @ Term.Block(args) =>
            if (args.exists(containsARandomNextInt)  )
                Patch.addLeft(t, s"""for {\n      ${newClockNowDeclaration}\n    } yield """) + 
                args.map(replaceRandInt).asPatch
            else  args.collect(convertRandomUsageInBlocks).asPatch
    }

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect {
        convertRandomUsageInBlocks
    }.asPatch
  }
  
}

