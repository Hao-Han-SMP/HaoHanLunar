# Architecture Audit — HaoHanLunar

## Target Platform Context
- **Minecraft**: 1.21.11 / Purpur 1.21.11
- **API Runtime**: Paper / Purpur 1.21.1-R0.1-SNAPSHOT (`paper-plugin.yml`, `api-version: '1.21'`)
- **Java Toolchain**: Java 21 (`options.release = 21`)
- **External Dependencies**:
  - `HaoHanItemCore` (hard dependency, `load: BEFORE`, custom items & item IDs)
  - `ModelEngine` (hard dependency, `load: BEFORE`, 3D models & animations)
  - `SkyboxEngine` (soft dependency, `load: BEFORE`, optional lunar skybox)

---

## 1. Current Architecture

The project is currently split across two primary namespaces: `vn.haohan.lunar.api` and `vn.haohan.lunar.core`, along with the plugin entry point `vn.haohan.lunar.HaoHanLunarPlugin`.

### 1.1 Plugin Entry Point & Core Mechanics
- `HaoHanLunarPlugin`: Extends `JavaPlugin`. On enable, it initializes legacy survival mechanics:
  - `GravityMechanic` (low gravity attribute modifiers for `haohan:lunar`)
  - `OxygenMechanic` (custom oxygen gauge, space helmets, oxygen tank consumption)
  - `MiningMechanic` (block break speed attenuation on lunar ores)
  - `VisualMechanic` (SkyboxEngine hooks)
  - `BeaconShieldMechanic`, `LunarSurfaceSpreadMechanic`, `TelescopeMechanic`
  - `LunarWardenMechanic` (hardcoded boss fight with state machine, evasion, slash combos)
  - `LunarClaymoreMechanic` & `SmoothSlashTask` (ModelEngine-driven visual weapon combos)
- `PlayerDataManager`: Manages in-memory player oxygen and dimension states, backed by YAML storage.
- Inline commands: `/tplunar`, `/spawnwarden`, `/wardenshowcase`, `/clearwarden` are registered as anonymous `BukkitCommand` instances in `onEnable()`.

### 1.2 The Engine & Subsystem Layer (`core.subsystem`)
- `LunarSubSystem`: Interface representing an engine subsystem (`name`, `priority`, `isTickable`, `init`, `tick`, `disable`).
- `LunarSubSystems`: Central static orchestrator for sorting and dispatching lifecycle ticks/disables.
- `MobCoreSubSystem`: Instantiates mob definition registries, skill registries, damage pipeline, drop tables, spawner managers, and publishes them into `LunarAPI`.
- Other engine subsystems: `ItemCoreSubSystem`, `PinSubSystem`, `PlayerDataSubSystem`.
- **Note**: `LunarSubSystems.init(this)` is never invoked in `HaoHanLunarPlugin.java`, leaving the engine subsystem orchestrator completely unhooked at runtime.

### 1.3 The Mythic-Style Mob & Combat Engine (`api.system.*` and `core.*`)
- Built following `plan2.md` to emulate MythicMobs features:
  - Mob definitions, options, attributes, equipment loadouts, disguises, mounts, packs.
  - PDC-based entity identity (`LunarMobIdentity`).
  - Active runtime representations (`ActiveMob`, `ActiveLunarMob`).
  - 12-step combat damage pipeline (`DamagePipeline`, `DamageContext`, `DamageResult`, `DamageModifierTable`, `ImmunityTable`).
  - Threat table & threat decay (`ThreatTable`, `ThreatManager`).
  - Skill engine (`SkillRegistry`, `SkillScheduler`, `CooldownRegistry`, conditions, mechanics, targeters, aura, projectile, chain, composite nodes).
  - Loot engine (`DropManager`, `DropTableDefinition`, `PityManager`, `InstancedDropTracker`).
  - Spawners (`FixedSpawnerManager`, `LunarFixedSpawner`, `RandomSpawnManager`).

---

## 2. Dependency Graph

### Intended Flow:
```
External Callers / Integrations
            ↓
vn.haohan.lunar.api (Contracts, Interfaces, Events, Facade)
            ↓
vn.haohan.lunar.core (Implementations, Subsystems, Mechanics, State)
            ↓
Paper / Purpur 1.21 API + Bukkit + JVM 21
            ↓
External Plugins (HaoHanItemCore, ModelEngine, SkyboxEngine)
```

### Actual (Broken) Reverse Dependencies Identified:
1. `vn.haohan.lunar.api.system.command.LunarMobCommand` imports:
   - `vn.haohan.lunar.core.subsystem.mob.ActiveMob`
   - `vn.haohan.lunar.core.system.debug.*`
   - `vn.haohan.lunar.core.system.item.*`
   - `vn.haohan.lunar.core.mob.LunarMobManager`
   *(API depending on core implementation)*
2. `vn.haohan.lunar.api.system.combat.DamageTracker` calls `mobManager.get(UUID)` on an unimported or uncast `MobManager` type.
3. `vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry` imports `vn.haohan.lunar.core.subsystem.mob.LunarMobManager`, but passes it to methods expecting `vn.haohan.lunar.core.mob.LunarMobManager`.
4. `vn.haohan.lunar.api.system.world.environment.LunarEnvironmentTracker` imports `vn.haohan.lunar.core.subsystem.mob.LunarMobManager`.
5. `vn.haohan.lunar.api.system.loot.DropManager` imports `vn.haohan.lunar.core.subsystem.mob.ActiveMob`.

---

## 3. Duplicate Code

1. **`LunarMobManager`**:
   - `vn.haohan.lunar.core.subsystem.mob.LunarMobManager`: Contains the primary implementation (180 lines, `activeMobs` map, goal applier, model cleanup).
   - `vn.haohan.lunar.core.mob.LunarMobManager`: Subclass forwarding to `core.subsystem.mob.LunarMobManager`.
   - *Issue*: Having two classes with the exact same simple name across packages causes constructor type mismatches (e.g., `MobCommand` requiring `core.mob.LunarMobManager` while `MobCoreSubSystem` creates `core.subsystem.mob.LunarMobManager`).
2. **`ActiveMob` vs `ActiveLunarMob`**:
   - `vn.haohan.lunar.core.subsystem.mob.ActiveMob`: Real domain model.
   - `vn.haohan.lunar.core.mob.ActiveLunarMob`: Trivial subclass extending `ActiveMob`.
3. **Command Registration Duplication**:
   - `HaoHanLunarPlugin.java` lines 105–243: Hardcoded anonymous `BukkitCommand`s for `/tplunar`, `/spawnwarden`, `/wardenshowcase`, `/clearwarden`.
   - `vn.haohan.lunar.core.command.commands.*`: Dedicated classes `TpLunarCommand`, `SpawnWardenCommand`, `WardenShowcaseCommand`, `ClearWardenCommand` containing the exact same logic.
4. **`ThreatTable` Simple Name Collision**:
   - `vn.haohan.lunar.api.system.combat.ThreatTable`: Interface.
   - `vn.haohan.lunar.api.system.combat.threat.ThreatTable`: Implementation.
   - *Issue*: Identical simple name requires fully-qualified names or causes confusion when imported.
5. **`TargeterRegistry` Split**:
   - `vn.haohan.lunar.api.system.combat.skill.target.TargeterRegistry`: Context-based functional targeter registry.
   - `vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry`: Regex-based geometric targeter registry (`@PlayersInRadius`, cone, cylinder, etc.).
6. **Config Validation/Exception Aliases**:
   - `vn.haohan.lunar.api.system.config.ConfigLoadException` & `vn.haohan.lunar.core.system.config.ConfigLoadException`.
   - `vn.haohan.lunar.api.system.config.ConfigValidationReport` & `vn.haohan.lunar.core.system.config.ConfigValidationReport`.

---

## 4. Dead / Redundant Code

1. **Unwired Subsystems**:
   - `LunarSubSystems.init(this)` and `LunarSubSystems.disable(this)` are never called in `HaoHanLunarPlugin`.
   - `MobCoreSubSystem`, `ItemCoreSubSystem`, `PinSubSystem`, and `PlayerDataSubSystem` are never automatically booted in production.
2. **Unwired Commands**:
   - `LunarCommands.init(this)` is never called in `HaoHanLunarPlugin`. The classes in `vn.haohan.lunar.core.command.commands.*` sit idle while duplicate inline commands run instead.
3. **Empty Stub Classes**:
   - `ActiveLunarMob` is an empty subclass.
   - `LunarMobManager` in `core.mob` is an empty subclass.

---

## 5. Architectural Problems

1. **API Package Misplacement**:
   - Almost all internal subsystems (`combat`, `loot`, `spawner`, `world`) were placed in `vn.haohan.lunar.api.system.*`.
   - Half of them were partially moved to `vn.haohan.lunar.core.system.*` (`config`, `data`, `debug`, `item`, `scheduler`, `util`, `validator`, `variable`), leaving the codebase split and broken.
2. **Interface Incompleteness**:
   - `vn.haohan.lunar.api.manager.MobManager` only exposed `getMob`, `getActiveMobs`, `isManaged`, and `activeCount`.
   - Internal systems (`DamageTracker`, `DropManager`, `ThreatManager`, `FixedSpawnerManager`, `RandomSpawnManager`) needed methods like `get(UUID)` returning `ActiveMob`, `snapshot()`, and `register(...)`, but held the `MobManager` interface reference, breaking compilation.
3. **Circular Subsystem References**:
   - Subsystems instantiate mechanics and registers, but also register commands and publish to static singletons (`LunarAPI`), causing tight coupling.

---

## 6. Platform Problems (Purpur 1.21.11 / Paper 1.21)

1. **Paper Attributes**:
   - Good: `GravityMechanic` and `MiningMechanic` use modern 1.21 Paper attribute APIs (`Attribute.GENERIC_GRAVITY`, `Attribute.PLAYER_BLOCK_BREAK_SPEED`, `AttributeModifier` with `NamespacedKey` and `EquipmentSlotGroup`).
2. **Entity Persistence**:
   - Good: Custom mob identification correctly relies on `PersistentDataContainer` via `LunarMobIdentity`, avoiding fragile custom names.
3. **Async / Ticking**:
   - The plugin runs a 1-tick repeating task on the Bukkit main scheduler ticking multiple mechanics.
   - Folia / multi-threading: `PlatformScheduler` abstracts Folia region schedulers vs Paper global schedulers.

---

## 7. External Integration Problems

1. **HaoHanItemCore**:
   - Declared as required in `paper-plugin.yml`.
   - `HaoHanItemBridge` provides fallback for test environments, which is resilient.
   - `LunarItems.register()` handles custom item registration on enable.
2. **ModelEngine 4**:
   - Declared as required in `paper-plugin.yml`.
   - `DefaultBoneLocationResolver` and `LunarMobManager` properly check `ModelEngineAPI.getAPI() == null` before querying modeled entities, preventing hard crashes if ModelEngine is missing or reloading.
3. **SkyboxEngine**:
   - Correctly treated as soft dependency. `VisualMechanic` uses reflection or safe null guards.

---

## 8. Runtime Bugs Identified

1. **Compilation Failure (P0)**:
   - 20 compilation errors preventing build execution:
     - `DamageTracker.java` (missing import for `MobManager`).
     - `DropManager.java`, `ThreatManager.java`, `FixedSpawnerManager.java`, `LunarFixedSpawner.java`, `RandomSpawnManager.java` attempting to invoke concrete methods on interface `MobManager`.
     - `MechanicRegistry.java` lines 593 & 608 type mismatch between `subsystem.mob.LunarMobManager` and `core.mob.LunarMobManager`.
     - `MobCoreSubSystem.java` line 83 constructor type mismatch for `MobCommand`.
2. **Orphaned Engine Subsystems (P1)**:
   - `MobCoreSubSystem` and `ItemCoreSubSystem` are never booted in `HaoHanLunarPlugin.onEnable()`, meaning Mythic mob configs, loot tables, custom skills, and spawner loops are never active on a live server.
3. **Command Aliasing Desync (P2)**:
   - Inlined commands in `HaoHanLunarPlugin` do not share registration with `LunarCommands`, leading to redundant command map registrations and potential alias clashes.

---

## 9. Refactoring Plan

### Phase P0: Correctness & Build Restoration (Immediate)
1. **Unify `LunarMobManager` Reference**:
   - Ensure `DamageTracker`, `DropManager`, `ThreatManager`, `FixedSpawnerManager`, `LunarFixedSpawner`, and `RandomSpawnManager` reference `vn.haohan.lunar.core.mob.LunarMobManager`.
   - Resolve type mismatches in `MechanicRegistry` and `MobCoreSubSystem` by using consistent `vn.haohan.lunar.core.mob.LunarMobManager` imports.
2. **Verify Compilation**:
   - Run `./gradlew compileJava` and verify 0 errors.

### Phase P1: Subsystem & Command Wiring
1. **Connect `LunarSubSystems`**:
   - Register and initialize `MobCoreSubSystem`, `ItemCoreSubSystem`, `PinSubSystem`, and `PlayerDataSubSystem` in `HaoHanLunarPlugin.onEnable()`.
   - Replace redundant inline BukkitCommand registrations with `LunarCommands.init(this)`.
   - Verify graceful cleanup in `onDisable()`.

### Phase P2: Package & Boundary Cleanup
1. **Clean up Reverse Dependencies**:
   - Move `LunarMobCommand` from `api.system.command` into `core.command` so the API package has 0 dependencies on `core`.
   - Move internal `api.system.*` implementations to `core.system.*` while retaining clean interfaces in `api`.
2. **Eliminate Artificial Duplicate Classes**:
   - Merge `vn.haohan.lunar.core.subsystem.mob.LunarMobManager` and `vn.haohan.lunar.core.mob.LunarMobManager` cleanly into one canonical class without breaking test imports.

### Phase P3: Verification & Test Suite
1. Run `./gradlew test` to ensure all 64 test suites pass without regression.
2. Run `./gradlew check` / `./gradlew build` to confirm final artifact packaging.

---

## 10. Proposed Final Architecture

```
vn.haohan.lunar
├── HaoHanLunarPlugin (Main entry point, bootstrap, lifecycle)
├── api
│   ├── LunarAPI (Static service locator facade)
│   ├── event (Public Bukkit events: LunarMobSpawnEvent, etc.)
│   ├── integration (Bridge contracts for ItemCore, ModelEngine, PAPI)
│   ├── manager (MobManager, SkillManager, CombatManager, LootManager, SpawnerManager)
│   └── mob (Mob contract, MobDefinition, MobAttributeDefinition, MobOptionDefinition)
└── core
    ├── command (Command implementations: TpLunarCommand, MobCommand, etc.)
    ├── features (GravityMechanic, OxygenMechanic, MiningMechanic, VisualMechanic, Boss Warden)
    ├── mob (ActiveMob, LunarMobManager, LunarMobIdentity, AI, Mounts, Packs)
    ├── subsystem (LunarSubSystem, LunarSubSystems, MobCoreSubSystem, etc.)
    └── system
        ├── combat (DamagePipeline, ThreatTable, SkillRegistry, Mechanics)
        ├── config (LunarYamlLoader, ConfigValidationReport)
        ├── data (PlayerDataManager, PlayerLunarData)
        ├── debug (PerformanceMetrics, TickBudgetWatchdog, SkillTracer)
        ├── item (ItemDefinitionRegistry, ItemSkillRuntime)
        ├── loot (DropManager, DropTableDefinition, PityManager)
        ├── scheduler (PlatformScheduler, FoliaAdapter, PaperAdapter)
        └── spawner (FixedSpawnerManager, RandomSpawnManager)
```
