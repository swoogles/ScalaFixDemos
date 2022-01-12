package fix

import scala.util.Random

object RandomUsage {
    def useRandom() = for {
      randInt <- zio.Random.int
    } yield {
        randInt
    }
}
