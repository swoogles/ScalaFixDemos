package fix.standard_services

import scala.io.StdIn.readLine

object ConsoleUsage {
  def consoleProgram() = for {
      _ <- zio.Console.printLine("Please enter your name")
      name <- zio.Console.readLine
      x <- zio.ZIO(3)
      _ <- zio.Console.printLine("Hello " + name)
    } yield ()
}
