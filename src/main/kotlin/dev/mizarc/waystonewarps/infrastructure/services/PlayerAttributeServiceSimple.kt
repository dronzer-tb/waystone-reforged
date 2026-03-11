package dev.mizarc.waystonewarps.infrastructure.services

import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import org.bukkit.Bukkit
import java.util.*

class PlayerAttributeServiceSimple(private val configService: ConfigService): PlayerAttributeService {

    companion object {
        private const val LIMIT_PERMISSION_PREFIX = "waystonewarps.limit."
    }

    override fun getWarpLimit(playerId: UUID): Int {
        // Check for permission-based limit (waystonewarps.limit.<n>)
        // Uses the highest matching value
        val player = Bukkit.getPlayer(playerId)
        if (player != null) {
            var highest = -1
            for (perm in player.effectivePermissions) {
                val name = perm.permission.lowercase()
                if (name.startsWith(LIMIT_PERMISSION_PREFIX)) {
                    val num = name.removePrefix(LIMIT_PERMISSION_PREFIX).toIntOrNull()
                    if (num != null && num > highest) highest = num
                }
            }
            if (highest >= 0) return highest
        }
        return configService.getWarpLimit()
    }

    override fun getTeleportCost(playerId: UUID): Double {
        return configService.getTeleportCostAmount()
    }

    override fun getTeleportTimer(playerId: UUID): Int {
        return configService.getTeleportTimer()
    }
}