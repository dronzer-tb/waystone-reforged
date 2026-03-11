package dev.mizarc.waystonewarps.infrastructure.services.geyser

import com.geysermenu.companion.api.GeyserMenuAPI
import dev.mizarc.waystonewarps.application.actions.discovery.GetWarpPlayerAccess
import dev.mizarc.waystonewarps.application.actions.discovery.RevokeDiscovery
import dev.mizarc.waystonewarps.application.actions.management.GetAllWarpSkins
import dev.mizarc.waystonewarps.application.actions.management.ToggleHome
import dev.mizarc.waystonewarps.application.actions.management.ToggleLock
import dev.mizarc.waystonewarps.application.actions.management.ToggleProtection
import dev.mizarc.waystonewarps.application.actions.management.UpdateWarpName
import dev.mizarc.waystonewarps.application.actions.management.UpdateWarpSkin
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.services.teleportation.CostType
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.utils.PermissionHelper
import dev.mizarc.waystonewarps.interaction.utils.getWarpMoveTool
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Form-based waystone editor menu for Bedrock players using GeyserMenu API.
 * Layout matches the Excalidraw design:
 * Private/Public → Discovered Players → Rename → Skins → Home → Move → Protection Mode → Back
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
    private val updateWarpSkin: UpdateWarpSkin by inject()
    private val getWarpPlayerAccess: GetWarpPlayerAccess by inject()
    private val revokeDiscovery: RevokeDiscovery by inject()
    private val getAllWarpSkins: GetAllWarpSkins by inject()
    private val localizationProvider: LocalizationProvider by inject()
    private val configService: ConfigService by inject()
    private val playerAttributeService: PlayerAttributeService by inject()

    fun open() {
        val title = "Waystone Editor"
        val accessLabel = if (warp.isLocked) "§cPrivate" else "§aPublic"

        val builder = api.createSimpleMenu(title, player.uniqueId)
            .content("Managing: ${warp.name}")

        val buttons = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // 1. Private/Public
        buttons.add("$accessLabel §r- Private/Public")
        actions.add { openAccessSubMenu() }

        // 2. Discovered Players
        val playerCount = getWarpPlayerAccess.execute(warp.id).count() - 1
        buttons.add("Discovered Players §7($playerCount)")
        actions.add { openDiscoveredPlayersMenu() }

        // 3. Rename
        buttons.add("Rename")
        actions.add { openRenameForm() }

        // 4. Skins
        buttons.add("Skins §7(${warp.block})")
        actions.add { openSkinsMenu() }

        // 5. Home
        val homeLabel = if (warp.isHome) "§aHome (Set)" else "§7Home (Not Set)"
        buttons.add(homeLabel)
        actions.add { openHomeSubMenu() }

        // 6. Move
        buttons.add("Move")
        actions.add {
            if (PermissionHelper.canRelocate(player, warp.playerId)) {
                givePlayerMoveTool()
                player.sendMessage("§aMove tool added to your inventory. Place it where you want the waystone.")
            } else {
                player.sendMessage("§cYou don't have permission to move this waystone.")
            }
        }

        // 7. Protection Mode
        val protLabel = if (warp.isProtected) "§aProtection Mode (On)" else "§cProtection Mode (Off)"
        buttons.add(protLabel)
        actions.add { openProtectionSubMenu() }

        // 8. Back
        buttons.add("§7Back")
        actions.add { /* close */ }

        for (btn in buttons) {
            builder.button(btn)
        }

        builder.send { response ->
            if (response.wasClosed()) return@send
            val index = response.buttonId
            if (index < 0 || index >= actions.size) return@send
            actions[index]()
        }
    }

    // --- Access Sub-Menu (PUBLIC/PRIVATE toggle + whitelist/revoke) ---

    private fun openAccessSubMenu() {
        if (!PermissionHelper.canChangeAccessControl(player, warp.playerId)) {
            player.sendMessage("§cYou don't have permission to change access control.")
            return
        }

        val currentStatus = if (warp.isLocked) "§cPRIVATE" else "§aPUBLIC"
        val builder = api.createSimpleMenu("Access Control", player.uniqueId)
            .content("Current: $currentStatus")

        val buttons = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Toggle to opposite
        if (warp.isLocked) {
            buttons.add("§aSet PUBLIC")
            actions.add {
                toggleLock.execute(player.uniqueId, warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"))
                player.sendMessage("§aWaystone is now Public.")
                open()
            }

            // Whitelist management (only when private)
            buttons.add("Whitelist")
            actions.add { openDiscoveredPlayersMenu() }

            buttons.add("Revoke Player")
            actions.add { openRevokePlayerMenu() }
        } else {
            buttons.add("§cSet PRIVATE")
            actions.add {
                toggleLock.execute(player.uniqueId, warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"))
                player.sendMessage("§cWaystone is now Private.")
                open()
            }
        }

        buttons.add("§7Back")
        actions.add { open() }

        for (btn in buttons) builder.button(btn)
        builder.send { response ->
            if (response.wasClosed()) return@send
            val index = response.buttonId
            if (index < 0 || index >= actions.size) return@send
            actions[index]()
        }
    }

    // --- Discovered Players Menu ---

    private fun openDiscoveredPlayersMenu() {
        if (!PermissionHelper.canManageWhitelist(player, warp.playerId)) {
            player.sendMessage("§cYou don't have permission to manage players.")
            return
        }

        val playerIds = getWarpPlayerAccess.execute(warp.id).filter { it != warp.playerId }
        if (playerIds.isEmpty()) {
            api.createSimpleMenu("Discovered Players", player.uniqueId)
                .content("No players have discovered this waystone yet.")
                .button("§7Back")
                .send { open() }
            return
        }

        val builder = api.createSimpleMenu("Discovered Players", player.uniqueId)
            .content("Players who discovered this waystone:")

        val names = playerIds.map { Bukkit.getOfflinePlayer(it).name ?: it.toString() }
        for (name in names) builder.button(name)
        builder.button("§7Back")

        builder.send { response ->
            if (response.wasClosed()) return@send
            if (response.buttonId == names.size) { open(); return@send }
            // Tapping a player name does nothing for now (info only)
            openDiscoveredPlayersMenu()
        }
    }

    // --- Revoke Player Menu ---

    private fun openRevokePlayerMenu() {
        val playerIds = getWarpPlayerAccess.execute(warp.id).filter { it != warp.playerId }
        if (playerIds.isEmpty()) {
            api.createSimpleMenu("Revoke Discovery", player.uniqueId)
                .content("No players to revoke.")
                .button("§7Back")
                .send { openAccessSubMenu() }
            return
        }

        val builder = api.createSimpleMenu("Revoke Discovery", player.uniqueId)
            .content("Select a player to revoke their discovery:")

        val names = playerIds.map { Pair(it, Bukkit.getOfflinePlayer(it).name ?: it.toString()) }
        for ((_, name) in names) builder.button("§c$name")
        builder.button("§7Back")

        builder.send { response ->
            if (response.wasClosed()) return@send
            if (response.buttonId == names.size) { openAccessSubMenu(); return@send }
            val (targetId, targetName) = names[response.buttonId]
            revokeDiscovery.execute(targetId, warp.id)
            player.sendMessage("§aRevoked discovery for $targetName.")
            openRevokePlayerMenu()
        }
    }

    // --- Rename Form ---

    private fun openRenameForm() {
        if (!PermissionHelper.canRename(player, warp.playerId)) {
            player.sendMessage("§cYou don't have permission to rename this waystone.")
            return
        }

        api.createCustomMenu("Rename", player.uniqueId)
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
                open()
            }
    }

    // --- Skins Menu ---

    private fun openSkinsMenu() {
        val skins = getAllWarpSkins.execute()
        if (skins.isEmpty()) {
            api.createSimpleMenu("Skins", player.uniqueId)
                .content("No skins available.")
                .button("§7Back")
                .send { open() }
            return
        }

        val builder = api.createSimpleMenu("Skins", player.uniqueId)
            .content("Current: ${warp.block}\nSelect a new skin:")

        for (skin in skins) {
            val label = if (skin == warp.block) "§a✓ $skin" else skin
            builder.button(label)
        }
        builder.button("§7Back")

        builder.send { response ->
            if (response.wasClosed()) return@send
            if (response.buttonId == skins.size) { open(); return@send }
            val selectedSkin = skins[response.buttonId]
            updateWarpSkin.execute(
                warpId = warp.id,
                blockName = selectedSkin,
            )
            player.sendMessage("§aWaystone skin changed to: $selectedSkin")
            open()
        }
    }

    // --- Home Sub-Menu (Set Home / Unset Home) ---

    private fun openHomeSubMenu() {
        val canSetHome = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.home")
        if (!canSetHome) {
            player.sendMessage("§cYou don't have permission to manage home waystone.")
            return
        }

        val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)
        val homeUnsetCost = baseCost * configService.getHomeUnsetCostMultiplier()

        val builder = api.createSimpleMenu("Home", player.uniqueId)
        val buttons = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (warp.isHome) {
            builder.content("This waystone is your §ahome§r.\nUnset cost: §e${homeUnsetCost.toInt()}")

            buttons.add("§cUnset Home")
            actions.add {
                if (!checkAndDeductCost(player, homeUnsetCost)) {
                    player.sendMessage("§cNot enough funds to unset home!")
                    return@add
                }
                toggleHome.execute(player.uniqueId, warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"))
                player.sendMessage("§aHome waystone has been unset.")
                open()
            }
        } else {
            builder.content("Set this waystone as your home.\nHome warps have §ahalf teleport cost§r.\n§aFree to set!")

            buttons.add("§aSet Home")
            actions.add {
                toggleHome.execute(player.uniqueId, warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"))
                player.sendMessage("§aThis waystone is now your home!")
                open()
            }
        }

        buttons.add("§7Back")
        actions.add { open() }

        for (btn in buttons) builder.button(btn)
        builder.send { response ->
            if (response.wasClosed()) return@send
            val index = response.buttonId
            if (index < 0 || index >= actions.size) return@send
            actions[index]()
        }
    }

    // --- Protection Sub-Menu (On / Off) ---

    private fun openProtectionSubMenu() {
        val canToggle = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.bypass.protection")
        if (!canToggle) {
            player.sendMessage("§cYou don't have permission to toggle protection.")
            return
        }

        val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)
        val protectionEnableCost = baseCost * configService.getProtectionModeCostMultiplier()

        val builder = api.createSimpleMenu("Protection Mode", player.uniqueId)
        val buttons = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (warp.isProtected) {
            builder.content("Protection is §aON§r.\nOnly you can break this waystone.\n§aFree to disable.")

            buttons.add("§cProtection Mode Off")
            actions.add {
                toggleProtection.execute(player.uniqueId, warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.protection"))
                player.sendMessage("§cProtection mode disabled.")
                open()
            }
        } else {
            builder.content("Protection is §cOFF§r.\nAnyone can break this waystone.\nCost to enable: §e${protectionEnableCost.toInt()}")

            buttons.add("§aProtection Mode On")
            actions.add {
                if (!checkAndDeductCost(player, protectionEnableCost)) {
                    player.sendMessage("§cNot enough funds to enable protection!")
                    return@add
                }
                toggleProtection.execute(player.uniqueId, warp.id,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.protection"))
                player.sendMessage("§aProtection mode enabled.")
                open()
            }
        }

        buttons.add("§7Back")
        actions.add { open() }

        for (btn in buttons) builder.button(btn)
        builder.send { response ->
            if (response.wasClosed()) return@send
            val index = response.buttonId
            if (index < 0 || index >= actions.size) return@send
            actions[index]()
        }
    }

    // --- Cost Helper ---

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
                } catch (_: IllegalArgumentException) { Material.ENDER_PEARL }
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

    // --- Move Tool Helper ---

    private fun givePlayerMoveTool() {
        val moveTool = getWarpMoveTool(warp)
        for (item in player.inventory.contents) {
            if (item == null) continue
            if (item.itemMeta != null && item.itemMeta == moveTool.itemMeta) return
        }
        player.inventory.addItem(moveTool)
    }
}
