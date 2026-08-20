package dev.mizarc.waystonewarps.infrastructure.persistence.playerstate

import dev.mizarc.waystonewarps.domain.playerstate.PlayerState
import dev.mizarc.waystonewarps.domain.playerstate.PlayerStateRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerStateRepositoryMemory: PlayerStateRepository {
    // Players join, leave and act from several region threads at once.
    private val playerStates: MutableMap<UUID, PlayerState> = ConcurrentHashMap()

    override fun isExists(playerId: UUID): Boolean {
        return playerStates.containsKey(playerId)
    }

    override fun getAll(): Set<PlayerState> {
        return playerStates.values.toSet()
    }

    override fun getById(id: UUID): PlayerState? {
        return playerStates[id]
    }

    override fun add(playerState: PlayerState) {
        playerStates[playerState.playerId] = playerState
    }

    override fun remove(playerId: UUID) {
        playerStates.remove(playerId)
    }
}