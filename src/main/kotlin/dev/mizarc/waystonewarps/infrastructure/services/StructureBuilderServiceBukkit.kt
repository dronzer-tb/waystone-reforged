package dev.mizarc.waystonewarps.infrastructure.services

import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.application.services.StructureBuilderService
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.infrastructure.mappers.toLocation
import dev.mizarc.waystonewarps.infrastructure.scheduling.FoliaScheduler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.Levelled
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.*

/**
 * Builds the physical waystone structure and its block displays.
 *
 * Folia note: every method here writes blocks and spawns/removes entities at the warp's location.
 * That location frequently belongs to a different region than the caller (an admin editing a remote
 * warp, the plugin restoring every warp on enable, a warp being moved), so all world access is
 * dispatched onto the region that owns the warp before it runs.
 */
class StructureBuilderServiceBukkit(private val plugin: Plugin, private val configService: ConfigService): StructureBuilderService {

    /** Displays sit within a block or two of the warp; this bounds the region-local entity search. */
    private val displaySearchRadius = 4.0

    override fun spawnStructure(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)
        FoliaScheduler.atRegion(plugin, location) {
            // Idempotent: drop any stale displays first so a restart cannot stack duplicates.
            removeBlockDisplay(warp, location)
            generateStructure(warp, getStructureBlocks(warp), world, location)
        }
    }

    override fun updateStructure(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)
        FoliaScheduler.atRegion(plugin, location) {
            // Generate and then remove the existing block displays after 2 ticks to prevent flashing.
            val entityList = generateStructure(warp, getStructureBlocks(warp), world, location)
            FoliaScheduler.atRegionLater(plugin, location, 2L) {
                removeBlockDisplay(warp, location, entityList)
            }
        }
    }

    override fun revertStructure(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)
        FoliaScheduler.atRegion(plugin, location) {
            world.getBlockAt(location.blockX, location.blockY, location.blockZ).type = Material.LODESTONE
            world.getBlockAt(location.blockX, location.blockY - 1, location.blockZ).type =
                runCatching { Material.valueOf(warp.block) }.getOrDefault(Material.SMOOTH_STONE)
            removeLight(world, location.blockX, location.blockY + 1, location.blockZ)
            removeBlockDisplay(warp, location)
        }
    }

    /**
     * Reverts the structure immediately on the calling thread. Only safe to call when the caller
     * already owns the warp's region, or during shutdown when the regions are no longer ticking.
     */
    fun revertStructureImmediately(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)
        world.getBlockAt(location.blockX, location.blockY, location.blockZ).type = Material.LODESTONE
        world.getBlockAt(location.blockX, location.blockY - 1, location.blockZ).type =
            runCatching { Material.valueOf(warp.block) }.getOrDefault(Material.SMOOTH_STONE)
        removeLight(world, location.blockX, location.blockY + 1, location.blockZ)
        removeBlockDisplay(warp, location)
    }

    override fun destroyStructure(warp: Warp) {
        val world = Bukkit.getWorld(warp.worldId) ?: return
        val location = warp.position.toLocation(world)
        FoliaScheduler.atRegion(plugin, location) {
            location.block.type = Material.AIR
            world.getBlockAt(location.blockX, location.blockY - 1, location.blockZ).type = Material.AIR
            removeLight(world, location.blockX, location.blockY + 1, location.blockZ)
            removeBlockDisplay(warp, location)
        }
    }

    private fun getStructureBlocks(warp: Warp): List<Material> {
        val defaultStructureBlocks = listOf(Material.SMOOTH_STONE, Material.LODESTONE, Material.SMOOTH_STONE,
            Material.SMOOTH_STONE, Material.SMOOTH_STONE_SLAB
        )
        return try {
            val blocks = configService.getStructureBlocks(warp.block)
            if (blocks.count() == 5) {
                blocks.map { Material.valueOf(it) }
            } else {
                defaultStructureBlocks
            }
        } catch (_: IllegalArgumentException) {
            defaultStructureBlocks
        }
    }

    private fun generateStructure(warp: Warp, structureBlocks: List<Material>, world: World,
                                  location: Location): MutableList<Entity> {
        // Replace top block with main block type
        location.block.type = structureBlocks[1]

        // Replace bottom block with slab (delay to avoid POI data mismatch error)
        FoliaScheduler.atRegionLater(plugin, location, 2L) {
            world.getBlockAt(location.blockX, location.blockY - 1, location.blockZ).type = structureBlocks[4]
        }

        // Place invisible light block above the waystone for passive lighting
        placeLight(world, location.blockX, location.blockY + 1, location.blockZ)

        // Create and return entities
        return mutableListOf(
            createBlockDisplay(warp.id, location, structureBlocks[0],
                0.075f, 1.3f, 0.075f,
                0.85f, 0.85f, 0.85f),
            createBlockDisplay(warp.id, location, structureBlocks[2],
                0.075f, 0.8f, 0.075f,
                0.85f, 0.85f, 0.85f),
            createBlockDisplay(warp.id, location, structureBlocks[3],
                0.2f, 0.4f, 0.2f,
                0.6f, 0.6f, 0.6f)
        )
    }

    private fun createBlockDisplay(warpId: UUID, baseLocation: Location, material: Material,
                                   offsetX: Float, offsetY: Float, offsetZ: Float,
                                   scaleX: Float, scaleY: Float, scaleZ: Float): Entity {
        // Create BlockData
        val blockData = material.createBlockData()
        val location = baseLocation.clone()
        location.y -= 1
        val blockDisplay = baseLocation.world.spawnEntity(location, EntityType.BLOCK_DISPLAY) as BlockDisplay
        blockDisplay.block = blockData

        // Transform display
        val transformation = Transformation(
            Vector3f(offsetX, offsetY, offsetZ), AxisAngle4f(),
            Vector3f(scaleX, scaleY, scaleZ), AxisAngle4f())
        blockDisplay.transformation = transformation
        blockDisplay.customName(Component.text((warpId.toString())))

        return blockDisplay
    }

    /**
     * Removes this warp's block displays. Folia forbids iterating [World.getEntities] from a region
     * thread, so this looks only at entities near the warp - which is where the displays live.
     * Must be called from the thread owning [location].
     */
    private fun removeBlockDisplay(warp: Warp, location: Location, entityExclusions: List<Entity> = listOf()) {
        val nearby = try {
            location.getNearbyEntitiesByType(BlockDisplay::class.java, displaySearchRadius)
        } catch (ex: Exception) {
            plugin.logger.warning("Could not scan for waystone displays at ${warp.position}: ${ex.message}")
            return
        }

        for (entity in nearby) {
            if (entityExclusions.contains(entity)) continue
            val customName = entity.customName() ?: continue
            if (customName is TextComponent && customName.content() == warp.id.toString()) {
                entity.remove()
            }
        }
    }

    private fun placeLight(world: World, x: Int, y: Int, z: Int) {
        val block = world.getBlockAt(x, y, z)
        if (block.type != Material.AIR && block.type != Material.LIGHT) return
        block.type = Material.LIGHT
        val data = block.blockData
        if (data is Levelled) {
            data.level = data.maximumLevel
            block.setBlockData(data, false)
        }
    }

    private fun removeLight(world: World, x: Int, y: Int, z: Int) {
        val block = world.getBlockAt(x, y, z)
        if (block.type == Material.LIGHT) {
            block.type = Material.AIR
        }
    }
}
