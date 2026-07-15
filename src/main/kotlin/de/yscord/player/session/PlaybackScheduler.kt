package de.yscord.player.session

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Drives time-based server behaviour:
 *  - every 1s: advance the queue when the current track runs out (kept in sync for all)
 *  - every 5s: persist the live snapshot (incl. position) so a crash/restart resumes here
 */
@Component
class PlaybackScheduler(private val session: PlayerSessionService) {

    @Scheduled(fixedRate = 1_000)
    fun advanceWhenFinished() = session.tick()

    @Scheduled(fixedRate = 5_000)
    fun persistState() = session.persistPeriodic()
}
