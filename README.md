![Banner](branding/banner.png)

# Waystone Warps

Transform your travels with the Waystone Warps plugin! Craft and place waystones to set up teleportation points throughout your world. Discover new waystones placed by others as you explore and easily teleport between them. Streamline your journey across the world knowing you can always teleport back home in a snap, or simply visit your friends in an instant.

## 🆕 Recent Major Changes

### Version 1.0.0 - Complete Bedrock Integration & Feature Expansion

**Critical Bug Fixes:**
- **Bedrock Menu Timeout Fix** - Fixed issue where menus stopped appearing after 15-20 minutes by implementing aggressive keepalive with real API calls (5-second interval instead of 10-second status checks)

**New Features:**
- **Home Waystone System** - Set one personal waystone as home with `/home` command and reduced teleport costs
- **Protection Mode** - Lock waystones to prevent non-owners from breaking them with thorns defense damage
- **Bedrock Warp Deletion** - Replace Move option with permanent Delete button in Bedrock editor menu
- **Full GeyserMC Integration** - Native Bedrock forms for all menu interactions (custom forms, modals, and simple menus)
- **Bedrock Main Menu Buttons** - Quick access "Warp" and "Home" buttons in GeyserMenu interface

**UI/UX Improvements:**
- **Bedrock Editor Menu Redesign** - Complete overhaul with Minecraft texture paths for icons
- **Sub-Menu System** - Organized menu hierarchy for search, filters, and discovered players
- **Favorite Markers** - Visual indicators (♥) for favorited waystones
- **Owner Markers** - Visual indicators (★) for owned waystones
- **Search Functionality** - Find waystones by name in both Java and Bedrock menus

**Technical Improvements:**
- **Pure Reflection API** - Full JDK 21+ support with reflection-based GeyserMenu integration
- **Stable Connections** - Aggressive keepalive prevents internal GeyserMenu timeout
- **Icon Removal** - Simplified UI by removing waystone icon customization feature
- **Schema Migration** - Added database migrations for Home and Protection features
- **Particle Optimization** - Fixed looping particle issues on Bedrock clients

## Core Features

### Waystone Creation & Management
- **Physically Grounded** - Waystones are bound to a physical lodestone on smooth stone block structure in the world, making the experience immersive
- **Intuitive GUI Menus** - Manage all waystone settings through click menus - no commands needed
- **Waystone Skins** - Customize waystone appearance with multiple visual variants
- **Rename Waystones** - Give each waystone a unique, personalized name
- **Delete Waystones** - Remove waystones permanently with confirmation dialogs

### Access Control & Permissions
- **Privacy Modes** - Set waystones as public, private, or whitelist-based
- **Player Whitelist** - Grant specific players access to private waystones
- **Permission-Based Access** - Fine-grained permissions for creation, discovery, and teleportation
- **Owner Revocation** - Remove player discoveries without affecting other players

### Teleportation System
- **Compass Menu** - Right-click with compass to open warp navigation menu
- **Easy Discovery** - Right-click waystones to discover them (particles change color)
- **Search & Filter** - Find waystones by name, view discovered vs owned, mark favorites
- **Favorite Markers** - Mark frequently used waystones with heart icons
- **Teleport Costs** - Configure costs in XP, items, or economy currency
- **Teleport Timer** - Configurable delays before teleportation
- **Cooldown System** - Per-player or global teleport cooldowns with bypass permissions

### Home Waystone
- **Personal Fast Travel** - Set one waystone as your personal home
- **Reduced Cost** - Home teleports cost half the normal fare
- **Quick Access** - Use `/home` command for instant teleportation
- **Smart Setup** - One home per player, clear visual indication (red bed icon)

### Protection System
- **Anti-Grief** - Lock waystones so only the owner can break them
- **Thorns Defense** - Non-owners take damage when attempting to break protected waystones
- **Clear Indicators** - Dark blue hologram for protected waystones
- **Customizable Costs** - Adjustable price multipliers for enabling protection

### Bedrock Edition Support (Java & Bedrock Compatible)
- **Native Bedrock Forms** - GeyserMC players get native Bedrock UI forms
- **Menu Parity** - Bedrock and Java players have equivalent feature access
- **Smooth Integration** - Reflection-based API integration for full JDK 21+ support
- **Connection Stability** - Aggressive keepalive prevents 15-20 min timeout issues
- **Bedrock-Specific UI** - Custom textures and layouts optimized for Bedrock clients
- **Form Types** - Simple menus, modals, and custom forms for complex interactions

### Advanced Player Management
- **Per-Player Limits** - Vault integration for warp limits per player/group
- **Customizable Costs** - Different teleport costs for different players/ranks
- **Adjustable Timers** - Per-player teleport delay configuration
- **Graceful Cost Deduction** - Automatic payment from economy, XP, or inventory

### World & Administrative Tools
- **Multi-World Support** - Create and discover waystones across multiple worlds
- **Invalid Waystone Management** - Admin commands to list and clean up broken waystones
- **World-Specific Cleanup** - Remove orphaned waystones from unloaded worlds
- **Event System** - Public API events for warp creation, deletion, and updates
- **Hologram System** - Dynamic hologram displays for waystone information
- **Particle Effects** - Visual waystone discovery effects (customizable)

## Installation

Download the latest release (.jar file) from the releases tab and place it in your server's plugins folder. 

For additional functionality such as per player/rank permissions and warp limits, you must install 
[Vault](https://www.spigotmc.org/resources/vault.34315/) as well as a compatible permission and chat
metadata provider. [LuckPerms](https://luckperms.net/) is a recommended plugin for handling both.

## Getting Started

### Creating a Waystone
1. Gather materials: Find or craft a **lodestone** and place it on top of a **smooth stone** block
2. Right-click the lodestone to open the creation menu
3. Enter a name for your waystone
4. Your waystone is created! You'll now see the management menu with options to customize it

### Discovering Waystones
1. Find a waystone placed by another player
2. Right-click it to begin discovery
3. Watch the particles change color to show you've discovered it
4. The waystone is now available in your teleport menu

### Teleporting Between Waystones
- **On Java Edition**: Right-click with a **compass** in hand to open the warp navigation menu
- **On Bedrock Edition**: Use the **Warp button** in the GeyserMenu main menu (if installed)
- Select a waystone from the list to teleport

### Advanced Features
- **Mark as Home**: Set one waystone as your personal home (accessible via `/home`)
- **Enable Protection**: Lock your waystone so only you can break it
- **Manage Access**: Set to private or add players to a whitelist
- **Search & Filter**: Use the menu search to find specific waystones
- **Favorites**: Mark frequently used waystones with a heart icon

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/warpmenu` or `/ww` | `waystonewarps.command.warpmenu` | Opens the warp navigation menu |
| `/home` | `waystonewarps.command.home` | Teleports you to your home waystone |
| `/waystonewarps break <warp-id>` | Admin | Deletes a waystone by ID |
| `/waystonewarps move <warp-id>` | Admin | Moves a waystone to your current location |
| `/waystonewarps invalids list` | `waystonewarps.admin.invalids.list` | Lists invalid/orphaned waystones |
| `/waystonewarps invalids remove <world>` | `waystonewarps.admin.invalids.remove` | Removes invalid waystones in a world |
| `/waystonewarps invalids removeall` | `waystonewarps.admin.invalids.removeall` | Removes all invalid waystones |

## Permissions

| Permission Node | Description |
|-----------------|-------------|
| `waystonewarps.command.warpmenu` | Allows use of the warp menu command to open teleport menu |
| `waystonewarps.command.home` | Allows use of the `/home` command to teleport to home waystone |
| `waystonewarps.create` | Allows creation of waystones |
| `waystonewarps.discover` | Allows discovery of waystones |
| `waystonewarps.teleport` | Allows teleportation between waystones |
| `waystonewarps.teleport.interworld` | Allows teleportation to undiscovered waystones in other worlds |
| `waystonewarps.teleport.cooldown_bypass` | Bypasses the teleport cooldown timer |
| `waystonewarps.bypass.open_menu` | Allows opening the management menu on any waystone |
| `waystonewarps.bypass.access_control` | Allows changing privacy mode (public/private/whitelist) |
| `waystonewarps.bypass.manage_players` | Allows managing discovered players and whitelist |
| `waystonewarps.bypass.rename` | Allows renaming waystones |
| `waystonewarps.bypass.skins` | Allows changing waystone skins |
| `waystonewarps.bypass.home` | Allows setting/unsetting home waystone |
| `waystonewarps.bypass.protection` | Allows enabling/disabling protection mode |
| `waystonewarps.bypass.delete` | Allows deleting waystones |
| `waystonewarps.bypass.relocate` | Allows moving waystones to new locations |
| `waystonewarps.admin.invalids.list` | Allows listing invalid/orphaned waystones |
| `waystonewarps.admin.invalids.remove` | Allows removing invalid waystones for specific world |
| `waystonewarps.admin.invalids.removeall` | Allows removing all invalid waystones from all worlds |

## Per Player Limits

Ensure that you have a Vault provider installed to set limits as described in the Installation section. Each Vault provider plugin has its own way of implementing this feature. LuckPerms is the recommended provider.

To set metadata for a player or group, use:

**For groups:**
```
/lp group <group_name> meta set <limit_name> <desired_number>
```

**For individual players:**
```
/lp user <user_name> meta set <limit_name> <desired_number>
```

Available limits:
- `waystonewarps.warp_limit` - Maximum number of waystones a player can create
- `waystonewarps.teleport_cost` - Custom teleport cost for this player/group
- `waystonewarps.teleport_timer` - Custom teleport delay for this player/group

## Bedrock Edition Support

### Requirements
- **[GeyserMC](https://geysermc.org/)** - Allows Bedrock players to join Java servers
- **[GeyserMenu Companion](https://github.com/dronzer-tb/geyser-menu-companion)** (optional) - Enables native Bedrock form UI for waystones

Without GeyserMenu Companion, Bedrock players can still use waystones through the Java menu system, but will have a better experience with native Bedrock forms.

### Bedrock Features
- **Native Form UI** - Custom forms optimized for Bedrock Edition clients
- **Search & Filter** - Find waystones with native Bedrock input
- **Editor Menu** - Manage waystone settings with Bedrock-specific icons
- **Warp Button** - Quick access button in GeyserMenu main menu
- **Stable Connection** - 5-second keepalive prevents connection timeouts
- **Full Feature Parity** - All Java features available to Bedrock players

## Configuration

The plugin stores configuration in `config.yml`. Key settings include:

- **Teleportation Costs** - Set base costs in XP, items, or economy currency
- **Teleportation Timer** - Delay before teleportation occurs (in seconds)
- **Cooldown Duration** - Time between teleports for each player
- **Protection Mode Multiplier** - Cost multiplier for enabling protection (default 5×)
- **Home Unset Cost Multiplier** - Cost multiplier for disabling home waystone (default 2×)
- **Home Teleport Reduction** - Home teleport cost reduction (default 0.5×)
- **Compass Menu** - Toggle waystone menu on compass right-click
- **Lodestone Menu** - Toggle waystone menu on lodestone right-click

Example configuration values can be found in `sample-config.yml` after first run.

## Architecture

Waystone Warps uses a clean layered architecture:

```
┌─────────────────────────────────────────┐
│         Interaction (UI/Commands)       │  - Menus, Forms, Commands
├─────────────────────────────────────────┤
│         Application (Actions/Services)  │  - Use cases, orchestration
├─────────────────────────────────────────┤
│           Domain (Business Logic)       │  - Entities, repositories
├─────────────────────────────────────────┤
│      Infrastructure (Data & External)   │  - SQLite, Bukkit, GeyserMenu
└─────────────────────────────────────────┘
```

### Components

**Interaction Layer:**
- Command handlers (WarpMenuCommand, HomeCommand, InvalidsCommand, etc.)
- Menu implementations (Java GUI, Bedrock forms)
- Event listeners (discovery, destruction, placement)

**Application Layer:**
- Action classes (CreateWarp, TeleportPlayer, ToggleHome, etc.)
- Service classes (movement tracking, particles, holograms)
- Configuration management
- Event publishing system

**Domain Layer:**
- Warp entity with ownership, privacy, home, and protection flags
- Repositories for warps, discoveries, whitelist, and player state
- Business rules and validation

**Infrastructure Layer:**
- SQLite persistence with schema migrations
- Bukkit/Paper integration
- GeyserMenu reflection-based integration
- Vault integration for economy and permissions

## Database Schema

Waystone Warps uses SQLite with the following main tables:

- **warps** - Waystone data (name, location, owner, privacy, skins, home, protection)
- **discoveries** - Player waystone discoveries with favorite status
- **whitelist** - Waystone access control per player
- **player_state** - In-memory player session state

Schema is automatically created and migrated on first run.

## API

The plugin exposes a public API through `WaystoneWarpsAPI`:

```kotlin
WaystoneWarpsAPI.getInstance()?.getWarpRepository()?.getAll()
```

**Available Events:**
- `WarpCreateEvent` - Fired when a waystone is created
- `WarpDeleteEvent` - Fired when a waystone is deleted
- `WarpUpdateEvent` - Fired when a waystone is modified

Listen to these events in your plugins:

```kotlin
@EventHandler
fun onWarpCreate(event: WarpCreateEvent) {
    val warp = event.warp
    // Handle warp creation
}
```

## Troubleshooting

### Bedrock Forms Not Appearing
- Ensure GeyserMenu Companion plugin is installed and running
- Check server logs for `[Bedrock]` messages
- Verify GeyserMC/Floodgate are loaded before WaystoneWarps
- Try restarting the server if forms timeout

### Waystones Not Being Discovered
- Ensure player has `waystonewarps.discover` permission
- Check that the waystone base structure is intact
- Right-click the lodestone (center block) to discover

### Teleportation Costs Not Working
- Install Vault and an economy provider (e.g., EssentialsX Economy)
- Ensure economy plugin is loaded before WaystoneWarps
- Check that players have sufficient balance/XP

### Commands Not Working
- Verify permissions are assigned correctly
- Ensure plugin is fully loaded (check startup messages)
- Try using full command path: `/waystonewarps:warpmenu`

## Performance Considerations

- **Particle Effects** - Uses player-specific particles to avoid performance impact
- **Hologram System** - Dynamic holograms only update when necessary
- **Database** - SQLite with optimized queries and connection pooling
- **Keepalive** - 5-second tick for Bedrock connection is low-overhead
- **Caching** - Player Bedrock status is cached to reduce repeated checks

For servers with thousands of waystones, consider:
- Setting reasonable teleport cooldowns
- Limiting particle effect ranges
- Using permission-based warp limits

## Contributing

Contributions are welcome! Please feel free to:
- Report bugs via GitHub Issues
- Suggest features and improvements
- Submit pull requests with enhancements

## Future Roadmap

Potential features for future releases:
- Waystone networks and categories
- Cross-server waystone teleportation (with Bungeecord support)
- Waystone claim system
- Advanced permission system
- Voice/sound notifications
- Particle customization per waystone

## Building from Source

### Requirements

- Java JDK 21 or newer
- Git
- Gradle (included via gradlew)

### Compiling

```bash
git clone https://github.com/mizarc/waystone-warps.git
cd waystone-warps/
./gradlew build
```

The compiled .jar binary can be found in the `build/libs` folder.

### Development Setup

```bash
# Clone repository
git clone https://github.com/mizarc/waystone-warps.git
cd waystone-warps/

# Build with tests
./gradlew build

# Deploy to local server
./gradlew deploy
# (Set plugin.server.path in gradle.properties first)
```

## Support

If you encounter any bugs, crashes, or unexpected behaviour, please:

1. **Check the logs** - Look for error messages with `[WaystoneWarps]` prefix
2. **Update to latest version** - Your issue may already be fixed
3. **Open an issue** - Provide logs, server version, plugin version, and steps to reproduce
4. **Join the community** - Discuss on Spigot forums or Discord servers

**GitHub Issues:** [https://github.com/mizarc/waystone-warps/issues](https://github.com/mizarc/waystone-warps/issues)

## Credits

Developed and maintained by the Waystone Warps team.

Special thanks to:
- **Geyser Project** - Cross-platform Minecraft compatibility
- **Paper/Spigot Community** - Excellent server APIs
- **GeyserMenu Contributors** - Native Bedrock menu integration
- **Community Testers** - Bug reports and feature feedback

## License

Waystone Warps is licensed under the GNU General Public License v3 (GPL-3.0). 

This means:
- ✅ You can use, modify, and distribute this plugin
- ✅ Any derivative works must also be licensed under GPL-3.0
- ✅ Source code must be made available when distributed
- ✅ No warranty is provided

Please view [LICENSE](LICENSE) for the full license text and terms.
