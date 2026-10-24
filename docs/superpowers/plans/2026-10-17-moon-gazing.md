# Kế Hoạch Triển Khai Tính Năng Ngắm Mặt Trăng (Moon Gazing) & 2 Thành Tựu

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai tính năng phát hiện góc nhìn ngắm Mặt Trăng trên bầu trời đêm ở Overworld và trao 2 thành tựu "Ngắm nhìn chú cuội" (đứng yên 10s) và "Cái đó là gì vậy?" (dùng spyglass 0.75s).

**Architecture:** Sử dụng cơ chế tính toán vector hướng Mặt Trăng từ `world.getTime()` trong Minecraft, kiểm tra góc lệch (dot product) giữa hướng nhìn của Player và Mặt Trăng, kết hợp kiểm tra vị trí đứng yên / đang dùng spyglass. Trao advancement qua `Advancement.awardCriteria` với custom advancement JSON trong Datapack tích hợp.

**Tech Stack:** Java 21, PaperMC API 1.21.x, JUnit 5, MockBukkit v1.21, Datapack Advancements JSON.

**Spec:** `docs/superpowers/specs/2026-10-17-moon-gazing-design.md`

## Global Constraints
- Chỉ kích hoạt ở Overworld (`world.getEnvironment() == World.Environment.NORMAL`).
- Thời gian ban đêm có mặt trăng (~12500 đến 23500 ticks), không mưa bão (`!world.hasStorm()`).
- Bầu trời không bị che khuất tại vị trí người chơi (`highestBlockY <= eyeLocationY`).
- Tuân thủ nguyên tắc Ponytail: code tối giản, hiệu quả, không tạo abstraction thừa.

## Review Focus
- Trường hợp người chơi quay người hoặc di chuyển khi đang ngắm chú cuội: bộ đếm đứng yên 10s phải bị reset.
- Trường hợp người chơi hạ spyglass giữa chừng trước khi đủ 0.75s: bộ đếm spyglass phải bị reset.
- Trường hợp người chơi đã nhận advancement rồi: không lặp lại logic trao thưởng không cần thiết.
- Trường hợp người chơi thoát game (quit) hoặc chuyển thế giới: giải phóng bộ nhớ tracking.

---

### Task 1: Tạo các Advancement JSON trong Datapack tích hợp

**Files:**
- Create: `src/main/resources/datapack/data/haohan/advancement/lunar/moon_cuoi.json`
- Create: `src/main/resources/datapack/data/haohan/advancement/lunar/moon_what_is_that.json`

**Interfaces:**
- Produces: 
  - Advancement key: `haohan:lunar/moon_cuoi`
  - Advancement key: `haohan:lunar/moon_what_is_that`

- [ ] **Step 1: Viết file `moon_cuoi.json`**
  - Parent: `haohan:lunar/root`
  - Icon: `minecraft:ender_eye` hoặc `minecraft:clock`
  - Title: Ngắm nhìn chú cuội
  - Description: Đứng yên và ngắm nhìn mặt trăng trên bầu trời đêm
  - Frame: `task`
  - Criteria: `look` với trigger `minecraft:impossible`

- [ ] **Step 2: Viết file `moon_what_is_that.json`**
  - Parent: `haohan:lunar/moon_cuoi`
  - Icon: `minecraft:spyglass`
  - Title: Cái đó là gì vậy?
  - Description: Dùng kính viễn vọng quan sát kỹ mặt trăng...
  - Frame: `challenge`
  - Criteria: `look_spyglass` với trigger `minecraft:impossible`

- [ ] **Step 3: Validate file JSON**
  - Kiểm tra tính hợp lệ của cú pháp JSON bằng tool hoặc script.

---

### Task 2: Viết MoonUtil tính toán Vector hướng Mặt Trăng và Unit Tests

**Files:**
- Create: `src/main/java/vn/haohan/lunar/util/MoonUtil.java`
- Create: `src/test/java/vn/haohan/lunar/util/MoonUtilTest.java`

**Interfaces:**
- Produces:
  - `MoonUtil.isNightTime(long worldTime)` -> boolean
  - `MoonUtil.getMoonDirection(long worldTime)` -> Vector (normalized)
  - `MoonUtil.isLookingAtMoon(Player player, double maxAngleDegrees)` -> boolean

- [ ] **Step 1: Viết test failing cho `MoonUtil`**
  - Test `isNightTime`: tick 18000 -> true, tick 6000 -> false.
  - Test `getMoonDirection`: tick 18000 -> vector hướng thẳng lên thiên đỉnh (y gần 1, x, z gần 0).
  - Test góc nhìn: người chơi nhìn thẳng lên thiên đỉnh lúc 18000 tick -> `isLookingAtMoon` trả về true.

- [ ] **Step 2: Chạy test kiểm tra lỗi**
  - Chạy `./gradlew test --tests vn.haohan.lunar.util.MoonUtilTest`.

- [ ] **Step 3: Viết mã triển khai `MoonUtil.java`**
  - Triển khai tính toán góc chuyển động của thiên thể quanh trục X hoặc Z trong Minecraft (Minecraft mặt trăng và mặt trời quay quanh trục X hoặc Z tuỳ theo tọa độ Đông - Tây).
  - Lưu ý: Trong Minecraft Overworld, Mặt Trời mọc ở hướng Đông (+X), lặn ở Tây (-X). Mặt Trăng đối xứng 180 độ so với Mặt Trời.
  - Thiên thể quay quanh trục Z (East +X, West -X, Up +Y). Hoặc theo chuẩn trục tọa độ Minecraft.

- [ ] **Step 4: Chạy test và xác minh pass**
  - Chạy `./gradlew test --tests vn.haohan.lunar.util.MoonUtilTest`.

---

### Task 3: Triển khai `MoonGazingMechanic` và tích hợp vào Plugin

**Files:**
- Create: `src/main/java/vn/haohan/lunar/features/MoonGazingMechanic.java`
- Modify: `src/main/java/vn/haohan/lunar/HaoHanLunarPlugin.java`
- Create: `src/test/java/vn/haohan/lunar/features/MoonGazingMechanicTest.java`

**Interfaces:**
- Consumes:
  - `MoonUtil`
  - Advancement keys `haohan:lunar/moon_cuoi`, `haohan:lunar/moon_what_is_that`
- Produces:
  - `MoonGazingMechanic` implement `Listener, IService`
  - BukkitTask chạy mỗi 5 ticks (0.25s) kiểm tra người chơi online.

- [ ] **Step 1: Viết test cho `MoonGazingMechanic`**
  - Kiểm tra logic tích luỹ thời gian ngắm (ticks count) khi đứng yên và khi dùng spyglass.
  - Reset khi người chơi di chuyển hoặc đổi góc nhìn ra xa mặt trăng.

- [ ] **Step 2: Viết mã `MoonGazingMechanic.java`**
  - Theo dõi tọa độ `lastLocation`: nếu khoảng cách di chuyển > 0.08 block -> reset `cuoiTicks`.
  - Theo dõi trạng thái `isHandRaised()` với `SPYGLASS` -> tích lũy `spyglassTicks`.
  - Khi `cuoiTicks >= 200` (10 giây) -> trao advancement `haohan:lunar/moon_cuoi`.
  - Khi `spyglassTicks >= 15` (0.75 giây) -> trao advancement `haohan:lunar/moon_what_is_that`.

- [ ] **Step 3: Đăng ký `MoonGazingMechanic` vào `HaoHanLunarPlugin`**
  - Khởi tạo trong `onEnable()`, quản lý lifecycle trong `onDisable()`.

- [ ] **Step 4: Chạy test toàn bộ dự án**
  - Chạy `./gradlew test` và build `./gradlew build`.
