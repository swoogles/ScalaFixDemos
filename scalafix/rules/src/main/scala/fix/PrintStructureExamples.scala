package fix

import scalafix.v1._
import scala.annotation.tailrec
import scala.meta._

object PrintStructureExamples extends App {
    println(q"val x = 3".structure)
    // println(q"import zio.ZIO".structure)
    // println(q"Instant.now()".structure)
  
}
