/*
rule = RewriteRandomUsage
 */
package fix

import scala.util.Random

object RandomUsage {
    def useRandom() = {
        Random.nextInt()
    }
}
