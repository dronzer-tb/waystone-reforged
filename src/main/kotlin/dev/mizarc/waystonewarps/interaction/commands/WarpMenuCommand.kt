package dev.mizarc.waystonewarps.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional
import dev.mizarc.waystonewarps.WaystoneWarps
import dev.mizarc.waystonewarps.infrastructure.services.geyser.BedrockWarpMenu
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.menus.use.WarpMenu
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@CommandAlias("warpmenu|ww")
@CommandPermission("waystonewarps.command.warpmenu")
class WarpMenuCommand: BaseCommand(), KoinComponent {
    private val localizationProvider: LocalizationProvider by inject()

    @Default
    fun onWarp(player: Player, @Optional backCommand: String? = null) {
        val plugin = JavaPlugin.getPlugin(WaystoneWarps::class.java)
        val geyserIntegration = plugin.getGeyserMenuIntegration()
        if (geyserIntegration?.isBedrockPlayer(player) == true) {
            BedrockWarpMenu(player, plugin).open()
        } else {
            val menuNavigator = MenuNavigator(player)
            WarpMenu(player, menuNavigator, localizationProvider).open()
        }
    }
}