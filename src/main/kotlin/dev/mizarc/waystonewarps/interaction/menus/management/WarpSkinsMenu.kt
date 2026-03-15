package dev.mizarc.waystonewarps.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import dev.mizarc.waystonewarps.application.actions.management.GetAllWarpSkins
import dev.mizarc.waystonewarps.application.actions.management.UpdateWarpSkin
import dev.mizarc.waystonewarps.application.results.UpdateWarpSkinResult
import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.services.teleportation.CostType
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.lore
import dev.mizarc.waystonewarps.interaction.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarpSkinsMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val warp: Warp,
    private val localizationProvider: LocalizationProvider
): Menu, KoinComponent {
    private val getAllWarpSkins: GetAllWarpSkins by inject()
    private val updateWarpSkin: UpdateWarpSkin by inject()
    private val configService: ConfigService by inject()

    override fun open() {
        // Create menu
        val gui = ChestGui(3, localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_SKINS_TITLE))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        // Add divider pane
        val dividerPane = StaticPane(1, 0, 1, 3)
        val dividerItem = ItemStack(Material.BLACK_STAINED_GLASS_PANE).name(" ")
        val guiDividerItem = GuiItem(dividerItem) { guiEvent -> guiEvent.isCancelled = true }
        for (i in 0..2) {
            dividerPane.addItem(guiDividerItem, 0, i)
        }
        gui.addPane(dividerPane)

        // Add back menu item
        val navigationPane = StaticPane(0, 0, 1, 3)
        gui.addPane(navigationPane)
        val backItem = ItemStack(Material.NETHER_STAR)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_COMMON_ITEM_BACK_NAME), PrimaryColourPalette.CANCELLED.color!!)
        val backGuiItem = GuiItem(backItem) { menuNavigator.goBack() }
        navigationPane.addItem(backGuiItem, 0, 0)

        // Add tooltip menu item
        val costTypeLabel = when (configService.getTeleportCostType()) {
            CostType.MONEY -> "coins"
            CostType.XP -> "XP"
            CostType.ITEM -> configService.getTeleportCostItem().lowercase().replace("_", " ")
        }
        val tooltipItem = ItemStack(Material.PAPER)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_SKINS_ITEM_TOOLTIP_NAME), PrimaryColourPalette.INFO.color!!)
            .lore("§7Click a skin to apply it.")
            .lore("§7Cost is paid in §e$costTypeLabel§7.")
        val tooltipGuiItem = GuiItem(tooltipItem) { guiEvent -> guiEvent.isCancelled = true }
        navigationPane.addItem(tooltipGuiItem, 0, 2)

        // Display list of blocks
        displayBlockList(gui)

        gui.show(player)
    }

    private fun displayBlockList(gui: ChestGui) {
        val skins = getAllWarpSkins.execute()

        val blockListPane = OutlinePane(2, 0, 7, 3)
        for (skinKey in skins) {
            val material = runCatching { Material.valueOf(skinKey) }.getOrNull() ?: continue
            val price = configService.getSkinPrice(skinKey)
            val isActive = skinKey == warp.block

            val blockItem = ItemStack(material)
            val meta = blockItem.itemMeta
            val skinName = skinKey.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }

            if (isActive) {
                meta.displayName(Component.text("$skinName §a(Active)")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false))
                meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                meta.lore(listOf(
                    Component.text("§7This skin is currently active.").decoration(TextDecoration.ITALIC, false)
                ))
            } else {
                meta.displayName(Component.text(skinName)
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
                val priceText = if (price <= 0) "§aFree" else "§eCost: ${price.toInt()}"
                meta.lore(listOf(
                    Component.text(priceText).decoration(TextDecoration.ITALIC, false),
                    Component.text("§7Click to apply.").decoration(TextDecoration.ITALIC, false)
                ))
            }
            blockItem.itemMeta = meta

            val blockGuiItem = GuiItem(blockItem) {
                if (isActive) return@GuiItem
                if (price > 0 && !checkAndDeductCost(player, price)) {
                    player.sendMessage(Component.text("Not enough funds to purchase this skin!", NamedTextColor.RED))
                    return@GuiItem
                }
                val result = updateWarpSkin.execute(warp.id, skinKey)
                when (result) {
                    UpdateWarpSkinResult.SUCCESS -> {
                        player.sendMessage(Component.text("Waystone skin changed to $skinName!", NamedTextColor.GREEN))
                    }
                    UpdateWarpSkinResult.BLOCK_NOT_VALID -> {
                        player.sendMessage(Component.text("Invalid skin.", NamedTextColor.RED))
                    }
                    else -> {}
                }
                open()
            }
            blockListPane.addItem(blockGuiItem)
        }
        gui.addPane(blockListPane)
    }

    private fun checkAndDeductCost(player: Player, cost: Double): Boolean {
        if (cost <= 0) return true
        return when (configService.getTeleportCostType()) {
            CostType.MONEY -> {
                val economy = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
                if (economy?.has(player, cost) == true) {
                    economy.withdrawPlayer(player, cost)
                    true
                } else false
            }
            CostType.XP -> {
                if (player.totalExperience >= cost.toInt()) {
                    player.giveExp(-cost.toInt())
                    true
                } else false
            }
            CostType.ITEM -> {
                val material = try {
                    Material.valueOf(configService.getTeleportCostItem())
                } catch (_: IllegalArgumentException) {
                    Material.ENDER_PEARL
                }
                val needed = cost.toInt()
                var count = 0
                for (item in player.inventory.contents.filterNotNull()) {
                    if (item.type == material) count += item.amount
                }
                if (count < needed) return false
                var remaining = needed
                for (item in player.inventory.contents.filterNotNull()) {
                    if (item.type == material) {
                        val remove = minOf(item.amount, remaining)
                        item.amount -= remove
                        remaining -= remove
                        if (remaining <= 0) break
                    }
                }
                true
            }
        }
    }
}
