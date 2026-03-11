# Waystone Warps — Development Log

Full record of all modifications made to the Waystone Warps plugin during this development cycle, covering Bedrock integration, new gameplay features, UI redesigns, and bug fixes.

---

## Table of Contents

1. [Phase 1 — Icon Removal](#phase-1--icon-removal)
2. [Phase 2 — GeyserMC / Bedrock Integration](#phase-2--geysermc--bedrock-integration)
3. [Phase 3 — Home Waystone & Protection Mode](#phase-3--home-waystone--protection-mode)
4. [Phase 4 — Bedrock Editor Menu Redesign](#phase-4--bedrock-editor-menu-redesign)
5. [Phase 5 — Bedrock Warp Navigation Menu](#phase-5--bedrock-warp-navigation-menu)
6. [Phase 6 — Bug Fixes & Stability](#phase-6--bug-fixes--stability)
7. [Technical Architecture](#technical-architecture)
8. [Files Changed](#files-changed)
9. [Configuration & Permissions](#configuration--permissions)

---

## Phase 1 — Icon Removal

**Goal:** Remove the waystone icon customisation feature from the editor menu.

### Changes

- **Deleted** `WarpIconMenu.kt` — the entire icon selection GUI
- **Deleted** `UpdateWarpIcon.kt` — the action class for persisting icon changes
- **Removed** the icon button from `WarpManagementMenu.kt` (Java editor)
- **Removed** associated localisation keys and permission nodes

---

## Phase 2 — GeyserMC / Bedrock Integration

**Goal:** Allow Bedrock Edition players (connecting via Geyser) to interact with waystones through native Bedrock forms delivered by the GeyserMenu Companion API.

### Architecture

The plugin integrates with two external projects (neither is a compile-time dependency):

| Component | Role |
|-----------|------|
| [geyser-menu](https://github.com/dronzer-tb/geyser-menu) | Geyser extension — TCP server + in-game menu overlay |
| [geyser-menu-companion](https://github.com/dronzer-tb/geyser-menu-companion) | Spigot plugin — TCP client, exposes `GeyserMenuAPI` |

**All integration is done via pure Java reflection** to avoid classpath/module issues on JDK 17+.

### Key Technical Decision — Reflection Pattern

Direct imports of the GeyserMenu Companion API fail at runtime because the API returns **private inner class** implementations. On JDK 17+ calling methods through a private type causes `IllegalAccessException`.

**Solution:** Cache the **method return type** at init time (the public interface), then route all builder calls through that type:

```kotlin
val builderMethod = menuButtonClass.getMethod("builder")
val builder = builderMethod.invoke(null)
val builderType = builderMethod.returnType  // ← public interface
builderType.getMethod("id", String::class.java).invoke(builder, id)
```

### New Files Created

| File | Purpose |
|------|---------|
| `BedrockSupport.kt` | Core reflection-based form abstraction — `sendSimpleForm()`, `sendModalForm()`, `sendCustomForm()`, `isBedrockPlayer()` |
| `GeyserMenuIntegration.kt` | Registers "Warp" and "Home" buttons in the GeyserMenu main menu |
| `BedrockWarpMenu.kt` | Warp list / navigation / search / filter menu system |
| `BedrockWarpManagementMenu.kt` | Waystone editor menu (owner management) |
| `BedrockWarpNamingMenu.kt` | Waystone creation form (custom form with text input) |

### Plugin Configuration

- Added `load: POSTWORLD` to `plugin.yml` (ensures Geyser/Floodgate load first)
- Added `Geyser-Spigot`, `floodgate`, `GeyserMenuCompanion` to `softdepend`
- 40-tick delayed button registration (GeyserMenu needs time to fully initialise)

### Bedrock Player Detection

Uses Floodgate API via reflection (`FloodgateApi.getInstance().isFloodgatePlayer(uuid)`) with a local cache for performance.

---

## Phase 3 — Home Waystone & Protection Mode

**Goal:** Add two new waystone features — Home (personal fast-travel) and Protection (anti-grief).

### Home Waystone

| Feature | Detail |
|---------|--------|
| Limit | One home waystone per player |
| Cost to set | Free |
| Cost to unset | `home_unset_cost_multiplier × base_cost` (default 2×) |
| Teleport cost | Half the normal base cost |
| Hologram | Displays "Home: PlayerName" |
| Command | `/home` overrides Essentials if a home waystone is set |
| Icons | White bed (off), Red bed (on) |

### Protection Mode

| Feature | Detail |
|---------|--------|
| Cost to enable | `protection_mode_cost_multiplier × base_cost` (default 5×) |
| Cost to disable | Free |
| Effect | Only the owner can break the waystone |
| Non-owner break attempt | Event cancelled + 2 hearts thorns damage |
| Hologram | Owner name turns dark blue when protected |
| Icons | Obsidian (on), Crying obsidian (off) |

### Database Migration

`Migration2_AddHomeAndProtection` adds two boolean columns to the `warps` table:

```sql
ALTER TABLE warps ADD COLUMN isHome INTEGER NOT NULL DEFAULT 0;
ALTER TABLE warps ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 0;
```

### New Action Classes

- `ToggleHome` — toggles the home flag, enforces one-per-player
- `ToggleProtection` — toggles protection, updates hologram
- `GetHomeWarp` — retrieves a player's home waystone
- `HomeCommand` — `/home` command implementation

### Listener Changes

- `WaystoneDestructionListener` — checks `warp.isProtected`, cancels break for non-owners, applies thorns damage
- `WarpManagementMenu` — Java editor redesigned with new layout: Private, Blank, Players, Home, Rename, Skins, Protection, Blank, Move

---

## Phase 4 — Bedrock Editor Menu Redesign

**Goal:** Redesign the Bedrock waystone editor menu (`BedrockWarpManagementMenu`) with specific Minecraft texture icons and a full sub-menu system.

### Main Editor Menu Buttons

| Button | Icon (Bedrock texture path) | Behaviour |
|--------|-----------------------------|-----------|
| Public/Private | `textures/items/lever` / `textures/blocks/redstone_torch_on` | Toggle lock state |
| Discovered Players | `textures/blocks/steve` | List discoverers with whitelist/revoke |
| Home | `textures/items/bed_white` / `textures/items/bed_red` | Toggle home waystone |
| Rename | `textures/items/name_tag` | Open rename form |
| Skins | `textures/blocks/lodestone_top` | Open skin selection |
| Protection | `textures/blocks/obsidian` / `textures/blocks/crying_obsidian` | Toggle protection mode |
| Move | `textures/blocks/piston_side` | Move waystone |
| Back | `textures/items/nether_star` | Close menu |

### Discovered Players Sub-Menu

- Lists all players who have discovered the waystone
- Owner marked with gold star (★) and **cannot be revoked**
- Per-player actions: Whitelist (lantern icon), Revoke (barrier icon)

### Visual Style

- All text uses `§l§8` (bold dark grey)
- Status labels (On/Off) use `§a` (green) / `§8` (grey)
- All images use **built-in Bedrock texture paths** (no external URLs)

---

## Phase 5 — Bedrock Warp Navigation Menu

**Goal:** Redesign the Bedrock warp menu (opened via GeyserMenu button or `/ww`) with waystone browsing, search, filtering, favourites, and deletion.

### GeyserMenu Buttons (Main Bedrock Menu)

| Button | Icon | Action |
|--------|------|--------|
| **Warp** | `textures/blocks/lodestone_top` | Opens warp navigation menu |
| **Home** | `textures/items/bed_red` | Instant teleport to home waystone |

### Warp Navigation Menu Hierarchy

```
Warp (main)
├── Waystone → List of all discovered waystones
│   └── [Click waystone] → Waystone Detail
│       ├── Warp (ender pearl, shows cost)
│       ├── Favourite (redstone dust ↔ gunpowder toggle)
│       ├── Delete (barrier → confirmation modal)
│       └── Back
├── Search & Filter
│   ├── Search (spyglass) → Custom form text input
│   │   ├── Results found → Waystone list
│   │   └── No results → "Try Again" / "Cancel"
│   ├── Filter (hopper)
│   │   ├── Discovered (sugar) → Filtered list
│   │   ├── Favourites (redstone dust) → Filtered list
│   │   ├── Owned (glowstone dust) → Filtered list
│   │   └── Back
│   └── Back
└── Back (nether star)
```

### Waystone List Features

- Waystones sorted alphabetically
- Favourite marker: `§c♥`
- Owned marker: `§6★`
- Empty lists show "No waystones found" with Back button

---

## Phase 6 — Bug Fixes & Stability

### Discovery Particle/Sound Loop (Critical)

**Problem:** When a Bedrock player discovered a waystone, the totem animation and sound played in an infinite loop.

**Root Cause:** `TOTEM_OF_UNDYING` particle type triggers a full totem activation animation on Bedrock clients (including built-in looping sound). Additionally, `world.spawnParticle()` / `world.playSound()` broadcast to ALL nearby players including Bedrock clients connected via Geyser.

**Fixes Applied:**
1. Replaced `Particle.TOTEM_OF_UNDYING` with `Particle.HAPPY_VILLAGER` (safe on Bedrock)
2. Changed all `world.spawnParticle()` → `player.spawnParticle()` (player-specific, no broadcast)
3. Changed all `world.playSound()` → `player.playSound()` (player-specific)
4. Added timed cleanup of `recentDiscoveryEffects` guard entries (10-second TTL)
5. Added `PlayerQuitEvent` handler to clean up `bedrockInteractCooldowns` map (memory leak fix)
6. Applied same player-specific fix to `PlayerParticleServiceBukkit` (teleport particles/sounds)

### Privacy Toggle Double-Mutation

**Problem:** Clicking Public/Private in the Bedrock editor didn't toggle the state.

**Root Cause:** The repository returns the **same cached object instance**. Action classes (e.g., `ToggleLock`) already mutate `warp.isLocked` directly. The Bedrock menu then did `warp.isLocked = !warp.isLocked` manually — flipping it **back** to the original state.

**Fix:** Removed all redundant manual state assignments from the Bedrock menu toggle handlers.

### Protection Mode via Bedrock Not Preventing Breaking

**Problem:** Protection enabled from the Bedrock editor didn't actually prevent other players from breaking the waystone. Java editor worked fine.

**Root Cause:** The Bedrock menu was not passing the `bypassOwnership` permission parameter to `toggleLock`, `toggleHome`, and `toggleProtection` action calls (the Java menu did pass them).

**Fix:** Added `bypassOwnership = player.hasPermission(...)` to all three toggle calls in `BedrockWarpManagementMenu`.

### Menu Stops Working After 30–40 Minutes

**Problem:** After ~30 minutes the Bedrock forms stopped being delivered.

**Root Cause:** GeyserMenu Companion uses a TCP (Netty) connection to the Geyser extension. Linux `SO_KEEPALIVE` defaults to 2 hours, but NAT/firewalls drop idle connections at ~30 minutes. `sendMenu()` silently returns if not authenticated (form lost).

**Fixes:**
1. 60-second keepalive task pinging `isConnected()` via reflection
2. `ensureConnected()` check before every form send
3. Auto-retry after 1 second if disconnected
4. `refreshApi()` to re-fetch singleton if companion plugin was reloaded

### Database Schema Migration Crash

**Problem:** Plugin failed to enable with "Database schema version 3 is newer than supported version 2".

**Root Cause:** A previous development build had a migration 3 that was later removed.

**Fix:** `SchemaMigrator` now gracefully skips when the DB version is ahead (instead of throwing).

### Cost Deduction Missing

**Problem:** Protection mode could be enabled and home unset without paying.

**Fix:** Added `checkAndDeductCost()` calls before `toggleProtection` and `toggleHome` in the Bedrock menu, matching the Java menu behaviour.

### Permission-Based Warp Limits

**Added:** `waystonewarps.limit.<n>` permission nodes scanned from `player.effectivePermissions`. Uses highest matching value. Falls back to config `warp_limit` (default 3) if no permission node is set.

---

## Technical Architecture

### Reflection-Based Bedrock Support

All Bedrock form delivery goes through `BedrockSupport.kt` which uses pure reflection:

```
BedrockSupport.kt
├── sendSimpleForm()    → GeyserMenuAPI.createSimpleMenu()
├── sendModalForm()     → GeyserMenuAPI.createModalMenu()
├── sendCustomForm()    → GeyserMenuAPI.createCustomMenu()
├── isBedrockPlayer()   → FloodgateApi.isFloodgatePlayer()
├── keepalive task      → 60s ping to prevent TCP timeout
└── ensureConnected()   → Health check + retry before each send
```

### Repository Caching (Critical Knowledge)

`WarpRepositorySQLite` uses an in-memory `MutableMap<UUID, Warp>` cache. All `getById()`, `getByPosition()`, `getByPlayer()` calls return references to the **same cached object**. Action classes mutate these objects directly — **never manually flip state after calling an action**.

### Cost System

| Operation | Cost |
|-----------|------|
| Teleport | Base cost (`getTeleportCost()`) |
| Home teleport | Half base cost |
| Enable protection | 5× base cost |
| Disable protection | Free |
| Unset home | 2× base cost |
| Set home | Free |

### Build & Deploy

```bash
./gradlew build --no-daemon
# Output: build/libs/WaystoneWarps-1.0.0.jar
# Deploy to Pterodactyl server plugins folder
```

---

## Files Changed

### New Files

| File | Lines | Purpose |
|------|-------|---------|
| `infrastructure/services/geyser/BedrockSupport.kt` | ~580 | Core reflection-based Bedrock form engine |
| `infrastructure/services/geyser/GeyserMenuIntegration.kt` | ~160 | GeyserMenu button registration (Warp + Home) |
| `infrastructure/services/geyser/BedrockWarpMenu.kt` | ~260 | Warp navigation, search, filter, detail menus |
| `infrastructure/services/geyser/BedrockWarpManagementMenu.kt` | ~370 | Waystone editor (privacy, players, home, protection, etc.) |
| `infrastructure/services/geyser/BedrockWarpNamingMenu.kt` | ~60 | Waystone creation custom form |
| `application/actions/management/ToggleHome.kt` | ~30 | Toggle home waystone action |
| `application/actions/management/ToggleProtection.kt` | ~30 | Toggle protection mode action |
| `application/actions/management/GetHomeWarp.kt` | ~10 | Get player's home waystone |
| `interaction/commands/HomeCommand.kt` | ~30 | `/home` command |
| `infrastructure/persistence/migrations/Migration2_AddHomeAndProtection.kt` | ~20 | DB migration |

### Modified Files

| File | Changes |
|------|---------|
| `WaystoneWarps.kt` | Koin registrations for new actions, GeyserMenuIntegration init |
| `WaystoneInteractListener.kt` | Bedrock routing, debounce, discovery effect fix |
| `WaystoneDestructionListener.kt` | Protection mode check + thorns damage |
| `WarpManagementMenu.kt` | New layout, home/protection buttons |
| `PlayerParticleServiceBukkit.kt` | Player-specific particle/sound methods |
| `HologramServiceBukkit.kt` | "Home: Player" display, dark blue name when protected |
| `TeleportationServiceBukkit.kt` | Half-price home teleport |
| `PlayerAttributeServiceSimple.kt` | Permission-based warp limits |
| `SchemaMigrator.kt` | Tolerant of newer DB versions |
| `plugin.yml` | `load: POSTWORLD`, new permissions, softdepend |
| `build.gradle.kts` | Removed compile-time geyser deps |

### Deleted Files

| File | Reason |
|------|--------|
| `WarpIconMenu.kt` | Icon feature removed |
| `UpdateWarpIcon.kt` | Icon feature removed |

---

## Configuration & Permissions

### New Config Options

```yaml
home_unset_cost_multiplier: 2      # Multiplier for unsetting home waystone
protection_mode_cost_multiplier: 5  # Multiplier for enabling protection
```

### New Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `waystonewarps.limit.<n>` | — | Max waystones a player can create (highest wins) |
| `waystonewarps.bypass.protection` | op | Bypass protection mode restrictions |
| `waystonewarps.home` | true | Use home waystone feature |

### Existing Permissions Used

| Permission | Where Used |
|------------|------------|
| `waystonewarps.bypass.access_control` | Bedrock & Java toggle handlers |
| `waystonewarps.create` | Waystone creation |
| `waystonewarps.discover` | Waystone discovery |
| `waystonewarps.teleport` | Teleportation |
| `waystonewarps.command.warpmenu` | `/ww` command |
