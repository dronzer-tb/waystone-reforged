package dev.mizarc.waystonewarps.infrastructure.services.geyser

import com.geysermenu.companion.api.GeyserMenuAPI
import dev.mizarc.waystonewarps.application.actions.world.CreateWarp
import dev.mizarc.waystonewarps.application.results.CreateWarpResult
import dev.mizarc.waystonewarps.infrastructure.mappers.toPosition3D
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Bedrock Custom Form for waystone creation/naming using GeyserMenu API.
 * Matches the Excalidraw design: title "Waystone Creator", name input, Submit/Cancel.
 */
class BedrockWarpNamingMenu(
    private val player: Player,
    private val api: GeyserMenuAPI,
    private val location: Location
) : KoinComponent {
    private val createWarp: CreateWarp by inject()

    fun open() {
        api.createCustomMenu("Waystone Creator", player.uniqueId)
            .input("name", "Waystone Name", "Enter a name here", "")
            .send { response ->
                if (response.wasClosed()) return@send

                val name = response.getString("name")
                if (name.isNullOrBlank()) {
                    player.sendMessage("§cName cannot be blank.")
                    open()
                    return@send
                }

                val belowLocation = location.clone().subtract(0.0, 1.0, 0.0)
                val result = createWarp.execute(
                    player.uniqueId,
                    name,
                    location.toPosition3D(),
                    location.world.uid,
                    location.world.getBlockAt(belowLocation).type.name
                )

                when (result) {
                    is CreateWarpResult.Success -> {
                        player.sendMessage("§aWaystone §e$name §acreated!")
                        location.world.playSound(
                            player.location,
                            Sound.BLOCK_VAULT_OPEN_SHUTTER,
                            SoundCategory.BLOCKS,
                            1.0f,
                            1.0f
                        )
                        BedrockWarpManagementMenu(player, api, result.warp).open()
                    }
                    is CreateWarpResult.LimitExceeded ->
                        player.sendMessage("§cYou have reached the maximum number of waystones.")
                    is CreateWarpResult.NameAlreadyExists -> {
                        player.sendMessage("§cA waystone with that name already exists.")
                        open()
                    }
                    is CreateWarpResult.NameCannotBeBlank -> {
                        player.sendMessage("§cName cannot be blank.")
                        open()
                    }
                }
            }
    }
}
