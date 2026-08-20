package dev.mizarc.waystonewarps.infrastructure.services

import com.destroystokyo.paper.ParticleBuilder
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import dev.mizarc.waystonewarps.application.services.PlayerParticleService
import dev.mizarc.waystonewarps.infrastructure.scheduling.FoliaScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class PlayerParticleServiceBukkit(private val plugin: JavaPlugin,
                                  private val playerAttributeService: PlayerAttributeService): PlayerParticleService {
    // Reachable from every region thread (any player can start/stop a charge-up), so it has to be concurrent.
    private val activeParticles: MutableMap<UUID, ScheduledTask> = ConcurrentHashMap()

    override fun spawnPreParticles(playerId: UUID) {
        val player = Bukkit.getPlayer(playerId) ?: return

        // Folia: bound to the player's entity scheduler so the ticks always run on whichever thread
        // currently owns the player, and stop automatically when the player is removed.
        var teleportTime = playerAttributeService.getTeleportTimer(playerId) * 20
        val task = FoliaScheduler.forEntityTimer(plugin, player, 1L, 1L) { scheduled ->
            if (!player.isOnline) {
                scheduled.cancel()
                activeParticles.remove(playerId, scheduled)
                return@forEntityTimer
            }

            teleportTime -= 1
            if (teleportTime == 80) {
                player.playSound(player.location, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 1.0f, 1.0f)
            }

            player.spawnParticle(Particle.PORTAL, player.location, 1)
        } ?: return

        activeParticles.put(playerId, task)?.cancel()
    }

    override fun spawnPostParticles(playerId: UUID) {
        val player = Bukkit.getPlayer(playerId) ?: return

        // The teleport itself is asynchronous on Folia, so the arrival effects are deferred onto the
        // player's own scheduler. Entity tasks travel with the player, so by the time this runs the
        // player is on the destination region and player.location is the arrival location.
        FoliaScheduler.forEntityLater(plugin, player, 5L) {
            if (!player.isOnline) return@forEntityLater
            val playerLocation = player.location
            ParticleBuilder(Particle.REVERSE_PORTAL)
                .location(playerLocation)
                .offset(0.5, 1.0, 0.5)
                .count(100)
                .receivers(player)
                .spawn()

            player.playSound(playerLocation, Sound.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f)
        }
    }

    override fun removeParticles(playerId: UUID) {
        activeParticles.remove(playerId)?.cancel()
    }
}
