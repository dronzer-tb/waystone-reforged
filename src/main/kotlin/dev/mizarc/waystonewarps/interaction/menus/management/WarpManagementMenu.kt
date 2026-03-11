package dev.mizarc.waystonewarps.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import dev.mizarc.waystonewarps.application.actions.discovery.GetWarpPlayerAccess
import dev.mizarc.waystonewarps.application.actions.management.ToggleHome
import dev.mizarc.waystonewarps.application.actions.management.ToggleLock
import dev.mizarc.waystonewarps.application.actions.management.ToggleProtection
import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.services.teleportation.CostType
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.PermissionHelper
import dev.mizarc.waystonewarps.interaction.utils.getWarpMoveTool
import dev.mizarc.waystonewarps.interaction.utils.lore
import dev.mizarc.waystonewarps.interaction.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarpManagementMenu(private val player: Player, private val menuNavigator: MenuNavigator,
                         private val warp: Warp): Menu, KoinComponent {
    private val getWarpPlayerAccess: GetWarpPlayerAccess by inject()
    private val toggleLock: ToggleLock by inject()
    private val toggleHome: ToggleHome by inject()
    private val toggleProtection: ToggleProtection by inject()
    private val localizationProvider: LocalizationProvider by inject()
    private val configService: ConfigService by inject()
    private val playerAttributeService: PlayerAttributeService by inject()

    override fun open() {
        val title = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_TITLE, warp.name)
        val gui = ChestGui(1, title)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }

        val pane = StaticPane(0, 0, 9, 1)
        gui.addPane(pane)

        // Slot 0: Access toggle (Public/Private)
        val canChangeAccess = PermissionHelper.canChangeAccessControl(player, warp.playerId)

        val privacyIcon: ItemStack = if (warp.isLocked) {
            val accessName = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_NAME)
            val privateStatus = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_NAME_PRIVATE)
            val accessParts = accessName.split("{0}")
            
            val privateText = Component.text()
                .append(Component.text(accessParts[0].trimEnd(), PrimaryColourPalette.PRIMARY.color!!).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" "))
                .append(Component.text(
                    privateStatus,
                    PrimaryColourPalette.CANCELLED.color!!
                ).decoration(TextDecoration.ITALIC, false))
                .append(if (accessParts.size > 1) Component.text(accessParts[1].trimStart()) else Component.empty())
                .build()

            val item = ItemStack(Material.LEVER)
                .name(privateText)
            if (canChangeAccess) {
                item.lore(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_LORE_PRIVATE))
            } else {
                item.lore(
                    localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_LORE_PRIVATE),
                    localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_LORE_NO_PERM)
                )
            }
            item
        } else {
            val accessName = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_NAME)
            val publicStatus = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_NAME_PUBLIC)
            val accessParts = accessName.split("{0}")

            val publicText = Component.text()
                .append(Component.text(accessParts[0].trimEnd(), PrimaryColourPalette.PRIMARY.color!!).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" "))
                .append(Component.text(
                    publicStatus,
                    PrimaryColourPalette.SUCCESS.color!!
                ).decoration(TextDecoration.ITALIC, false))
                .append(if (accessParts.size > 1) Component.text(accessParts[1].trimStart()) else Component.empty())
                .build()

            val item = ItemStack(Material.REDSTONE_TORCH)
                .name(publicText)
            if (canChangeAccess) {
                item.lore(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_LORE_PUBLIC))
            } else {
                item.lore(
                    localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_LORE_PUBLIC),
                    localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_ACCESS_LORE_NO_PERM)
                )
            }
            item
        }
        val guiPrivacyItem = GuiItem(privacyIcon) {
            if (canChangeAccess) {
                toggleLock.execute(
                    playerId = player.uniqueId,
                    warpId = warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"),
                )
                open()
            }
        }
        pane.addItem(guiPrivacyItem, 0, 0)

        // Slot 1: Player management
        val canManageWhitelist = PermissionHelper.canManageWhitelist(player, warp.playerId)
        val playerCount = getWarpPlayerAccess.execute(warp.id).count() - 1
        val playerCountItem = ItemStack(Material.PLAYER_HEAD)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_PLAYERS))
        if (canManageWhitelist) {
            playerCountItem.lore(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_PLAYERS_LORE, playerCount.toString()))
        } else {
            playerCountItem.lore(
                localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_PLAYERS_LORE, playerCount.toString()),
                localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_COMMON_NO_PERMISSION)
            )
        }
        val guiPlayerCountItem = GuiItem(playerCountItem) {
            if (canManageWhitelist) {
                menuNavigator.openMenu(WarpPlayerMenu(player, menuNavigator, warp, localizationProvider))
            }
        }
        pane.addItem(guiPlayerCountItem, 1, 0)

        // Slot 2: Home toggle (free to set, costs to unset)
        val canSetHome = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.home")
        val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)
        val homeUnsetCost = baseCost * configService.getHomeUnsetCostMultiplier()
        val homeItem = if (warp.isHome) {
            ItemStack(Material.RED_BED)
                .name(Component.text("Home", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
                .lore("§7This waystone is set as your home.", "§7Cost to unset: §e${homeUnsetCost.toInt()}", "§eClick to unset home.")
        } else {
            ItemStack(Material.WHITE_BED)
                .name(Component.text("Home", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .lore("§7Set this waystone as your home.", "§7Home warps have reduced teleport cost.", "§aFree to set!", "§eClick to set as home.")
        }
        if (!canSetHome) {
            homeItem.lore("§7Home waystone setting.", "§cYou don't have permission.")
        }
        val guiHomeItem = GuiItem(homeItem) {
            if (canSetHome) {
                // Unsetting home costs money
                if (warp.isHome) {
                    if (!checkAndDeductCost(player, homeUnsetCost)) {
                        player.sendMessage(Component.text("Not enough funds to unset home!", NamedTextColor.RED))
                        return@GuiItem
                    }
                }
                toggleHome.execute(
                    playerId = player.uniqueId,
                    warpId = warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"),
                )
                open()
            }
        }
        pane.addItem(guiHomeItem, 2, 0)

        // Slot 3: Rename
        val canRename = PermissionHelper.canRename(player, warp.playerId)
        val renamingItem = ItemStack(Material.NAME_TAG)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_RENAME))
        if (canRename) {
            renamingItem.lore(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_RENAME_LORE))
        } else {
            renamingItem.lore(
                localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_RENAME_LORE),
                localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_COMMON_NO_PERMISSION)
            )
        }
        val guiRenamingItem = GuiItem(renamingItem) {
            if (canRename) {
                menuNavigator.openMenu(WarpRenamingMenu(player, menuNavigator, warp, localizationProvider))
            }
        }
        pane.addItem(guiRenamingItem, 3, 0)

        // Slot 4: Skins
        val skinViewItem = ItemStack(Material.valueOf(warp.block))
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_SKINS))
            .lore(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_SKINS_LORE))
        val guiSkinViewItem = GuiItem(skinViewItem) {
            menuNavigator.openMenu(WarpSkinsMenu(player, menuNavigator, localizationProvider))
        }
        pane.addItem(guiSkinViewItem, 4, 0)

        // Slot 5: Protection mode (costs to enable, free to disable)
        val canToggleProtection = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.bypass.protection")
        val protectionEnableCost = baseCost * configService.getProtectionModeCostMultiplier()
        val protectionItem = if (warp.isProtected) {
            ItemStack(Material.OBSIDIAN)
                .name(Component.text("Protection: On", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
                .lore("§7Only the owner can break this waystone.", "§7Other players take damage when attempting.", "§aFree to disable.", "§eClick to disable protection.")
        } else {
            ItemStack(Material.CRYING_OBSIDIAN)
                .name(Component.text("Protection: Off", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                .lore("§7Anyone can break this waystone.", "§7Cost to enable: §e${protectionEnableCost.toInt()}", "§eClick to enable protection.")
        }
        if (!canToggleProtection) {
            protectionItem.lore("§7Waystone protection mode.", "§cYou don't have permission.")
        }
        val guiProtectionItem = GuiItem(protectionItem) {
            if (canToggleProtection) {
                // Enabling protection costs money
                if (!warp.isProtected) {
                    if (!checkAndDeductCost(player, protectionEnableCost)) {
                        player.sendMessage(Component.text("Not enough funds to enable protection!", NamedTextColor.RED))
                        return@GuiItem
                    }
                }
                toggleProtection.execute(
                    playerId = player.uniqueId,
                    warpId = warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.protection"),
                )
                open()
            }
        }
        pane.addItem(guiProtectionItem, 5, 0)

        // Slot 6: Space (empty/decorative)
        val spaceItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
            .name(Component.text(" "))
        val guiSpaceItem = GuiItem(spaceItem) { /* no action */ }
        pane.addItem(guiSpaceItem, 6, 0)

        // Slot 8: Move
        val canRelocate = PermissionHelper.canRelocate(player, warp.playerId)
        val moveItem = ItemStack(Material.PISTON)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_MOVE))
        if (canRelocate) {
            moveItem.lore(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_MOVE_LORE))
        } else {
            moveItem.lore(
                localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_MOVE_LORE),
                localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_COMMON_NO_PERMISSION)
            )
        }
        val guiMoveItem = GuiItem(moveItem) {
            if (canRelocate) {
                givePlayerMoveTool(player)
            }
        }
        pane.addItem(guiMoveItem, 8, 0)

        gui.show(player)
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

    private fun givePlayerMoveTool(player: Player) {
        for (item in player.inventory.contents) {
            if (item == null) continue
            if (item.itemMeta != null && item.itemMeta == getWarpMoveTool(warp).itemMeta) {
                return
            }
        }
        player.inventory.addItem(getWarpMoveTool(warp))
    }
}