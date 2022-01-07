/*
rule = RewriteClockUsage
 */
import java.time.Instant
object ClockUsage {
    Instant.now()

    {
        println("Other stuff")
        Instant.now()
        val x = 3
    }
}
