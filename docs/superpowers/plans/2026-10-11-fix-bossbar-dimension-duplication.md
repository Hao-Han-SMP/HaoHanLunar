# BossBar Dimension Duplication Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triệt tiêu hoàn toàn lỗi duplicate / ghost BossBar khi người chơi chuyển dimension (Nether, End, Moon, Overworld), áp dụng tổng quát cho Robot HUD, Custom Bosses, và Mobs.

**Architecture:** 
1. **Root cause fix in `LunarRobotEntity`:** Giữ nguyên instance `BossBar` (`statusBossBar`) xuyên suốt vòng đời robot thay vì hủy và gán `null` mỗi khi `targetViewers` rỗng. Việc gán `null` khiến mỗi lần viewer quay lại hoặc đổi dimension lại tạo ra một `BossBar` mới với random UUID khác, làm Minecraft client ghi nhận thêm 1 bar mới trong `BossHealthOverlay.events` map.
2. **Proactive detachment on Player Dimension Change:** Trong `LunarRobotMechanic`, lắng nghe `PlayerChangedWorldEvent` và `PlayerTeleportEvent` (cross-world), chủ động gửi packet hide BossBar ngay lập tức tới client trước/ngay khi dimension load, ngăn chặn ghost bar bị kẹt ở client.
3. **Cross-world rider validation:** Kiểm tra `rider.getWorld().equals(entity.getWorld())` để không coi rider đã sang dimension khác là viewer của robot ở dimension cũ.
4. **Generic Engine Mob Protection:** Trong `MobSkillRuntime`, khi `PlayerChangedWorldEvent` và `PlayerQuitEvent` kích hoạt, chủ động remove player viewer khỏi tất cả các `LunarBossBar` của mob không cùng world.

**Tech Stack:** Paper API 1.21.x (Java 21), Kyori Adventure BossBar (`net.kyori.adventure.bossbar.BossBar`), JUnit 5, Mockito/Dynamic Proxies.

**Spec:** Bug report & Screenshot analysis — 3 overlapping duplicate BossBars ("MODULE TỐC HÀNH / SẴN SÀNG [SPACE] / «« ♨ 0% • 0.0 m/s • 5,000 EU »»").

---

## Global Constraints

- **Paper 1.21.x & Java 21 standard.**
- **Kyori Adventure API:** Dùng `player.showBossBar(bar)` và `player.hideBossBar(bar)` đồng nhất trên cùng một instance.
- **Ponytail Doctrine:** 
  - Đơn giản, ngắn gọn nhất, không sinh ra thêm class quản lý cồng kềnh không cần thiết.
  - Sửa đúng tận gốc vấn đề (root-cause), tái sử dụng các method sẵn có.
- **An toàn luồng & Null-safe:** Mọi thao tác đều an toàn với exception và null check.

---

## Review Focus

1. **BossBar Instance Churn:** Khắc phục triệt để việc gán `statusBossBar = null` khi `targetViewers.isEmpty()`. Đảm bảo UUID của BossBar không bị đổi ngẫu nhiên, client Minecraft luôn cập nhật in-place thay vì xếp chồng.
2. **Dimension Boundary for Riders:** Bắt buộc có kiểm tra `rider.getWorld().equals(entity.getWorld())` trong `updateStatusBossBar()`.
3. **Packet Timing & Event Hooks:** Dọn dẹp BossBar ngay tại `PlayerTeleportEvent` (nếu đổi world) và `PlayerChangedWorldEvent` để gửi gói remove trước/ngay khi client vào world mới.
4. **Mob BossBar Dimension Leak:** Đảm bảo `MobSkillRuntime` quét và gỡ người chơi khỏi bossbar của các mob ở world khác khi người chơi đổi world hoặc quit.

---

## Proposed Changes

### Component 1: `vn.haohan.lunar.robot` (Robot BossBar Stability & Dimension Isolation)

#### [MODIFY] [`LunarRobotEntity.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotEntity.java)
- Trong `updateStatusBossBar()`:
  - Thêm điều kiện `rider.getWorld().equals(entity.getWorld())` khi kiểm tra `getRider()`.
  - Khi `targetViewers.isEmpty()`: ẩn `statusBossBar` khỏi toàn bộ người chơi trong `statusBossBarViewers`, xóa `statusBossBarViewers`, nhưng **KHÔNG GÁN `statusBossBar = null`** (giữ nguyên instance để giữ nguyên UUID).
  - Sử dụng helper `getPlayerByUuid(uuid)` (ưu tiên `getOnlineOwner()`, `getRider()`, rồi `Bukkit.getPlayer(uuid)`).
- Bổ sung method `hideStatusBossBarFrom(Player player)`:
  - Gọi `player.hideBossBar(statusBossBar)` và xóa UUID khỏi `statusBossBarViewers`.
- Bổ sung method `getStatusBossBarViewersCount()` phục vụ kiểm thử.
- Trong `cleanupStatusBossBar()`:
  - Ẩn bossbar khỏi viewers trước khi gán `statusBossBar = null` khi robot thực sự bị tiêu hủy.

#### [MODIFY] [`LunarRobotMechanic.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotMechanic.java)
- Bổ sung method `cleanupPlayerRobotBossBar(Player player)`:
  - Tra cứu robot mà player sở hữu (`playerRobotMap`) và robot player đang cưỡi (`getPlayerMountedRobot`).
  - Gọi `robot.hideStatusBossBarFrom(player)`.
- Trong `onPlayerTeleport(PlayerTeleportEvent event)`:
  - Nếu `worldChanged` (`!from.getWorld().equals(to.getWorld())`), gọi `cleanupPlayerRobotBossBar(player)`.
- Trong `onPlayerChangeWorld(PlayerChangedWorldEvent event)`:
  - Gọi `cleanupPlayerRobotBossBar(event.getPlayer())`.
- Trong `onPlayerQuit(PlayerQuitEvent event)`:
  - Gọi `cleanupPlayerRobotBossBar(event.getPlayer())`.

---

### Component 2: `vn.haohan.engine` (Generic Mob BossBar Cross-Dimension Cleanup)

#### [MODIFY] [`LunarBossBar.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/engine/api/presentation/display/bossbar/LunarBossBar.java)
- Bổ sung method `removeViewer(Player player)`:
  - Gỡ `player` khỏi `currentViewers` và gọi `player.hideBossBar(bar)`.

#### [MODIFY] [`LunarBossBarTracker.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/engine/api/presentation/display/bossbar/LunarBossBarTracker.java)
- Bổ sung method `removeViewerFromAll(Player player)`:
  - Lặp qua tất cả `bars` và gọi `bar.removeViewer(player)`.

#### [MODIFY] [`MobSkillRuntime.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/engine/core/mob/MobSkillRuntime.java)
- Trong `onPlayerChangedWorld(PlayerChangedWorldEvent event)`:
  - Duyệt các mob đang active: nếu mob ở world khác với world mới của player, gọi `mob.bossBars().removeViewerFromAll(player)`.
- Trong `onPlayerQuit(PlayerQuitEvent event)`:
  - Gọi `mob.bossBars().removeViewerFromAll(player)` cho tất cả active mobs.

---

### Component 3: Unit & Integration Tests

#### [MODIFY] [`LunarRobotBossBarDimensionTest.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java)
- Sửa lỗi cú pháp ngoặc nhọn ở file test hiện tại.
- Test 1: `testBossBarReusedAcrossEmptyViewers`: Kiểm tra khi viewer rời đi và quay lại, `statusBossBar` giữ nguyên tham chiếu instance, không sinh ra BossBar mới / UUID mới.
- Test 2: `testCrossDimensionRiderExcluded`: Kiểm tra rider ở world khác không bị nhận nhầm là viewer.
- Test 3: `testHideStatusBossBarFromPlayer`: Kiểm tra `hideStatusBossBarFrom` gỡ viewer và gửi lệnh hide chính xác.
- Test 4: `testMechanicCleanupPlayerRobotBossBar`: Kiểm tra listener của mechanic dọn dẹp sạch sẽ khi đổi world.

#### [MODIFY] [`Phase8BossPresentationTest.java`](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/test/java/vn/haohan/engine/core/presentation/display/Phase8BossPresentationTest.java)
- Thêm unit test kiểm tra `removeViewer` và `removeViewerFromAll` trên `LunarBossBarTracker`.

---

## Tasks

### Task 1: Fix `LunarRobotEntity` BossBar Instance Retention & Rider Dimension Check
**Files:**
- Modify: `src/main/java/vn/haohan/lunar/robot/LunarRobotEntity.java`
- Modify: `src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java`

- [ ] **Step 1: Sửa file test `LunarRobotBossBarDimensionTest.java`** để sửa lỗi cú pháp class và hoàn thiện test cases 1, 2, 3.
- [ ] **Step 2: Chạy test để verify failure** (`./gradlew test --tests vn.haohan.lunar.robot.LunarRobotBossBarDimensionTest`).
- [ ] **Step 3: Cập nhật `LunarRobotEntity.java`**:
  - Tái sử dụng `statusBossBar`, không set null khi `targetViewers.isEmpty()`.
  - Thêm kiểm tra world cho `getRider()`.
  - Thêm `hideStatusBossBarFrom(Player)` và `getPlayerByUuid(UUID)`.
- [ ] **Step 4: Chạy test để verify PASS**.
- [ ] **Step 5: Commit:** `git commit -m "fix(robot): preserve BossBar instance and isolate cross-world riders"`

---

### Task 2: Implement Proactive World-Change Cleanup in `LunarRobotMechanic`
**Files:**
- Modify: `src/main/java/vn/haohan/lunar/robot/LunarRobotMechanic.java`
- Modify: `src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java`

- [ ] **Step 1: Viết test `testMechanicCleanupPlayerRobotBossBar`** mô phỏng `cleanupPlayerRobotBossBar`.
- [ ] **Step 2: Chạy test để verify failure**.
- [ ] **Step 3: Thêm logic trong `LunarRobotMechanic.java`**:
  - Viết `cleanupPlayerRobotBossBar(Player)`.
  - Bổ sung gọi hàm trong `onPlayerTeleport`, `onPlayerChangeWorld`, `onPlayerQuit`.
- [ ] **Step 4: Chạy test để verify PASS**.
- [ ] **Step 5: Commit:** `git commit -m "fix(robot): proactively detach BossBar on dimension change and quit"`

---

### Task 3: Generic Dimension Cleanup for Engine Mobs & Phase 8 Presentation
**Files:**
- Modify: `src/main/java/vn/haohan/engine/api/presentation/display/bossbar/LunarBossBar.java`
- Modify: `src/main/java/vn/haohan/engine/api/presentation/display/bossbar/LunarBossBarTracker.java`
- Modify: `src/main/java/vn/haohan/engine/core/mob/MobSkillRuntime.java`
- Modify: `src/test/java/vn/haohan/engine/core/presentation/display/Phase8BossPresentationTest.java`

- [ ] **Step 1: Viết test `testRemoveViewerAndRemoveViewerFromAll`** trong `Phase8BossPresentationTest.java`.
- [ ] **Step 2: Chạy test để verify failure**.
- [ ] **Step 3: Implement `removeViewer` trong `LunarBossBar` và `removeViewerFromAll` trong `LunarBossBarTracker`**.
- [ ] **Step 4: Cập nhật `MobSkillRuntime.java`** để gọi `removeViewerFromAll` trong `onPlayerChangedWorld` và `onPlayerQuit`.
- [ ] **Step 5: Chạy toàn bộ test `./gradlew test` để verify PASS**.
- [ ] **Step 6: Commit:** `git commit -m "fix(engine): detach mob bossbars across dimension changes"`

---

## Verification Plan

### Automated Tests
- Chạy riêng bài test Robot BossBar:
  ```bash
  ./gradlew test --tests vn.haohan.lunar.robot.LunarRobotBossBarDimensionTest
  ```
- Chạy riêng bài test Engine Mob BossBar:
  ```bash
  ./gradlew test --tests vn.haohan.engine.core.presentation.display.Phase8BossPresentationTest
  ```
- Chạy toàn bộ test suite của project:
  ```bash
  ./gradlew test
  ```

### Manual Verification
1. Đăng nhập server Minecraft, spawn Robot 4 chân và kích hoạt Module Tốc hành (Speed Module) hiển thị thanh HUD BossBar.
2. Bước qua Nether Portal hoặc gõ `/world` để đổi sang dimension khác.
3. Quan sát góc trên màn hình:
   - BossBar cũ ngay lập tức biến mất khi sang dimension mới.
   - Khi Robot dịch chuyển đến hoặc đoàn tụ, chỉ xuất hiện **đúng 1 thanh BossBar duy nhất**, hoàn toàn không bị chồng lấn hay duplicate.
   - Quay trở lại dimension ban đầu: thanh BossBar tiếp tục hiển thị chính xác 1 thanh.
