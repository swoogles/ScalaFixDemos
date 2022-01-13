package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class RewriteConsoleUsage extends SemanticRule("RewriteConsoleUsage") {

    private val indentation = "      "
    def replace(tree: Tree, containingBlock: Term.Block)(implicit doc: SemanticDocument): Patch =
        tree match {
                case printEffect @ Term.Apply(
                    Term.Name("println"),
                    // TODO Switch to this version
                    // badPrintLn(_),
                    List(
                        anyArguments
                    )
                ) => 
                    Patch.addLeft(containingBlock, s"\n${indentation}_ <- zio.Console.printLine($anyArguments)") +
                    Patch.replaceTree(printEffect, "")

                case sideEffectingCall @ 
                          Defn.Val(
                            List(),
                            List(Pat.Var(Term.Name(variableName))),
                            None,
                            Term.Apply(badReadLn(_), List())
                        ) => 

                    Patch.addLeft(containingBlock, s"""\n${indentation}$variableName <- zio.Console.readLine""") +
                    Patch.replaceTree(sideEffectingCall, "")
                
                case sideEffectingCall @ 
                          Defn.Val(
                            List(),
                            List(Pat.Var(Term.Name(variableName))),
                            None,
                            rhs
                        ) => 
                    Patch.addLeft(containingBlock, s"\n$indentation$variableName <- zio.ZIO($rhs)") +
                    Patch.replaceTree(sideEffectingCall, "")
                case _ => tree.children.map { child =>
                            if (containsAClock(child))
                                replace(child, containingBlock)(doc)
                            else
                                Patch.empty
                }.asPatch
            }

    val badPrintLn = SymbolMatcher.normalized("scala.Predef.println")
    val badReadLn = SymbolMatcher.normalized("scala.io.StdIn.readLine")

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
                case printEffect @ Term.Apply(
                    Term.Name("println"),
                    List(
                        anyArguments
                    )
                ) => true
                case other =>
                    other.children.exists(containsAClock)

        }

    def identifyClockInABlock(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case t @ Term.Block(args) =>
            if (args.exists(containsAClock) )
                Patch.addLeft(t, s"""for {""") + 
                    args.map(replace(_, t)).asPatch + 
                    Patch.addLeft(t, s"""\n    } yield ()""")  +
                    Patch.replaceTree(t, "")
            else  args.collect(identifyClockInABlock).asPatch
    }

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect {
        identifyClockInABlock
    }.asPatch
  }
  
}

