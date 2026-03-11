package dev.mizarc.waystonewarps.interaction.listeners

import dev.mizarc.waystonewarps.application.actions.discovery.DiscoverWarp
import dev.mizarc.waystonewarps.application.actions.whitelist.GetWhitelistedPlayers
import dev.mizarc.waystonewarps.application.actions.world.GetWarpAtPosition
import dev.mizarc.waystonewarps.application.actions.world.IsValidWarpBase
import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.infrastructure.mappers.toPosition3D
import dev.mizarc.waystonewarps.infrastructure.services.geyser.BedrockWarpManagementMenu
import dev.mizarc.waystonewarps.infrastructure.services.geyser.BedrockWarpMenu
import dev.mizarc.waystonewarps.infrastructure.services.geyser.BedrockWarpNamingMenu
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.infrastructure.services.geyser.GeyserMenuIntegration
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.menus.management.WarpManagementMenu
import dev.mizarc.waystonewarps.interaction.menus.management.WarpNamingMenu
import dev.mizarc.waystonewarps.interaction.menus.use.WarpMenu
import dev.mizarc.waystonewarps.interaction.messaging.AccentColourPalette
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class WaystoneInteractListener(
    private val configService: ConfigService,
    private val geyserMenuIntegration: GeyserMenuIntegration? = null
): Listener, KoinComponent {
    private val getWarpAtPosition: GetWarpAtPosition by inject()
    private val discoverWarp: DiscoverWarp by inject()
    private val getWhitelistedPlayers: GetWhitelistedPlayers by inject()
    private val isValidWarpBase: IsValidWarpBase by inject()
    private val localizationProvider: LocalizationProvider by inject()

    private val openOtherMenuPermission = "waystonewarps.bypass.open_menu"

    // Debounce for Bedrock players to prevent repeated interactions
    private val bedrockInteractCooldowns = mutableMapOf<UUID, Long>()
    private val BEDROCK_COOLDOWN_MS = 3000L

    // Track recent discoveries to prevent duplicate effects
    private val recentDiscoveryEffects = mutableSetOf<Pair<UUID, UUID>>()

    private fun isBedrockPlayer(player: Player): Boolean {
        return geyserMenuIntegration?.isBedrockPlayer(player) == true
    }

    private fun openWarpMenuFor(player: Player, menuNavigator: MenuNavigator) {
        if (isBedrockPlayer(player)) {
            val api = geyserMenuIntegration?.getApi() ?: return
            BedrockWarpMenu(player, api).open()
        } else {
            menuNavigator.openMenu(WarpMenu(player, menuNavigator, localizationProvider))
        }
    }

    private fun openManagementMenuFor(player: Player, menuNavigator: MenuNavigator, warp: dev.mizarc.waystonewarps.domain.warps.Warp) {
        if (isBedrockPlayer(player)) {
            val api = geyserMenuIntegration?.getApi() ?: return
            BedrockWarpManagementMenu(player, api, warp).open()
        } else {
            menuNavigator.openMenu(WarpManagementMenu(player, menuNavigator, warp))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onLodestoneInteract(event: PlayerInteractEvent) {
        val player: Player = event.player
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand == EquipmentSlot.OFF_HAND) return
        val clickedBlock: Block = event.clickedBlock ?: return

        // Check for holding compass
        val itemInHand = event.player.inventory.itemInMainHand
        if (itemInHand.type == Material.COMPASS) return

        // Check for existing warp
        val warp = getWarpAtPosition.execute(clickedBlock.location.toPosition3D(), clickedBlock.world.uid)
        val menuNavigator = MenuNavigator(player)

        // Create new warp if not found, open management menu if owner, discover otherwise
        warp?.let {
            // Check if warp is locked and alert if no access
            player.swingMainHand()
            event.isCancelled = true

            // Debounce Bedrock players to prevent repeated interactions
            if (isBedrockPlayer(player)) {
                val now = System.currentTimeMillis()
                val last = bedrockInteractCooldowns[player.uniqueId] ?: 0L
                if (now - last < BEDROCK_COOLDOWN_MS) return
                bedrockInteractCooldowns[player.uniqueId] = now
            }

            val isOwner = warp.playerId == player.uniqueId
            val canOpenOtherMenu = player.hasPermission(openOtherMenuPermission)
            val isAdminMenuOpenAttempt = !isOwner && canOpenOtherMenu && player.isSneaking

            if (warp.isLocked && !isOwner && !isAdminMenuOpenAttempt
                    && !getWhitelistedPlayers.execute(warp.id).contains(player.uniqueId)) {
                player.sendActionBar(
                    Component.text(localizationProvider.get(player.uniqueId, LocalizationKeys.FEEDBACK_WAYSTONE_PRIVATE))
                        .color(PrimaryColourPalette.FAILED.color)
                )
                return
            }

            // Set location of particle spawn
            val particleLocation = clickedBlock.location.clone()
            particleLocation.x += 0.5
            particleLocation.y += 0.5
            particleLocation.z += 0.5

            // Waystone owner path.
            if (it.playerId == player.uniqueId) {
                if (configService.allowWarpsMenuViaWaystone()) {
                    if (event.player.isSneaking) {
                        openManagementMenuFor(player, menuNavigator, it)
                    } else {
                        openWarpMenuFor(player, menuNavigator)
                    }
                } else {
                    openManagementMenuFor(player, menuNavigator, it)
                }

            // Non-owner path.
            } else {
                // Allow server admins to open the management menu.
                if (isAdminMenuOpenAttempt) {
                    openManagementMenuFor(player, menuNavigator, it)
                    return
                }

                // Check if player has permission to discover warps
                if (!player.hasPermission("waystonewarps.discover")) {
                    player.sendActionBar(Component.text("You don't have permission to discover warps").color(PrimaryColourPalette.FAILED.color))
                    return
                }

                // Try to discover first
                val result = discoverWarp.execute(player.uniqueId, it.id)
                if (result) {
                    player.sendActionBar(Component.text("Warp ").color(PrimaryColourPalette.SUCCESS.color)
                        .append(Component.text(warp.name).color(AccentColourPalette.SUCCESS.color))
                        .append(Component.text( " has been discovered!").color(PrimaryColourPalette.SUCCESS.color)))

                    // Play discovery effects only once per player-warp (guard against duplicate events)
                    val discoveryKey = Pair(player.uniqueId, it.id)
                    if (!recentDiscoveryEffects.contains(discoveryKey)) {
                        recentDiscoveryEffects.add(discoveryKey)
                        // Skip particles and sound entirely for Bedrock (TOTEM_OF_UNDYING has built-in audio on Bedrock)
                        if (!isBedrockPlayer(player)) {
                            clickedBlock.world.spawnParticle(Particle.TOTEM_OF_UNDYING, particleLocation, 20)
                            clickedBlock.world.playSound(particleLocation, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.BLOCKS, 1.0f, 1.0f)
                        }
                    }
                } else {
                    // Already discovered - open warp menu if allowed
                    if (configService.allowWarpsMenuViaWaystone()) {
                        openWarpMenuFor(player, menuNavigator)
                    }
                    else {
                        player.sendActionBar(Component.text("Warp ").color(PrimaryColourPalette.INFO.color)
                            .append(Component.text(warp.name).color(AccentColourPalette.INFO.color))
                            .append(Component.text( " already discovered").color(PrimaryColourPalette.INFO.color)))
                    }
                }
            }
        }

        // Check if valid warp base to create warp
        val baseBlock = clickedBlock.getRelative(BlockFace.DOWN)
        if (isValidWarpBase.execute(baseBlock.type.toString()) && clickedBlock.type == Material.LODESTONE) {
            player.swingMainHand()
            event.isCancelled = true
            val clicked = event.clickedBlock ?: return

            // Send out a fake BlockPlaceEvent for protection plugins to hook
            @Suppress("UnstableApiUsage")
            val testEvent = BlockPlaceEvent(
                clicked,
                clicked.state,
                clicked,
                ItemStack(Material.LODESTONE),
                player,
                true,
                EquipmentSlot.HAND
            )
            Bukkit.getPluginManager().callEvent(testEvent)
            if (testEvent.isCancelled) return

            if (!player.hasPermission("waystonewarps.create")) {
                player.sendActionBar(Component.text("You don't have permission to create warps").color(PrimaryColourPalette.FAILED.color))
                return
            }

            // Open the naming menu (Bedrock custom form or Java anvil GUI)
            if (isBedrockPlayer(player)) {
                val api = geyserMenuIntegration?.getApi() ?: return
                BedrockWarpNamingMenu(player, api, clickedBlock.location).open()
            } else {
                menuNavigator.openMenu(WarpNamingMenu(player, menuNavigator, clickedBlock.location))
            }
        }
    }
}