package dev.mizarc.waystonewarps.infrastructure.services

import dev.mizarc.waystonewarps.application.services.StructureParticleService
import dev.mizarc.waystonewarps.domain.discoveries.DiscoveryRepository
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.domain.whitelist.WhitelistRepository
import dev.mizarc.waystonewarps.infrastructure.mappers.toLocation
import dev.mizarc.waystonewarps.infrastructure.services.geyser.BedrockSupport
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*


class StructureParticleServiceBukkit(private val plugin: JavaPlugin,
                                     private val playerDiscoveryRepository: DiscoveryRepository,
                                     private val whitelistRepository: WhitelistRepository): StructureParticleService {
    private val activeParticles: MutableMap<UUID, BukkitTask> = mutableMapOf()

    override fun spawnParticles(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)
        location.x += 0.5
        location.y += 0.5
        location.z += 0.5

        val particles = object : BukkitRunnable() {
            override fun run() {
                for (player in Bukkit.getOnlinePlayers()) {
                    if (player.location.world == location.world && player.location.distance(location)
                        <= Bukkit.getServer().viewDistance * 16
                    ) {
                        val discovered = playerDiscoveryRepository.getByWarpAndPlayer(warp.id, player.uniqueId)
                        val whitelisted = whitelistRepository.isWhitelisted(warp.id, player.uniqueId)
                        val isBedrock = BedrockSupport.isBedrockPlayer(player)

                        if (warp.playerId == player.uniqueId) {
                            if (isBedrock) {
                                player.spawnParticle(Particle.DUST, location, 1, 0.5, 0.5, 0.5, 0.0,
                                    Particle.DustOptions(Color.fromRGB(0, 200, 0), 1.0f))
                            } else {
                                player.spawnParticle(Particle.HAPPY_VILLAGER, location, 1, 0.5, 0.5, 0.5)
                            }
                        } else if (warp.isLocked && !whitelisted) {
                            if (isBedrock) {
                                player.spawnParticle(Particle.DUST, location, 1, 0.5, 0.5, 0.5, 0.0,
                                    Particle.DustOptions(Color.fromRGB(255, 80, 80), 1.0f))
                            } else {
                                player.spawnParticle(Particle.WAX_ON, location, 1, 0.5, 0.5, 0.5)
                            }
                        } else {
                            if (isBedrock) {
                                val dustColor = if (discovered != null) Color.fromRGB(100, 200, 255) else Color.fromRGB(255, 200, 50)
                                player.spawnParticle(Particle.DUST, location, 1, 0.5, 0.5, 0.5, 0.0,
                                    Particle.DustOptions(dustColor, 1.0f))
                            } else {
                                val particle = if (discovered != null) Particle.SCRAPE else Particle.WAX_OFF
                                player.spawnParticle(particle, location, 1, 0.5, 0.5, 0.5)
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L)
        activeParticles.put(warp.id, particles)
    }

    override fun removeParticles(warp: Warp) {
        val particles = activeParticles[warp.id] ?: return
        particles.cancel()
        activeParticles.remove(warp.id)
    }
}