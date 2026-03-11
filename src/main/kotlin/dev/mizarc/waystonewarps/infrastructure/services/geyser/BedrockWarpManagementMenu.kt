package dev.mizarc.waystonewarps.infrastructure.services.geyser

import com.geysermenu.companion.api.GeyserMenuAPI
import dev.mizarc.waystonewarps.application.actions.management.ToggleHome
import dev.mizarc.waystonewarps.application.actions.management.ToggleLock
import dev.mizarc.waystonewarps.application.actions.management.ToggleProtection
import dev.mizarc.waystonewarps.application.actions.management.UpdateWarpName
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.services.teleportation.CostType
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.utils.PermissionHelper
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Form-based waystone management menu for Bedrock players using GeyserMenu API.
 */
class BedrockWarpManagementMenu(
    private val player: Player,
    private val api: GeyserMenuAPI,
    private val warp: Warp
) : KoinComponent {
    private val toggleLock: ToggleLock by inject()
    private val toggleHome: ToggleHome by inject()
    private val toggleProtection: ToggleProtection by inject()
    private val updateWarpName: UpdateWarpName by inject()
    private val localizationProvider: LocalizationProvider by inject()
    private val configService: ConfigService by inject()
    private val playerAttributeService: PlayerAttributeService by inject()

    fun open() {
        val title = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_TITLE, warp.name)
        val accessStatus = if (warp.isLocked) "Private" else "Public"

        val builder = api.createSimpleMenu(title, player.uniqueId)
            .content("Manage your waystone warp.")

        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)

        // Access toggle
        if (PermissionHelper.canChangeAccessControl(player, warp.playerId)) {
            options.add("Access: $accessStatus (Toggle)")
            actions.add {
                toggleLock.execute(
                    playerId = player.uniqueId,
                    warpId = warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"),
                )
                open()
            }
        } else {
            options.add("Access: $accessStatus")
            actions.add { open() }
        }

        // Home toggle
        val homeStatus = if (warp.isHome) "§aSet" else "§7Not Set"
        val homeUnsetCost = baseCost * configService.getHomeUnsetCostMultiplier()
        val canSetHome = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.home")
        if (canSetHome) {
            val costInfo = if (warp.isHome) " (Unset cost: ${homeUnsetCost.toInt()})" else " (Free)"
            options.add("Home: $homeStatus$costInfo")
            actions.add {
                if (warp.isHome) {
                    if (!checkAndDeductCost(player, homeUnsetCost)) {
                        player.sendMessage("§cNot enough funds to unset home!")
                        return@add
                    }
                }
                toggleHome.execute(
                    playerId = player.uniqueId,
                    warpId = warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"),
                )
                open()
            }
        } else {
            options.add("Home: $homeStatus")
            actions.add { open() }
        }

        // Rename
        if (PermissionHelper.canRename(player, warp.playerId)) {
            options.add("Rename Waystone")
            actions.add { openRenameForm() }
        }

        // Protection toggle
        val protectionStatus = if (warp.isProtected) "§aOn" else "§cOff"
        val protectionEnableCost = baseCost * configService.getProtectionModeCostMultiplier()
        val canToggleProt = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.bypass.protection")
        if (canToggleProt) {
            val costInfo = if (!warp.isProtected) " (Enable cost: ${protectionEnableCost.toInt()})" else " (Free to disable)"
            options.add("Protection: $protectionStatus$costInfo")
            actions.add {
                if (!warp.isProtected) {
                    if (!checkAndDeductCost(player, protectionEnableCost)) {
                        player.sendMessage("§cNot enough funds to enable protection!")
                        return@add
                    }
                }
                toggleProtection.execute(
                    playerId = player.uniqueId,
                    warpId = warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.protection"),
                )
                open()
            }
        } else {
            options.add("Protection: $protectionStatus")
            actions.add { open() }
        }

        // Close
        options.add("Close")
        actions.add { /* do nothing */ }

        for (option in options) {
            builder.button(option)
        }

        builder.send { response ->
            if (response.wasClosed()) return@send
            val index = response.buttonId
            if (index < 0 || index >= actions.size) return@send
            actions[index]()
        }
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

    private fun openRenameForm() {
        api.createCustomMenu("Rename Waystone", player.uniqueId)
            .label("Enter a new name for the waystone:")
            .input("name", "New Name", "Enter name...", warp.name)
            .send { response ->
                if (response.wasClosed()) return@send
                val newName = response.getString("name")
                if (newName != null && newName.isNotBlank()) {
                    val result = updateWarpName.execute(
                        warpId = warp.id,
                        editorPlayerId = player.uniqueId,
                        name = newName,
                        bypassOwnership = player.hasPermission("waystonewarps.bypass.rename"),
                    )
                    when (result) {
                        UpdateWarpNameResult.SUCCESS ->
                            player.sendMessage("§aWaystone renamed to: $newName")
                        UpdateWarpNameResult.NAME_BLANK ->
                            player.sendMessage("§cName cannot be blank.")
                        UpdateWarpNameResult.NAME_ALREADY_TAKEN ->
                            player.sendMessage("§cA waystone with that name already exists.")
                        UpdateWarpNameResult.NOT_AUTHORIZED ->
                            player.sendMessage("§cYou don't have permission to rename this waystone.")
                        UpdateWarpNameResult.WARP_NOT_FOUND ->
                            player.sendMessage("§cWaystone not found.")
                    }
                }
            }
    }
}
