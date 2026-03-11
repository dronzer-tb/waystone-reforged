package dev.mizarc.waystonewarps.infrastructure.services.geyser

import com.geysermenu.companion.api.GeyserMenuAPI
import com.geysermenu.companion.api.MenuButton
import dev.mizarc.waystonewarps.application.services.ConfigService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Logger

/**
 * Handles integration with GeyserMenu Companion plugin and Floodgate.
 * When present, registers a Waystone Warps button in the Bedrock menu
 * and provides form-based menus for Bedrock players.
 */
class GeyserMenuIntegration(
    private val plugin: JavaPlugin,
    private val configService: ConfigService
) {
    private val logger: Logger = plugin.logger
    private var geyserMenuEnabled = false
    private var floodgateAvailable = false

    fun initialize() {
        if (!configService.isGeyserEnabled()) {
            logger.info("Geyser integration disabled in config.")
            return
        }

        // Check for Floodgate (used for Bedrock player detection)
        floodgateAvailable = try {
            Bukkit.getPluginManager().getPlugin("floodgate") != null
                && Class.forName("org.geysermc.floodgate.api.FloodgateApi") != null
        } catch (_: ClassNotFoundException) {
            false
        }

        if (floodgateAvailable) {
            logger.info("Floodgate detected. Bedrock player detection enabled.")
        }

        // Check for GeyserMenu Companion (used for form menus)
        if (Bukkit.getPluginManager().getPlugin("GeyserMenuCompanion") != null) {
            val api = GeyserMenuAPI.getInstance()
            if (api != null) {
                registerMenuButton(api)
                geyserMenuEnabled = true
                logger.info("GeyserMenu Companion integration enabled.")
            } else {
                logger.warning("GeyserMenu Companion found but API not available.")
            }
        } else {
            logger.info("GeyserMenu Companion not found. Menu button registration skipped.")
        }

        if (!floodgateAvailable && !geyserMenuEnabled) {
            logger.info("Neither Floodgate nor GeyserMenu Companion found. Bedrock integration fully disabled.")
        }
    }

    fun shutdown() {
        if (!geyserMenuEnabled) return
        val api = GeyserMenuAPI.getInstance() ?: return
        api.unregisterButton("waystonewarps-menu")
    }

    fun isBedrockPlayer(playerUuid: UUID): Boolean {
        // Try Floodgate first (most reliable)
        if (floodgateAvailable) {
            try {
                val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
                if (floodgateApi.isFloodgatePlayer(playerUuid)) return true
            } catch (_: Exception) {
                // Floodgate API call failed, fall through
            }
        }

        // Fallback to GeyserMenu API
        if (geyserMenuEnabled) {
            val api = GeyserMenuAPI.getInstance()
            if (api != null && api.isBedrockPlayer(playerUuid)) return true
        }

        return false
    }

    fun isBedrockPlayer(player: Player): Boolean {
        return isBedrockPlayer(player.uniqueId)
    }

    fun isEnabled(): Boolean = floodgateAvailable || geyserMenuEnabled

    fun getApi(): GeyserMenuAPI? {
        if (!geyserMenuEnabled) return null
        return GeyserMenuAPI.getInstance()
    }

    private fun registerMenuButton(api: GeyserMenuAPI) {
        api.registerButton(
            MenuButton.builder()
                .id("waystonewarps-menu")
                .text(configService.getGeyserMenuButtonText())
                .imagePath(configService.getGeyserMenuButtonImage())
                .priority(configService.getGeyserMenuButtonPriority())
                .condition { playerObj ->
                    val player = playerObj as? Player ?: return@condition false
                    player.hasPermission("waystonewarps.teleport")
                }
                .onClick { playerObj, _ ->
                    val player = playerObj as? Player ?: return@onClick
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        player.performCommand("ww")
                    })
                }
                .build()
        )
    }
}