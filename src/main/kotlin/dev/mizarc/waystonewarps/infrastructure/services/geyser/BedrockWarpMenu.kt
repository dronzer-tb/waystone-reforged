package dev.mizarc.waystonewarps.infrastructure.services.geyser

import dev.mizarc.waystonewarps.application.actions.discovery.GetPlayerWarpAccess
import dev.mizarc.waystonewarps.application.actions.teleport.TeleportPlayer
import dev.mizarc.waystonewarps.application.actions.whitelist.GetWhitelistedPlayers
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.floodgate.api.FloodgateApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Form-based warp list menu for Bedrock players using Floodgate + Cumulus forms.
 */
class BedrockWarpMenu(
    private val player: Player,
    private val plugin: JavaPlugin
) : KoinComponent {
    private val getPlayerWarpAccess: GetPlayerWarpAccess by inject()
    private val teleportPlayer: TeleportPlayer by inject()
    private val getWhitelistedPlayers: GetWhitelistedPlayers by inject()
    private val localizationProvider: LocalizationProvider by inject()

    fun open() {
        val warps = getPlayerWarpAccess.execute(player.uniqueId).sortedBy { it.name }

        if (warps.isEmpty()) {
            val form = SimpleForm.builder()
                .title("Waystone Warps")
                .content("You haven't discovered any waystone warps yet.")
                .button("Close")
                .build()
            FloodgateApi.getInstance().sendForm(player.uniqueId, form)
            return
        }

        val builder = SimpleForm.builder()
            .title("Waystone Warps")
            .content("Select a waystone to teleport to:")

        for (warp in warps) {
            val isLocked = warp.isLocked
                && !getWhitelistedPlayers.execute(warp.id).contains(player.uniqueId)
                && player.uniqueId != warp.playerId
            val label = if (isLocked) "§c🔒 ${warp.name}" else warp.name
            builder.button(label)
        }

        builder.validResultHandler { response ->
            val index = response.clickedButtonId()
            if (index < 0 || index >= warps.size) return@validResultHandler

            val selectedWarp = warps[index]
            val isLocked = selectedWarp.isLocked
                && !getWhitelistedPlayers.execute(selectedWarp.id).contains(player.uniqueId)
                && player.uniqueId != selectedWarp.playerId

            if (isLocked) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendActionBar(
                        Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_LOCKED))
                            .color(PrimaryColourPalette.CANCELLED.color)
                    )
                })
                return@validResultHandler
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                teleportToWarp(selectedWarp)
            })
        }

        FloodgateApi.getInstance().sendForm(player.uniqueId, builder.build())
    }

    private fun teleportToWarp(warp: Warp) {
        teleportPlayer.execute(
            player.uniqueId, warp,
            onPending = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_PENDING, warp.name))
                        .color(PrimaryColourPalette.INFO.color)
                )
            },
            onSuccess = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_SUCCESS, warp.name))
                        .color(PrimaryColourPalette.SUCCESS.color)
                )
            },
            onFailure = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_FAILED))
                        .color(PrimaryColourPalette.FAILED.color)
                )
            },
            onInsufficientFunds = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_INSUFFICIENT_FUNDS))
                        .color(PrimaryColourPalette.CANCELLED.color)
                )
            },
            onWorldNotFound = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_WORLD_NOT_FOUND))
                        .color(PrimaryColourPalette.FAILED.color)
                )
            },
            onLocked = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_LOCKED))
                        .color(PrimaryColourPalette.CANCELLED.color)
                )
            },
            onCanceled = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_CANCELLED))
                        .color(PrimaryColourPalette.CANCELLED.color)
                )
            },
            onPermissionDenied = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_NO_PERMISSION))
                        .color(PrimaryColourPalette.CANCELLED.color)
                )
            },
            onInterworldPermissionDenied = {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_TELEPORT_NO_INTERWORLD_PERMISSION))
                        .color(PrimaryColourPalette.CANCELLED.color)
                )
            }
        )
    }
}
