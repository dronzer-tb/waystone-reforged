package dev.mizarc.waystonewarps.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import dev.mizarc.waystonewarps.application.actions.management.GetHomeWarp
import dev.mizarc.waystonewarps.application.services.TeleportationService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@CommandAlias("home")
@CommandPermission("waystonewarps.home")
class HomeCommand: BaseCommand(), KoinComponent {
    private val getHomeWarp: GetHomeWarp by inject()
    private val teleportationService: TeleportationService by inject()

    @Default
    fun onHome(player: Player) {
        val homeWarp = getHomeWarp.execute(player.uniqueId)
        if (homeWarp == null) {
            player.sendMessage(Component.text("You don't have a home waystone set.", NamedTextColor.RED))
            return
        }

        teleportationService.scheduleDelayedTeleport(
            playerId = player.uniqueId,
            warp = homeWarp,
            delaySeconds = 0,
            onSuccess = {
                player.sendMessage(Component.text("Teleported to home waystone: ${homeWarp.name}", NamedTextColor.GREEN))
            },
            onPending = {
                player.sendMessage(Component.text("Teleporting to home waystone...", NamedTextColor.YELLOW))
            },
            onInsufficientFunds = {
                player.sendMessage(Component.text("You don't have enough to teleport.", NamedTextColor.RED))
            },
            onCanceled = {
                player.sendMessage(Component.text("Teleportation cancelled.", NamedTextColor.RED))
            },
            onWorldNotFound = {
                player.sendMessage(Component.text("The world containing your home waystone could not be found.", NamedTextColor.RED))
            },
            onLocked = {
                player.sendMessage(Component.text("Your home waystone is locked.", NamedTextColor.RED))
            },
            onFailure = {
                player.sendMessage(Component.text("Failed to teleport.", NamedTextColor.RED))
            },
            onPermissionDenied = {
                player.sendMessage(Component.text("You don't have permission to teleport.", NamedTextColor.RED))
            },
            onInterworldPermissionDenied = {
                player.sendMessage(Component.text("You don't have permission for inter-world teleportation.", NamedTextColor.RED))
            }
        )
    }
}
