package dev.mizarc.waystonewarps.infrastructure.compat

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.gui.type.MerchantGui
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui
import com.github.stefvanschie.inventoryframework.gui.type.util.NamedGui
import com.github.stefvanschie.inventoryframework.util.InventoryViewUtil
import com.github.stefvanschie.inventoryframework.util.UUIDTagType
import dev.mizarc.waystonewarps.infrastructure.scheduling.FoliaScheduler
import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.DragType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.TradeSelectEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap

/**
 * A Folia-safe replacement for InventoryFramework's own `GuiListener`.
 *
 * IF 0.12.0 (the newest release, and the only one with 26.x inventory support) calls
 * `Bukkit.getScheduler().runTask(...)` from `onInventoryClick` and `onInventoryClose`. On Folia
 * `Bukkit.getScheduler()` throws `UnsupportedOperationException`, which means *every* click in an
 * IF menu and every menu close would blow up. This class is a line-for-line port of IF 0.12.0's
 * listener with three changes:
 *
 *  1. the two scheduler calls are dispatched onto the clicking player's entity scheduler,
 *  2. `activeGuiInstances` is a concurrent set, because inventory events for players in different
 *     regions are delivered on different threads simultaneously,
 *  3. [getGui] resolves the inventory holder before falling back to IF's static
 *     `Gui.getGui(Inventory)` lookup, which is backed by a plain `WeakHashMap`. Only anvil-backed
 *     menus can reach that map now.
 *
 * [FoliaGuiCompat] swaps IF's listener out for this one during plugin enable.
 */
class FoliaGuiListener(private val plugin: Plugin) : Listener {

    private val activeGuiInstances: MutableSet<Gui> = ConcurrentHashMap.newKeySet()

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val gui = getGui(event.inventory) ?: return

        val view = event.view
        val inventory = InventoryViewUtil.getInstance().getInventory(view, event.rawSlot)

        if (inventory == null) {
            gui.callOnOutsideClick(event)
            return
        }

        gui.callOnGlobalClick(event)
        if (inventory == InventoryViewUtil.getInstance().getTopInventory(view)) {
            gui.callOnTopClick(event)
        } else {
            gui.callOnBottomClick(event)
        }

        gui.click(event)

        if (event.isCancelled) {
            // Folia: must run on the thread owning the clicking player, not the (absent) main thread.
            val clicker = event.whoClicked
            FoliaScheduler.forEntity(plugin, clicker) {
                // Due to a client issue off-hand items appear as ghost items; this resyncs them.
                val playerInventory = clicker.inventory
                playerInventory.setItemInOffHand(playerInventory.itemInOffHand)
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val entity = event.entity
        if (entity !is HumanEntity) return

        val gui = getGui(InventoryViewUtil.getInstance().getTopInventory(entity.openInventory)) ?: return
        if (!gui.isPlayerInventoryUsed) return

        val leftOver = gui.humanEntityCache.add(entity, event.item.itemStack)
        if (leftOver == 0) {
            event.item.remove()
        } else {
            val itemStack = event.item.itemStack
            itemStack.amount = leftOver
            event.item.itemStack = itemStack
        }

        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val gui = getGui(event.inventory) ?: return

        val view = event.view
        val inventorySlots = event.rawSlots

        if (inventorySlots.size > 1) {
            var top = false
            var bottom = false

            for (inventorySlot in inventorySlots) {
                val inventory = InventoryViewUtil.getInstance().getInventory(view, inventorySlot)

                if (InventoryViewUtil.getInstance().getTopInventory(view) == inventory) {
                    top = true
                } else if (InventoryViewUtil.getInstance().getBottomInventory(view) == inventory) {
                    bottom = true
                }

                if (top && bottom) break
            }

            gui.callOnGlobalDrag(event)
            if (top) gui.callOnTopDrag(event)
            if (bottom) gui.callOnBottomDrag(event)
        } else {
            val index = inventorySlots.toTypedArray()[0]
            val slotType = InventoryViewUtil.getInstance().getSlotType(view, index)

            val even = event.type == DragType.EVEN
            val clickType = if (even) ClickType.LEFT else ClickType.RIGHT
            val inventoryAction = if (even) InventoryAction.PLACE_SOME else InventoryAction.PLACE_ONE

            val previousViewCursor = InventoryViewUtil.getInstance().getCursor(view)
            InventoryViewUtil.getInstance().setCursor(view, event.oldCursor)
            // Local fake click event, mirroring what IF itself does.
            val inventoryClickEvent = InventoryClickEvent(view, slotType, index, clickType, inventoryAction)

            onInventoryClick(inventoryClickEvent)

            if (InventoryViewUtil.getInstance().getCursor(view) == event.oldCursor) {
                InventoryViewUtil.getInstance().setCursor(view, previousViewCursor)
            }

            event.isCancelled = inventoryClickEvent.isCancelled
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onTradeSelect(event: TradeSelectEvent) {
        val gui = getGui(event.inventory)
        if (gui !is MerchantGui) return
        gui.callOnTradeSelect(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val gui = getGui(event.inventory) ?: return
        if (isNamedGuiUpdatingDirtily(gui)) return

        val humanEntity = event.player
        val playerInventory = humanEntity.inventory

        // Off-hand ghost item workaround, as per IF.
        playerInventory.setItemInOffHand(playerInventory.itemInOffHand)

        gui.callOnClose(event)

        val humanEntityCache = gui.humanEntityCache
        if (humanEntityCache.contains(humanEntity)) {
            humanEntityCache.restoreAndForget(humanEntity)
        } else {
            for (itemStack in humanEntity.inventory) {
                if (itemStack == null || !itemStack.hasItemMeta()) continue

                val itemMeta = itemStack.itemMeta ?: continue
                val persistentDataContainer = itemMeta.persistentDataContainer

                for (item in gui.items) {
                    val key = item.key
                    if (persistentDataContainer.has(key, UUIDTagType.INSTANCE)) {
                        persistentDataContainer.remove(key)
                        break
                    }
                }

                itemStack.itemMeta = itemMeta
            }
        }

        if (gui.viewerCount == 1) {
            activeGuiInstances.remove(gui)
        }

        // Bukkit dislikes opening an inventory while the previous one is closing, so defer a tick.
        // Folia: this has to happen on the thread that owns the closing player.
        FoliaScheduler.forEntity(plugin, humanEntity) { gui.navigateToParent(humanEntity) }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val gui = getGui(event.player.openInventory.topInventory) ?: return

        val itemStack = event.itemDrop.itemStack
        if (!itemStack.hasItemMeta()) return

        val itemMeta = itemStack.itemMeta ?: return
        val persistentDataContainer = itemMeta.persistentDataContainer

        for (item in gui.items) {
            val key = item.key
            if (persistentDataContainer.has(key, UUIDTagType.INSTANCE)) {
                persistentDataContainer.remove(key)
                break
            }
        }

        itemStack.itemMeta = itemMeta
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val gui = getGui(event.inventory) ?: return
        if (isNamedGuiUpdatingDirtily(gui)) return
        activeGuiInstances.add(gui)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin !== plugin) return

        var counter = 0 // Callbacks might open GUIs, e.g. in nested menus.
        val maxCount = 10
        while (activeGuiInstances.isNotEmpty() && counter++ < maxCount) {
            for (gui in ArrayList(activeGuiInstances)) {
                for (viewer in gui.viewers) {
                    viewer.closeInventory()
                }
            }
        }

        if (counter == maxCount) {
            plugin.logger.warning(
                "Unable to close GUIs on plugin disable: they keep getting opened (tried: $maxCount times)")
        }
    }

    /**
     * Resolves the GUI backing an inventory. The holder is checked first so that chest-backed menus
     * never touch IF's static `WeakHashMap`, which is not safe for concurrent region threads.
     */
    private fun getGui(inventory: Inventory): Gui? {
        val holder: InventoryHolder? = inventory.holder
        if (holder is Gui) return holder
        return Gui.getGui(inventory)
    }

    private fun isNamedGuiUpdatingDirtily(gui: Gui): Boolean {
        val dirtyTitle = gui is NamedGui && gui.isDirty
        val dirtyRows = gui is ChestGui && gui.isDirtyRows
        return gui.isUpdating && (dirtyTitle || dirtyRows)
    }
}
