/*
rule = RewriteClockUsage
 */
import java.time.Instant

case class UserAction(timestamp: Instant)
object ClockUsage {
    Instant.now()

    def useIt() = {
        UserAction(Instant.now())
    }
}
