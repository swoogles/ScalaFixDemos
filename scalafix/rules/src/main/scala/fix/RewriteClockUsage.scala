package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class RewriteClockUsage extends SemanticRule("RewriteClockUsage") {

    def identifyClockUsage(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case t @ Term.Apply(Term.Select(Term.Name("Instant"), Term.Name("now")), List()) => 
            println("Recognized unsafe clock usage")
            Patch.replaceTree(t, "Instant.NEVER")
            // Patch.empty
        /*
        case Term.Apply(fun, args) => 
            fun.symbol.info match {
                case Some(info) => 
                    info.signature match {
                        case method: MethodSignature if method.parameterLists.nonEmpty =>
                            Patch.empty
                        case _ =>
                            Patch.empty
                    }
                case None => Patch.empty
            }
        */
    }
    
    def identifyClockInABlock(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case t @ Term.Block(args) =>
            args.collect {
                case badClockUsage @ Term.Apply(Term.Select(Term.Name("Instant"), Term.Name("now")), List()) => 
                    println("One of statements was a bad clock usage!")
                    Patch.addLeft(t, 
                        """for { now <- zio.Clock.instant } yield """) + Patch.replaceTree(badClockUsage, "now")
                case otherStatement =>
                    println("other statement: " +  otherStatement)
                    Patch.empty
            }.asPatch
            
            // println("Clock in a block")
            // Patch.empty
    }

  override def fix(implicit doc: SemanticDocument): Patch = {
    // println(q"""complete(true, "blah")""".structure)
    println(
        q"""{
        println("Other stuff")
        Instant.now()
        }""".structure)
    doc.tree.collect {
        // identifyClockUsage
        identifyClockInABlock
    }.asPatch
  }
  
}

