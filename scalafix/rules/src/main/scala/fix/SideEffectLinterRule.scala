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

class SideEffectLinterRule extends SyntacticRule("SideEffectLinterRule"){
    override def fix(implicit doc: SyntacticDocument): Patch =
        doc.tree.collect {
            case t @ Term.Apply(Term.Name("println"), List(_)) => 
                Patch.lint(SideEffect(t, ConsoleZ))
            case t @ Term.Apply(Term.Select(Term.Name("Instant"), Term.Name("now")), List()) => 
                Patch.lint(SideEffect(t, ClockZ))

        }.asPatch
  
}


sealed class SideEffectingService(val zioDocLink: String)
case object SystemZ extends SideEffectingService("https://zio.dev/version-1.x/services/system")
case object ClockZ extends SideEffectingService("https://zio.dev/version-1.x/services/clock")
case object ConsoleZ extends SideEffectingService("https://zio.dev/version-1.x/services/console")
case object RandomZ extends SideEffectingService("https://zio.dev/version-1.x/services/random")

case class SideEffect(invocation: Term.Apply, service: SideEffectingService) extends Diagnostic {
  override def position: Position = invocation.pos
  override def message: String =
    s"Try to use ZIO effectful equivalent instead: ${service.zioDocLink}"
}