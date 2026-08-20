package dev.mizarc.waystonewarps.infrastructure.services.scheduling

import dev.mizarc.waystonewarps.application.services.scheduling.Task
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

/**
 * Wraps a Folia [ScheduledTask]. The handle is null when the task could not be scheduled at all
 * (for example the target player had already disconnected), in which case cancelling is a no-op.
 */
class TaskBukkit(private val scheduledTask: ScheduledTask?) : Task {
    override fun cancel() {
        scheduledTask?.cancel()
    }
}
