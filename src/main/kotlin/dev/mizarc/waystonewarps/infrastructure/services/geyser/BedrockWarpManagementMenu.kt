package dev.mizarc.waystonewarps.infrastructure.services.geyser

import dev.mizarc.waystonewarps.application.actions.management.*
import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.services.teleportation.CostType
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Bedrock form-based waystone editor menu matching the Excalidraw design.
 * Layout: Private, Player Management, Home, Rename, Skins, Protection Mode, Move
 */
class BedrockWarpManagementMenu(
    private val player: Player,
    private val warp: Warp
) : KoinComponent {
    private val toggleLock: ToggleLock by inject()
    private val updateWarpName: UpdateWarpName by inject()
    private val toggleHome: ToggleHome by inject()
    private val toggleProtection: ToggleProtection by inject()
    private val configService: ConfigService by inject()
    private val playerAttributeService: PlayerAttributeService by inject()

    fun open() {
        val buttons = mutableListOf<FormButton>()

        // Private / Public toggle
        val privacyLabel = if (warp.isLocked) "§c\uD83D\uDD12 Private" else "§a\uD83D\uDD13 Public"
        buttons.add(FormButton(privacyLabel))

        // Player Management
        buttons.add(FormButton("§e\uD83D\uDC65 Player Management"))

        // Home toggle
        val homeLabel = if (warp.isHome) "§c\uD83D\uDECF Home (Unset)" else "§a\uD83D\uDECF Set Home"
        buttons.add(FormButton(homeLabel))

        // Rename
        buttons.add(FormButton("§b✏ Rename"))

        // Skins
        buttons.add(FormButton("§d\uD83C\uDFA8 Skins"))

        // Protection Mode toggle
        val protLabel = if (warp.isProtected)
            "§c\uD83D\uDEE1 Protection (Disable)"
        else
            "§a\uD83D\uDEE1 Protection (Enable)"
        buttons.add(FormButton(protLabel))

        // Move
        buttons.add(FormButton("§6↕ Move"))

        BedrockSupport.sendSimpleForm(player,
            title = "§1Waystone Editor - ${warp.name}",
            content = buildStatusContent(),
            buttons = buttons,
            onButtonClicked = { index ->
                when (index) {
                    0 -> handleTogglePrivacy()
                    1 -> openPlayerManagementForm()
                    2 -> handleToggleHome()
                    3 -> openRenameForm()
                    4 -> player.sendMessage("§eSkins are only available on Java Edition.")
                    5 -> handleToggleProtection()
                    6 -> handleMove()
                }
            }
        )
    }

    private fun buildStatusContent(): String {
        val sb = StringBuilder()
        sb.appendLine("§7Status:")
        sb.appendLine(if (warp.isLocked) "  §c🔒 Private" else "  §a🔓 Public")
        sb.appendLine(if (warp.isHome) "  §a🏠 Home" else "  §7🏠 Not Home")
        sb.appendLine(if (warp.isProtected) "  §a🛡 Protected" else "  §7🛡 Not Protected")
        return sb.toString()
    }

    // --- Privacy ---

    private fun handleTogglePrivacy() {
        toggleLock.execute(player.uniqueId, warp.id)
        warp.isLocked = !warp.isLocked
        val msg = if (warp.isLocked) "§cWaystone set to private." else "§aWaystone set to public."
        player.sendMessage(msg)
        open()
    }

    // --- Player Management ---

    private fun openPlayerManagementForm() {
        BedrockSupport.sendSimpleForm(player,
            title = "Player Management - ${warp.name}",
            content = "Manage who can access this waystone.",
            buttons = listOf(FormButton("§aAdd Player"), FormButton("§cRemove Player"), FormButton("§7Back")),
            onButtonClicked = { index ->
                when (index) {
                    0 -> openAddPlayerForm()
                    1 -> openRemovePlayerForm()
                    2 -> open()
                }
            }
        )
    }

    private fun openAddPlayerForm() {
        BedrockSupport.sendCustomForm(player,
            title = "Add Player - ${warp.name}",
            elements = listOf(
                FormElement.Input("player", "Player Name", "Enter player name", "")
            ),
            onSubmit = { values ->
                val name = values["player"] ?: ""
                if (name.isBlank()) {
                    player.sendMessage("§cPlayer name cannot be blank.")
                } else {
                    player.performCommand("waystonewarps whitelist add ${warp.id} $name")
                    player.sendMessage("§aAdded §e$name §ato the whitelist.")
                }
                openPlayerManagementForm()
            }
        )
    }

    private fun openRemovePlayerForm() {
        BedrockSupport.sendCustomForm(player,
            title = "Remove Player - ${warp.name}",
            elements = listOf(
                FormElement.Input("player", "Player Name", "Enter player name", "")
            ),
            onSubmit = { values ->
                val name = values["player"] ?: ""
                if (name.isBlank()) {
                    player.sendMessage("§cPlayer name cannot be blank.")
                } else {
                    player.performCommand("waystonewarps whitelist remove ${warp.id} $name")
                    player.sendMessage("§cRemoved §e$name §cfrom the whitelist.")
                }
                openPlayerManagementForm()
            }
        )
    }

    // --- Home ---

    private fun handleToggleHome() {
        if (warp.isHome) {
            // Unsetting home costs money
            val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)
            val multiplier = configService.getHomeUnsetCostMultiplier()
            val unsetCost = baseCost * multiplier

            BedrockSupport.sendModalForm(player,
                title = "Unset Home",
                content = "Unsetting your home will cost §e${unsetCost.toInt()}§r. Continue?",
                button1 = "§aYes, Unset",
                button2 = "§cCancel",
                onButton1 = {
                    if (!checkAndDeductCost(player, unsetCost)) {
                        player.sendMessage("§cNot enough funds to unset home!")
                        open()
                        return@sendModalForm
                    }
                    val result = toggleHome.execute(player.uniqueId, warp.id)
                    if (result.isSuccess) {
                        warp.isHome = false
                        player.sendMessage("§cHome waystone unset.")
                    } else {
                        player.sendMessage("§cFailed to unset home.")
                    }
                    open()
                },
                onButton2 = { open() }
            )
        } else {
            val result = toggleHome.execute(player.uniqueId, warp.id)
            if (result.isSuccess) {
                warp.isHome = true
                player.sendMessage("§aThis waystone is now your home!")
            } else {
                player.sendMessage("§cFailed to set home.")
            }
            open()
        }
    }

    // --- Rename ---

    private fun openRenameForm() {
        BedrockSupport.sendCustomForm(player,
            title = "Rename Waystone",
            elements = listOf(
                FormElement.Input("name", "New Name", "Enter new name", warp.name)
            ),
            onSubmit = { values ->
                val name = values["name"] ?: ""
                if (name.isBlank()) {
                    player.sendMessage("§cName cannot be blank.")
                    openRenameForm()
                    return@sendCustomForm
                }
                val result = updateWarpName.execute(warp.id, player.uniqueId, name)
                when (result) {
                    UpdateWarpNameResult.SUCCESS -> {
                        warp.name = name
                        player.sendMessage("§aWaystone renamed to §e$name§a.")
                    }
                    UpdateWarpNameResult.NAME_ALREADY_TAKEN ->
                        player.sendMessage("§cA waystone with that name already exists.")
                    UpdateWarpNameResult.NAME_BLANK ->
                        player.sendMessage("§cName cannot be blank.")
                    else -> player.sendMessage("§cRename failed.")
                }
                open()
            }
        )
    }

    // --- Protection ---

    private fun handleToggleProtection() {
        if (!warp.isProtected) {
            val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)
            val multiplier = configService.getProtectionModeCostMultiplier()
            val protCost = baseCost * multiplier

            BedrockSupport.sendModalForm(player,
                title = "Enable Protection",
                content = "Enabling protection will cost §e${protCost.toInt()}§r. Continue?",
                button1 = "§aYes, Enable",
                button2 = "§cCancel",
                onButton1 = {
                    if (!checkAndDeductCost(player, protCost)) {
                        player.sendMessage("§cNot enough funds to enable protection!")
                        open()
                        return@sendModalForm
                    }
                    val result = toggleProtection.execute(player.uniqueId, warp.id)
                    if (result.isSuccess) {
                        warp.isProtected = true
                        player.sendMessage("§aProtection mode enabled!")
                    } else {
                        player.sendMessage("§cFailed to enable protection.")
                    }
                    open()
                },
                onButton2 = { open() }
            )
        } else {
            // Free to disable
            val result = toggleProtection.execute(player.uniqueId, warp.id)
            if (result.isSuccess) {
                warp.isProtected = false
                player.sendMessage("§cProtection mode disabled.")
            } else {
                player.sendMessage("§cFailed to disable protection.")
            }
            open()
        }
    }

    // --- Move ---

    private fun handleMove() {
        BedrockSupport.sendModalForm(player,
            title = "Move Waystone",
            content = "Move this waystone to your current location?",
            button1 = "§aYes, Move",
            button2 = "§cCancel",
            onButton1 = {
                player.performCommand("waystonewarps move ${warp.id}")
                player.sendMessage("§aWaystone moved to your current location.")
            },
            onButton2 = { open() }
        )
    }

    // --- Cost Deduction ---

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
