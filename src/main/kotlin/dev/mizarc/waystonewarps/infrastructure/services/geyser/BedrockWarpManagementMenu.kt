package dev.mizarc.waystonewarps.infrastructure.services.geyser

import com.geysermenu.companion.api.GeyserMenuAPI
import dev.mizarc.waystonewarps.application.actions.management.ToggleHome
import dev.mizarc.waystonewarps.application.actions.management.ToggleLock
import dev.mizarc.waystonewarps.application.actions.management.ToggleProtection
import dev.mizarc.waystonewarps.application.actions.management.UpdateWarpName
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.utils.PermissionHelper
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

    fun open() {
        val title = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_TITLE, warp.name)
        val accessStatus = if (warp.isLocked) "Private" else "Public"

        val builder = api.createSimpleMenu(title, player.uniqueId)
            .content("Manage your waystone warp.")

        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

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
        val canSetHome = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.home")
        if (canSetHome) {
            options.add("Home: $homeStatus (Toggle)")
            actions.add {
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
        val canToggleProt = PermissionHelper.canModifyWaystone(player, warp.playerId, "waystonewarps.bypass.protection")
        if (canToggleProt) {
            options.add("Protection: $protectionStatus (Toggle)")
            actions.add {
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
