# Frostor's Carpet Addition

English | [简体中文](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/README.md)

Frostor's Carpet Addition (FCA) is a Minecraft Java add-on for
[Carpet](https://github.com/gnembon/fabric-carpet). It provides a server-side big
ender chest, Player Ladder, Easy Place V2-Extra protocol support, and an
online-profile fallback.

> **AI-assisted development disclosure:** Some code, tests, and documentation in
> this project were produced with generative AI assistance and reviewed by the
> project maintainer. Feature design, release decisions, final review, and
> maintenance remain the responsibility of the maintainer.

The mod ID is `frostors-carpet-addition`, and the Java package is
`top.frostor.carpet`. Most FCA logic runs on the server. Except for options
explicitly marked as client rules, FCA features do not require the client to
install FCA or any other specific client mod.

## Supported Versions

| Minecraft | Fabric Loader | Fabric API | Carpet |
| --- | --- | --- | --- |
| 26.2 | >= 0.19.3 | >= 0.152.1 | >= 26.2 < 26.3 |
| 26.3 | >= 0.19.5 | >= 0.160.5 | >= 26.3 < 26.4 |

FCA requires Java 25. You must install the FCA build matching your Minecraft
version.

## Installation

Place FCA in the server's `mods` directory together with matching versions of
Fabric Loader, Fabric API, and Carpet. The client only needs FCA when using a
client rule or compatibility feature.

FCA rules are managed by Carpet and stored with the world configuration. Use:

```text
/carpet list fca
```

Rule names are case-sensitive. Use `/carpet <rule>` to inspect a rule or
`/carpet <rule> <value>` to change it.

## Rules

See the complete [rule reference](RULES.md). The following table is a quick
index only.

| Rule | Default | Description |
| --- | --- | --- |
| `bigEnderChest` | `false` | Enables the server-side big ender chest |
| `bigEnderChestPageLimit` | `64` | Per-player extra page limit; `1..1024`, with `-1` meaning no configured limit |
| `bigEnderChestLiveFakePlayers` | `false` | Whether pages used by real storage transfers may bring their fake players online |
| `easyPlaceProtocolExtra` | `false` | Enables the server-side Easy Place V2-Extra protocol |
| `onlineProfileFallback` | `false` | Enables username resolution and profile fallback in online mode |
| `playerLadder` | `false` | Enables Player Ladder |
| `playerLadderClickMode` | `ride` | Right-click behavior: `ride`, `pick_up`, or `none` |
| `playerLadderPickupDepthLimit` | `16` | Maximum stack depth searched when picking up an entity |
| `playerLadderRideDepthLimit` | `16` | Maximum stack depth searched when riding an entity |
| `playerLadderAllowNonPlayers` | `false` | Whether non-player entities can be ridden or picked up |
| `playerLadderIgnoredEntities` | See below | Comma-separated ignored entity IDs or `#` tags |
| `playerLadderDismountOnGameModeChange` | `false` | Whether passengers dismount when the carrier changes game mode |
| `playerLadderAllowRiderStacking` | `true` | Whether stacks of more than two players are allowed |
| `playerLadderAllowFakePlayers` | `false` | Whether Carpet fake players can be ridden or picked up |
| `playerLadderClientAimingFix` | `true` | Client-side aiming fix |

Default value of `playerLadderIgnoredEntities`:

```text
minecraft:wither,minecraft:ender_dragon,minecraft:warden,minecraft:minecart,#minecraft:boat,#minecraft:dismounts_underwater
```

## Big Ender Chest

When `bigEnderChest` is enabled, using an ender chest opens a vanilla
`GENERIC_9x6` menu regardless of the item held. Holding an item does not prevent
the menu from opening, and the item is neither consumed nor written to storage
by that interaction. Clients without an optional client tool can still use the
full aggregate view, insert and extract items, sort, switch pages, and discard
items.

### Vanilla 6×9 Menu

- The top `5×9` slots show aggregated item templates from all storage pages.
- The bottom nine slots provide first page, previous page, page number, next
  page, last page, sort field, sort direction, and refresh controls.
- Items retain their vanilla names, components, and icons; the full stored
  amount appears in the lore.
- Left-click extracts one stack. Left-click with a held cursor item inserts it.
- Shift + left-click moves a stack into the player's inventory.
- Right-click extracts half a stack, rounded up according to the visible stack
  capacity.
- Q discards one item; Ctrl + Q discards a stack.
- Shift + left-click can still insert into aggregate storage when the player
  inventory is full.
- The view page only changes paging and sorting of the aggregate list; it does
  not create a new physical storage page.

Aggregate slots are server-managed virtual slots. Control slots cannot be
swapped, dropped, duplicated, or extracted as ordinary items. The server limits
rapid clicks and batch virtual actions submitted by inventory-management mods,
then corrects client state using authoritative transaction results.

### Storage Pages

Page 0 directly maps to the player's vanilla 27-slot ender chest without copying
its contents. Additional pages are backed by fake-player profiles that do not
participate in game ticks. Every page is a standard 27-slot `EnderItems`
container.

Page identities, fake-player UUIDs, names, and sort preferences are stored in
FCA metadata in the world `data` directory. Page items are stored in vanilla
server `playerdata`. Metadata and page profiles use background I/O, temporary
files, and atomic replacement; shutdown waits for pending writes.

Pages are created according to actual capacity needs. The first open prepares a
small number of buffer pages; viewing, paging, and sorting do not trigger
expansion. Lowering `bigEnderChestPageLimit` never deletes existing pages.
Existing pages remain accessible but no further pages are created.

If a page's fake player is already online through Carpet
`/player <name> spawn`, FCA uses that online player's ender chest container and
synchronizes external slot changes into the aggregate index. It does not access
that UUID's disk profile while the player is online. After logout, the page
automatically returns to the `playerdata` path.

`bigEnderChestLiveFakePlayers` is disabled by default. When enabled, index
creation, search, sorting, and read-only browsing still do not bring fake
players online. Only an actual storage insertion or extraction requests the
corresponding page fake player. The player stays online until the page has been
unused for 200 consecutive ticks. Closing the menu only ends the current
session; it does not immediately stop page fake players. Page fake players are
stopped only after their idle timeout, when the feature rule is disabled, or
when the server stops. Manually spawned fake players are unaffected. Automatic
page fake players use FCA's stored local UUID and name without querying Mojang
profiles; they use spectator mode and forced flight to avoid network waits or
falling under gravity.

Disabling `bigEnderChest` only hides the feature; it does not delete pages or
items. Re-enabling it makes existing pages accessible again.

Additional page names use this fixed 16-character readable format:

```text
fec_p<base-36 page number>_<player-name prefix><owner digest>
```

Names are written to the server username cache when created and remain stable
across restarts.

### Sessions and Source Validation

When opened from an ender chest block, FCA manages the vanilla lid animation,
open count, and sounds. Closing the menu, disabling the rule, disconnecting, or
invalidating the session ends the animation correctly.

Sessions opened from an ender chest block validate their source:

- The terminal session becomes invalid after the player moves more than eight
  blocks from the source or changes dimension.
- Protocol terminal actions require the player to still have the current
  session menu open.
- External sessions without a source block, such as Quick Shulker, retain their
  own opening conditions.

### Quick Shulker

When Quick Shulker uses the vanilla three-row ender chest menu to open a
player's ender chest, FCA replaces it server-side with the `GENERIC_9x6` big
ender chest. When the rule is disabled, Quick Shulker keeps its original
27-slot behavior.

This compatibility path is registered only on the server. It does not modify
Quick Shulker's client UI and does not require FCA on the client.

### Client Tool Protocol

FCA does not include a custom terminal GUI. Compatible client tools can provide
a separate terminal outside the vanilla 6×9 menu.

Protocol channel:

```text
frostors-carpet-addition:big_ender_chest
```

FCA exposes this channel's payload, message DTOs, and codec through
`top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.Payload`. TriWasa
references that API at compile time and directly reuses the same payload class
when FCA is loaded in the same process; clients without FCA use their own
wire-compatible implementation.

The current protocol version is `2`. Clients that have not completed the
handshake continue to use the vanilla 6×9 menu. Clients that complete the
handshake and declare terminal capability use:

```text
frostors-carpet-addition:big_ender_chest_terminal
```

Clients may send `HELLO` again during a session to add or remove terminal
capability. The server closes the current session and reopens the same source
with the new capability set, falling back to the vanilla 6×9 menu when terminal
capability is removed and restoring the terminal when it is declared again.

The terminal menu synchronizes only the player inventory and carried item, not
the 45 display slots of the vanilla menu. The protocol includes:

- Capability intersection and server session information.
- Page catalog, page states, capacity, and read-only state.
- Item-template snapshots generated from the complete aggregate index.
- CRC32 chunks, `SnapshotEnd`, ACK, missing-chunk retransmission, and explicit
  resynchronization.
- Template additions, removals, total changes, and page-revision deltas.
- Authoritative server-side extraction and insertion by `templateSerial` and
  amount.
- Batch insertion, moving stacks back to the player inventory, dropping,
  action sequence numbers, request idempotency, and per-tick rate limits.
- Menu-close, disconnect, rule-disable, and session-invalid notifications.

`ActionRequest.playerSlots` and `QUICK_MOVE_FROM_PLAYER.button` use player
`Inventory` indices `0..35`, not menu slot indices. Protocol actions never
submit trusted item stacks directly; the server revalidates the menu, session,
revision, page state, templates, and player inventory.

## Easy Place Protocol V2-Extra

`easyPlaceProtocolExtra` provides Servux-compatible server-side easy-place
semantics and receives precise client state through the EarlyCompat network
channel. Client encoding, Litematica schematic access, and input handling are
not included in this mod.

FCA exposes `top.frostor.carpet.easyplace.EasyPlaceExtraPayload`, which defines
the `earlycompat:network` channel, message DTOs, and codec. TriWasa references
that public API at compile time and directly reuses the same payload class when
FCA is loaded in the same process. A client without FCA uses TriWasa's
wire-compatible payload only when connected to a dedicated server.

The server restores or synchronizes vanilla states including direction,
orientation, open state, stack count, bed head, cake bites, copper-golem pose,
copper-bulb powered state, door half, double-chest pairing, and multiface
attachment faces.

After the rule is enabled and a client completes the handshake, FCA relaxes the
vanilla center-distance check for V2-Extra encoded hit positions while still
enforcing player reach and final `BlockItem.canPlace` validation. This relaxation
does not depend on Carpet's `antiCheatDisabled`.

State semantics come from FCA's own implementation and do not change based on
whether Servux is installed.

## Player Ladder

With `playerLadder` enabled, players can ride or pick up entities according to
the configured rules. The feature supports Carpet fake players, player stacking,
ignored entities, depth limits, and game-mode changes.

- `ride`: right-click makes the target the vehicle.
- `pick_up`: right-click lifts the target above the player.
- `none`: disables right-click interaction while retaining state cleanup.

`playerLadderAllowNonPlayers`, `playerLadderAllowFakePlayers`,
`playerLadderAllowRiderStacking`, and the two depth limits independently control
allowed targets and traversal range.

`playerLadderClientAimingFix` is a client rule that only affects crosshair
selection for stacked players. Server logic and ride structures do not depend
on it.

## Online Profile Fallback

`onlineProfileFallback` is intended only for controlled private servers. When a
player's requested username resolves to an online-mode UUID that already has
player data on this server, online-mode login may continue and resolve that
username's historical skin and cape data.

This feature does not verify account ownership and cannot replace Mojang
session authentication. Do not enable it on public or untrusted servers.

## Building

Use the project's Gradle Wrapper and ensure the JVM used by Gradle is Java 25.
Do not substitute Java 17 or Java 21. `JAVA_HOME` may point to any JDK 25
installation; no specific vendor is required.

POSIX shell:

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew :26.2:build :26.3:build
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = '<JDK 25 installation>'
.\gradlew.bat :26.2:build :26.3:build
```

If Gradle cannot create its cache in the default location, set
`GRADLE_USER_HOME` to a writable directory. This is environment configuration
and does not require changing the project Wrapper.

FCA does not commit development dependency JARs. Before building, place the
matching Carpet compile dependency in `libs/` using the names in
[`libs/README.md`](libs/README.md). Dependency files and build outputs are not
packaged or published through the mod artifact.

Shared source, resource, or Mixin changes require building both 26.2 and 26.3.
The build runs non-game logic and protocol tests and verifies that both versions
can be packaged.

CI uses GitHub Actions with Java 25, Gradle caching, and a 26.2/26.3 matrix, and
downloads each Carpet compile dependency from official Carpet releases. Each
version checks its production JAR metadata and confirms Carpet classes were not
bundled before uploading artifacts.

Every PR runs change classification and the stable `CI Gate`. The 26.2/26.3
matrix runs only when source, resources, version properties, Gradle, Wrapper, or
workflow files change; README, documentation, and license-only changes pass
through classification and the gate without a full Minecraft build.

Branch protection requires only `CI Gate`; whether the build matrix runs is
decided by change classification, so documentation PRs are not blocked by
missing matrix checks.

The repository owner may add the `auto-merge` label to an owner-authored,
same-repository, non-draft PR. It squash-merges after both CI versions pass.
Fork PRs, draft PRs, and PRs changing workflows or Gradle/Wrapper configuration
still require manual review. Removing the label disables Auto-merge.

## Data and Compatibility

- Rules are stored in the world's `carpet.conf`; there is no separate
  `config/playerladder.json`.
- Big ender chest page metadata is stored in the world directory, not in client
  configuration.
- Carpet is referenced only as a compile-time dependency and is never bundled
  into FCA.
- The Quick Shulker compatibility path does not require packaging its compile
  dependency or binary into FCA.
- The client-tool protocol does not include a terminal GUI; compatible clients
  are implemented by separate projects.

## Project Structure

```text
src/main/java/top/frostor/carpet
├── bigenderchest/      Big ender chest storage, pages, menus, and tool protocol
├── easyplace/          Server-side Easy Place V2-Extra implementation
├── onlineprofile/      Online-profile fallback
├── playerladder/       Player Ladder
├── FrostorSettings.java
└── FrostorsCarpetAddition.java

src/main/resources      Metadata, Mixin configuration, and translations
src/test/java           Logic and protocol tests without a running game
versions/26.2           Version properties for 26.2
versions/26.3           Version properties for 26.3
```

FCA registers itself through `CarpetServer.manageExtension(this)` in
`FrostorsCarpetAddition.onInitialize()` and registers the rules in
`FrostorSettings` after game startup.

## License and Third-Party Sources

FCA itself uses the [MIT License](LICENSE).

Player Ladder is adapted from ForwarD-NerN's PlayerLadder (MIT). The server-side
Easy Place V2-Extra protocol implementation is adapted from chenjunfu2's
EarlyCompat (CC0-1.0). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for
additional behavior references and source notices.
