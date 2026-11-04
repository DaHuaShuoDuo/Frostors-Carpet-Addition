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

Place FCA and the required dependencies in the server's `mods` directory. The
client only needs FCA for client rules or optional compatibility features.

FCA rules are managed by Carpet and stored with the world configuration:

```text
/carpet list fca
```

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
- Quick Shulker's three-row ender chest menu is converted to the big ender chest
  menu server-side.

Automatic page fake players track idle time per page. Closing the menu does not
stop all page fake players; data is synchronized before idle cleanup, rule
shutdown, or server shutdown. The first batch of real fake-player logins can
produce a significant server-tick spike, so this path is disabled by default.

### Easy Place V2-Extra

`easyPlaceProtocolExtra` provides Servux-compatible server-side easy-place
semantics and receives precise block-state encoding from compatible clients over
`earlycompat:network`. The server still enforces player reach, block placement,
and final `BlockItem.canPlace` validation. FCA does not include client
schematic access or placement interaction.

### Player Ladder

`playerLadder` provides riding, picking up, player stacking, entity filtering,
and depth limits. Carpet fake players are supported, with an optional client
aiming fix.

### Client Compatibility and Public Protocols

FCA does not include a custom big ender chest terminal GUI. Clients such as
TriWasa can use the public `BigEnderChestProtocol.Payload` and
`EasyPlaceExtraPayload` types, message DTOs, and codecs defined by FCA. When FCA
is loaded in the same process, clients reuse those types instead of registering
a different payload class for the same channel.

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

- FCA rules are stored in the world's `carpet.conf`; there is no separate
  configuration file.
- Big ender chest page metadata is stored in the world directory, not in client
  configuration.
- Carpet is a compile-time dependency only and is never bundled into FCA.
- The client-tool protocol does not include a terminal GUI; compatible clients
  are separate projects.

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

Prepare the matching Carpet compile dependency as described in
[`libs/README.md`](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/libs/README.md). Shared source, resource, or Mixin changes
must be built for both 26.2 and 26.3.

Every PR runs change classification and `CI Gate`. The full two-version build
runs only when source, resources, version properties, Gradle, Wrapper, or
workflow files change.

## License and Third-Party Sources

FCA uses the [MIT License](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/LICENSE).

Player Ladder is adapted from ForwarD-NerN's PlayerLadder (MIT). The Easy Place
V2-Extra server implementation is adapted from chenjunfu2's EarlyCompat
(CC0-1.0). See [THIRD_PARTY_NOTICES.md](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/THIRD_PARTY_NOTICES.md) for additional
notices.
