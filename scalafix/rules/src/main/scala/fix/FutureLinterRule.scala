package fix

import scalafix.v1.SyntacticRule
import scalafix.v1.SyntacticDocument
import scalafix.v1.Patch
import scala.meta.Lit
import scala.meta.inputs.Position
import scalafix.lint.Diagnostic
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

// TODO Update for ZIO2?

case class FutureLintReport(invocation: Term.Apply) extends Diagnostic {
  val url = "https://zio.dev/version-1.x/overview/#zio"
  override def position: Position = invocation.pos
  override def message: String =
    s"The ZIO type can replace your Future usages in all cases: ${url}"
}

class FutureLinterRule extends SyntacticRule("FutureLinterRule"){
    override def fix(implicit doc: SyntacticDocument): Patch = {
        // println(q"""Future.successful(3)""".structure)
        // println(q"""Future{
        //                 val x = 3
        //                 val y = "name"
        //                 name * 3
        //             }""".structure)
        doc.tree.collect {
            case t @ Term.Apply(
                Term.Select(Term.Name("Future"), Term.Name("successful")),
                List(Lit(_))
            ) => 
                Patch.lint(FutureLintReport(t))

        }.asPatch
    }
  
}
