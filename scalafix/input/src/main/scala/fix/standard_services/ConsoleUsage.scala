/*
rule = RewriteConsoleUsage
 */
package fix.standard_services

import scala.io.StdIn.readLine

object ConsoleUsage {
  def consoleProgram() = {
    println("Please enter your name")
    val name = readLine()
    println("Hello " + name)
  }
}
