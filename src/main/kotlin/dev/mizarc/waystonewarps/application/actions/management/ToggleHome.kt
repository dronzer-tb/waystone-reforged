package dev.mizarc.waystonewarps.application.actions.management

import dev.mizarc.waystonewarps.application.services.HologramService
import dev.mizarc.waystonewarps.application.services.WarpEventPublisher
import dev.mizarc.waystonewarps.domain.warps.WarpRepository
import java.util.*

class ToggleHome(
    private val warpRepository: WarpRepository,
    private val hologramService: HologramService,
    private val warpEventPublisher: WarpEventPublisher
) {
    fun execute(playerId: UUID, warpId: UUID, bypassOwnership: Boolean = false): Result<Unit> {
        val warp = warpRepository.getById(warpId) ?: return Result.failure(Exception("Warp not found"))

        if (warp.playerId != playerId && !bypassOwnership) {
            return Result.failure(Exception("Not authorized"))
        }

        val oldWarp = warp.copy()

        if (!warp.isHome) {
            // Unset any existing home for this player first
            val existingHome = warpRepository.getHomeWarp(warp.playerId)
            if (existingHome != null) {
                val oldExisting = existingHome.copy()
                existingHome.isHome = false
                warpRepository.update(existingHome)
                hologramService.updateHologram(existingHome)
                warpEventPublisher.warpModified(oldExisting, existingHome)
            }
        }

        warp.isHome = !warp.isHome
        warpRepository.update(warp)
        hologramService.updateHologram(warp)
        warpEventPublisher.warpModified(oldWarp, warp)
        return Result.success(Unit)
    }
}
