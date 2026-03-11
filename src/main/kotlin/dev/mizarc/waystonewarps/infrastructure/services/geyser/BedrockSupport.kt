package dev.mizarc.waystonewarps.infrastructure.services.geyser

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Data class representing a button in a Bedrock form.
 */
data class FormButton(
    val text: String,
    val imagePath: String? = null,
    val imageUrl: String? = null
)

/**
 * Sealed class representing elements in a custom Bedrock form.
 */
sealed class FormElement {
    data class Label(val text: String) : FormElement()
    data class Input(
        val id: String,
        val text: String,
        val placeholder: String = "",
        val defaultValue: String = ""
    ) : FormElement()
}

/**
 * Centralized Bedrock Edition support for WaystoneWarps.
 *
 * Uses ONLY GeyserMenu Companion API for all Bedrock form delivery.
 * All reflected methods are resolved from the PUBLIC API interface types
 * (return types of GeyserMenuAPI methods), NOT from concrete implementation classes
 * (which are private inner classes and cause IllegalAccessException on JDK 17+).
 */
object BedrockSupport : Listener {

    private var geyserMenuAvailable = false
    private var initialized = false
    private lateinit var logger: Logger
    private lateinit var plugin: JavaPlugin

    private const val BEDROCK_UUID_PREFIX = "00000000-0000-0000"

    // Cache for Bedrock player status
    private val bedrockPlayerCache = ConcurrentHashMap<UUID, Boolean>()

    // ================================================================
    // Cached GeyserMenu API references
    // ================================================================
    private var geyserMenuApi: Any? = null
    private var apiClass: Class<*>? = null

    // createSimpleMenu(String title, UUID playerUuid) -> SimpleMenuBuilder
    private var createSimpleMenuMethod: Method? = null
    private var simpleBuilderType: Class<*>? = null

    // createModalMenu(String title, UUID playerUuid) -> ModalMenuBuilder
    private var createModalMenuMethod: Method? = null
    private var modalBuilderType: Class<*>? = null

    // createCustomMenu(String title, UUID playerUuid) -> CustomMenuBuilder
    private var createCustomMenuMethod: Method? = null
    private var customBuilderType: Class<*>? = null

    /**
     * Initialize Bedrock support. Should be called during plugin enable.
     */
    fun initialize(plugin: JavaPlugin): Boolean {
        this.plugin = plugin
        logger = plugin.logger

        geyserMenuAvailable = try {
            apiClass = Class.forName("com.geysermenu.companion.api.GeyserMenuAPI")
            val api = apiClass!!.getMethod("getInstance").invoke(null)
            if (api != null) {
                geyserMenuApi = api

                // Cache method references AND their return types (the PUBLIC interface types).
                // This is critical: calling methods through the public return type avoids
                // IllegalAccessException when the implementation is a private inner class.
                createSimpleMenuMethod = apiClass!!.getMethod("createSimpleMenu", String::class.java, UUID::class.java)
                simpleBuilderType = createSimpleMenuMethod!!.returnType

                createModalMenuMethod = apiClass!!.getMethod("createModalMenu", String::class.java, UUID::class.java)
                modalBuilderType = createModalMenuMethod!!.returnType

                createCustomMenuMethod = apiClass!!.getMethod("createCustomMenu", String::class.java, UUID::class.java)
                customBuilderType = createCustomMenuMethod!!.returnType

                logger.info("[Bedrock] GeyserMenu API initialized")
                logger.info("[Bedrock]   SimpleBuilder: ${simpleBuilderType?.name}")
                logger.info("[Bedrock]   ModalBuilder:  ${modalBuilderType?.name}")
                logger.info("[Bedrock]   CustomBuilder: ${customBuilderType?.name}")
                true
            } else {
                logger.warning("[Bedrock] GeyserMenu API returned null instance")
                false
            }
        } catch (e: ClassNotFoundException) {
            logger.info("[Bedrock] GeyserMenu Companion not found - Bedrock forms unavailable")
            false
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to initialize GeyserMenu API: ${e.message}")
            e.printStackTrace()
            false
        }

        if (geyserMenuAvailable) {
            logger.info("[Bedrock] Bedrock Edition support enabled via GeyserMenu")

            // Register as listener for player caching
            plugin.server.pluginManager.registerEvents(this, plugin)

            // Pre-cache all online players
            plugin.server.onlinePlayers.forEach { cachePlayerStatus(it) }
        } else {
            logger.info("[Bedrock] Bedrock forms disabled - GeyserMenu Companion plugin is required")
        }

        initialized = true
        return geyserMenuAvailable
    }

    fun shutdown() {
        bedrockPlayerCache.clear()
    }

    // ================================================================
    // Player join/quit caching
    // ================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            cachePlayerStatus(event.player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        bedrockPlayerCache.remove(event.player.uniqueId)
    }

    private fun cachePlayerStatus(player: Player) {
        val isBedrock = detectBedrockPlayer(player.uniqueId)
        bedrockPlayerCache[player.uniqueId] = isBedrock
        if (isBedrock) {
            logger.info("[Bedrock] Cached Bedrock player: ${player.name}")
        }
    }

    // ================================================================
    // Player detection
    // ================================================================

    fun isAvailable(): Boolean = geyserMenuAvailable

    fun isBedrockPlayer(player: Player): Boolean = isBedrockPlayer(player.uniqueId)

    fun isBedrockPlayer(uuid: UUID): Boolean = detectBedrockPlayer(uuid)

    private fun detectBedrockPlayer(uuid: UUID): Boolean {
        // Check cache
        bedrockPlayerCache[uuid]?.let { return it }

        // UUID prefix detection (Floodgate assigns UUIDs starting with 00000000-0000-0000)
        if (uuid.toString().startsWith(BEDROCK_UUID_PREFIX, ignoreCase = true)) return true

        // Check via GeyserMenu API
        if (geyserMenuAvailable && geyserMenuApi != null) {
            try {
                val result = apiClass!!.getMethod("isBedrockPlayer", UUID::class.java)
                    .invoke(geyserMenuApi, uuid)
                if (result as? Boolean == true) return true
            } catch (_: Exception) {}
        }

        return false
    }

    // ================================================================
    // Reflection helper
    // ================================================================

    /**
     * Finds a method on an object by searching interfaces and superclasses first
     * (which are public API types), avoiding IllegalAccessException on private
     * inner class implementations.
     */
    private fun findMethod(obj: Any, name: String, vararg paramTypes: Class<*>): Method {
        // Try interfaces first (these are the public API types)
        for (iface in obj.javaClass.interfaces) {
            try { return iface.getMethod(name, *paramTypes) } catch (_: NoSuchMethodException) {}
        }
        // Try superclass hierarchy
        var cls: Class<*>? = obj.javaClass.superclass
        while (cls != null && cls != Any::class.java) {
            try { return cls.getMethod(name, *paramTypes) } catch (_: NoSuchMethodException) {}
            cls = cls.superclass
        }
        // Last resort: concrete class with setAccessible
        return obj.javaClass.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
    }

    // ================================================================
    // Form API
    // All methods called through the PUBLIC interface types to avoid
    // IllegalAccessException on private implementation classes.
    // ================================================================

    /**
     * Send a simple form (button list) to a Bedrock player.
     */
    fun sendSimpleForm(
        player: Player,
        title: String,
        content: String,
        buttons: List<FormButton>,
        onButtonClicked: (Int) -> Unit,
        onClosed: () -> Unit = {}
    ): Boolean {
        if (!geyserMenuAvailable || createSimpleMenuMethod == null) {
            logger.warning("[Bedrock] Cannot send simple form - GeyserMenu not available")
            return false
        }

        try {
            val builder = createSimpleMenuMethod!!.invoke(geyserMenuApi, title, player.uniqueId)
            val bt = simpleBuilderType!!

            // Set content
            bt.getMethod("content", String::class.java).invoke(builder, content)

            // Add buttons
            val buttonTextOnly = bt.getMethod("button", String::class.java)
            val buttonWithIcon = try {
                bt.getMethod("button", String::class.java, String::class.java)
            } catch (_: NoSuchMethodException) { null }

            for (btn in buttons) {
                val icon = btn.imagePath ?: btn.imageUrl
                if (icon != null && buttonWithIcon != null) {
                    buttonWithIcon.invoke(builder, btn.text, icon)
                } else {
                    buttonTextOnly.invoke(builder, btn.text)
                }
            }

            // Send with response handler
            val responseHandler = java.util.function.Consumer<Any> { response ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        val wasClosed = findMethod(response, "wasClosed").invoke(response) as? Boolean ?: false
                        if (wasClosed) { onClosed(); return@Runnable }

                        val buttonId = findMethod(response, "getButtonId").invoke(response) as? Int ?: -1
                        if (buttonId >= 0) onButtonClicked(buttonId)
                    } catch (e: Exception) {
                        logger.warning("[Bedrock] Simple form response error: ${e.message}")
                        e.printStackTrace()
                    }
                })
            }

            bt.getMethod("send", java.util.function.Consumer::class.java)
                .invoke(builder, responseHandler)

            logger.info("[Bedrock] Simple form '${title}' sent to ${player.name}")
            return true
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to send simple form: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Send a modal form (two-button dialog) to a Bedrock player.
     */
    fun sendModalForm(
        player: Player,
        title: String,
        content: String,
        button1: String,
        button2: String,
        onButton1: () -> Unit,
        onButton2: () -> Unit,
        onClosed: () -> Unit = {}
    ): Boolean {
        if (!geyserMenuAvailable || createModalMenuMethod == null) {
            logger.warning("[Bedrock] Cannot send modal form - GeyserMenu not available")
            return false
        }

        try {
            val builder = createModalMenuMethod!!.invoke(geyserMenuApi, title, player.uniqueId)
            val bt = modalBuilderType!!

            bt.getMethod("content", String::class.java).invoke(builder, content)

            val buttonMethod = bt.getMethod("button", String::class.java)
            buttonMethod.invoke(builder, button1)
            buttonMethod.invoke(builder, button2)

            val responseHandler = java.util.function.Consumer<Any> { response ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        val buttonId = findMethod(response, "getButtonId").invoke(response) as? Int ?: -1
                        when (buttonId) {
                            0 -> onButton1()
                            1 -> onButton2()
                            else -> onClosed()
                        }
                    } catch (e: Exception) {
                        logger.warning("[Bedrock] Modal form response error: ${e.message}")
                        e.printStackTrace()
                    }
                })
            }

            bt.getMethod("send", java.util.function.Consumer::class.java)
                .invoke(builder, responseHandler)

            logger.info("[Bedrock] Modal form '${title}' sent to ${player.name}")
            return true
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to send modal form: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Send a custom form (labels + inputs) to a Bedrock player.
     */
    fun sendCustomForm(
        player: Player,
        title: String,
        elements: List<FormElement>,
        onSubmit: (Map<String, String>) -> Unit,
        onClosed: () -> Unit = {}
    ): Boolean {
        if (!geyserMenuAvailable || createCustomMenuMethod == null) {
            logger.warning("[Bedrock] Cannot send custom form - GeyserMenu not available")
            return false
        }

        try {
            val builder = createCustomMenuMethod!!.invoke(geyserMenuApi, title, player.uniqueId)
            val bt = customBuilderType!!

            val inputIds = mutableListOf<String>()
            val labelMethod = bt.getMethod("label", String::class.java)

            // Detect input method arity
            val inputMethod4 = try {
                bt.getMethod("input", String::class.java, String::class.java, String::class.java, String::class.java)
            } catch (_: NoSuchMethodException) { null }

            val inputMethod3 = try {
                bt.getMethod("input", String::class.java, String::class.java, String::class.java)
            } catch (_: NoSuchMethodException) { null }

            elements.forEach { element ->
                when (element) {
                    is FormElement.Label -> labelMethod.invoke(builder, element.text)
                    is FormElement.Input -> {
                        inputIds.add(element.id)
                        when {
                            inputMethod4 != null -> inputMethod4.invoke(
                                builder, element.id, element.text, element.placeholder, element.defaultValue
                            )
                            inputMethod3 != null -> {
                                val placeholder = element.defaultValue.ifBlank { element.placeholder }
                                inputMethod3.invoke(builder, element.id, element.text, placeholder)
                            }
                            else -> logger.warning("[Bedrock] No compatible input() method found")
                        }
                    }
                }
            }

            val responseHandler = java.util.function.Consumer<Any> { response ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    try {
                        try {
                            val wasClosed = findMethod(response, "wasClosed").invoke(response) as? Boolean
                            if (wasClosed == true) { onClosed(); return@Runnable }
                        } catch (_: Exception) {}

                        val result = mutableMapOf<String, String>()
                        inputIds.forEach { id ->
                            try {
                                val value = findMethod(response, "getString", String::class.java)
                                    .invoke(response, id)
                                result[id] = (value as? String) ?: ""
                            } catch (e: Exception) {
                                logger.warning("[Bedrock] Failed to read input '$id': ${e.message}")
                                result[id] = ""
                            }
                        }
                        onSubmit(result)
                    } catch (e: Exception) {
                        logger.warning("[Bedrock] Custom form response error: ${e.message}")
                        e.printStackTrace()
                    }
                })
            }

            bt.getMethod("send", java.util.function.Consumer::class.java)
                .invoke(builder, responseHandler)

            logger.info("[Bedrock] Custom form '${title}' sent to ${player.name}")
            return true
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to send custom form: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}
