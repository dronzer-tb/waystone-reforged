package dev.mizarc.waystonewarps.infrastructure.services.geyser

import dev.mizarc.waystonewarps.application.actions.discovery.GetWarpPlayerAccess
import dev.mizarc.waystonewarps.application.actions.discovery.RevokeDiscovery
import dev.mizarc.waystonewarps.application.actions.management.*
import dev.mizarc.waystonewarps.application.actions.whitelist.GetWhitelistedPlayers
import dev.mizarc.waystonewarps.application.actions.whitelist.ToggleWhitelist
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.services.teleportation.CostType
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Bedrock form-based waystone editor menu.
 * Buttons use built-in Bedrock texture paths for icons.
 */
class BedrockWarpManagementMenu(
    private val player: Player,
    private val warp: Warp
) : KoinComponent {
    private val toggleLock: ToggleLock by inject()
    private val updateWarpName: UpdateWarpName by inject()
    private val toggleHome: ToggleHome by inject()
    private val toggleProtection: ToggleProtection by inject()
    private val getWarpPlayerAccess: GetWarpPlayerAccess by inject()
    private val getWhitelistedPlayers: GetWhitelistedPlayers by inject()
    private val toggleWhitelist: ToggleWhitelist by inject()
    private val revokeDiscovery: RevokeDiscovery by inject()
    private val configService: ConfigService by inject()
    private val playerAttributeService: PlayerAttributeService by inject()

    fun open() {
        val buttons = mutableListOf<FormButton>()

        // 0: Public / Private
        if (warp.isLocked) {
            buttons.add(FormButton("§l§8Private", imagePath = "textures/blocks/redstone_torch_on"))
        } else {
            buttons.add(FormButton("§l§8Public", imagePath = "textures/blocks/lever"))
        }

        // 1: Discovered Players
        buttons.add(FormButton("§l§8Discovered Players", imagePath = "textures/ui/icon_steve"))

        // 2: Rename
        buttons.add(FormButton("§l§8Rename", imagePath = "textures/items/name_tag"))

        // 3: Skins
        buttons.add(FormButton("§l§8Skins", imageUrl = "https://static.wikia.nocookie.net/minecraft_gamepedia/images/3/32/Lodestone_JE1_BE1.png/revision/latest?cb=20200325183527"))

        // 4: Home
        if (warp.isHome) {
            buttons.add(FormButton("§l§8Home §c(On)", imagePath = "textures/items/bed_red"))
        } else {
            buttons.add(FormButton("§l§8Home §8(Off)", imagePath = "textures/items/bed_white"))
        }

        // 5: Move
        buttons.add(FormButton("§l§8Move", imagePath = "textures/blocks/piston_side"))

        // 6: Protection
        if (warp.isProtected) {
            buttons.add(FormButton("§l§8Protection §a(On)", imageUrl = "https://static.wikia.nocookie.net/minecraft_gamepedia/images/9/99/Obsidian_JE3_BE2.png/revision/latest?cb=20200124042057"))
        } else {
            buttons.add(FormButton("§l§8Protection §8(Off)", imageUrl = "https://static.wikia.nocookie.net/minecraft_gamepedia/images/7/7f/Crying_Obsidian_JE1_BE1.png/revision/latest?cb=20200302214526"))
        }

        // 7: Back
        buttons.add(FormButton("§l§8Back", imagePath = "textures/items/nether_star"))

        BedrockSupport.sendSimpleForm(player,
            title = "§l§8Waystone Editor - ${warp.name}",
            content = "",
            buttons = buttons,
            onButtonClicked = { index ->
                when (index) {
                    0 -> handleTogglePrivacy()
                    1 -> openDiscoveredPlayersMenu()
                    2 -> openRenameForm()
                    3 -> player.sendMessage("§eSkins are only available on Java Edition.")
                    4 -> handleToggleHome()
                    5 -> handleMove()
                    6 -> handleToggleProtection()
                    7 -> { /* back / close */ }
                }
            }
        )
    }

    // --- Privacy ---

    private fun handleTogglePrivacy() {
        toggleLock.execute(player.uniqueId, warp.id)
        warp.isLocked = !warp.isLocked
        val msg = if (warp.isLocked) "§cWaystone set to private." else "§aWaystone set to public."
        player.sendMessage(msg)
        open()
    }

    // --- Discovered Players ---

    private fun openDiscoveredPlayersMenu() {
        val discoveredPlayerIds = getWarpPlayerAccess.execute(warp.id)
        val whitelistedIds = getWhitelistedPlayers.execute(warp.id).toSet()

        if (discoveredPlayerIds.isEmpty()) {
            BedrockSupport.sendSimpleForm(player,
                title = "§l§8Discovered Players",
                content = "No players have discovered this waystone yet.",
                buttons = listOf(FormButton("§l§8Back", imagePath = "textures/items/nether_star")),
                onButtonClicked = { open() }
            )
            return
        }

        val buttons = discoveredPlayerIds.map { uuid ->
            val name = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString().take(8)
            val ownerTag = if (uuid == warp.playerId) " §6★" else ""
            val whitelistTag = if (whitelistedIds.contains(uuid)) " §a✔" else ""
            FormButton("§l§8$name$ownerTag$whitelistTag", imagePath = "textures/ui/icon_steve")
        }.toMutableList()
        buttons.add(FormButton("§l§8Back", imagePath = "textures/items/nether_star"))

        BedrockSupport.sendSimpleForm(player,
            title = "§l§8Discovered Players - ${warp.name}",
            content = "Select a player to manage. §a✔ = whitelisted §6★ = owner",
            buttons = buttons,
            onButtonClicked = { index ->
                if (index >= discoveredPlayerIds.size) {
                    open()
                    return@sendSimpleForm
                }
                openPlayerActionMenu(discoveredPlayerIds[index])
            }
        )
    }

    private fun openPlayerActionMenu(targetId: java.util.UUID) {
        val targetName = Bukkit.getOfflinePlayer(targetId).name ?: targetId.toString().take(8)
        val isWhitelisted = getWhitelistedPlayers.execute(warp.id).contains(targetId)
        val isOwner = targetId == warp.playerId

        val whitelistLabel = if (isWhitelisted) "§l§8Remove from Whitelist" else "§l§8Add to Whitelist"

        val buttons = mutableListOf(
            FormButton(whitelistLabel, imagePath = "textures/items/lantern")
        )
        // Owner cannot be revoked
        if (!isOwner) {
            buttons.add(FormButton("§l§cRevoke Discovery", imageUrl = "https://mcdf.wiki.gg/images/Barrier.png?ff8ff1"))
        }
        buttons.add(FormButton("§l§8Back", imagePath = "textures/items/nether_star"))

        val statusText = buildString {
            if (isOwner) append("§6Owner of this waystone\n")
            append(if (isWhitelisted) "§aCurrently whitelisted" else "§7Not whitelisted")
        }

        BedrockSupport.sendSimpleForm(player,
            title = "§l§8Player: $targetName",
            content = statusText,
            buttons = buttons,
            onButtonClicked = { index ->
                // Button indices shift if owner (no revoke button)
                if (isOwner) {
                    when (index) {
                        0 -> {
                            handleWhitelistToggle(targetId, targetName)
                        }
                        1 -> openDiscoveredPlayersMenu()
                    }
                } else {
                    when (index) {
                        0 -> {
                            handleWhitelistToggle(targetId, targetName)
                        }
                        1 -> {
                            BedrockSupport.sendModalForm(player,
                                title = "§l§8Revoke Discovery",
                                content = "Remove §e$targetName§r's discovery of this waystone?",
                                button1 = "§cYes, Revoke",
                                button2 = "§aCancel",
                                onButton1 = {
                                    revokeDiscovery.execute(targetId, warp.id)
                                    player.sendMessage("§c$targetName's discovery revoked.")
                                    openDiscoveredPlayersMenu()
                                },
                                onButton2 = { openPlayerActionMenu(targetId) }
                            )
                        }
                        2 -> openDiscoveredPlayersMenu()
                    }
                }
            }
        )
    }

    private fun handleWhitelistToggle(targetId: java.util.UUID, targetName: String) {
        val result = toggleWhitelist.execute(player.uniqueId, warp.id, targetId)
        if (result.isSuccess) {
            val added = result.getOrNull() == true
            player.sendMessage(if (added) "§a$targetName added to whitelist." else "§c$targetName removed from whitelist.")
        } else {
            player.sendMessage("§cFailed to toggle whitelist.")
        }
        openDiscoveredPlayersMenu()
    }

    // --- Rename ---

    private fun openRenameForm() {
        BedrockSupport.sendCustomForm(player,
            title = "§l§8Rename Waystone",
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

    // --- Home ---

    private fun handleToggleHome() {
        if (warp.isHome) {
            val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)
            val multiplier = configService.getHomeUnsetCostMultiplier()
            val unsetCost = baseCost * multiplier

            BedrockSupport.sendModalForm(player,
                title = "§l§8Unset Home",
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

    // --- Move ---

    private fun handleMove() {
        BedrockSupport.sendModalForm(player,
            title = "§l§8Move Waystone",
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

    // --- Protection ---

    private fun handleToggleProtection() {
        if (!warp.isProtected) {
            val baseCost = playerAttributeService.getTeleportCost(player.uniqueId)
            val multiplier = configService.getProtectionModeCostMultiplier()
            val protCost = baseCost * multiplier

            BedrockSupport.sendModalForm(player,
                title = "§l§8Enable Protection",
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
