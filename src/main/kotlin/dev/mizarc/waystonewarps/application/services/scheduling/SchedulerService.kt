package dev.mizarc.waystonewarps.application.services.scheduling

import java.util.UUID

/**
 * Schedules an event to run after an X amount of time
 */
interface SchedulerService {
    /**
     * Schedules work that is not tied to any particular player or location.
     */
    fun schedule(delayTicks: Long, task: () -> Unit): Task

    /**
     * Schedules work that touches the given player. On a regionised server the task is executed on
     * whichever thread owns that player at the time, and follows the player between regions.
     */
    fun scheduleForPlayer(playerId: UUID, delayTicks: Long, task: () -> Unit): Task
}
