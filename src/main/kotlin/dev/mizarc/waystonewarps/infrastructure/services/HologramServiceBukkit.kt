package dev.mizarc.waystonewarps.infrastructure.services

import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.HologramService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.mappers.toLocation
import dev.mizarc.waystonewarps.infrastructure.scheduling.FoliaScheduler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.Plugin

/**
 * Folia note: text displays are entities living in the warp's region, so both spawning and removing
 * them is dispatched onto the thread owning that location, and the removal scan is bounded to the
 * warp's immediate surroundings instead of walking every entity in the world.
 */
class HologramServiceBukkit(private val plugin: Plugin,
                            private val configService: ConfigService): HologramService {

    private val hologramSearchRadius = 4.0

    override fun spawnHologram(warp: Warp) {
        if (!configService.hologramsEnabled()) return
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)

        // Create the display component consisting of a name and coordinates
        val nameComponent = if (warp.isProtected) {
            Component.text()
                .append(Component.text("Protected : ", NamedTextColor.DARK_BLUE).decorate(TextDecoration.BOLD))
                .append(Component.text(warp.name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .build()
        } else {
            Component.text(warp.name)
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        }
        val coordinatesComponent = Component.text("X: ${warp.position.x}  Y: ${warp.position.y}  Z: ${warp.position.z}")
            .color(NamedTextColor.GRAY)
        val componentsToJoin = mutableListOf<Component>(nameComponent, coordinatesComponent)

        // Add home indicator if this warp is set as home
        if (warp.isHome) {
            val ownerName = Bukkit.getOfflinePlayer(warp.playerId).name ?: "Unknown"
            val homeComponent = Component.text()
                .append(Component.text("Home: ", NamedTextColor.GREEN))
                .append(Component.text(ownerName, NamedTextColor.LIGHT_PURPLE))
                .build()
            componentsToJoin.add(1, homeComponent)
        }

        val combinedText = Component.join(
            JoinConfiguration.separator(Component.newline()), componentsToJoin)

        FoliaScheduler.atRegion(plugin, location) {
            // Idempotent: clear any leftover hologram for this warp before spawning a fresh one.
            removeHologramAt(warp, location)
            val display: TextDisplay = world.spawn(location.clone().add(0.5, 1.5, 0.5), TextDisplay::class.java) { entity ->
                entity.text(combinedText)
                entity.billboard = Display.Billboard.VERTICAL
            }
            display.customName(Component.text((warp.id.toString())))
        }
    }

    override fun updateHologram(warp: Warp) {
        // spawnHologram already clears the previous hologram inside the region task, which keeps the
        // remove-then-spawn pair atomic with respect to the owning thread.
        spawnHologram(warp)
    }

    override fun removeHologram(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)
        FoliaScheduler.atRegion(plugin, location) {
            removeHologramAt(warp, location)
        }
    }

    /**
     * Removes this warp's hologram immediately on the calling thread. Only safe when the caller owns
     * the region, or during shutdown when regions are no longer ticking.
     */
    fun removeHologramImmediately(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        removeHologramAt(warp, warp.position.toLocation(world))
    }

    /** Must be called from the thread owning [location]. */
    private fun removeHologramAt(warp: Warp, location: Location) {
        val nearby = try {
            location.getNearbyEntitiesByType(TextDisplay::class.java, hologramSearchRadius)
        } catch (ex: Exception) {
            plugin.logger.warning("Could not scan for waystone holograms at ${warp.position}: ${ex.message}")
            return
        }

        for (entity in nearby) {
            val customName = entity.customName() ?: continue
            if (customName is TextComponent && customName.content() == warp.id.toString()) {
                entity.remove()
            }
        }
    }
}
