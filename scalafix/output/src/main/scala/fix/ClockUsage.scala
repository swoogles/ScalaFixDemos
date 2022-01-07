import java.time.Instant
object ClockUsage {
    Instant.now()

    for { now <- zio.Clock.instant } yield {
        println("Other stuff")
        now
        val x = 3
    }
}
