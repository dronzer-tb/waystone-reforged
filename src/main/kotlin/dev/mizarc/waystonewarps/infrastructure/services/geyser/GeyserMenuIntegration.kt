package dev.mizarc.waystonewarps.infrastructure.services.geyser

import dev.mizarc.waystonewarps.application.actions.management.GetHomeWarp
import dev.mizarc.waystonewarps.application.actions.teleport.TeleportPlayer
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.reflect.Method
import java.util.UUID
import java.util.logging.Logger

/**
 * Handles GeyserMenu Companion integration.
 * Registers "Warp" and "Home" buttons in the Bedrock main menu.
 */
class GeyserMenuIntegration(private val plugin: JavaPlugin) : KoinComponent {
    private val logger: Logger = plugin.logger
    private var geyserMenuApi: Any? = null
    private var apiClass: Class<*>? = null
    private var isAvailable = false
    private var buttonsRegistered = false

    private val getHomeWarp: GetHomeWarp by inject()
    private val teleportPlayer: TeleportPlayer by inject()
    private val localizationProvider: LocalizationProvider by inject()

    companion object {
        private const val WARP_BUTTON_ID = "waystonewarps-warp"
        private const val HOME_BUTTON_ID = "waystonewarps-home"
    }

    private fun findMethod(obj: Any, name: String, vararg paramTypes: Class<*>): Method {
        for (iface in obj.javaClass.interfaces) {
            try { return iface.getMethod(name, *paramTypes) } catch (_: NoSuchMethodException) {}
        }
        var cls: Class<*>? = obj.javaClass.superclass
        while (cls != null && cls != Any::class.java) {
            try { return cls.getMethod(name, *paramTypes) } catch (_: NoSuchMethodException) {}
            cls = cls.superclass
        }
        return obj.javaClass.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
    }

    fun initialize(): Boolean {
        val formsAvailable = BedrockSupport.initialize(plugin)
        if (!formsAvailable) {
            logger.info("[GeyserMenu] BedrockSupport not available.")
            return false
        }

        try {
            apiClass = Class.forName("com.geysermenu.companion.api.GeyserMenuAPI")
            val getInstanceMethod = apiClass!!.getMethod("getInstance")
            geyserMenuApi = getInstanceMethod.invoke(null)

            if (geyserMenuApi != null) {
                isAvailable = true
                logger.info("[GeyserMenu] API available - registering buttons with delay")

                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    registerButtons()
                }, 40L)

                return true
            } else {
                logger.info("[GeyserMenu] API returned null")
            }
        } catch (e: ClassNotFoundException) {
            logger.info("[GeyserMenu] GeyserMenuCompanion not found")
        } catch (e: Exception) {
            logger.warning("[GeyserMenu] Failed to initialize: ${e.message}")
            e.printStackTrace()
        }
        return false
    }

    private fun registerButtons() {
        if (!isAvailable || geyserMenuApi == null || buttonsRegistered) return
        registerButton(
            id = WARP_BUTTON_ID,
            text = "§l§8Warp",
            imagePath = "textures/blocks/lodestone_top",
            priority = 20
        ) { player ->
            BedrockWarpMenu(player).open()
        }
        registerButton(
            id = HOME_BUTTON_ID,
            text = "§l§8Home",
            imagePath = "textures/items/bed_red",
            priority = 21
        ) { player ->
            val homeWarp = getHomeWarp.execute(player.uniqueId)
            if (homeWarp == null) {
                player.sendMessage("§cYou don't have a home waystone set.")
                return@registerButton
            }
            teleportPlayer.execute(
                player.uniqueId, homeWarp,
                onPending = { player.sendMessage("§eTeleporting to home...") },
                onSuccess = { player.sendMessage("§aTeleported to home!") },
                onFailure = { player.sendMessage("§cTeleportation failed.") },
                onInsufficientFunds = { player.sendMessage("§cNot enough funds to teleport home.") },
                onWorldNotFound = { player.sendMessage("§cWorld not found.") },
                onLocked = { player.sendMessage("§cHome waystone is locked.") },
                onCanceled = { player.sendMessage("§eTeleportation cancelled.") },
                onPermissionDenied = { player.sendMessage("§cNo teleport permission.") },
                onInterworldPermissionDenied = { player.sendMessage("§cNo cross-world teleport permission.") }
            )
        }
        buttonsRegistered = true
        logger.info("[GeyserMenu] Warp and Home buttons registered successfully")
    }

    private fun registerButton(id: String, text: String, imagePath: String, priority: Int, onClick: (org.bukkit.entity.Player) -> Unit) {
        try {
            val menuButtonClass = Class.forName("com.geysermenu.companion.api.MenuButton")
            val builderMethod = menuButtonClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            val bt = builderMethod.returnType

            bt.getMethod("id", String::class.java).invoke(builder, id)
            bt.getMethod("text", String::class.java).invoke(builder, text)
            bt.getMethod("imagePath", String::class.java).invoke(builder, imagePath)
            bt.getMethod("priority", Int::class.javaPrimitiveType).invoke(builder, priority)

            val pluginRef = plugin
            val loggerRef = logger
            val clickHandler = java.util.function.BiConsumer<Any, Any?> { bedrockPlayer, _ ->
                try {
                    val uuid = findMethod(bedrockPlayer, "getUuid").invoke(bedrockPlayer) as UUID
                    Bukkit.getScheduler().runTask(pluginRef, Runnable {
                        val bukkitPlayer = Bukkit.getPlayer(uuid)
                        if (bukkitPlayer != null) {
                            onClick(bukkitPlayer)
                        } else {
                            loggerRef.warning("[GeyserMenu] Could not find Bukkit player for UUID: $uuid")
                        }
                    })
                } catch (e: Exception) {
                    loggerRef.warning("[GeyserMenu] Error handling button click: ${e.message}")
                }
            }
            bt.getMethod("onClick", java.util.function.BiConsumer::class.java).invoke(builder, clickHandler)

            val button = bt.getMethod("build").invoke(builder)
            apiClass!!.getMethod("registerButton", menuButtonClass).invoke(geyserMenuApi, button)
            logger.info("[GeyserMenu] Button '$id' registered")
        } catch (e: Exception) {
            logger.warning("[GeyserMenu] Failed to register button '$id': ${e.message}")
        }
    }

    fun shutdown() {
        BedrockSupport.shutdown()
        if (!isAvailable || geyserMenuApi == null) return

        try {
            val unregisterMethod = apiClass!!.getMethod("unregisterButton", String::class.java)
            if (buttonsRegistered) {
                unregisterMethod.invoke(geyserMenuApi, WARP_BUTTON_ID)
                unregisterMethod.invoke(geyserMenuApi, HOME_BUTTON_ID)
                buttonsRegistered = false
                logger.info("[GeyserMenu] Buttons unregistered")
            }
        } catch (_: Exception) {}
    }
}
