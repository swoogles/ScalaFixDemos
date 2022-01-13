package fix

import scalafix.v1.SemanticRule
import scalafix.v1.SemanticDocument
import scalafix.patch.Patch
import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

class CoffeeChatRule extends SemanticRule("CoffeeChatRule") {

    def noBooleanLiteralParameters(implicit doc: SemanticDocument): PartialFunction[Tree, Patch] = {
        case Term.Apply(fun, args) =>
            args.zipWithIndex.collect { 
                case (t @ Lit.Boolean(_), i) => fun.symbol.info match {
                    case Some(info) => 
                        info.signature match {
                            case method: MethodSignature if method.parameterLists.nonEmpty =>
                                val parameter = method.parameterLists.head(i)
                                val parameterName = parameter.displayName
                                Patch.addLeft(t, s"$parameterName = ")
                            case _ =>
                                // Do nothing, the symbol is not a method with matching signature
                                Patch.empty
                        }
                    case None => Patch.empty
                }
            }.asPatch
        }

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect {
        noBooleanLiteralParameters 
    }.asPatch
  }
  
}
