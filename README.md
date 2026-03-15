![Banner](branding/banner.png)

# Waystone Warps

Transform your travels with the Waystone Warps plugin! Craft and place waystones to set up teleportation points throughout your world. Discover new waystones placed by others as you explore and easily teleport between them. Streamline your journey across the world knowing you can always teleport back home in a snap, or simply visit your friends in an instant.

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

## Building from Source

### Requirements

- Java JDK 21 or newer
- Git

### Compiling

```
git clone https://github.com/mizarc/waystone-warps.git
cd waystone-warps/
./gradlew build
```

The compiled .jar binary can be found in the `build/libs` folder.

## Support

If you encounter any bugs, crashes, or unexpected behaviour, please [open an issue](https://github.com/mizarc/waystone-warps/issues) in this repository.

## License

Waystone Warps is licensed under the permissive MIT license. Please view [LICENSE](LICENSE) for more info.
