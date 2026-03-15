package dev.mizarc.waystonewarps.infrastructure.services

import dev.mizarc.waystonewarps.application.services.ConfigService
import dev.mizarc.waystonewarps.infrastructure.services.teleportation.CostType
import org.bukkit.configuration.file.FileConfiguration

class ConfigServiceBukkit(private val configFile: FileConfiguration): ConfigService {

    override fun getPluginLanguage(): String {
        return configFile.getString("plugin_language", "en").toString()
    }

    override fun getWarpLimit(): Int {
        return configFile.getInt("warp_limit", 3)
    }

    override fun getTeleportTimer(): Int {
        return configFile.getInt("teleport_timer", 5)
    }

    override fun getTeleportCostType(): CostType {
        return runCatching {
            CostType.valueOf(configFile.getString("teleport_cost_type", "ITEM").toString())
        }.getOrDefault(CostType.ITEM)
    }

    override fun getTeleportCostItem(): String {
        return configFile.getString("teleport_cost_item", "ENDER_PEARL").toString()
    }

    override fun getTeleportCostAmount(): Double {
        return configFile.getDouble("teleport_cost_amount", 3.0)
    }

    override fun getPlatformReplaceBlocks(): Set<String> {
        return configFile.getStringList("platform_replace_blocks").toSet()
    }

    override fun getAllSkinTypes(): List<String> {
        return configFile.getConfigurationSection("waystone_skins")?.getKeys(false)?.toList() ?: emptyList()
    }

    override fun getStructureBlocks(blockType: String): List<String> {
        if (blockType !in getAllSkinTypes()) return emptyList()
        // Support new nested format (blocks key) with fallback to legacy flat list
        val blocksList = configFile.getStringList("waystone_skins.$blockType.blocks")
        if (blocksList.isNotEmpty()) return blocksList
        return configFile.getStringList("waystone_skins.$blockType")
    }

    override fun getSkinPrice(skinType: String): Double {
        return configFile.getDouble("waystone_skins.$skinType.price", 0.0)
    }

    override fun allowWarpsMenuViaCompass(): Boolean {
        return configFile.getBoolean("warps_menu_via_compass")
    }

    override fun allowWarpsMenuViaWaystone(): Boolean {
        return configFile.getBoolean("warps_menu_via_waystone")
    }

    override fun hologramsEnabled(): Boolean {
        return configFile.getBoolean("holograms_enabled")
    }

    override fun isGeyserEnabled(): Boolean {
        return configFile.getBoolean("geyser.enabled", true)
    }

    override fun getGeyserMenuButtonText(): String {
        return configFile.getString("geyser.menu_button_text", "Waystone Warps").toString()
    }

    override fun getGeyserMenuButtonPriority(): Int {
        return configFile.getInt("geyser.menu_button_priority", 50)
    }

    override fun getGeyserMenuButtonImage(): String {
        return configFile.getString("geyser.menu_button_image", "textures/items/compass_item").toString()
    }

    override fun getHomeUnsetCostMultiplier(): Double {
        return configFile.getDouble("home_unset_cost_multiplier", 2.0)
    }

    override fun getProtectionModeCostMultiplier(): Double {
        return configFile.getDouble("protection_mode_cost_multiplier", 5.0)
    }
}