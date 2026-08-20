package dev.mizarc.waystonewarps.infrastructure.compat

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

/**
 * Replaces InventoryFramework's built-in `GuiListener` with the Folia-safe [FoliaGuiListener].
 *
 * IF registers its listener lazily, from the [com.github.stefvanschie.inventoryframework.gui.type.util.Gui]
 * constructor, guarded by a plain (non-volatile) static boolean. That is racy on Folia because two
 * players in different regions can open their first menu at the same instant and register the
 * listener twice, which would double-handle every click. Constructing a throwaway GUI during plugin
 * enable forces that registration to happen exactly once, on a known thread, before any player can
 * open a menu.
 *
 * Once IF's listener exists it is unregistered and [FoliaGuiListener] takes its place.
 */
object FoliaGuiCompat {

    private const val IF_LISTENER_CLASS = "com.github.stefvanschie.inventoryframework.gui.GuiListener"

    /**
     * @return true when IF's listener was found and successfully swapped out.
     */
    fun install(plugin: Plugin): Boolean {
        // Force IF to run its lazy listener registration now, on the enabling thread.
        try {
            ChestGui(1, "waystonewarps-init", plugin)
        } catch (ex: Throwable) {
            plugin.logger.severe("Could not initialise InventoryFramework: ${ex.message}")
            return false
        }

        val original = HandlerList.getRegisteredListeners(plugin)
            .map { it.listener }
            .distinct()
            .filter { it.javaClass.name == IF_LISTENER_CLASS }

        if (original.isEmpty()) {
            plugin.logger.warning(
                "InventoryFramework's GuiListener was not found; menus may not work on Folia.")
            return false
        }

        original.forEach { listener: Listener -> HandlerList.unregisterAll(listener) }
        plugin.server.pluginManager.registerEvents(FoliaGuiListener(plugin), plugin)
        plugin.logger.info("Installed Folia-safe InventoryFramework listener.")
        return true
    }
}
