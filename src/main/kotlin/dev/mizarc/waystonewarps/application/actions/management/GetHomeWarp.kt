package dev.mizarc.waystonewarps.application.actions.management

import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.domain.warps.WarpRepository
import java.util.*

class GetHomeWarp(private val warpRepository: WarpRepository) {
    fun execute(playerId: UUID): Warp? {
        return warpRepository.getHomeWarp(playerId)
    }
}
