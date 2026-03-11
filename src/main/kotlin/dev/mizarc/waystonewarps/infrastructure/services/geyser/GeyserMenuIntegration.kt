package dev.mizarc.waystonewarps.infrastructure.services.geyser

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

/**
 * Handles GeyserMenu Companion integration. Uses pure reflection (no compile-time dependency).
 * Registers a "Waystone Warps" button in the Bedrock main menu.
 */
class GeyserMenuIntegration(private val plugin: JavaPlugin) {
    private val logger: Logger = Logger.getLogger("GeyserMenuIntegration")
    private var registered = false

    fun initialize(): Boolean {
        val available = BedrockSupport.initialize(plugin)
        if (!available) {
            logger.info("GeyserMenu integration not available.")
            return false
        }

        registerMenuButton()
        return true
    }

    private fun registerMenuButton() {
        BedrockSupport.registerButton(
            id = "waystone_warps_menu",
            text = "Waystone Warps",
            imagePath = "textures/blocks/lodestone_top",
            priority = 10,
            permissionCheck = { player ->
                player.hasPermission("waystonewarps.use")
            },
            onClick = { player ->
                player.performCommand("ww")
            }
        )
        registered = true
        logger.info("Registered 'Waystone Warps' button in GeyserMenu.")
    }

    fun shutdown() {
        if (registered) {
            BedrockSupport.unregisterButton("waystone_warps_menu")
            registered = false
        }
    }
}
