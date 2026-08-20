package dev.mizarc.waystonewarps.infrastructure.services

import dev.mizarc.waystonewarps.application.services.MovementMonitorService
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MovementMonitorServiceBukkit : MovementMonitorService {
    // Region threads register and fire movement callbacks concurrently on Folia.
    private val monitoredPlayers = ConcurrentHashMap<UUID, () -> Unit>()

    override fun monitorPlayerMovement(playerId: UUID, onMove: () -> Unit) {
        monitoredPlayers[playerId] = onMove
    }

    override fun stopMonitoringPlayer(playerId: UUID) {
        monitoredPlayers.remove(playerId)
    }

    override fun logPlayerMovement(playerId: UUID) {
        // Atomic take-and-clear so two movement events cannot both fire the same callback.
        val onMove = monitoredPlayers.remove(playerId) ?: return
        onMove.invoke()
    }
}
