package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class RewriteConsoleUsage extends SemanticRule("RewriteConsoleUsage") {
    def replaceNow(tree: Tree)(implicit doc: SemanticDocument): Patch =
        tree match {

                case sideEffectingCall @ 
                          Defn.Val(
                            List(),
                            List(Pat.Var(Term.Name(variableName))),
                            None,
                            Term.Apply(Term.Name("readLine"), List())
                        ) => 
                    Patch.replaceTree(sideEffectingCall, s"val $variableName = input")
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
                        case other => replaceNow(child)(doc)
                    }
                }.asPatch
            }

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

    private val newClockNowDeclaration = "input <- zio.Console.readLine"
    def identifyClockInABlock(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case t @ Term.Block(args) =>
            if (args.exists(containsAClock) )
                Patch.addLeft(t, s"""for {\n      ${newClockNowDeclaration}\n    } yield """) + 
                args.map(replaceNow).asPatch
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

