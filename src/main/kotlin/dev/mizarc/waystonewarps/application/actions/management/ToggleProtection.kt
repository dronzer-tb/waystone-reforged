package dev.mizarc.waystonewarps.application.actions.management

import dev.mizarc.waystonewarps.application.services.WarpEventPublisher
import dev.mizarc.waystonewarps.domain.warps.WarpRepository
import java.util.*

class ToggleProtection(
    private val warpRepository: WarpRepository,
    private val warpEventPublisher: WarpEventPublisher
) {
    fun execute(playerId: UUID, warpId: UUID, bypassOwnership: Boolean = false): Result<Unit> {
        val warp = warpRepository.getById(warpId) ?: return Result.failure(Exception("Warp not found"))

        if (warp.playerId != playerId && !bypassOwnership) {
            return Result.failure(Exception("Not authorized"))
        }

        val oldWarp = warp.copy()
        warp.isProtected = !warp.isProtected
        warpRepository.update(warp)
        warpEventPublisher.warpModified(oldWarp, warp)
        return Result.success(Unit)
    }
}
