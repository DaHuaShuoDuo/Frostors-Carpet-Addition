# Easy Place Registry Refactor Plan

Status: Phase 1-6 complete on `codex/easy-place-registry`, including the family
test split; dual-version runtime manual verification completed

Last updated: 2026-11-09

Canonical repositories:

- FCA server: `D:\mcgaoshou\Frostors-Carpet-Addition`
- TriWasa client: `D:\mcgaoshou\TriWasa`

Current branch for both repositories: `codex/easy-place-registry`.

Completed checkpoints:

- Registry migration: FCA `c543cf1`, TriWasa `2ac269a`
- Family test split: FCA `bc4accc`, TriWasa `f3dc45f`

This file is the canonical cross-repository plan. TriWasa contains a short
locator file that points here. If the plan changes, update this file first and
then update the TriWasa locator only if its scope or links changed.

## Purpose

Replace the monolithic Easy Place state mapping in:

- `src/main/java/top/frostor/carpet/easyplace/EasyPlaceStateSync.java`
- `src/main/java/top/frostor/triwasa/easyplace/EasyPlaceStateCodec.java`

with categorized adapters and explicit registries.

The refactor must preserve protocol and placement behavior while making new
block families easier to add, review, and test. It is not a license to redesign
printer internals, change wire values, or broaden placement permissions.

## Non-Negotiable Boundaries

1. FCA remains authoritative for server-side placement state and final
   `BlockItem.canPlace` validation.
2. FCA must not depend on Litematica, MaLiLib, TriWasa, or printer classes.
3. TriWasa must not duplicate server authority. Its adapters only encode a
   requested protocol state and apply that state to the client placement
   preview.
4. Keep `EasyPlaceStateSync.apply(...)` and
   `EasyPlaceStateCodec.encode(...)`/`apply(...)` as stable facade entry points
   during the migration. Mixins and call sites should not be forced to change
   in the first phase.
5. Do not bump the V2-Extra protocol version or redefine existing wire fields
   for this refactor. Existing raw X-axis values and auxiliary Z-axis values
   must remain wire-compatible.
6. Do not put world mutation, packet sending, Mixin logic, or printer queueing
   into state adapters.
7. Do not add one adapter per block. Group blocks by behavior family and add an
   exact-block adapter only for a real exception.
8. Registry registration is explicit and centralized. Do not use reflection,
   ServiceLoader, annotation scanning, or mixin-time discovery.
9. Registry entries are frozen after initialization and lookup results may be
   cached. Do not support arbitrary runtime registration unless a concrete
   requirement appears.
10. Carpet Extra `BlockPlacer` is not the general printer solution. Hana,
    BiliXWhite, and zhaixianyu use Litematica protocol entry points.
11. Do not add printer-specific class references unless the generic Litematica
    injection point cannot express the required behavior. Any unavoidable
    printer adapter must be a thin target/click transform, not a second state
    mapping table.

## Target Architecture

Use two independent registries:

- FCA: server-side `EasyPlaceStateRestorerRegistry`
- TriWasa: client-side `EasyPlaceCodecRegistry`

They share protocol constants and wire helpers only. Do not create a shared
Minecraft-placement context type that pulls client dependencies into FCA.

Recommended FCA layout:

```text
easyplace/
  EasyPlaceStateSync.java          # compatibility facade, delegates to registry
  registry/
    EasyPlaceStateRestorer.java
    EasyPlaceMatcher.java
    EasyPlaceRestoreContext.java
    EasyPlaceStateRestorerRegistry.java
  adapter/
    directional/
    attachment/
    rail/
    multiface/
    quantity/
    container/
    sign/
    redstone/
    special/
```

Recommended TriWasa layout:

```text
easyplace/
  EasyPlaceStateCodec.java         # compatibility facade, delegates to registry
  registry/
    EasyPlaceStateEncoder.java
    EasyPlaceStateDecoder.java
    EasyPlaceCodecContext.java
    EasyPlaceCodecRegistry.java
  adapter/
    directional/
    attachment/
    rail/
    multiface/
    quantity/
    container/
    sign/
    redstone/
    special/
```

Exact package names may change during the infrastructure phase, but the
server/client separation and behavior-family grouping are fixed decisions.

## Adapter Resolution

The placement view must include more than `Block`:

- block
- item stack
- placement state
- current world state when available
- placement context
- raw X-axis protocol value
- auxiliary Z-axis value, or an explicit `-1` when absent

Resolution order:

1. Exact-block or exact-item exception.
2. Behavior family matcher.
3. Generic fallback.
4. Legacy fallback only while the migration is in progress.

Matchers must be deterministic. Before freezing the registry, validate that no
two entries claim the same priority for the same expected family. Do not rely
on classpath order.

During migration, keep a `LegacyEasyPlaceAdapter` that delegates to the
remaining monolithic implementation. Migrated families register ahead of it.
This allows one family to move at a time without changing unmigrated behavior.
Remove the legacy adapter only after every family has an owner.

## Adapter Contract

Server adapter:

- receives the placement view
- returns an adjusted `BlockState`, or `null` when the requested state is
  invalid
- may read current state through the supplied context
- must not call `Level#setBlock`, send packets, mutate inventories, or suppress
  updates directly

Client adapter:

- returns a raw protocol value and optional auxiliary value for the wire
- returns `-1`/absent when the block is not encodable or the current state
  already satisfies the projection
- applies projected values to client-side placement state only
- must not decide server placement success

Adapters must be pure with respect to the world. If behavior requires a
lifecycle action, define a separate policy hook.

## Behavior Families

Initial target families:

| Family | Examples | Main responsibility |
| --- | --- | --- |
| Generic directional | slabs, axis blocks, repeaters, comparators | first direction, axis, slab half, delay/mode |
| FaceAttached | buttons, levers, wall torches | face and horizontal facing |
| Door | wooden and iron doors | facing, hinge, half, open |
| Trapdoor | wooden and iron trapdoors | facing, half, open |
| Fence gate | all fence gates | facing and open |
| Stair | all stairs | facing, half, shape |
| Sign | standing signs, wall signs, banners, skulls | rotation/facing |
| Hanging sign | ceiling and wall forms | rotation, attached, wall facing |
| Multiface | glow lichen, resin, sculk veins, vine | one missing face at a time, merge current state |
| Quantity | candle, flower bed, leaf litter, sea pickle, snow, turtle egg | incremental amount and projected upper bound |
| Rail | normal, powered, detector, activator | shape and placement-wide update suppression |
| Mushroom | huge mushroom blocks | six face booleans |
| Container | chests and paired halves | facing and pairing reconciliation |
| Redstone special | wire, torches, lever-like special cases | only states not covered by a family |
| Administrative | command, jigsaw, structure, light, note | exact enum/int mapping |

Do not make `Generic directional` absorb a more specific family. Specific
families always register ahead of it.

## Cross-Cutting Policies

Keep these outside state registries:

- `EasyPlaceItemPayloadPolicy`: crafter disabled slots and future item data
- `EasyPlacePlacementEligibilityPolicy`: candles, unsupported wall hanging
  signs, and other placement-stage gates
- `EasyPlaceUpdateSuppressionPolicy`: rails and any future proven block family
- `EasyPlaceTargetTransformPolicy`: hanging-sign click selection and minimal
  printer-specific target transforms

These policies may be registered separately using the same explicit/frozen
rules, but they must not become hidden branches inside every state adapter.

## Migration Phases

### Phase 0: Freeze Behavior

- Record the current supported families and protocol tests.
- Add adapter IDs to planned documentation, not runtime code yet.
- Capture known regressions as named test cases:
  hanging signs, candles, chests, huge mushrooms, flower bed/leaf litter
  facing, vine/multiface, rail shape/update suppression, crafter slots.
- Confirm both repositories build independently before the first migration.

Exit gate: baseline tests pass and no unrelated work is mixed into the refactor.

### Phase 1: Registry Skeleton

- Add the registry interfaces, deterministic matcher, lookup cache, and facade
  delegation in both repositories.
- Register only the legacy fallback.
- Add registry tests for priority, duplicate detection, freezing, and cache
  invalidation failure behavior.

Exit gate: behavior is byte-for-byte compatible for existing protocol tests.

### Phase 2: Simple Families

- Move generic directional blocks.
- Move doors, trapdoors, fence gates, stairs, signs, and administrative blocks.
- Keep old tests passing and split their cases into family tests.

Exit gate: server and client tests pass for both versions after each family.

### Phase 3: Attachment And Multiface

- Move FaceAttached.
- Move hanging signs and their wall/ceiling distinction.
- Move multiface blocks and vine.
- Preserve current-state merging and one-face-per-interaction behavior.

Exit gate: all direction combinations and existing special-hook regressions
pass.

### Phase 4: Stateful Families

- Move quantity blocks.
- Move rail blocks.
- Move huge mushrooms.
- Move chest pairing.

Exit gate: incremental placement, rail suppression, chest normalization, and
the full mushroom mask matrix pass.

### Phase 5: Client Mirror

- Mirror the finalized server families in TriWasa.
- Keep wire tests against the FCA public protocol helper.
- Test Hana, BiliXWhite, and zhaixianyu only through the common Litematica
  protocol entry points; add printer-specific hooks only if a concrete path
  bypasses those entry points.

Exit gate: FCA and TriWasa encode/decode the same raw and auxiliary values.

### Phase 6: Remove Legacy Path

- Delete the monolithic `instanceof` chain from the two facade classes.
- Keep facade methods as thin registry delegation.
- Split remaining giant tests by family while retaining one integrated matrix.
- Update README/RULES only if behavior or user-visible compatibility changed.

Exit gate: no references to `LegacyEasyPlaceAdapter`, duplicate mappings,
unreachable instance branches, or dead helpers remain.

## Test Plan

Every family migration must add or move focused tests:

- valid encode/decode round trip
- invalid values rejected
- current-state-dependent behavior
- item/block dual identity where applicable
- auxiliary channel behavior where applicable
- exact expected raw values for representative states

Cross-cutting regression tests:

- wall hanging signs with no lateral support
- attached ceiling hanging signs
- candle placement-stage support bypass
- crafter disabled slots
- chest pair normalization
- flower bed and leaf litter facing retention
- huge mushroom six-face mask
- vine and multiface one-face-per-request behavior
- rail shape preservation and neighbor update suppression
- quantity increment and per-interaction item consumption

Registry tests:

- exact match beats family match
- family match beats generic fallback
- no duplicate claim for the same priority
- registry freezes after initialization
- lookup cache returns only frozen decisions

Build and test gate after source, resource, or Mixin changes:

```text
FCA:      .\gradlew.bat :26.2:build :26.3:build
TriWasa:  .\gradlew.bat :26.2:build :26.3:build
```

Run the no-window Mixin preflight for both versions whenever a Mixin or target
signature changes. Do not write into the live `mods` directory.

## Definition Of Done

The refactor is complete only when:

- both facade classes are thin wrappers
- no monolithic state chain remains
- all behavior families have exactly one registered owner
- FCA and TriWasa agree on wire values
- FCA retains server authority and final `canPlace`
- both Minecraft versions build and protocol/logic tests pass
- no printer-specific state table was introduced
- the migration and behavioral decisions are reflected in this file

## Anti-Drift Checklist

Before touching code, a future agent must:

1. Read this file, `AGENTS.md`, and the current repository status.
2. Identify the requested phase and confirm that its exit gate is the only
   scope of the change.
3. Preserve unrelated dirty-worktree changes.
4. Avoid moving multiple complex families in one commit unless the plan is
   explicitly revised first.
5. Update this plan if a fixed decision changes; do not silently diverge.
6. Prefer a smaller adapter plus a focused test over a broad generic helper.
7. Stop and report a blocker if a proposed change requires a protocol version
   bump, server trust relaxation, or printer-specific coupling.

## Decision Log

- Two registries, separate server and client: accepted.
- Explicit centralized registration, no reflection/discovery: accepted.
- Behavior families instead of one adapter per block: accepted.
- Keep facade methods during migration: accepted.
- Separate item payload, eligibility, update suppression, and target
  transforms from state adapters: accepted.
- Carpet Extra `BlockPlacer` as the universal printer integration: rejected;
  the three audited printer forks use Litematica protocol entry points.
- First implementation milestone: registry skeleton plus simple directional
  families only. Do not start with multiface, quantity, rails, or printers.
- Canonical multiface protocol order: explicit
  `DOWN, UP, NORTH, SOUTH, WEST, EAST`; vine order derives from it by filtering
  `DOWN`. The historical vine order is not restored; mixed old/new face-order
  builds are a documented compatibility boundary unless versioned dual decode
  is added later.

## Implementation Log

### 2026-11-09 - Phase 1-2

- FCA `EasyPlaceStateSync` and TriWasa `EasyPlaceStateCodec` are now stable
  facades that delegate regular state work to frozen explicit registries.
- The previous monolithic implementations remain as
  `LegacyEasyPlaceStateSync` and `LegacyEasyPlaceStateCodec`. They own every
  family not yet migrated and preserve existing behavior during the migration.
- Server adapter families moved in this milestone: generic directional, door,
  trapdoor, fence gate, stair, standing sign/banner/skull, and administrative
  blocks.
- Client encoder/decoder families moved in this milestone: the same groups,
  with raw wire values unchanged.
- Registry entries are registered centrally, sorted by a unique priority, and
  frozen before lookup. Duplicate ids, duplicate priorities, registration
  after freeze, and lookup before freeze are rejected.
- Multiface, quantity, rail, mushroom, chest, hanging sign, redstone special,
  and other legacy families are intentionally unchanged and still delegate to
  the legacy implementations.
- Both repositories build for 26.2 and 26.3, protocol/logic tests pass, and the
  no-window Mixin preflight passes for both versions.

### 2026-11-09 - Phase 3

- Added explicit FaceAttached adapters. Buttons, levers, and other
  face-attached directional blocks now own their face/facing restoration; lever
  powered state remains in the same family adapter.
- Added explicit hanging-sign adapters. Ceiling hanging signs own rotation and
  attachment restoration, while wall hanging signs continue through the
  standing/wall facade and sign adapter without gaining ceiling-only
  properties.
- Added explicit multiface adapters on both sides. The client selects one
  missing attachable face from current world state; the server merges a single
  requested face into the target state. Wire values and one-face-per-interaction
  behavior are unchanged.
- FaceAttached tests cover all three attach faces, four horizontal facings, and
  the lever powered bit. Existing multiface direction matrices, vine/vine
  attachment behavior, hanging-sign support rules, and current-state merging
  tests continue to pass.
- The newly migrated families and the client facade now use the registry
  before the legacy fallback. Both repositories build and test for 26.2 and
  26.3.

### 2026-11-09 - Phase 4

- Added quantity adapters for candles, flower beds, leaf litter, sea pickles,
  snow layers, and turtle eggs. Client wire encoding and server incremental
  upper-bound validation are unchanged.
- Corrected the shared server quantity parser so `SegmentableBlock` amounts use
  the low two wire bits; facing remains in bits 2-3. This restores increment
  checks for flower beds and leaf litter and prevents failed retry loops in
  both easy place and printer placement.
- Added rail adapters for normal and powered rails. Client no-update bits and
  server shape restoration are unchanged; placement-wide rail update
  suppression remains outside the state adapters and in the existing policy
  hooks.
- Added huge-mushroom adapters for the full six-face mask.
- Added chest adapters. Client encoding retains the explicit type marker and
  protocol indices; server decoding still delegates pairing and reconciliation
  to `EasyPlaceChestSync`, including its controlled block updates.
- Rails now have focused encode/decode and server restoration tests. Existing
  quantity, mushroom, chest type, hanging-sign, and multiface tests continue to
  pass. Chest pair normalization, rails' neighbor-update suppression, and
  quantity placement still require their existing runtime GameTest/manual
  coverage.
- Both repositories build and test for 26.2 and 26.3.

### 2026-11-09 - Phase 5-6

- Migrated the remaining state families: bell, campfire, cake, copper bulb,
  copper golem statue, crafter, daylight detector, detector rail, lantern,
  redstone torches/wire, server-side beds, and a generic fallback owner.
- Extracted shared pure state helpers into `EasyPlaceStateSupport` on both
  sides. Facades now delegate regular state work to frozen registries and no
  longer depend on a monolithic state chain.
- Removed `LegacyEasyPlaceStateSync`, `LegacyEasyPlaceStateCodec`, both legacy
  adapters, and the now-redundant generic directional adapter. No repository
  reference to a legacy Easy Place state mapper remains.
- Client and server adapters preserve the existing wire layout and authority
  boundaries. Rail update suppression, item payload handling, chest placement
  reconciliation, placement eligibility, and hanging-sign target transforms
  remain in their existing policy/Mixin layers.
- Explicit tests cover the remaining state families without comparing against
  deleted legacy code. Both repositories build and test for 26.2 and 26.3.

### 2026-11-09 - Phase 6 Test Split And Final Verification

- Kept `EasyPlaceExtraProtocolTest` in FCA and `EasyPlaceProtocolTest` in
  TriWasa as the stable Gradle/build entry points. Both now bootstrap once,
  run the registry suite, aggregate the family suites, and report one total.
- Split FCA coverage into explicit wire, multiface, hanging-sign, quantity,
  mushroom, rail, special, directional, and attachment suites.
- Split TriWasa coverage into explicit wire, basic-state, container, multiface,
  quantity, hanging-sign, mushroom, attachment, rail, special, and
  printer/config suites.
- The split changed test organization only. No production source, resource,
  Mixin, JAR output, or protocol behavior changed. The preserved totals are
  597 FCA checks and 1832 TriWasa checks on each Minecraft version.
- Dual-version manual verification of Easy Place and printer behavior completed
  successfully, including the temporary wall-bell click path logs and their
  removal after resolution.

### 2026-11-09 - Review Follow-Up

- Multiface wire order is now a frozen explicit array:
  `DOWN, UP, NORTH, SOUTH, WEST, EAST`. Vine uses the same array with `DOWN`
  filtered out, yielding `UP, NORTH, SOUTH, WEST, EAST`. This keeps one source
  of truth while preserving the current five-face vine semantics.
- The V2-Extra payload marker and protocol version remain 1, but the canonical
  face ordering is now documented as a build compatibility contract. Builds
  using the historical face order must be upgraded together to avoid
  misdecoding multiface and vine faces.
- Generic direction decoding no longer throws when protocol index 6 is used
  without a placement context; it falls back to the state's horizontal facing.
  Fence-gate interaction coverage exercises the null-context raw value.
- Removed a duplicated generic `HALF` state assignment introduced during the
  registry migration.
- Added exact multiface/vine wire-index round-trip checks and null-context
  fence-gate coverage. Current totals are 598 FCA checks and 1843 TriWasa
  checks on each Minecraft version.
