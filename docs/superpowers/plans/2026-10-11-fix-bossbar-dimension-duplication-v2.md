# BossBar Dimension Duplication Fix (V2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triệt tiêu hoàn toàn lỗi duplicate / ghost BossBar khi người chơi hoặc robot chuyển dimension, đảm bảo mỗi người chơi chỉ sở hữu tối đa 1 instance BossBar cố định (persistent) xuyên suốt mọi lần đổi thế giới và dịch chuyển.

**Architecture:** Quản lý vòng đời `BossBar` tập trung ở cấp độ `LunarRobotMechanic` (persistent singleton) thay vì để `LunarRobotEntity` tự khởi tạo và hủy khi chuyển world. Tái sử dụng cùng một instance Kyori Adventure `BossBar` (cùng UUID) cho mỗi người chơi, kết hợp cơ chế dọn dẹp chủ động trên `PlayerTeleportEvent`, `PlayerChangedWorldEvent`, và `PlayerRespawnEvent`.

**Tech Stack:** Paper API 1.21.x, Kyori Adventure BossBar, JUnit 5, Mockito.

---

## Global Constraints

- **Platform:** Paper 1.21.x (Java 21).
- **Adventure API Standard:** 1 Player = Max 1 Robot Status BossBar instance. Cập nhật thuộc tính in-place thay vì hủy/tạo mới.
- **Ponytail Doctrine:** Tối thiểu hóa diff, không sinh class mới không cần thiết, giải quyết tại gốc rễ chứ không vá triệu chứng.

---

## Tasks

### Task 1: Thêm bộ test mô phỏng thực tế chu kỳ chuyển Dimension của Robot
**Files:**
- Modify: `src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java`

- [ ] **Step 1:** Viết failing test mô phỏng robot đổi dimension (`EntityRemoveFromWorldEvent` -> `EntityAddToWorldEvent`), kiểm tra xem `BossBar` có bị tạo instance mới hay không.
- [ ] **Step 2:** Viết test kiểm tra `PlayerRespawnEvent` khi chết và hồi sinh ở dimension khác.
- [ ] **Step 3:** Chạy test để xác nhận các bài test mới fail trước khi fix.

### Task 2: Quản lý Persistent BossBar trong `LunarRobotMechanic`
**Files:**
- Modify: `src/main/java/vn/haohan/lunar/robot/LunarRobotMechanic.java`
- Modify: `src/main/java/vn/haohan/lunar/robot/LunarRobotEntity.java`

- [ ] **Step 1:** Thêm `Map<UUID, BossBar> playerStatusBossBars` và `getOrCreatePlayerStatusBossBar(UUID playerUuid)` vào `LunarRobotMechanic`.
- [ ] **Step 2:** Cập nhật `cleanupPlayerRobotBossBar(Player player)` để ẩn chính xác persistent bar.
- [ ] **Step 3:** Thêm listener `@EventHandler` cho `PlayerRespawnEvent`.
- [ ] **Step 4:** Cập nhật `LunarRobotEntity.updateStatusBossBar()` để tái sử dụng persistent BossBar từ mechanic.
- [ ] **Step 5:** Chạy `./gradlew test --tests vn.haohan.lunar.robot.LunarRobotBossBarDimensionTest` để xác nhận test PASS.

### Task 3: Bảo vệ tổng quát cho Mob BossBar trong `MobSkillRuntime`
**Files:**
- Modify: `src/main/java/vn/haohan/engine/core/mob/MobSkillRuntime.java`
- Modify: `src/test/java/vn/haohan/lunar/robot/LunarRobotBossBarDimensionTest.java`

- [ ] **Step 1:** Cập nhật `MobSkillRuntime.onPlayerChangedWorld` để gỡ viewer khi mob entity là null hoặc khác world.
- [ ] **Step 2:** Thêm test case cho kịch bản mob bossbar khi entity null trong test file.
- [ ] **Step 3:** Chạy toàn bộ test suite `./gradlew test`.
