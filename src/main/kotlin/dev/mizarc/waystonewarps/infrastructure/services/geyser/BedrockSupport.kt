package dev.mizarc.waystonewarps.infrastructure.services.geyser

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.util.UUID
import java.util.function.Consumer
import java.util.logging.Logger

/**
 * Reflection-based Bedrock form delivery using GeyserMenu Companion API.
 * Uses pure reflection (no compile-time dependency) for JDK 17+ compatibility.
 */
object BedrockSupport {
    private var apiClass: Class<*>? = null
    private var geyserMenuApi: Any? = null
    private var isAvailable = false
    private var plugin: JavaPlugin? = null
    private val logger: Logger = Logger.getLogger("BedrockSupport")

    // Cached method references
    private val methodCache = mutableMapOf<String, Method>()

    fun initialize(plugin: JavaPlugin): Boolean {
        this.plugin = plugin
        try {
            apiClass = Class.forName("com.geysermenu.companion.api.GeyserMenuAPI")
            val getInstanceMethod = apiClass!!.getMethod("getInstance")
            geyserMenuApi = getInstanceMethod.invoke(null)
            if (geyserMenuApi != null) {
                isAvailable = true
                logger.info("GeyserMenu API initialized via reflection.")
                return true
            }
        } catch (e: ClassNotFoundException) {
            logger.info("GeyserMenuCompanion not found. Bedrock forms disabled.")
        } catch (e: Exception) {
            logger.warning("Failed to initialize GeyserMenu API: ${e.message}")
        }
        return false
    }

    fun isAvailable(): Boolean = isAvailable

    fun isBedrockPlayer(uuid: UUID): Boolean {
        // UUID prefix detection (Floodgate default prefix)
        if (uuid.toString().startsWith("00000000-0000-0000", ignoreCase = true)) return true

        // Query GeyserMenu API
        if (isAvailable && geyserMenuApi != null) {
            try {
                val result = apiClass!!.getMethod("isBedrockPlayer", UUID::class.java)
                    .invoke(geyserMenuApi, uuid)
                if (result as? Boolean == true) return true
            } catch (_: Exception) {}
        }

        // Try Floodgate API
        try {
            val floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            val getInstance = floodgateClass.getMethod("getInstance")
            val api = getInstance.invoke(null)
            val isFloodgate = floodgateClass.getMethod("isFloodgatePlayer", UUID::class.java)
            if (isFloodgate.invoke(api, uuid) as Boolean) return true
        } catch (_: Exception) {}

        return false
    }

    fun isBedrockPlayer(player: Player): Boolean = isBedrockPlayer(player.uniqueId)

    // --- Simple Form (Button List) ---

    fun sendSimpleForm(
        player: Player,
        title: String,
        content: String,
        buttons: List<FormButton>,
        onButtonClicked: (Int) -> Unit
    ) {
        if (!isAvailable || geyserMenuApi == null) return
        try {
            val createMethod = findMethod(geyserMenuApi!!, "createSimpleMenu",
                String::class.java, UUID::class.java)
            val builder = createMethod.invoke(geyserMenuApi, title, player.uniqueId) ?: return
            val builderType = getPublicType(builder)

            // Set content
            findMethodOnType(builderType, "content", String::class.java)
                .invoke(builder, content)

            // Add buttons
            val buttonMethod1 = try {
                findMethodOnType(builderType, "button", String::class.java, String::class.java)
            } catch (_: Exception) { null }
            val buttonMethod0 = findMethodOnType(builderType, "button", String::class.java)

            for (btn in buttons) {
                if (btn.imagePath != null && buttonMethod1 != null) {
                    buttonMethod1.invoke(builder, btn.text, btn.imagePath)
                } else {
                    buttonMethod0.invoke(builder, btn.text)
                }
            }

            // Send with response handler
            val responseHandler = Consumer<Any> { response ->
                plugin?.server?.scheduler?.runTask(plugin!!, Runnable {
                    try {
                        val wasClosed = findMethod(response, "wasClosed").invoke(response) as? Boolean
                        if (wasClosed == true) return@Runnable
                        val buttonId = findMethod(response, "getButtonId").invoke(response) as? Int
                            ?: (findMethod(response, "buttonId").invoke(response) as? Int)
                        if (buttonId != null && buttonId >= 0) {
                            onButtonClicked(buttonId)
                        }
                    } catch (e: Exception) {
                        logger.warning("[Bedrock] Simple form response error: ${e.message}")
                    }
                })
            }

            findMethodOnType(builderType, "send", Consumer::class.java)
                .invoke(builder, responseHandler)
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to send simple form: ${e.message}")
        }
    }

    // --- Modal Form (Two-Button Dialog) ---

    fun sendModalForm(
        player: Player,
        title: String,
        content: String,
        button1: String,
        button2: String,
        onButton1: () -> Unit,
        onButton2: () -> Unit
    ) {
        if (!isAvailable || geyserMenuApi == null) return
        try {
            val createMethod = findMethod(geyserMenuApi!!, "createModalMenu",
                String::class.java, UUID::class.java)
            val builder = createMethod.invoke(geyserMenuApi, title, player.uniqueId) ?: return
            val builderType = getPublicType(builder)

            findMethodOnType(builderType, "content", String::class.java).invoke(builder, content)
            val btnMethod = findMethodOnType(builderType, "button", String::class.java)
            btnMethod.invoke(builder, button1)
            btnMethod.invoke(builder, button2)

            val responseHandler = Consumer<Any> { response ->
                plugin?.server?.scheduler?.runTask(plugin!!, Runnable {
                    try {
                        val buttonId = findMethod(response, "getButtonId").invoke(response) as? Int
                            ?: (findMethod(response, "buttonId").invoke(response) as? Int) ?: -1
                        when (buttonId) {
                            0 -> onButton1()
                            1 -> onButton2()
                        }
                    } catch (e: Exception) {
                        logger.warning("[Bedrock] Modal form response error: ${e.message}")
                    }
                })
            }

            findMethodOnType(builderType, "send", Consumer::class.java)
                .invoke(builder, responseHandler)
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to send modal form: ${e.message}")
        }
    }

    // --- Custom Form (Labels + Inputs) ---

    fun sendCustomForm(
        player: Player,
        title: String,
        elements: List<FormElement>,
        onSubmit: (Map<String, String>) -> Unit
    ) {
        if (!isAvailable || geyserMenuApi == null) return
        try {
            val createMethod = findMethod(geyserMenuApi!!, "createCustomMenu",
                String::class.java, UUID::class.java)
            val builder = createMethod.invoke(geyserMenuApi, title, player.uniqueId) ?: return
            val builderType = getPublicType(builder)

            val inputIds = mutableListOf<String>()

            for (element in elements) {
                when (element) {
                    is FormElement.Label -> {
                        findMethodOnType(builderType, "label", String::class.java)
                            .invoke(builder, element.text)
                    }
                    is FormElement.Input -> {
                        inputIds.add(element.id)
                        findMethodOnType(builderType, "input",
                            String::class.java, String::class.java, String::class.java, String::class.java)
                            .invoke(builder, element.id, element.text, element.placeholder, element.defaultValue)
                    }
                }
            }

            val responseHandler = Consumer<Any> { response ->
                plugin?.server?.scheduler?.runTask(plugin!!, Runnable {
                    try {
                        val wasClosed = findMethod(response, "wasClosed").invoke(response) as? Boolean
                        if (wasClosed == true) return@Runnable
                        val values = mutableMapOf<String, String>()
                        for (id in inputIds) {
                            val getStr = findMethod(response, "getString", String::class.java)
                            val value = getStr.invoke(response, id) as? String ?: ""
                            values[id] = value
                        }
                        onSubmit(values)
                    } catch (e: Exception) {
                        logger.warning("[Bedrock] Custom form response error: ${e.message}")
                    }
                })
            }

            findMethodOnType(builderType, "send", Consumer::class.java)
                .invoke(builder, responseHandler)
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to send custom form: ${e.message}")
        }
    }

    // --- Button Registration ---

    fun registerButton(
        id: String,
        text: String,
        imagePath: String,
        priority: Int,
        permissionCheck: (Player) -> Boolean,
        onClick: (Player) -> Unit
    ) {
        if (!isAvailable || geyserMenuApi == null) return
        try {
            val menuButtonClass = Class.forName("com.geysermenu.companion.api.MenuButton")
            val builderMethod = menuButtonClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            val builderType = builderMethod.returnType

            builderType.getMethod("id", String::class.java).invoke(builder, id)
            builderType.getMethod("text", String::class.java).invoke(builder, text)
            builderType.getMethod("imagePath", String::class.java).invoke(builder, imagePath)
            builderType.getMethod("priority", Int::class.javaPrimitiveType).invoke(builder, priority)

            val conditionHandler = java.util.function.Predicate<Any> { playerObj ->
                val player = playerObj as? Player ?: return@Predicate false
                permissionCheck(player)
            }
            builderType.getMethod("condition", java.util.function.Predicate::class.java)
                .invoke(builder, conditionHandler)

            val clickHandler = java.util.function.BiConsumer<Any, Any?> { playerObj, _ ->
                val player = playerObj as? Player ?: return@BiConsumer
                plugin?.server?.scheduler?.runTask(plugin!!, Runnable {
                    onClick(player)
                })
            }
            builderType.getMethod("onClick", java.util.function.BiConsumer::class.java)
                .invoke(builder, clickHandler)

            val button = builderType.getMethod("build").invoke(builder)
            val registerMethod = apiClass!!.getMethod("registerButton", menuButtonClass)
            registerMethod.invoke(geyserMenuApi, button)
        } catch (e: Exception) {
            logger.warning("[Bedrock] Failed to register button: ${e.message}")
        }
    }

    fun unregisterButton(id: String) {
        if (!isAvailable || geyserMenuApi == null) return
        try {
            apiClass!!.getMethod("unregisterButton", String::class.java)
                .invoke(geyserMenuApi, id)
        } catch (_: Exception) {}
    }

    // --- Reflection Helpers (JDK 17+ safe) ---

    private fun findMethod(obj: Any, name: String, vararg paramTypes: Class<*>): Method {
        val key = "${obj.javaClass.name}.$name(${paramTypes.joinToString { it.name }})"
        methodCache[key]?.let { return it }

        // Try public interfaces first (safest on JDK 17+)
        for (iface in obj.javaClass.interfaces) {
            try {
                val m = iface.getMethod(name, *paramTypes)
                methodCache[key] = m
                return m
            } catch (_: NoSuchMethodException) {}
        }

        // Try superclass hierarchy
        var cls: Class<*>? = obj.javaClass.superclass
        while (cls != null && cls != Any::class.java) {
            try {
                val m = cls.getMethod(name, *paramTypes)
                methodCache[key] = m
                return m
            } catch (_: NoSuchMethodException) {}
            cls = cls.superclass
        }

        // Last resort: declared method with setAccessible
        val m = obj.javaClass.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
        methodCache[key] = m
        return m
    }

    private fun findMethodOnType(type: Class<*>, name: String, vararg paramTypes: Class<*>): Method {
        val key = "${type.name}.$name(${paramTypes.joinToString { it.name }})"
        methodCache[key]?.let { return it }
        val m = type.getMethod(name, *paramTypes)
        methodCache[key] = m
        return m
    }

    private fun getPublicType(obj: Any): Class<*> {
        // Return the first public interface (the builder interface)
        for (iface in obj.javaClass.interfaces) {
            if (java.lang.reflect.Modifier.isPublic(iface.modifiers)) return iface
        }
        return obj.javaClass
    }
}

// --- Data Classes ---

data class FormButton(
    val text: String,
    val imagePath: String? = null
)

sealed class FormElement {
    data class Label(val text: String) : FormElement()
    data class Input(
        val id: String,
        val text: String,
        val placeholder: String = "",
        val defaultValue: String = ""
    ) : FormElement()
}
