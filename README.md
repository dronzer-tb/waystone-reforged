<div align="center">

<table border="0" cellspacing="0" cellpadding="0">
<tr>
<td width="370" align="center" valign="middle">
<img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/waystones%20logo.png" width="350"/>
</td>
<td width="1" style="border-left: 2px solid #444; padding: 0;"><img width="1" height="350" src="https://via.placeholder.com/1x350/444444/444444"/></td>
<td valign="middle" align="left" style="padding-left: 20px;">

> **A fork of [Waystone Warps](https://github.com/mizarc/waystone-warps) by mizarc** — extended with Bedrock support, home waystones, protection mode, and a lot more.

> [!WARNING]
> 🚧 **Work in Progress** — Expect bugs and breaking changes. Use at your own risk.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Platform-Paper-brightgreen.svg)](https://papermc.io/)
[![Geyser](https://img.shields.io/badge/Bedrock-GeyserMC-yellow.svg)](https://geysermc.org/)

</td>
</tr>
</table>

</div>

---

## 📖 What is Waystone Reforged?
Waystone Reforged transforms travel on your Minecraft server. Place lodestones on smooth stone blocks to create physical teleportation anchors — **waystones** — that players can discover, manage, and warp between through intuitive GUI menus.

This fork builds on the solid foundation of the original Waystone Warps plugin and adds **full Bedrock Edition support**, a **home waystone system**, **anti-grief protection**, search & favourites, waystone skins, and a much more polished UX for both Java and Bedrock players.

---

## 🆕 What's New in Reforged

### 🗺️ Warp Menu

The main warp navigation menu — right-click with a compass to open. Lists all your discovered waystones with ♥ favourite and ★ owner markers.

<div align="center">
<img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/warp%20menu.png" alt="Warp Menu" width="70%"/>
</div>

---

### ⚙️ Waystone Editor

Full management menu for each waystone. Rename, change privacy, manage players, set home, enable protection, and more — all without typing a single command.

<div align="center">

| Editor View 1 | Editor View 2 |
|:-:|:-:|
| <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/waystone-editor(1).png" width="380"/> | <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/waystones-editor(2).png" width="380"/> |

</div>

---

### 🔍 Search & Filter

Find any waystone instantly by name, or filter by owned, discovered, and favourites. Both Java and Bedrock players get the full search experience.

<div align="center">

| Search | Search & Filter | Filter Options |
|:-:|:-:|:-:|
| <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/search.png" width="240"/> | <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/search%20and%20filter.png" width="240"/> | <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/filter%20options.png" width="240"/> |

</div>

---

### 🎨 Waystone Skins

Customize your waystone's appearance with multiple visual variants to make yours stand out.

<div align="center">

| Skin Option 1 | Skin Option 2 | Skin Option 3 |
|:-:|:-:|:-:|
| <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/waystones%20skins(1).png" width="240"/> | <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/waystone%20skins(2).png" width="240"/> | <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/waystones%20skins(3).png" width="240"/> |

</div>

---

### 👥 Player Management

Manage who has discovered your waystone, revoke access from individual players, or whitelist specific ones.

<div align="center">

| Discovered Players | Player Options |
|:-:|:-:|
| <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/Discovered%20players.png" width="380"/> | <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/player%20options.png" width="380"/> |

</div>

---

### 🧭 Discovered Waystones & Warp Options

View all your discovered waystones in one place, and quickly act on them with warp options.

<div align="center">

| Discovered Waystones | Warp Options |
|:-:|:-:|
| <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/Discovered%20waystones.png" width="380"/> | <img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/warp%20option.png" width="380"/> |

</div>

---

### ✏️ Renaming Waystones

Give each waystone a unique name directly from the management menu.

<div align="center">
<img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/Renaming%20waystones.png" alt="Renaming Waystones" width="70%"/>
</div>

---

### 🛡️ Protection Mode

Lock your waystone so only you can break it. Non-owners take thorns damage on attempt. Protected waystones display a **dark blue hologram** indicator.

<div align="center">
<img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/Protection%20mode.png" alt="Protection Mode" width="70%"/>
</div>

---

### 📱 Bedrock Edition — GeyserMenu Integration

Full native Bedrock form support via GeyserMC. Bedrock players get touch-friendly forms for every menu interaction with complete feature parity to Java. Also fixes the infamous **15–20 minute form timeout** with an aggressive 5-second keepalive.

<div align="center">
<img src="https://raw.githubusercontent.com/dronzer-tb/waystone-reforged/main/assets/geyser%20menu%20integration.png" alt="GeyserMenu Integration" width="70%"/>
</div>

---

## 🧱 Core Features

| Feature | Description |
|---|---|
| 🪨 **Physically Grounded** | Waystones are real structures — lodestone on smooth stone |
| 🖱️ **GUI Menus** | Full click-menu management, zero commands required |
| 🔒 **Access Control** | Public, private, or whitelist per waystone |
| 🏠 **Home Waystone** | Set one waystone as home — `/home` teleports at half cost |
| 🛡️ **Protection Mode** | Anti-grief lock with thorns damage for non-owners |
| 💸 **Teleport Costs** | XP, items, or economy currency via Vault |
| ⏱️ **Cooldowns & Timers** | Per-player configurable delays and cooldowns |
| ❤️ **Favourites** | Mark waystones with ♥ for quick access |
| 🌍 **Multi-World** | Waystones across all worlds |
| 🎨 **Waystone Skins** | Visual variants to make yours stand out |
| 🧹 **Admin Tools** | Clean up orphaned waystones easily |

---

## 📥 Installation

1. Download the latest `.jar` from the [Releases](../../releases) tab
2. Drop it into your server's `plugins/` folder
3. Restart the server

**Optional but recommended:**
- [Vault](https://www.spigotmc.org/resources/vault.34315/) + [LuckPerms](https://luckperms.net/) — for per-player warp limits and economy
- [GeyserMC](https://geysermc.org/) + GeyserMenu Companion — for native Bedrock UI forms

---

## 🚀 Getting Started

### Creating a Waystone
1. Place a **lodestone** on top of a **smooth stone block**
2. Right-click the lodestone to open the creation menu
3. Give your waystone a name — you're done!

### Discovering Waystones
- Find another player's waystone and right-click it
- Watch the particles shift colour — it's now in your list

### Teleporting
- **Java:** Right-click with a **compass** in hand
- **Bedrock:** Use the **Warp** button in the GeyserMenu main menu

---

## 🔧 Commands

| Command | Permission | Description |
|---|---|---|
| `/warpmenu` or `/ww` | `waystonewarps.command.warpmenu` | Open the warp navigation menu |
| `/home` | `waystonewarps.command.home` | Teleport to your home waystone |
| `/waystonewarps break <id>` | Admin | Delete a waystone by ID |
| `/waystonewarps move <id>` | Admin | Move a waystone to your position |
| `/waystonewarps invalids list` | `waystonewarps.admin.invalids.list` | List orphaned waystones |
| `/waystonewarps invalids remove <world>` | `waystonewarps.admin.invalids.remove` | Remove orphaned waystones in a world |
| `/waystonewarps invalids removeall` | `waystonewarps.admin.invalids.removeall` | Remove all orphaned waystones |

---

## 🔑 Permissions

<details>
<summary>Click to expand full permissions list</summary>

| Permission Node | Description |
|---|---|
| `waystonewarps.command.warpmenu` | Use the warp menu command |
| `waystonewarps.command.home` | Use the `/home` command |
| `waystonewarps.create` | Create waystones |
| `waystonewarps.discover` | Discover waystones |
| `waystonewarps.teleport` | Teleport between waystones |
| `waystonewarps.teleport.interworld` | Teleport to undiscovered waystones in other worlds |
| `waystonewarps.teleport.cooldown_bypass` | Bypass teleport cooldown |
| `waystonewarps.bypass.open_menu` | Open management menu on any waystone |
| `waystonewarps.bypass.access_control` | Change waystone privacy mode |
| `waystonewarps.bypass.manage_players` | Manage discovered players and whitelist |
| `waystonewarps.bypass.rename` | Rename waystones |
| `waystonewarps.bypass.skins` | Change waystone skins |
| `waystonewarps.bypass.home` | Set/unset home waystone |
| `waystonewarps.bypass.protection` | Enable/disable protection mode |
| `waystonewarps.bypass.delete` | Delete waystones |
| `waystonewarps.bypass.relocate` | Move waystones |
| `waystonewarps.admin.invalids.list` | List invalid waystones |
| `waystonewarps.admin.invalids.remove` | Remove invalid waystones for a world |
| `waystonewarps.admin.invalids.removeall` | Remove all invalid waystones |

</details>

---

## 📊 Per Player Limits

Requires **Vault** + **LuckPerms** (or a compatible provider).

```bash
# Set limit for a group
/lp group <group_name> meta set <limit_name> <value>

# Set limit for a specific player
/lp user <user_name> meta set <limit_name> <value>
```

| Limit Key | Description |
|---|---|
| `waystonewarps.warp_limit` | Max waystones a player can create |
| `waystonewarps.teleport_cost` | Custom teleport cost |
| `waystonewarps.teleport_timer` | Custom teleport delay (seconds) |

---

## 📡 Bedrock Edition

Bedrock players are fully supported via **GeyserMC**. Install GeyserMenu Companion for native form UI — without it, Bedrock players fall back to the Java menu system.

**Key Bedrock improvements in Reforged:**
- 5-second keepalive fixes the 15–20 min form timeout bug
- Native `SimpleMenu`, `ModalMenu`, and `CustomForm` support
- Bedrock-optimized icons and layout
- Full feature parity with Java Edition

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│     Interaction (UI / Commands)         │  Menus, Forms, Commands
├─────────────────────────────────────────┤
│     Application (Actions / Services)    │  Use cases, orchestration
├─────────────────────────────────────────┤
│     Domain (Business Logic)             │  Entities, repositories
├─────────────────────────────────────────┤
│     Infrastructure (Data & External)    │  SQLite, Bukkit, GeyserMenu
└─────────────────────────────────────────┘
```

---

## 🔌 API

```kotlin
// Get all warps
WaystoneWarpsAPI.getInstance()?.getWarpRepository()?.getAll()
```

**Available Events:**

```kotlin
@EventHandler
fun onWarpCreate(event: WarpCreateEvent) {
    val warp = event.warp
    // handle warp creation
}
```

| Event | Fired When |
|---|---|
| `WarpCreateEvent` | A waystone is created |
| `WarpDeleteEvent` | A waystone is deleted |
| `WarpUpdateEvent` | A waystone is modified |

---

## 🔨 Building from Source

**Requirements:** Java JDK 21+, Git

```bash
git clone https://github.com/dronzer-tb/waystone-reforged.git
cd waystone-reforged/
./gradlew build
```

Output jar: `build/libs/`

```bash
# Deploy to local test server
# Set plugin.server.path in gradle.properties first
./gradlew deploy
```

---

## 🐛 Troubleshooting

<details>
<summary><strong>Bedrock forms not appearing</strong></summary>

- Ensure GeyserMenu Companion is installed and running
- Check server logs for `[Bedrock]` messages
- Verify GeyserMC/Floodgate load **before** WaystoneWarps
- Restart the server if forms timeout after an extended session

</details>

<details>
<summary><strong>Waystones not being discovered</strong></summary>

- Check the player has `waystonewarps.discover` permission
- Ensure the base structure (lodestone on smooth stone) is intact
- Right-click the lodestone specifically, not adjacent blocks

</details>

<details>
<summary><strong>Teleport costs not working</strong></summary>

- Install Vault and an economy provider (e.g. EssentialsX Economy)
- Ensure the economy plugin loads **before** WaystoneWarps
- Check the player has sufficient balance/XP

</details>

---

## 🗺️ Roadmap

- [ ] Waystone networks and categories
- [ ] Cross-server teleportation (BungeeCord/Velocity support)
- [ ] Waystone claim integration
- [ ] Sound/voice notifications on discovery
- [ ] Per-waystone particle customization
- [ ] Advanced permission tiers

---

## 🤝 Contributing

PRs and issues are welcome! This is a community fork — open an issue first if you want to discuss direction before submitting a PR.

---

## 📜 Credits

- **[mizarc](https://github.com/mizarc)** — Original [Waystone Warps](https://github.com/mizarc/waystone-warps) plugin
- **[Geyser Project](https://geysermc.org/)** — Cross-platform Minecraft compatibility
- **[Paper/Spigot Community](https://papermc.io/)** — Excellent server APIs
- **Community Testers** — Bug reports and feedback

---

## ⚖️ License

Licensed under **GPL-3.0**. Any derivative works must also be open-source under GPL-3.0.

See [LICENSE](LICENSE) for full terms.

<div align="center">

---

*Waystone Reforged — A fork built for DronzerSMP and beyond.*

</div>
