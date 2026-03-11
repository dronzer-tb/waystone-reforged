package dev.mizarc.waystonewarps.infrastructure.services.geyser

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.util.UUID
import java.util.logging.Logger

/**
 * Handles GeyserMenu Companion integration.
 * Registers a "Waystone Warps" button in the Bedrock main menu.
 * Uses pure reflection (no compile-time dependency) with proper
 * BedrockPlayer UUID extraction matching the GeyserMenu API pattern.
 */
class GeyserMenuIntegration(private val plugin: JavaPlugin) {
    private val logger: Logger = plugin.logger
    private var geyserMenuApi: Any? = null
    private var apiClass: Class<*>? = null
    private var isAvailable = false
    private var warpButtonRegistered = false

    companion object {
        private const val WARP_BUTTON_ID = "waystonewarps-warp"
    }

    /**
     * Finds a method on an object by searching interfaces and superclasses first
     * (public API types) to avoid IllegalAccessException on private inner classes.
     */
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
        // First initialize BedrockSupport (forms engine)
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

                // Delay button registration by 40 ticks (2s) so GeyserMenu is fully ready
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    registerWarpButton()
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

    /**
     * Registers the main "Warp" button in GeyserMenu.
     * Uses the MenuButton.builder() pattern with proper BiConsumer<BedrockPlayer, Session>.
     */
    private fun registerWarpButton() {
        if (!isAvailable || geyserMenuApi == null || warpButtonRegistered) return

        try {
            val menuButtonClass = Class.forName("com.geysermenu.companion.api.MenuButton")

            // Use MenuButton.builder() — get method return type for public interface
            val builderMethod = menuButtonClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            val builderType = builderMethod.returnType

            // Set button properties through public type
            val idMethod = builderType.getMethod("id", String::class.java)
            val textMethod = builderType.getMethod("text", String::class.java)
            val imagePathMethod = builderType.getMethod("imagePath", String::class.java)
            val priorityMethod = builderType.getMethod("priority", Int::class.javaPrimitiveType)
            val onClickMethod = builderType.getMethod("onClick", java.util.function.BiConsumer::class.java)
            val buildMethod = builderType.getMethod("build")

            idMethod.invoke(builder, WARP_BUTTON_ID)
            textMethod.invoke(builder, "§5Waystone Warps")
            imagePathMethod.invoke(builder, "textures/blocks/lodestone_top")
            priorityMethod.invoke(builder, 20)

            // onClick handler receives BedrockPlayer and session — extract UUID via reflection
            val pluginRef = plugin
            val loggerRef = logger
            val clickHandler = java.util.function.BiConsumer<Any, Any?> { bedrockPlayer, _ ->
                try {
                    val getUuidMethod = findMethod(bedrockPlayer, "getUuid")
                    val uuid = getUuidMethod.invoke(bedrockPlayer) as UUID

                    Bukkit.getScheduler().runTask(pluginRef, Runnable {
                        val bukkitPlayer = Bukkit.getPlayer(uuid)
                        if (bukkitPlayer != null) {
                            bukkitPlayer.performCommand("ww")
                        } else {
                            loggerRef.warning("[GeyserMenu] Could not find Bukkit player for UUID: $uuid")
                        }
                    })
                } catch (e: Exception) {
                    loggerRef.warning("[GeyserMenu] Error handling button click: ${e.message}")
                    e.printStackTrace()
                }
            }
            onClickMethod.invoke(builder, clickHandler)

            val button = buildMethod.invoke(builder)

            val registerMethod = apiClass!!.getMethod("registerButton", menuButtonClass)
            registerMethod.invoke(geyserMenuApi, button)

            warpButtonRegistered = true
            logger.info("[GeyserMenu] Warp button registered successfully")
        } catch (e: Exception) {
            logger.warning("[GeyserMenu] Failed to register Warp button: ${e.message}")
            e.printStackTrace()
        }
    }

    fun shutdown() {
        BedrockSupport.shutdown()
        if (!isAvailable || geyserMenuApi == null) return

        try {
            val unregisterMethod = apiClass!!.getMethod("unregisterButton", String::class.java)
            if (warpButtonRegistered) {
                unregisterMethod.invoke(geyserMenuApi, WARP_BUTTON_ID)
                warpButtonRegistered = false
                logger.info("[GeyserMenu] Warp button unregistered")
            }
        } catch (_: Exception) {}
    }
}
