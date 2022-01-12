import java.time.Instant

case class UserAction(timestamp: Instant)
object ClockUsage {
    Instant.now()

    def useIt() = for {
      now <- zio.Clock.instant
    } yield {
        UserAction(now)
    }

    def useMultiple() = for {
      now <- zio.Clock.instant
    } yield {
        UserAction(now)
    }
}
