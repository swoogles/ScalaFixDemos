/*
rule = SideEffectLinterRule
 */
package fix

import java.time.Instant

object SideEffectLinting {
    println("Other stuff")/* assert: SideEffectLinterRule
+   ^^^^^^^^^^^^^^^^^^^^^^
Try to use ZIO equivalents for: println("Other stuff")*/

    Instant.now()
}
