package dev.mizarc.waystonewarps.infrastructure.services.geyser

import com.geysermenu.companion.api.GeyserMenuAPI
import com.geysermenu.companion.api.MenuButton
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger

/**
 * Handles integration with GeyserMenu Companion plugin.
 * When present, registers a Waystone Warps button in the Bedrock menu
 * and provides form-based menus for Bedrock players.
 */
class GeyserMenuIntegration(private val plugin: JavaPlugin) {
    private val logger: Logger = plugin.logger
    private var enabled = false

    fun initialize() {
        if (Bukkit.getPluginManager().getPlugin("GeyserMenuCompanion") == null) {
            logger.info("GeyserMenu Companion not found. Bedrock menu integration disabled.")
            return
        }

        val api = GeyserMenuAPI.getInstance()
        if (api == null) {
            logger.warning("GeyserMenu Companion found but API not available.")
            return
        }

        registerMenuButton(api)
        enabled = true
        logger.info("GeyserMenu Companion integration enabled.")
    }

    fun shutdown() {
        if (!enabled) return
        val api = GeyserMenuAPI.getInstance() ?: return
        api.unregisterButton("waystonewarps-menu")
    }

    fun isBedrockPlayer(playerUuid: UUID): Boolean {
        if (!enabled) return false
        val api = GeyserMenuAPI.getInstance() ?: return false
        return api.isBedrockPlayer(playerUuid)
    }

    fun isBedrockPlayer(player: Player): Boolean {
        return isBedrockPlayer(player.uniqueId)
    }

    fun isEnabled(): Boolean = enabled

    fun getApi(): GeyserMenuAPI? {
        if (!enabled) return null
        return GeyserMenuAPI.getInstance()
    }

    private fun registerMenuButton(api: GeyserMenuAPI) {
        api.registerButton(
            MenuButton.builder()
                .id("waystonewarps-menu")
                .text("Waystone Warps")
                .imagePath("textures/items/compass_item")
                .priority(50)
                .condition { playerObj ->
                    val player = playerObj as? Player ?: return@condition false
                    player.hasPermission("waystonewarps.teleport")
                }
                .onClick { playerObj, _ ->
                    val player = playerObj as? Player ?: return@onClick
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        BedrockWarpMenu(player, api).open()
                    })
                }
                .build()
        )
    }
}
