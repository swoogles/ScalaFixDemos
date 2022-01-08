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

case class SideEffect(invocation: Term.Apply) extends Diagnostic {
  override def position: Position = invocation.pos
  override def message: String =
    s"Try to use ZIO equivalents for: $invocation"
}

class SideEffectLinterRule extends SyntacticRule("SideEffectLinterRule"){
    override def fix(implicit doc: SyntacticDocument): Patch = 
        doc.tree.collect {
            case t @ Term.Apply(Term.Name("println"), List(Lit.String(_))) => 
                println("ZZZ")
                Patch.lint(SideEffect(t))
        }.asPatch
  
}
