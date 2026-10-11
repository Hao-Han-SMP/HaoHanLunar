# BossBar Dimension Duplication Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate BossBar duplication and ghosting when players change dimensions or teleport between worlds, ensuring all HUD and mob BossBars cleanly detach and reuse stable instances.

**Architecture:** Address the root cause in `LunarRobotEntity` by making the Adventure `BossBar` instance persistent across viewer exits instead of nullifying it every time viewers become empty, which was generating a new UUID on every dimension change. In `LunarRobotMechanic`, proactively hide the robot's BossBar on `PlayerTeleportEvent` (cross-world) and `PlayerChangedWorldEvent` before client-side dimension transitions occur. Broaden the fix generically to all mob BossBars in `MobSkillRuntime` by removing cross-dimension viewers on world changes.

**Tech Stack:** Paper API 1.21.x, Kyori Adventure BossBar (`net.kyori.adventure.bossbar.BossBar`), JUnit 5, Mockito.

**Spec:** Codebase bug report - BossBar duplication on dimension change (screenshot analysis showing 3 stacked overlapping 3-line BossBar HUD cards).

---

## Global Constraints

- **Platform:** Paper 1.21.x (Java 21).
- **Adventure API Standard:** Use `Audience.showBossBar` and `Audience.hideBossBar` exclusively with the exact same `BossBar` instance.
- **YAGNI & Minimal Code (Ponytail):** No new bloated manager classes or unnecessary abstractions. Fix the root cause in existing lifecycle paths (`LunarRobotEntity`, `LunarRobotMechanic`, `MobSkillRuntime`).
- **Strict Single Robot Invariant:** Each player has at most 1 robot tracked in `playerRobotMap`.

---

## Review Focus

1. **BossBar Instance Churn:** Previously, `targetViewers.isEmpty()` called `cleanupStatusBossBar()` which set `statusBossBar = null`. Next time the player approached or dimension re-synced, a new `BossBar` was created with a brand-new random UUID, causing the Minecraft client's `BossHealthOverlay` map (`Map<UUID, LerpingBossEvent>`) to accumulate multiple stacked bars. Reusing the same `BossBar` instance ensures that even if duplicate `show` packets are received, the client updates in-place rather than stacking.
2. **Dimension Boundary for Riders:** `getRider()` was added to `targetViewers` without verifying `rider.getWorld().equals(entity.getWorld())`. If a rider changed dimension or was in another world, they remained in `targetViewers`.
3. **Packet Race during Dimension Teleport:** If a player teleports across dimensions, hiding the BossBar after the 2-tick delay or after chunk unload sends packets to a client that is already in a different world state. Hiding on `PlayerTeleportEvent` (cross-world) and `PlayerChangedWorldEvent` ensures clean removal before or right as the new dimension loads.
4. **Mob BossBar Dimension Leak:** Engine mobs with active `LunarBossBar` instances could retain players who switched dimensions if the mob's tick loop only passed `entity.getWorld().getPlayers()`.
5. **Clean Robot Re-instantiation on Cross-World Teleport:** When the robot itself teleports across dimensions, `EntityRemoveFromWorldEvent` triggers `cleanup()`. If `statusBossBar` was not already set to `null` prematurely, `cleanup()` can actually hide the bar before destruction.

---

## Proposed Changes

### Component 1: Robot Entity BossBar Lifecycle (`vn.haohan.lunar.robot`)

#### [MODIFY] [`LunarRobotEntity.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotEntity.java)
- In `updateStatusBossBar()`:
  - Check `rider.getWorld().equals(entity.getWorld())` when evaluating `getRider()`.
  - When `targetViewers.isEmpty()`: do **NOT** destroy `statusBossBar` (do not set `statusBossBar = null`). Instead, hide `statusBossBar` from all current viewers in `statusBossBarViewers` and clear `statusBossBarViewers`.
  - Ensure `statusBossBar` is lazily created once and retained for the lifetime of `LunarRobotEntity`.
  - Provide helper method `hideStatusBossBarFrom(Player player)` to cleanly detach a single player (e.g. during teleport/world change).
- In `cleanupStatusBossBar()`:
  - Hide `statusBossBar` from all viewers and then set `statusBossBar = null`.

#### [MODIFY] [`LunarRobotMechanic.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotMechanic.java)
- In `onPlayerTeleport(PlayerTeleportEvent event)`:
  - When `worldChanged` is true, immediately resolve the player's active robot and call `robot.hideStatusBossBarFrom(player)`.
- In `onPlayerChangeWorld(PlayerChangedWorldEvent event)`:
  - Immediately resolve the player's active robot and call `robot.hideStatusBossBarFrom(player)`.

---

### Component 2: Engine Mob Presentation Cross-Dimension Protection (`vn.haohan.engine.core.mob`)

#### [MODIFY] [`MobSkillRuntime.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/engine/core/mob/MobSkillRuntime.java)
- In `onPlayerChangedWorld(PlayerChangedWorldEvent event)`:
  - Iterate active mobs: for any mob whose world is not `event.getPlayer().getWorld()`, iterate `mob.bossBars().allBars()` and hide each bar from `event.getPlayer()` if currently viewed.

---

### Component 3: Unit & Integration Tests

#### [NEW] [`LunarRobotBossBarDimensionTest.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java)
- Test 1: Verifies `statusBossBar` is reused and not recreated with a new UUID when viewers empty and return.
- Test 2: Verifies `updateStatusBossBar()` excludes riders who are in a different world/dimension.
- Test 3: Verifies `hideStatusBossBarFrom(player)` detaches player cleanly and removes UUID from viewers.
- Test 4: Verifies `onPlayerTeleport` and `onPlayerChangeWorld` cleanly hide BossBar from player when crossing dimensions.
- Test 5: Verifies `MobSkillRuntime.onPlayerChangedWorld` hides boss bars of mobs residing in different worlds.

---

## Tasks

### Task 1: Write Failing Unit Tests for BossBar Dimension & Lifecycle Behavior
**Files:**
- Create: `src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java`

- [ ] **Step 1: Write failing tests** for `statusBossBar` instance reuse, cross-dimension rider exclusion, and `hideStatusBossBarFrom` method.
- [ ] **Step 2: Run test to verify failure** (`./gradlew test --tests vn.haohan.lunar.robot.LunarRobotBossBarDimensionTest`).
- [ ] **Step 3: Implement minimal changes in `LunarRobotEntity.java`** to make tests pass:
  - Add `hideStatusBossBarFrom(Player player)`
  - Keep `statusBossBar` reused instead of resetting to `null` on `targetViewers.isEmpty()`
  - Add world match check for `getRider()` in `updateStatusBossBar()`
- [ ] **Step 4: Run test to verify PASS** (`./gradlew test --tests vn.haohan.lunar.robot.LunarRobotBossBarDimensionTest`).
- [ ] **Step 5: Commit** `git commit -m "fix(robot): prevent BossBar recreation churn and cross-world rider leakage"`

---

### Task 2: Implement Proactive World-Change Cleanup in `LunarRobotMechanic`
**Files:**
- Modify: `src/main/java/vn/haohan/lunar/robot/LunarRobotMechanic.java`
- Modify: `src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java`

- [ ] **Step 1: Write failing test** in `LunarRobotBossBarDimensionTest` simulating `PlayerTeleportEvent` and `PlayerChangedWorldEvent` triggering `robot.hideStatusBossBarFrom(player)`.
- [ ] **Step 2: Run test to verify failure**.
- [ ] **Step 3: Implement minimal changes in `LunarRobotMechanic.java`**:
  - In `onPlayerTeleport`: if `worldChanged`, hide robot BossBar from player.
  - In `onPlayerChangeWorld`: hide robot BossBar from player.
- [ ] **Step 4: Run test to verify PASS** (`./gradlew test --tests vn.haohan.lunar.robot.LunarRobotBossBarDimensionTest`).
- [ ] **Step 5: Commit** `git commit -m "fix(robot): proactively hide robot BossBar on player dimension change"`

---

### Task 3: Generic Dimension Cleanup for Mob BossBars in `MobSkillRuntime`
**Files:**
- Modify: `src/main/java/vn/haohan/engine/core/mob/MobSkillRuntime.java`
- Modify: `src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java`

- [ ] **Step 1: Write test** for `MobSkillRuntime.onPlayerChangedWorld` detaching cross-world mob bossbars.
- [ ] **Step 2: Run test to verify failure**.
- [ ] **Step 3: Implement minimal changes in `MobSkillRuntime.java`**:
  - In `onPlayerChangedWorld`: hide any active mob BossBars if the mob's world does not match the player's new world.
- [ ] **Step 4: Run all tests to verify PASS** (`./gradlew test`).
- [ ] **Step 5: Commit** `git commit -m "fix(mob): clear cross-world mob BossBars on PlayerChangedWorldEvent"`

---

## Verification Plan

### Automated Tests
- Run full test suite: `./gradlew test`
- Run new dimension test: `./gradlew test --tests vn.haohan.lunar.robot.LunarRobotBossBarDimensionTest`
- Content schema validator: `./gradlew validateContent`

### Manual Verification
- Spawn a robot in Overworld and activate speed module (BossBar visible).
- Travel through Nether Portal or `/world` dimension teleport to another dimension.
- Check BossBar overlay: only at most 1 active BossBar HUD card appears after robot reunites, with 0 ghost or duplicated BossBars.
