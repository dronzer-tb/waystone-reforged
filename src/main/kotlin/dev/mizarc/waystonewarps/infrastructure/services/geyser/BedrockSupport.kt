package dev.mizarc.waystonewarps.infrastructure.services.geyser

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
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

    // Keepalive task to prevent TCP connection timeout
    private var keepaliveTask: BukkitTask? = null
    private var isConnectedMethod: Method? = null
    private var menuClientRequestPlayerListMethod: Method? = null
    private var menuClientInstance: Any? = null
    private val noopPlayerListConsumer = java.util.function.Consumer<Any> { _ -> }

    // Retry constants
    private const val MAX_SEND_RETRIES = 3
    private const val RETRY_DELAY_TICKS = 20L // 1 second
    private const val KEEPALIVE_INTERVAL_TICKS = 20L * 5 // 5 seconds
    private const val FORM_RESPONSE_TIMEOUT_TICKS = 20L * 90 // 90 seconds

    private data class PendingFormDispatch(
        val dispatchId: String,
        val formType: String,
        val title: String
    )

    private val pendingFormDispatches = ConcurrentHashMap<UUID, PendingFormDispatch>()
    private val deliveryTimeoutPlayers = ConcurrentHashMap<UUID, Long>()

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

    private fun cacheApiReferences(apiInstance: Any, resolvedApiClass: Class<*>) {
        geyserMenuApi = apiInstance
        apiClass = resolvedApiClass

        createSimpleMenuMethod = resolvedApiClass.getMethod("createSimpleMenu", String::class.java, UUID::class.java)
        simpleBuilderType = createSimpleMenuMethod!!.returnType

        createModalMenuMethod = resolvedApiClass.getMethod("createModalMenu", String::class.java, UUID::class.java)
        modalBuilderType = createModalMenuMethod!!.returnType

        createCustomMenuMethod = resolvedApiClass.getMethod("createCustomMenu", String::class.java, UUID::class.java)
        customBuilderType = createCustomMenuMethod!!.returnType

        isConnectedMethod = resolvedApiClass.getMethod("isConnected")
        resolveNetworkKeepalive(apiInstance)
    }

    private fun resolveNetworkKeepalive(apiInstance: Any) {
        menuClientRequestPlayerListMethod = null
        menuClientInstance = null

        try {
            val pluginField = apiInstance.javaClass.getDeclaredField("plugin").apply { isAccessible = true }
            val companionPlugin = pluginField.get(apiInstance) ?: return
            val resolvedMenuClient = companionPlugin.javaClass.getMethod("getMenuClient").invoke(companionPlugin) ?: return
            val requestMethod = resolvedMenuClient.javaClass.getMethod(
                "requestPlayerList",
                java.util.function.Consumer::class.java
            )
            menuClientInstance = resolvedMenuClient
            menuClientRequestPlayerListMethod = requestMethod
        } catch (_: Exception) {
            // Companion internals are optional; keepalive falls back to connection checks.
        }
    }

    /**
     * Initialize Bedrock support. Should be called during plugin enable.
     */
    fun initialize(plugin: JavaPlugin): Boolean {
        this.plugin = plugin
        logger = plugin.logger

        geyserMenuAvailable = try {
            val resolvedApiClass = Class.forName("com.geysermenu.companion.api.GeyserMenuAPI")
            val api = resolvedApiClass.getMethod("getInstance").invoke(null)
            if (api != null) {
                cacheApiReferences(api, resolvedApiClass)

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

            // Start keepalive task — pings the API every 60 seconds to prevent TCP idle timeout
            startKeepalive()
        } else {
            logger.info("[Bedrock] Bedrock forms disabled - GeyserMenu Companion plugin is required")
        }

        initialized = true
        return geyserMenuAvailable
    }

    fun shutdown() {
        keepaliveTask?.cancel()
        keepaliveTask = null
        bedrockPlayerCache.clear()
        pendingFormDispatches.clear()
        deliveryTimeoutPlayers.clear()
        geyserMenuAvailable = false
        initialized = false
        geyserMenuApi = null
        apiClass = null
        createSimpleMenuMethod = null
        simpleBuilderType = null
        createModalMenuMethod = null
        modalBuilderType = null
        createCustomMenuMethod = null
        customBuilderType = null
        isConnectedMethod = null
        menuClientRequestPlayerListMethod = null
        menuClientInstance = null
    }

    // ================================================================
    // Connection health & keepalive
    // ================================================================

    /**
     * Check if the GeyserMenu API TCP connection is currently authenticated.
     */
    private fun isApiConnected(): Boolean {
        if (!geyserMenuAvailable || geyserMenuApi == null || isConnectedMethod == null) return false
        return try {
            isConnectedMethod!!.invoke(geyserMenuApi) as? Boolean ?: false
        } catch (_: Exception) { false }
    }

    /**
     * Re-fetch the GeyserMenu API singleton. Handles cases where the
     * companion plugin was reloaded and the old instance is stale.
     */
    private fun refreshApi(): Boolean {
        return try {
            val resolvedApiClass = Class.forName("com.geysermenu.companion.api.GeyserMenuAPI")
            val freshApi = resolvedApiClass.getMethod("getInstance").invoke(null)
            if (freshApi == null) {
                logger.warning("[Bedrock] GeyserMenu API returned null during refresh")
                return false
            }

            val apiChanged = freshApi !== geyserMenuApi || resolvedApiClass != apiClass
            cacheApiReferences(freshApi, resolvedApiClass)
            if (apiChanged) {
                logger.info("[Bedrock] Refreshed GeyserMenu API bindings")
            }

            val connected = isConnectedMethod!!.invoke(geyserMenuApi) as? Boolean ?: false
            if (!connected) {
                logger.warning("[Bedrock] GeyserMenu API not connected after refresh")
            }
            connected
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to refresh API: ${e.message}")
            false
        }
    }

    private fun refreshApiWithRetries(maxAttempts: Int = MAX_SEND_RETRIES): Boolean {
        repeat(maxAttempts) { attempt ->
            if (refreshApi()) return true
            if (attempt < maxAttempts - 1) {
                logger.warning("[Bedrock] Refresh attempt ${attempt + 1}/$maxAttempts failed")
            }
        }
        return false
    }

    private fun sendNetworkKeepaliveProbe(): Boolean {
        val method = menuClientRequestPlayerListMethod ?: return false
        val client = menuClientInstance ?: return false
        return try {
            method.invoke(client, noopPlayerListConsumer)
            true
        } catch (_: Exception) {
            menuClientRequestPlayerListMethod = null
            menuClientInstance = null
            false
        }
    }

    private fun sendPublicApiKeepaliveProbe(): Boolean {
        val resolvedApiClass = apiClass ?: return false
        val resolvedApi = geyserMenuApi ?: return false

        return try {
            resolvedApiClass.getMethod("isBedrockPlayer", UUID::class.java)
                .invoke(resolvedApi, UUID.fromString("00000000-0000-0000-0000-000000000000"))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Periodic keepalive for long-lived GeyserMenu sessions.
     * Uses companion MenuClient requestPlayerList() when available, and falls
     * back to a public API probe otherwise.
     *
     * If disconnected, re-fetches the API singleton with retries.
     */
    private fun startKeepalive() {
        keepaliveTask?.cancel()
        keepaliveTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!geyserMenuAvailable || geyserMenuApi == null) return@Runnable

            val probeHealthy = sendNetworkKeepaliveProbe() || sendPublicApiKeepaliveProbe()

            if (!probeHealthy || !isApiConnected()) {
                val reason = if (!probeHealthy) "probe failed" else "API disconnected"
                logger.warning("[Bedrock] Keepalive: $reason — attempting refresh")
                if (!refreshApiWithRetries()) {
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        val delayedProbeHealthy = sendNetworkKeepaliveProbe() || sendPublicApiKeepaliveProbe()
                        if (!delayedProbeHealthy || !isApiConnected()) {
                            logger.warning("[Bedrock] Keepalive: delayed refresh attempt")
                            refreshApiWithRetries(1)
                        }
                    }, 40L)
                }
            }
        }, KEEPALIVE_INTERVAL_TICKS, KEEPALIVE_INTERVAL_TICKS)
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
        pendingFormDispatches.remove(event.player.uniqueId)
        deliveryTimeoutPlayers.remove(event.player.uniqueId)
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
    // Includes connection check + retry on disconnect.
    // ================================================================

    /**
     * Ensures the API is connected before sending a form.
     * Uses connection-state + API probe checks and retries refresh on failure.
     */
    private fun ensureConnected(): Boolean {
        // First, check raw connection status
        if (!isApiConnected()) {
            logger.warning("[Bedrock] API disconnected — refreshing before send")
            if (!refreshApi()) {
                logger.warning("[Bedrock] First refresh failed — retrying")
                return refreshApi()
            }
            return true
        }

        // Additionally, perform a health check with a real API call
        // to detect stale connections that still report as "connected"
        return try {
            apiClass!!.getMethod("isBedrockPlayer", UUID::class.java)
                .invoke(geyserMenuApi, UUID.fromString("00000000-0000-0000-0000-000000000000"))
            true
        } catch (e: Exception) {
            logger.warning("[Bedrock] Health check failed: ${e.message} — refreshing API")
            refreshApi()
        }
    }

    private fun recoverTimedOutDelivery(player: Player, formType: String, title: String): Boolean {
        val timeoutAt = deliveryTimeoutPlayers[player.uniqueId] ?: return true
        logger.warning(
            "[Bedrock] Previous ${formType.lowercase()} form timeout for ${player.name} at $timeoutAt " +
                "— refreshing before '${title}'"
        )
        if (!refreshApiWithRetries()) {
            return false
        }
        deliveryTimeoutPlayers.remove(player.uniqueId)
        return true
    }

    private fun startFormDispatchTracking(player: Player, formType: String, title: String): String {
        val existingDispatch = pendingFormDispatches[player.uniqueId]
        if (existingDispatch != null) {
            pendingFormDispatches[player.uniqueId] = existingDispatch.copy(
                formType = formType,
                title = title
            )
            return existingDispatch.dispatchId
        }

        val dispatchId = UUID.randomUUID().toString()
        pendingFormDispatches[player.uniqueId] = PendingFormDispatch(
            dispatchId = dispatchId,
            formType = formType,
            title = title
        )

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val current = pendingFormDispatches[player.uniqueId] ?: return@Runnable
            if (current.dispatchId != dispatchId) return@Runnable

            pendingFormDispatches.remove(player.uniqueId, current)
            deliveryTimeoutPlayers[player.uniqueId] = System.currentTimeMillis()
            logger.warning(
                "[Bedrock] ${current.formType} form '${current.title}' to ${player.name} timed out waiting for response " +
                    "(>${FORM_RESPONSE_TIMEOUT_TICKS / 20}s); delivery path may be stale"
            )
        }, FORM_RESPONSE_TIMEOUT_TICKS)

        return dispatchId
    }

    private fun finishFormDispatchTracking(playerUuid: UUID, dispatchId: String, clearTimeoutFlag: Boolean = true) {
        pendingFormDispatches.computeIfPresent(playerUuid) { _, current ->
            if (current.dispatchId == dispatchId) null else current
        }
        if (clearTimeoutFlag) {
            deliveryTimeoutPlayers.remove(playerUuid)
        }
    }

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
            logger.warning("[Bedrock] Cannot send simple form '$title' - GeyserMenu not available")
            player.sendMessage("§c[Waystone] Bedrock menu unavailable. Is GeyserMenu Companion running?")
            return false
        }

        if (!ensureConnected() || !recoverTimedOutDelivery(player, "Simple", title)) {
            // Schedule a retry after a short delay
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (ensureConnected() && recoverTimedOutDelivery(player, "Simple", title)) {
                    doSendSimpleForm(player, title, content, buttons, onButtonClicked, onClosed)
                } else {
                    logger.warning("[Bedrock] API still disconnected after retry — form '${title}' not sent")
                    player.sendMessage("§c[Waystone] Bedrock menu temporarily unavailable. Please try again.")
                }
            }, RETRY_DELAY_TICKS)
            return false
        }

        return doSendSimpleForm(player, title, content, buttons, onButtonClicked, onClosed)
    }

    private fun doSendSimpleForm(
        player: Player,
        title: String,
        content: String,
        buttons: List<FormButton>,
        onButtonClicked: (Int) -> Unit,
        onClosed: () -> Unit
    ): Boolean {
        var dispatchId: String? = null
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

            dispatchId = startFormDispatchTracking(player, "Simple", title)

            // Send with response handler
            val responseHandler = java.util.function.Consumer<Any> { response ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    dispatchId?.let { finishFormDispatchTracking(player.uniqueId, it) }
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

            logger.info("[Bedrock] Simple form '${title}' dispatched to GeyserMenu for ${player.name}")
            return true
        } catch (e: Exception) {
            dispatchId?.let { finishFormDispatchTracking(player.uniqueId, it, clearTimeoutFlag = false) }
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

        if (!ensureConnected() || !recoverTimedOutDelivery(player, "Modal", title)) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (ensureConnected() && recoverTimedOutDelivery(player, "Modal", title)) {
                    doSendModalForm(player, title, content, button1, button2, onButton1, onButton2, onClosed)
                } else {
                    logger.warning("[Bedrock] API still disconnected after retry — modal '${title}' not sent")
                    player.sendMessage("§c[Waystone] Bedrock menu temporarily unavailable. Please try again.")
                }
            }, RETRY_DELAY_TICKS)
            return false
        }

        return doSendModalForm(player, title, content, button1, button2, onButton1, onButton2, onClosed)
    }

    private fun doSendModalForm(
        player: Player,
        title: String,
        content: String,
        button1: String,
        button2: String,
        onButton1: () -> Unit,
        onButton2: () -> Unit,
        onClosed: () -> Unit
    ): Boolean {
        var dispatchId: String? = null
        try {
            val builder = createModalMenuMethod!!.invoke(geyserMenuApi, title, player.uniqueId)
            val bt = modalBuilderType!!

            bt.getMethod("content", String::class.java).invoke(builder, content)

            val buttonMethod = bt.getMethod("button", String::class.java)
            buttonMethod.invoke(builder, button1)
            buttonMethod.invoke(builder, button2)

            dispatchId = startFormDispatchTracking(player, "Modal", title)

            val responseHandler = java.util.function.Consumer<Any> { response ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    dispatchId?.let { finishFormDispatchTracking(player.uniqueId, it) }
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

            logger.info("[Bedrock] Modal form '${title}' dispatched to GeyserMenu for ${player.name}")
            return true
        } catch (e: Exception) {
            dispatchId?.let { finishFormDispatchTracking(player.uniqueId, it, clearTimeoutFlag = false) }
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

        if (!ensureConnected() || !recoverTimedOutDelivery(player, "Custom", title)) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (ensureConnected() && recoverTimedOutDelivery(player, "Custom", title)) {
                    doSendCustomForm(player, title, elements, onSubmit, onClosed)
                } else {
                    logger.warning("[Bedrock] API still disconnected after retry — custom form '${title}' not sent")
                    player.sendMessage("§c[Waystone] Bedrock menu temporarily unavailable. Please try again.")
                }
            }, RETRY_DELAY_TICKS)
            return false
        }

        return doSendCustomForm(player, title, elements, onSubmit, onClosed)
    }

    private fun doSendCustomForm(
        player: Player,
        title: String,
        elements: List<FormElement>,
        onSubmit: (Map<String, String>) -> Unit,
        onClosed: () -> Unit
    ): Boolean {
        var dispatchId: String? = null
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

            dispatchId = startFormDispatchTracking(player, "Custom", title)

            val responseHandler = java.util.function.Consumer<Any> { response ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    dispatchId?.let { finishFormDispatchTracking(player.uniqueId, it) }
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

            logger.info("[Bedrock] Custom form '${title}' dispatched to GeyserMenu for ${player.name}")
            return true
        } catch (e: Exception) {
            dispatchId?.let { finishFormDispatchTracking(player.uniqueId, it, clearTimeoutFlag = false) }
            logger.warning("[Bedrock] Failed to send custom form: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}
