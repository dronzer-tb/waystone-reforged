package dev.mizarc.waystonewarps.infrastructure.services

import dev.mizarc.waystonewarps.application.services.StructureParticleService
import dev.mizarc.waystonewarps.domain.discoveries.DiscoveryRepository
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.domain.whitelist.WhitelistRepository
import dev.mizarc.waystonewarps.infrastructure.scheduling.FoliaScheduler
import dev.mizarc.waystonewarps.infrastructure.services.geyser.BedrockSupport
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/**
 * Renders the ambient particles that mark every waystone.
 *
 * Folia note: the original implementation ran one repeating task *per warp* which then iterated over
 * every online player and read `player.location`. That is illegal on Folia - a task owned by the
 * warp's region is not allowed to touch players owned by other regions. This implementation inverts
 * the loop: one repeating task *per player*, running on that player's own entity scheduler, which
 * walks the (concurrent) registry of active warps. `Player#spawnParticle` only sends a packet to
 * that one player and never touches world state, so it is safe to call for any location from the
 * player's own thread.
 */
class StructureParticleServiceBukkit(private val plugin: JavaPlugin,
                                     private val playerDiscoveryRepository: DiscoveryRepository,
                                     private val whitelistRepository: WhitelistRepository)
    : StructureParticleService, Listener {

    /** Warps currently emitting particles. Written from any region thread, read from all of them. */
    private val activeWarps: MutableMap<UUID, Warp> = ConcurrentHashMap()

    /** One render task per online player, keyed by player id. */
    private val playerTasks: MutableMap<UUID, ScheduledTask> = ConcurrentHashMap()

    override fun spawnParticles(warp: Warp) {
        activeWarps[warp.id] = warp
    }

    override fun removeParticles(warp: Warp) {
        activeWarps.remove(warp.id)
    }

    /** Starts render tasks for everyone already online (plugin enable / reload). */
    fun startForOnlinePlayers() {
        for (player in Bukkit.getOnlinePlayers()) {
            startFor(player)
        }
    }

    /** Cancels every render task (plugin disable). */
    fun stopAll() {
        playerTasks.values.forEach { it.cancel() }
        playerTasks.clear()
        activeWarps.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        startFor(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        playerTasks.remove(event.player.uniqueId)?.cancel()
    }

    private fun startFor(player: Player) {
        val playerId = player.uniqueId
        val task = FoliaScheduler.forEntityTimer(plugin, player, 5L, 5L) { scheduled ->
            if (!player.isOnline) {
                scheduled.cancel()
                playerTasks.remove(playerId, scheduled)
                return@forEntityTimer
            }
            render(player)
        } ?: return
        playerTasks.put(playerId, task)?.cancel()
    }

    private fun render(player: Player) {
        if (activeWarps.isEmpty()) return

        val playerLocation = player.location
        val world = player.world
        val worldId = world.uid
        val range = Bukkit.getServer().viewDistance * 16.0
        val rangeSquared = range * range
        val isBedrock = BedrockSupport.isBedrockPlayer(player)

        for (warp in activeWarps.values) {
            if (warp.worldId != worldId) continue

            val x = warp.position.x + 0.5
            val y = warp.position.y + 0.5
            val z = warp.position.z + 0.5

            val dx = x - playerLocation.x
            val dy = y - playerLocation.y
            val dz = z - playerLocation.z
            if (dx * dx + dy * dy + dz * dz > rangeSquared) continue

            val location = Location(world, x, y, z)
            val discovered = playerDiscoveryRepository.getByWarpAndPlayer(warp.id, player.uniqueId)
            val whitelisted = whitelistRepository.isWhitelisted(warp.id, player.uniqueId)

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
                    val dustColor = if (discovered != null) Color.fromRGB(100, 200, 255)
                                    else Color.fromRGB(255, 200, 50)
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
