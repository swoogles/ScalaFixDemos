/*
rule = SideEffectLinterRule
 */
package fix

import java.time.Instant

object SideEffectLinting {
    println("Other stuff")/* assert: SideEffectLinterRule
+   ^^^^^^^^^^^^^^^^^^^^^^
Try to use ZIO effectful equivalent instead: https://zio.dev/version-1.x/services/console*/

    Instant.now()/* assert: SideEffectLinterRule
+   ^^^^^^^^^^^^^
Try to use ZIO effectful equivalent instead: https://zio.dev/version-1.x/services/clock*/

}
