package dev.mizarc.waystonewarps.infrastructure.services.geyser

import dev.mizarc.waystonewarps.application.actions.discovery.*
import dev.mizarc.waystonewarps.application.actions.management.GetOwnedWarps
import dev.mizarc.waystonewarps.application.actions.teleport.TeleportPlayer
import dev.mizarc.waystonewarps.application.actions.whitelist.GetWhitelistedPlayers
import dev.mizarc.waystonewarps.application.services.PlayerAttributeService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Bedrock warp navigation menu with sub-menus for waystone list,
 * search & filter, waystone details (warp/favorite/delete).
 */
class BedrockWarpMenu(
    private val player: Player
) : KoinComponent {
    private val getPlayerWarpAccess: GetPlayerWarpAccess by inject()
    private val getFavouritedWarpAccess: GetFavouritedWarpAccess by inject()
    private val getOwnedWarps: GetOwnedWarps by inject()
    private val isPlayerFavouriteWarp: IsPlayerFavouriteWarp by inject()
    private val toggleFavouriteDiscovery: ToggleFavouriteDiscovery by inject()
    private val revokeDiscovery: RevokeDiscovery by inject()
    private val teleportPlayer: TeleportPlayer by inject()
    private val getWhitelistedPlayers: GetWhitelistedPlayers by inject()
    private val playerAttributeService: PlayerAttributeService by inject()
    private val localizationProvider: LocalizationProvider by inject()

    // ── Main Menu ──────────────────────────────────────────────

    fun open() {
        BedrockSupport.sendSimpleForm(player,
            title = "§l§8Waystone Warps",
            content = "",
            buttons = listOf(
                FormButton("§l§8Waystone", imagePath = "textures/blocks/lodestone_top"),
                FormButton("§l§8Search & Filter", imagePath = "textures/items/spyglass"),
                FormButton("§l§cBack", imagePath = "textures/items/nether_star")
            ),
            onButtonClicked = { index ->
                when (index) {
                    0 -> openWaystoneList()
                    1 -> openSearchAndFilter()
                    // 2 = Back → close
                }
            }
        )
    }

    // ── Waystone List ──────────────────────────────────────────

    private fun openWaystoneList() {
        val warps = getPlayerWarpAccess.execute(player.uniqueId).sortedBy { it.name }
        showWarpList("§l§8Discovered Waystones", warps, backAction = { open() })
    }

    private fun showWarpList(title: String, warps: List<Warp>, backAction: () -> Unit) {
        if (warps.isEmpty()) {
            BedrockSupport.sendSimpleForm(player,
                title = title,
                content = "§8No waystones found.",
                buttons = listOf(
                    FormButton("§l§cBack", imagePath = "textures/items/nether_star")
                ),
                onButtonClicked = { backAction() }
            )
            return
        }

        val buttons = warps.map { warp ->
            val isFav = isPlayerFavouriteWarp.execute(player.uniqueId, warp.id)
            val isOwned = warp.playerId == player.uniqueId
            val prefix = buildString {
                if (isFav) append("§c♥ ")
                if (isOwned) append("§6★ ")
            }
            FormButton("$prefix§l§8${warp.name}", imagePath = "textures/blocks/lodestone_top")
        }.toMutableList()
        buttons.add(FormButton("§l§cBack", imagePath = "textures/items/nether_star"))

        BedrockSupport.sendSimpleForm(player,
            title = title,
            content = "§8Select a waystone:",
            buttons = buttons,
            onButtonClicked = { index ->
                if (index == warps.size) {
                    backAction()
                    return@sendSimpleForm
                }
                if (index < 0 || index >= warps.size) return@sendSimpleForm
                openWaystoneDetail(warps[index], backAction = { showWarpList(title, warps, backAction) })
            }
        )
    }

    // ── Waystone Detail ────────────────────────────────────────

    private fun openWaystoneDetail(warp: Warp, backAction: () -> Unit) {
        val cost = playerAttributeService.getTeleportCost(player.uniqueId)
        val isFav = isPlayerFavouriteWarp.execute(player.uniqueId, warp.id)
        val favIcon = if (isFav) "textures/items/redstone_dust" else "textures/items/gunpowder"
        val favLabel = if (isFav) "§l§c♥ Favourite" else "§l§8Favourite"

        BedrockSupport.sendSimpleForm(player,
            title = "§l§8${warp.name}",
            content = "",
            buttons = listOf(
                FormButton("§l§8Warp §7(${cost.toInt()} cost)", imagePath = "textures/items/ender_pearl"),
                FormButton(favLabel, imagePath = favIcon),
                FormButton("§l§cDelete Waystone", imagePath = "textures/blocks/barrier"),
                FormButton("§l§cBack", imagePath = "textures/items/nether_star")
            ),
            onButtonClicked = { index ->
                when (index) {
                    0 -> teleportToWarp(warp)
                    1 -> handleToggleFavourite(warp, backAction)
                    2 -> confirmDeleteWaystone(warp, backAction)
                    3 -> backAction()
                }
            }
        )
    }

    private fun handleToggleFavourite(warp: Warp, backAction: () -> Unit) {
        toggleFavouriteDiscovery.execute(player.uniqueId, warp.id)
        val nowFav = isPlayerFavouriteWarp.execute(player.uniqueId, warp.id)
        player.sendMessage(if (nowFav) "§a${warp.name} added to favourites!" else "§c${warp.name} removed from favourites.")
        openWaystoneDetail(warp, backAction)
    }

    private fun confirmDeleteWaystone(warp: Warp, backAction: () -> Unit) {
        BedrockSupport.sendModalForm(player,
            title = "§l§cDelete Waystone",
            content = "§8Are you sure you want to remove §l${warp.name}§r§8 from your discovered waystones?\n\nThis cannot be undone.",
            button1 = "§cYes, Delete",
            button2 = "§aCancel",
            onButton1 = {
                val result = revokeDiscovery.execute(player.uniqueId, warp.id)
                if (result) {
                    player.sendMessage("§c${warp.name} removed from discovered waystones.")
                } else {
                    player.sendMessage("§cFailed to remove waystone.")
                }
                backAction()
            },
            onButton2 = { openWaystoneDetail(warp, backAction) }
        )
    }

    // ── Search & Filter ────────────────────────────────────────

    private fun openSearchAndFilter() {
        BedrockSupport.sendSimpleForm(player,
            title = "§l§8Search & Filter",
            content = "",
            buttons = listOf(
                FormButton("§l§8Search", imagePath = "textures/items/spyglass"),
                FormButton("§l§8Filter", imagePath = "textures/blocks/hopper_top"),
                FormButton("§l§cBack", imagePath = "textures/items/nether_star")
            ),
            onButtonClicked = { index ->
                when (index) {
                    0 -> openSearchForm()
                    1 -> openFilterMenu()
                    2 -> open()
                }
            }
        )
    }

    // ── Search ─────────────────────────────────────────────────

    private fun openSearchForm() {
        BedrockSupport.sendCustomForm(player,
            title = "§l§8Search Waystone",
            elements = listOf(
                FormElement.Input(id = "name", text = "§8Enter waystone name:", placeholder = "Waystone name...")
            ),
            onSubmit = { responses ->
                val searchName = responses["name"]?.trim() ?: ""
                if (searchName.isEmpty()) {
                    openSearchForm()
                    return@sendCustomForm
                }
                performSearch(searchName)
            },
            onClosed = { openSearchAndFilter() }
        )
    }

    private fun performSearch(query: String) {
        val allWarps = getPlayerWarpAccess.execute(player.uniqueId)
        val matches = allWarps.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.name }

        if (matches.isEmpty()) {
            BedrockSupport.sendSimpleForm(player,
                title = "§l§8Search Results",
                content = "§cNo waystone found matching \"$query\".",
                buttons = listOf(
                    FormButton("§l§8Try Again", imagePath = "textures/items/spyglass"),
                    FormButton("§l§cCancel", imagePath = "textures/items/nether_star")
                ),
                onButtonClicked = { index ->
                    when (index) {
                        0 -> openSearchForm()
                        // 1 = Cancel → close
                    }
                }
            )
        } else {
            showWarpList("§l§8Results: \"$query\"", matches, backAction = { openSearchAndFilter() })
        }
    }

    // ── Filter ─────────────────────────────────────────────────

    private fun openFilterMenu() {
        BedrockSupport.sendSimpleForm(player,
            title = "§l§8Filter Waystones",
            content = "",
            buttons = listOf(
                FormButton("§l§8Discovered", imagePath = "textures/items/sugar"),
                FormButton("§l§8Favourites", imagePath = "textures/items/redstone_dust"),
                FormButton("§l§8Owned", imagePath = "textures/items/glowstone_dust"),
                FormButton("§l§cBack", imagePath = "textures/items/nether_star")
            ),
            onButtonClicked = { index ->
                when (index) {
                    0 -> showFilteredDiscovered()
                    1 -> showFilteredFavourites()
                    2 -> showFilteredOwned()
                    3 -> openSearchAndFilter()
                }
            }
        )
    }

    private fun showFilteredDiscovered() {
        val warps = getPlayerWarpAccess.execute(player.uniqueId).sortedBy { it.name }
        showWarpList("§l§8Discovered Waystones", warps, backAction = { openFilterMenu() })
    }

    private fun showFilteredFavourites() {
        val warps = getFavouritedWarpAccess.execute(player.uniqueId).sortedBy { it.name }
        showWarpList("§l§8Favourite Waystones", warps, backAction = { openFilterMenu() })
    }

    private fun showFilteredOwned() {
        val warps = getOwnedWarps.execute(player.uniqueId).sortedBy { it.name }
        showWarpList("§l§8Owned Waystones", warps, backAction = { openFilterMenu() })
    }

    // ── Teleportation ──────────────────────────────────────────

    private fun teleportToWarp(warp: Warp) {
        teleportPlayer.execute(
            player.uniqueId, warp,
            onPending = {
                player.sendMessage("§eTeleporting to ${warp.name}...")
            },
            onSuccess = {
                player.sendMessage("§aTeleported to ${warp.name}!")
            },
            onFailure = {
                player.sendMessage("§cTeleportation failed.")
            },
            onInsufficientFunds = {
                player.sendMessage("§cNot enough funds to teleport.")
            },
            onWorldNotFound = {
                player.sendMessage("§cWorld not found.")
            },
            onLocked = {
                player.sendMessage("§cThis waystone is locked.")
            },
            onCanceled = {
                player.sendMessage("§eTeleportation cancelled.")
            },
            onPermissionDenied = {
                player.sendMessage("§cNo teleport permission.")
            },
            onInterworldPermissionDenied = {
                player.sendMessage("§cNo cross-world teleport permission.")
            }
        )
    }
}
