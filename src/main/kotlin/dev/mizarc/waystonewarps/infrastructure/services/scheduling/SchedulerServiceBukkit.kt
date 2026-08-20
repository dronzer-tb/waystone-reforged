package dev.mizarc.waystonewarps.infrastructure.services.scheduling

import dev.mizarc.waystonewarps.application.services.scheduling.SchedulerService
import dev.mizarc.waystonewarps.application.services.scheduling.Task
import dev.mizarc.waystonewarps.infrastructure.scheduling.FoliaScheduler
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Folia-aware scheduler. Bukkit's shared scheduler does not exist on Folia, so player-bound work is
 * dispatched onto the owning player's entity scheduler and everything else onto the global region.
 */
class SchedulerServiceBukkit(private val plugin: Plugin): SchedulerService {

    override fun schedule(delayTicks: Long, task: () -> Unit): Task {
        return TaskBukkit(FoliaScheduler.globalLater(plugin, delayTicks) { task() })
    }

    override fun scheduleForPlayer(playerId: UUID, delayTicks: Long, task: () -> Unit): Task {
        val player = Bukkit.getPlayer(playerId)
            ?: return TaskBukkit(null) // Player left; nothing to schedule and nothing to cancel.
        return TaskBukkit(FoliaScheduler.forEntityLater(plugin, player, delayTicks) { task() })
    }
}
