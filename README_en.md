# Frostor's Carpet Addition

English | [简体中文](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/README.md)

Frostor's Carpet Addition (FCA) is a Minecraft Java add-on for
[Carpet](https://github.com/gnembon/fabric-carpet). It provides a big ender
chest, Easy Place V2-Extra, Player Ladder, and a private-server online-profile
fallback.

> **AI-assisted development disclosure:** Some code, tests, and documentation
> were produced with generative AI assistance and reviewed by the maintainer.
> Feature design, release decisions, and maintenance remain the maintainer's
> responsibility.

The mod ID is `frostors-carpet-addition`, and the Java package is
`top.frostor.carpet`. Most logic runs on the server; except for client rules,
the client does not need to install FCA.

## Supported Versions

| Minecraft | Fabric Loader | Fabric API | Carpet |
| --- | --- | --- | --- |
| 26.2 | >= 0.19.3 | >= 0.152.1 | >= 26.2 < 26.3 |
| 26.3 | >= 0.19.5 | >= 0.160.5 | >= 26.3 < 26.4 |

FCA requires Java 25. Install the FCA build matching your Minecraft version.

## Installation and Dependencies

Required:

- Java 25
- Fabric Loader
- Fabric API
- The Carpet version matching your Minecraft version

Optional:

- Quick Shulker: opens the big ender chest through its keybinding, inventory
  actions, or hand-use interaction; install it on both client and server.

Place FCA and the required dependencies in the server's `mods` directory. The
client only needs FCA for client rules or optional compatibility features.

FCA rules are managed by Carpet and stored with the world configuration:

```text
/carpet list fca
/playerladder
```

`/carpet list fca` shows the main rules. Enable `/carpet playerLadder true` first,
then use `/playerladder` to view or change Player Ladder sub-settings.

## Feature Overview

### Big Ender Chest

With `bigEnderChest` enabled, ender chests use a vanilla `GENERIC_9x6` menu that
shows an aggregate view of all storage pages. Vanilla clients can view, insert,
extract, sort, page, and discard items.

- Page 0 is the player's own vanilla ender chest.
- Additional pages use standard 27-slot fake-player `EnderItems` containers and
  store their profiles in `playerdata`.
- Aggregate slots are server-managed virtual slots; authoritative transaction
  results correct the client state.
- `bigEnderChestLiveFakePlayers` can bring a page fake player online only after
  an actual storage insertion or extraction. Indexing and read-only browsing
  stay on the offline file path.
- Quick Shulker's keybinding, inventory, and hand-use paths open the big ender
  chest through its public `QuickOpenableRegistry` API.
- After a protocol v3 handshake that advertises the generic carrier, FCA opens the same vanilla `GENERIC_9x6` carrier: 54 read-only placeholder slots plus 36 player-inventory slots. The complete item list still travels through protocol snapshots and deltas. Neither the vanilla aggregate menu nor the terminal carrier registers a custom `MenuType`.

Automatic page fake players track idle time per page. Closing the menu does not
stop all page fake players; data is synchronized before idle cleanup, rule
shutdown, or server shutdown. The first batch of real fake-player logins can
produce a significant server-tick spike, so this path is disabled by default.

### Easy Place V2-Extra

`easyPlaceProtocolExtra` provides Servux-compatible server-side easy-place
semantics and receives precise block-state encoding from compatible clients over
`earlycompat:network`. The server still enforces player reach, block placement,
and final `BlockItem.canPlace` validation. FCA does not include client
schematic access or placement interaction. Wall hanging signs follow vanilla's
placement-only lateral-support rule and remain valid after that support is
removed: V2-Extra permits unsupported placement and restores the projected
facing. Ceiling hanging signs restore both `rotation` and `attached`, while
target replaceability, collision, and general placement checks remain enforced.
Candles likewise require support only during placement; V2-Extra permits
unsupported placement without changing later `updateShape` behavior. Quantity-state blocks advance by one vanilla placement level per interaction, use the projected amount as the upper bound, and consume one matching item each time.
All vanilla `WallBlock` variants restore the projected `NONE/LOW/TALL`
connections on all four sides and the `UP` post state. `WATERLOGGED` is not
encoded and remains authoritative from the server's placement state. Wall
`UP` uses auxiliary bit 26 as a schema marker and bit 27 as its value; the
server still accepts the legacy bit-zero layout. Wall
placement independently suppresses structural `updateShape` recalculation
while preserving vanilla side effects such as water-tick scheduling.
During any V2-Extra block placement, rail structural recalculation is suppressed
at the actual placement position. Its four horizontal neighbors and the
position above it are protected only when their actual state matches the
projection. Wall protection always covers the current placement position;
adjacent walls are protected only when their actual state matches the
projection. Comparisons ignore `WATERLOGGED`, while block type and relevant
state mismatches exclude that neighbor. Rail neighbor matching compares only
rail type and shape, never redstone state. Diagonal positions, blocks two or
more steps away, and ordinary non-Easy-Place placements remain unaffected.
TriWasa controls these protections independently with the unified
`easyPlaceRailProtection` and `easyPlaceWallBlockNoShapeUpdate` child switches,
both enabled by default.
Observer projections restore only `FACING`; `POWERED` is always placed inactive.
An easy-place change on an existing observer's detection face does not schedule
its pulse tick, while changes on other faces retain vanilla observer behavior.
TriWasa's `easyPlaceObserverProtection` child switch controls this behavior and
defaults to enabled; disabling it restores vanilla observer placement and
detection-face updates.
TNT is not primed by an existing redstone signal when placed through easy place,
and existing TNT is not primed by neighbor updates caused by an easy-place
placement. The `easyPlaceTntProtection` switch covers both `onPlace` and
`neighborChanged` redstone paths; manual ignition, projectiles, and explosions
retain vanilla behavior.
The request is emitted from the common `BlockItem` placement path, including
`StandingAndWallBlockItem` variants such as coral fans.
If an immediately active placed component shares a powered redstone conductor
(including ordinary solid blocks and note blocks) with TNT, that second-hop TNT
neighbor update is also suppressed during the current placement. Redstone wire,
redstone torches, detector rails, and other immediate components use the same
conductor test. This remains limited to a synchronous single relay.
Regular and sticky pistons keep their existing facing restoration; the
projected `EXTENDED` is neither encoded nor restored. When the placement target
is an unextended piston in the projection, when one of the six face neighbors
exactly matches the projection, or when a piston reachable as a second-hop QC
target through a powered conductor directly above it matches the projection,
FCA suppresses that piston's `setPlacedBy`, `onPlace`, and `neighborChanged`
activation checks for the current `BlockItem.place` lifecycle. Mismatched
pistons keep vanilla updates; ordinary updates after the placement, manual
block events, and real piston movement are unaffected. TriWasa's
`easyPlacePistonProtection` child switch controls this and defaults to
enabled; the 12-bit mask travels in Y bits 8-19. The archived cross-packet
context architecture is not restored and X/Z are untouched.
Powered rails, activator rails, and detector rails are protected by TriWasa's
unified `easyPlaceRailProtection` child switch, enabled by default. The client
stores their initial `POWERED` in raw bit 4 and uses raw bit 3 (powered,
activator, and detector rails) or bit 4 (ordinary rails) as a fallback flag;
the unified protection request and the neighbor match mask travel in Z bit 9
and bits 17-21, in the fixed `NORTH,SOUTH,WEST,EAST,UP` order. FCA has only
the `RAIL` family, which covers shape, PP, neighbor matching, and the placed
position's state; the `RAIL_STATE` family has been removed. In 26.x powered and
activator rails are both `PoweredRailBlock` instances, while detector rails use
the separate `DetectorRailBlock`. Neighbor matching compares only rail type and
shape, never redstone state, and ordinary rails have no redstone state. The
`checkPressed` tick scheduled after placing a detector rail is covered by a
short suppression window at the placement position, itself constrained by the
neighbor mask, so rails are never frozen indefinitely.

Server-side state handling uses explicit behavior-family registries that freeze
after initialization. The stable `EasyPlaceStateSync.apply(...)` entry point
selects the owning adapter; concrete restoration lives under
`easyplace/adapter` in directional, attachment, rail, wall, observer, multiface, quantity,
container, sign, redstone, and special families, while shared pure state
operations live in `EasyPlaceStateSupport`. The old monolithic
`LegacyEasyPlaceStateSync` mapping and fallback adapter have been removed. The
V2-Extra payload marker and protocol version remain 1. In the auxiliary Z
channel the low nine bits remain the crafter slot mask; bits 9 and 10 request
rail and wall update protection, bits 11-16 mark matching wall neighbors,
bits 17-21 mark matching rail neighbors in `NORTH,SOUTH,WEST,EAST,UP` order,
bit 22 requests observer protection, bit 23 requests TNT protection, bit 24 is
the `AUXILIARY_SCHEMA` marker, bit 25 is free, and bits 26-27 are the wall
auxiliary schema and `UP`.

The same handshake adds `easy_place_y_precision` with `y_precision_bits=8` and
`easy_place_piston_protection` with `piston_protection_bits=12`. A supporting
client writes the eight-bit fixed-point hit height relative to the bottom of
the block into Y bits 0-7; Y bits 8-19 carry the 12-bit piston protection mask
and bit 20 is the `Y_PISTON_SCHEMA` marker, set only when the mask is non-zero.
Y never reuses raw, and a zero mask or missing capability keeps Y bit-identical
to the plain precision encoding. X raw and Y precision are always encoded
against the packet `BlockHitResult.blockPos`; Z auxiliary uses a **fixed
origin** (world Z=0, `z = auxiliary * 2 + 2`) and is independent of the click
position, hit face, and placement target, so the main Litematica path, legacy
`WorldUtils`, and printer adapters no longer need to agree on one `BlockPos`.
Bit 24 of Z is the `AUXILIARY_SCHEMA` marker: the server accepts only Z values
carrying it, strips that bit after decoding, and treats an unencoded or
unmarked Z as "no auxiliary".
The hit location travels as a binary32 float offset relative to `blockPos`,
whose exact-integer limit is 2^24; the largest Y payload is
`2 + 2*(2^21-1) = 2^22`, leaving two bits of headroom, so neither 26.2 nor
26.3 loses precision.

FCA saves the complete packet-level snapshot when `handleUseItemOn` receives
the unmodified hit result, then runs target transforms and placement, and
associates the snapshot with the `BlockItem.place` lifecycle. Wall-mounted
placements and other target transforms use that snapshot instead of
reinterpreting X raw or Z auxiliary against the transformed position.
`BlockPlaceContext.getClickedPos()` is one block away from the packet position
along the hit face, and `at(...)` may rebuild the click location from the block
centre, so decoding from the transformed context loses the marker, bit 9, or
the neighbor mask depending on the face. The snapshot lives on the processing
thread of a single packet inside a try/finally scope; no cross-packet context
architecture is used. The Z contract requires FCA and TriWasa to be built from
matching versions; mixing versions silently drops rail, wall, observer, and
TNT protection bits.
26.2 and 26.3 share one placement semantic: they depend on different
Litematica versions (0.28.6 / 0.29.1), and 26.3 turns
`ServerboundUseItemOnPacket` into a record whose `BlockHitResult.STREAM_CODEC`
transmits the hit location as a **float offset from `blockPos`**. To hide those
differences, the wire X/Y anchor is fixed to the packet `blockPos`; positions
resolved by `BellRules.resolveProtocolTarget` (`protocolPos`) are used only to
read the projection and current world state and must never be passed to
`encodeX`/`encodeY`. The server uses Z's `AUXILIARY_SCHEMA` bit as the primary
packet marker and falls back to the hit face and X-axis neighbors when the
primary X anchor has no marker. Both versions therefore follow the same decode
path, and side-mounted bells whose `facing` lies on the X axis no longer lose
the V2-Extra marker to an anchor shift.
Multiface wire indices use the fixed order
`DOWN, UP, NORTH, SOUTH, WEST, EAST`; vine derives `UP, NORTH, SOUTH, WEST,
EAST` by filtering out `DOWN`. Clients or servers built with the historical
face order must be upgraded to a matching release, because mixed versions can
decode multiface and vine faces in the wrong direction. The old `main` snapshot
is preserved by the `legacy` tag in both repositories for historical
compatibility only and is no longer maintained. See
[`EASY_PLACE_REGISTRY_PLAN.md`](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/EASY_PLACE_REGISTRY_PLAN.md)
for the architecture boundaries and migration record.

### Player Ladder

Enable `/carpet playerLadder true` first, then use `/playerladder` to query or
change riding, picking up, player stacking, entity filtering, and depth-limit
settings. The interaction path can be the direct `player_ladder` click or the
`carry_on` gesture, which requires sneaking with both hands empty; the action is
selected by `/playerladder clickMode`. After a successful `carry_on` interaction,
that Shift press cannot dismount; releasing Shift and pressing it again performs
the next dismount. Carpet fake players are supported.

When upgrading from an older version, Player Ladder sub-settings such as
`playerLadderClickMode`, depth limits, and entity filters are not imported from
the existing `carpet.conf` into the new `playerladder.conf`. Reconfigure them
with `/playerladder` after upgrading; unconfigured options use their defaults.

### Client Compatibility and Public Protocols

FCA does not include a custom big ender chest terminal GUI and does not register a custom terminal `MenuType`. Clients such as TriWasa can use the protocol v3 `BigEnderChestProtocol.Payload` and `EasyPlaceExtraPayload` types, message DTOs, and codecs defined by FCA. The terminal requires a TriWasa build matching FCA `0.2.1-beta.1`; vanilla clients do not need FCA or TriWasa. When FCA is loaded in the same process, clients reuse those types instead of registering a different payload class for the same channel.

### Online Profile Fallback

`onlineProfileFallback` is intended only for controlled private servers. It does
not verify account ownership and cannot replace Mojang session authentication.
Do not enable it on public or untrusted servers.

## Carpet Rules

See the complete [rule reference](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/RULES.md) for every rule name, default value,
side, accepted values, purpose, and command. Rule names are case-sensitive:

```text
/carpet <rule>
/carpet <rule> <value>
/carpet <rule> reset
```

## Compatibility and Data

- FCA Carpet rules are stored in the world's `carpet.conf`; Player Ladder
  sub-settings are managed by a separate Carpet settings manager and stored in
  the current world's `playerladder.conf`.
- Old Player Ladder sub-rules are not imported automatically from `carpet.conf`.
  Reapply customized values through `/playerladder` as described in `RULES.md`.
- Big ender chest page metadata is stored in the world directory, not in client
  configuration.
- Carpet is a compile-time dependency only and is never bundled into FCA.
- Quick Shulker is used only for an optional public API integration; it is not
  bundled into FCA and is not a hard dependency.
- The client-tool protocol does not include a terminal GUI; compatible clients
  are separate projects.
- Big ender chest and Easy Place capability handshakes are driven by Carpet
  `RuleObserver` events; live rule changes publish the new capability state to
  handshaken online clients.

## Building

Use the project Wrapper with a Java 25 JVM. `JAVA_HOME` may point to any JDK 25
installation.

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew :26.2:build :26.3:build
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = '<JDK 25 installation>'
.\gradlew.bat :26.2:build :26.3:build
```

Prepare the matching Carpet and Quick Shulker compile dependencies as described in
[`libs/README.md`](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/libs/README.md). Shared source, resource, or Mixin changes
must be built for both 26.2 and 26.3.
The build runs `logicTest` and `easyPlaceProtocolTest`. The latter keeps
`EasyPlaceExtraProtocolTest` as its stable entry point and aggregates the
protocol and state suites split by behavior family.

Every PR runs change classification and `CI Gate`. The full two-version build
runs only when source, resources, version properties, Gradle, Wrapper, or
workflow files change.

## License and Third-Party Sources

FCA uses the [MIT License](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/LICENSE).

Player Ladder is adapted from ForwarD-NerN's PlayerLadder (MIT). The Easy Place
V2-Extra server implementation is adapted from chenjunfu2's EarlyCompat
(CC0-1.0). See [THIRD_PARTY_NOTICES.md](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/THIRD_PARTY_NOTICES.md) for additional
notices.
