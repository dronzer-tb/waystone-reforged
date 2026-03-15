package dev.mizarc.waystonewarps.infrastructure.services.geyser

import dev.mizarc.waystonewarps.application.actions.discovery.GetWarpPlayerAccess
import dev.mizarc.waystonewarps.application.actions.discovery.RevokeDiscovery
import dev.mizarc.waystonewarps.application.actions.management.*
import dev.mizarc.waystonewarps.application.actions.whitelist.GetWhitelistedPlayers
import dev.mizarc.waystonewarps.application.actions.whitelist.ToggleWhitelist
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.application.results.UpdateWarpSkinResult
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
    private val getAllWarpSkins: GetAllWarpSkins by inject()
    private val updateWarpSkin: UpdateWarpSkin by inject()

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
        buttons.add(FormButton("§l§8Skins", imagePath = "textures/blocks/lodestone_top"))

        // 4: Home
        if (warp.isHome) {
            buttons.add(FormButton("§l§8Home §c(On)", imagePath = "textures/items/bed_red"))
        } else {
            buttons.add(FormButton("§l§8Home §8(Off)", imagePath = "textures/items/bed_white"))
        }

        // 5: Delete
        buttons.add(FormButton("§l§cDelete", imagePath = "textures/blocks/barrier"))

        // 6: Protection
        if (warp.isProtected) {
            buttons.add(FormButton("§l§8Protection §a(On)", imagePath = "textures/blocks/obsidian"))
        } else {
            buttons.add(FormButton("§l§8Protection §8(Off)", imagePath = "textures/blocks/crying_obsidian"))
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
                    3 -> openSkinsMenu()
                    4 -> handleToggleHome()
                    5 -> handleDelete()
                    6 -> handleToggleProtection()
                    7 -> { /* back / close */ }
                }
            }
        )
    }

    // --- Privacy ---

    private fun handleTogglePrivacy() {
        val result = toggleLock.execute(player.uniqueId, warp.id,
            bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"))
        if (result.isSuccess) {
            // warp is the cached instance — ToggleLock already mutated it
            val msg = if (warp.isLocked) "§cWaystone set to private." else "§aWaystone set to public."
            player.sendMessage(msg)
        } else {
            player.sendMessage("§cFailed to toggle privacy.")
        }
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
            buttons.add(FormButton("§l§cRevoke Discovery", imagePath = "textures/blocks/barrier"))
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

    // --- Skins ---

    private val bedrockTextures = mapOf(
        "SMOOTH_STONE" to "textures/blocks/stone_slab_top",
        "STONE_BRICKS" to "textures/blocks/stonebrick",
        "DEEPSLATE_TILES" to "textures/blocks/deepslate/deepslate_tiles",
        "POLISHED_TUFF" to "textures/blocks/polished_tuff",
        "TUFF_BRICKS" to "textures/blocks/tuff_bricks",
        "RESIN_BRICKS" to "textures/blocks/resin_bricks",
        "CUT_SANDSTONE" to "textures/blocks/sandstone_top",
        "CUT_RED_SANDSTONE" to "textures/blocks/red_sandstone_top",
        "NETHER_BRICKS" to "textures/blocks/nether_brick",
        "POLISHED_BLACKSTONE" to "textures/blocks/polished_blackstone",
        "QUARTZ_BLOCK" to "textures/blocks/quartz_block_side",
        "WAXED_CUT_COPPER" to "textures/blocks/cut_copper",
        "WAXED_EXPOSED_CUT_COPPER" to "textures/blocks/exposed_cut_copper",
        "WAXED_WEATHERED_CUT_COPPER" to "textures/blocks/weathered_cut_copper",
        "WAXED_OXIDIZED_CUT_COPPER" to "textures/blocks/oxidized_cut_copper"
    )

    private fun openSkinsMenu() {
        val skins = getAllWarpSkins.execute()
        val costTypeLabel = when (configService.getTeleportCostType()) {
            CostType.MONEY -> "coins"
            CostType.XP -> "XP"
            CostType.ITEM -> configService.getTeleportCostItem().lowercase().replace("_", " ")
        }

        val buttons = skins.map { skinKey ->
            val price = configService.getSkinPrice(skinKey)
            val isActive = skinKey == warp.block
            val skinName = skinKey.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
            val label = if (isActive) "§l§a$skinName §7(Active)" else "§l§8$skinName §e${price.toInt()}"
            val texture = bedrockTextures[skinKey] ?: "textures/blocks/lodestone_top"
            FormButton(label, imagePath = texture)
        }.toMutableList()
        buttons.add(FormButton("§l§8Back", imagePath = "textures/items/nether_star"))

        BedrockSupport.sendSimpleForm(player,
            title = "§l§8Waystone Skins",
            content = "Select a skin to apply. Cost is paid in §e$costTypeLabel§r.",
            buttons = buttons,
            onButtonClicked = { index ->
                if (index >= skins.size) {
                    open()
                    return@sendSimpleForm
                }
                val skinKey = skins[index]
                val isActive = skinKey == warp.block
                if (isActive) {
                    player.sendMessage("§7This skin is already active.")
                    openSkinsMenu()
                    return@sendSimpleForm
                }

                val price = configService.getSkinPrice(skinKey)
                val skinName = skinKey.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }

                if (price <= 0) {
                    applySkin(skinKey, skinName)
                } else {
                    BedrockSupport.sendModalForm(player,
                        title = "§l§8Apply Skin",
                        content = "Apply §e$skinName§r for §e${price.toInt()} $costTypeLabel§r?",
                        button1 = "§aYes, Apply",
                        button2 = "§cCancel",
                        onButton1 = {
                            if (!checkAndDeductCost(player, price)) {
                                player.sendMessage("§cNot enough funds to purchase this skin!")
                                openSkinsMenu()
                                return@sendModalForm
                            }
                            applySkin(skinKey, skinName)
                        },
                        onButton2 = { openSkinsMenu() }
                    )
                }
            }
        )
    }

    private fun applySkin(skinKey: String, skinName: String) {
        val result = updateWarpSkin.execute(warp.id, skinKey)
        when (result) {
            UpdateWarpSkinResult.SUCCESS -> player.sendMessage("§aSkin changed to $skinName!")
            UpdateWarpSkinResult.BLOCK_NOT_VALID -> player.sendMessage("§cInvalid skin.")
            else -> player.sendMessage("§cFailed to change skin.")
        }
        open()
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
                    val result = toggleHome.execute(player.uniqueId, warp.id,
                        bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"))
                    if (result.isSuccess) {
                        player.sendMessage("§cHome waystone unset.")
                    } else {
                        player.sendMessage("§cFailed to unset home.")
                    }
                    open()
                },
                onButton2 = { open() }
            )
        } else {
            val result = toggleHome.execute(player.uniqueId, warp.id,
                bypassOwnership = player.hasPermission("waystonewarps.bypass.access_control"))
            if (result.isSuccess) {
                player.sendMessage("§aThis waystone is now your home!")
            } else {
                player.sendMessage("§cFailed to set home.")
            }
            open()
        }
    }

    // --- Delete ---

    private fun handleDelete() {
        BedrockSupport.sendModalForm(player,
            title = "§l§cDelete Waystone",
            content = "Delete this waystone permanently?\n\n§c§lThis cannot be undone!",
            button1 = "§cYes, Delete",
            button2 = "§8Cancel",
            onButton1 = {
                player.performCommand("waystonewarps break ${warp.id}")
                player.sendMessage("§a${warp.name} has been deleted.")
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
                    val result = toggleProtection.execute(player.uniqueId, warp.id,
                        bypassOwnership = player.hasPermission("waystonewarps.bypass.protection"))
                    if (result.isSuccess) {
                        player.sendMessage("§aProtection mode enabled!")
                    } else {
                        player.sendMessage("§cFailed to enable protection.")
                    }
                    open()
                },
                onButton2 = { open() }
            )
        } else {
            val result = toggleProtection.execute(player.uniqueId, warp.id,
                bypassOwnership = player.hasPermission("waystonewarps.bypass.protection"))
            if (result.isSuccess) {
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
