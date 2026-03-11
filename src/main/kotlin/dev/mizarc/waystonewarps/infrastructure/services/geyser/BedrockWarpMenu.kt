package dev.mizarc.waystonewarps.infrastructure.services.geyser

import dev.mizarc.waystonewarps.application.actions.discovery.GetPlayerWarpAccess
import dev.mizarc.waystonewarps.application.actions.teleport.TeleportPlayer
import dev.mizarc.waystonewarps.application.actions.whitelist.GetWhitelistedPlayers
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BedrockWarpMenu(
    private val player: Player
) : KoinComponent {
    private val getPlayerWarpAccess: GetPlayerWarpAccess by inject()
    private val teleportPlayer: TeleportPlayer by inject()
    private val getWhitelistedPlayers: GetWhitelistedPlayers by inject()
    private val localizationProvider: LocalizationProvider by inject()

    fun open() {
        val warps = getPlayerWarpAccess.execute(player.uniqueId).sortedBy { it.name }

        if (warps.isEmpty()) {
            BedrockSupport.sendSimpleForm(player,
                title = "Waystone Warps",
                content = "You haven't discovered any waystone warps yet.",
                buttons = listOf(FormButton("Close")),
                onButtonClicked = { /* close */ }
            )
            return
        }

        val buttons = warps.map { warp ->
            val isLocked = warp.isLocked
                && !getWhitelistedPlayers.execute(warp.id).contains(player.uniqueId)
                && player.uniqueId != warp.playerId
            FormButton(if (isLocked) "§c🔒 ${warp.name}" else warp.name)
        }

        BedrockSupport.sendSimpleForm(player,
            title = "Waystone Warps",
            content = "Select a waystone to teleport to:",
            buttons = buttons,
            onButtonClicked = { index ->
                if (index < 0 || index >= warps.size) return@sendSimpleForm
                val selectedWarp = warps[index]
                val isLocked = selectedWarp.isLocked
                    && !getWhitelistedPlayers.execute(selectedWarp.id).contains(player.uniqueId)
                    && player.uniqueId != selectedWarp.playerId
                if (isLocked) {
                    player.sendActionBar(
                        Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_LOCKED))
                            .color(PrimaryColourPalette.CANCELLED.color)
                    )
                    return@sendSimpleForm
                }
                teleportToWarp(selectedWarp)
            }
        )
    }

    private fun teleportToWarp(warp: Warp) {
        teleportPlayer.execute(
            player.uniqueId, warp,
            onPending = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_PENDING, warp.name)).color(PrimaryColourPalette.INFO.color))
            },
            onSuccess = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_SUCCESS, warp.name)).color(PrimaryColourPalette.SUCCESS.color))
            },
            onFailure = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_FAILED)).color(PrimaryColourPalette.FAILED.color))
            },
            onInsufficientFunds = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_INSUFFICIENT_FUNDS)).color(PrimaryColourPalette.CANCELLED.color))
            },
            onWorldNotFound = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_WORLD_NOT_FOUND)).color(PrimaryColourPalette.FAILED.color))
            },
            onLocked = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_LOCKED)).color(PrimaryColourPalette.CANCELLED.color))
            },
            onCanceled = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_CANCELLED)).color(PrimaryColourPalette.CANCELLED.color))
            },
            onPermissionDenied = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_NO_PERMISSION)).color(PrimaryColourPalette.CANCELLED.color))
            },
            onInterworldPermissionDenied = {
                player.sendActionBar(Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_NO_INTERWORLD_PERMISSION)).color(PrimaryColourPalette.CANCELLED.color))
            }
        )
    }
}
