/*
rule = FutureLinterRule
 */
package fix

import scala.concurrent.Future

object FutureUsage {
  
    Future.successful("blah")/* assert: FutureLinterRule
+   ^^^^^^^^^^^^^^^^^^^^^^^^^
The ZIO type can replace your Future usages in all cases: https://zio.dev/version-1.x/overview/#zio*/

}
