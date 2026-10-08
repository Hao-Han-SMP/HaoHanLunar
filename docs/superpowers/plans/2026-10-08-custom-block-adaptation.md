# Kế hoạch Thích Ứng HaoHanLunar Với Hệ Thống Custom Block (Barrier + BlockDisplay) Mới

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thích ứng toàn diện plugin `HaoHanLunar` sang hệ thống Custom Block thế hệ mới của `HaoHanItemCore` (Physical Barrier + PacketEvents Client-side Virtual BlockDisplay), đảm bảo các loại quặng Mặt Trăng, cơ chế đào quặng (MiningMechanic), và chức năng dò quặng của Robot 4 Chân (LunarRobotEntity) hoạt động chính xác, ổn định và tương thích ngược.

**Architecture:** Sử dụng Facade `HaoHanItemCore.get().getCustomBlockService()` để nhận diện, truy vấn và quản lý quặng custom trên khối vật lý `Material.BARRIER` thay thế cách kiểm tra `Material.NOTE_BLOCK` truyền thống; chuyển cấu hình item properties từ `custom_block_data` sang `custom_block_id` & `custom_block_model`; kết hợp cơ chế fallback cho dữ liệu cũ nếu gặp NoteBlock còn sót lại.

**Tech Stack:** Java 21, Paper 1.21.11 API, HaoHanItemCore 1.0.0 (API CustomBlock), PacketEvents 2.14.0, JUnit 5, Mockito.

**Spec:** Branch `feat/custom-block-adapation` trên `HaoHanLunar`, tài liệu kiến trúc tại `HaoHanItemCore/API_DOCUMENTATION.md` và `HaoHanItemCore/WIKI.md`.

---

## Tóm Tắt Thay Đổi Bên HaoHanItemCore

`HaoHanItemCore` (nhánh `feat/customblock-blockdisplay`) đã thực hiện một bước cải tổ kiến trúc lớn đối với hệ thống Custom Block:

1. **Chuyển dịch Kiến trúc cốt lõi**:
   - **Cũ (Legacy)**: Sử dụng `Material.NOTE_BLOCK` làm vật mang vật lý, gán BlockState (`custom_block_data: "minecraft:note_block[note=...,instrument=...]"`) và lưu trữ metadata qua PersistentDataContainer (PDC) trong `ItemEventRouter`. Dẫn tới nhiều vấn đề như âm thanh gõ đàn không mong muốn, xung đột redstone, giới hạn tối đa 800 blockstate note_block, và phải can thiệp NMS để xóa component `BLOCK_STATE` trên tooltip item.
   - **Mới (New)**: Chuyển sang mô hình **Physical Barrier (`Material.BARRIER`) + PacketEvents Virtual `BlockDisplay`**.
     - Khối vật lý trong thế giới luôn là `Material.BARRIER`.
     - Phía client nhìn thấy một entity ảo `BLOCK_DISPLAY` được gửi trực tiếp qua gói tin PacketEvents (`WrapperPlayServerSpawnEntity`, `WrapperPlayServerEntityMetadata`).
     - **0 Server-side Entity Ticking**: Hoàn toàn không tạo Bukkit Entity trên server, triệt tiêu gánh nặng CPU của server.
     - **Chống Z-Fighting**: Scale được khóa chuẩn `1.00005f` và translation offset `-0.000025f`.
     - **Spatial Culling & Storage**: Chỉ gửi packet khi người chơi băng qua ranh giới chunk (zero packet khi di chuyển trong chunk). Dữ liệu vị trí custom block được bit-packed nén gọn trong Chunk PDC.

2. **API Mới Trong `HaoHanItemCore`**:
   - Thêm package `vn.haohan.itemcore.api.block`:
     - `CustomBlockDefinition`: Định nghĩa bất biến của khối (`id`, `model`, `dropItemId`, `properties`).
     - `CustomBlockModel`: Mô hình hiển thị của BlockDisplay (chứa `BlockData`, scale `1.00005f`, offset).
     - `CustomBlockRegistry`: Đăng ký và tra cứu định nghĩa.
     - `CustomBlockService`: Facade cung cấp các phương thức `isCustomBlock(Block)`, `getCustomBlock(Block)`, `getCustomBlockId(Block)`, `placeCustomBlock(Location, String)`, `removeCustomBlock(Location)`.
   - `HaoHanItemCore.get().getCustomBlockService()`: Điểm truy cập trung tâm cho mọi plugin gameplay.

3. **Thay Đổi Về Thuộc Tính Item & Event**:
   - `custom_block_id`: ID định danh của CustomBlock (để tự động liên kết khối vật lý Barrier và Virtual BlockDisplay).
   - `custom_block_model`: BlockState hiển thị cho BlockDisplay (VD: `"minecraft:note_block[note=24,instrument=pling,powered=true]"`).
   - `custom_block_drop`: ID item rơi ra khi phá khối (mặc định rơi ra chính item đặt).
   - Đã gỡ bỏ toàn bộ code xử lý NoteBlock cũ trong `ItemEventRouter`.
   - Toàn bộ sự kiện đặt/phá/nổ/piston hiện được điều phối tự động bởi `CustomBlockEventListener`.

---

## Global Constraints

- Mọi thay đổi mã nguồn chỉ áp dụng trong workspace `HaoHanLunar` trên branch `feat/custom-block-adapation`.
- Giữ nguyên tính toàn vẹn của logic nghiệp vụ hiện có (không làm hỏng robot, boss, đồ trang bị, oxy).
- Đảm bảo tương thích ngược: các phương thức kiểm tra quặng vẫn nhận biết được các khối NoteBlock cũ nếu server còn dữ liệu chunk cũ.
- Tương thích Paper 1.21.11 và Java 21.

---

## Review Focus

1. **Khối quặng Mặt Trăng (Ores) không rơi item khi phá**: `CustomBlockEventListener` của ItemCore tự động drop item theo `custom_block_drop`, cần đảm bảo `custom_block_drop` và `custom_block_id` được đăng ký đồng bộ.
2. **Cơ chế đào quặng (MiningMechanic) không áp dụng hiệu ứng đào**: Người chơi cầm cúp Netherite nhìn vào quặng Barrier phải được áp dụng đúng `AttributeModifier` (`slowMiningKey` / `noMiningKey`).
3. **Module Dò Quặng của Robot (RobotTask.ORE_SCAN)**: Robot phải quét được cả `Material.BARRIER` có registered ID là quặng Mặt Trăng, thay vì chỉ kiểm tra `Material.NOTE_BLOCK`.
4. **Trạm sạc pin (BatteryChargerMechanic)**: Trạm sạc cũng dùng Barrier nhưng sử dụng ItemDisplay xoay theo hướng yaw và quản lý dữ liệu riêng; cần đảm bảo không bị xung đột với `CustomBlockService`.
5. **Độ ổn định khi HaoHanItemCore chưa nạp xong hoặc CustomBlockService trả về null**: Code cần có null-check an toàn phòng ngừa crash server.

---

## Proposed Changes

### 1. Build & Dependency Layer

#### [MODIFY] [build.gradle](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/build.gradle)
- Cập nhật dependency `compileOnly 'vn.haohan:HaoHanItemCore:1.0.0'` và `testImplementation 'vn.haohan:HaoHanItemCore:1.0.0'` để sử dụng trực tiếp bản build chứa CustomBlockService từ Maven Local.

---

### 2. Custom Item & Block Definitions Layer

#### [MODIFY] [LunarItems.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/item/LunarItems.java)
- Cập nhật định nghĩa 4 loại quặng Mặt Trăng:
  - `haohan:anorthosite_ore`
  - `haohan:ilmenite_ore`
  - `haohan:pyroxene_ore`
  - `haohan:kreep_basalt`
- Bổ sung các properties:
  - `"custom_block_id"`: Đăng ký ID khối tương ứng.
  - `"custom_block_model"`: Trỏ tới chuỗi blockstate (`minecraft:note_block[note=...,instrument=pling,powered=true]`) để Virtual BlockDisplay lấy đúng texture từ Resource Pack.
  - `"custom_block_drop"`: Item quặng thô rơi ra khi khai thác.
- Đăng ký bổ sung trực tiếp với `CustomBlockRegistry` thông qua `HaoHanItemCore.get().getCustomBlockService()` nếu service khả dụng để đảm bảo tính sẵn sàng ngay lập tức.

---

### 3. Gameplay Mechanics Layer

#### [MODIFY] [MiningMechanic.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/features/MiningMechanic.java)
- Cập nhật phương thức `isLookingAtLunarOre(Player player)`:
  - Tra cứu khối mục tiêu `target`:
    - Nếu `target.getType() == Material.BARRIER`: Tra cứu `HaoHanItemCore.get().getCustomBlockService().getCustomBlockId(target)`. Nếu ID là một trong 4 loại quặng Mặt Trăng (`haohan:anorthosite_ore`, `haohan:ilmenite_ore`, `haohan:pyroxene_ore`, `haohan:kreep_basalt`), trả về `true`.
    - Fallback: Nếu `target.getType() == Material.NOTE_BLOCK` (khối cũ trong thế giới), vẫn giữ logic kiểm tra note 21-24 & instrument PLING.
- Trích xuất hằng số tập hợp `LUNAR_ORE_BLOCK_IDS = Set.of(...)` để tối ưu tra cứu O(1).

#### [MODIFY] [LunarRobotEntity.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotEntity.java)
- Cập nhật logic dò quặng trong `handleOreScanTask()`:
  - Khi quét khối trong bán kính xung quanh:
    - Nếu `block.getType() == Material.BARRIER`: Kiểm tra thông qua `CustomBlockService`.
    - Nếu là `haohan:pyroxene_ore` hoặc `haohan:kreep_basalt`: Xem như quặng hiếm (`nearestRareOre`), kích hoạt hiệu ứng beacon và tạo hologram.
    - Nếu là `haohan:anorthosite_ore` hoặc `haohan:ilmenite_ore`: Xem như quặng phổ thông (`nearestCommonOre`).
- Cập nhật helper `isCommonOre(Material mat)` và `isRareOre(Material mat)`.

---

### 4. Unit Testing Layer

#### [NEW] [LunarCustomBlockAdaptationTest.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/test/java/vn/haohan/lunar/block/LunarCustomBlockAdaptationTest.java)
- Viết unit test kiểm tra:
  1. Các ItemDefinition quặng Mặt Trăng chứa đầy đủ các thuộc tính `custom_block_id`, `custom_block_model`, `custom_block_drop`.
  2. Logic nhận diện quặng Mặt Trăng trong `MiningMechanic` đối với khối Barrier có CustomBlock ID hợp lệ, cũng như fallback với NoteBlock.
  3. Logic nhận diện quặng Mặt Trăng trong Robot 4 Chân đối với các khối CustomBlock.

---

## Bite-Sized Implementation Tasks

### Task 1: Cập nhật cấu hình phụ thuộc `build.gradle` của HaoHanLunar
**Files:**
- Modify: `HaoHanLunar/build.gradle:25-50`

- [ ] **Step 1: Cập nhật dependency trong `build.gradle`**
  Thay thế `com.github.Hao-Han-SMP:HaoHanItemCore:main-SNAPSHOT` bằng `vn.haohan:HaoHanItemCore:1.0.0` và thêm vào `testImplementation`.
- [ ] **Step 2: Chạy kiểm tra biên dịch**
  Chạy: `./gradlew compileJava compileTestJava`
  Kỳ vọng: BUILD SUCCESSFUL
- [ ] **Step 3: Commit**
  `git commit -m "build(deps): update HaoHanItemCore to local version 1.0.0 with custom block API"`

---

### Task 2: Thích ứng định nghĩa Item & Block Ores trong `LunarItems`
**Files:**
- Modify: `HaoHanLunar/src/main/java/vn/haohan/lunar/item/LunarItems.java:153-205`
- Test: `HaoHanLunar/src/test/java/vn/haohan/lunar/block/LunarCustomBlockAdaptationTest.java`

- [ ] **Step 1: Viết test kiểm tra thuộc tính custom block của các quặng**
  Kiểm tra các ItemDefinition `haohan:anorthosite_ore`, `haohan:ilmenite_ore`, `haohan:pyroxene_ore`, `haohan:kreep_basalt` có `custom_block_id`, `custom_block_model`, `custom_block_drop`.
- [ ] **Step 2: Chạy test để xác nhận FAIL**
  Chạy: `./gradlew test --tests vn.haohan.lunar.block.LunarCustomBlockAdaptationTest`
- [ ] **Step 3: Cập nhật `LunarItems.java`**
  Thêm `custom_block_id` và `custom_block_model` cho 4 loại quặng Mặt Trăng.
- [ ] **Step 4: Chạy test để xác nhận PASS**
  Chạy: `./gradlew test --tests vn.haohan.lunar.block.LunarCustomBlockAdaptationTest`
- [ ] **Step 5: Commit**
  `git commit -m "feat(items): adapt lunar ores to HaoHanItemCore custom block properties"`

---

### Task 3: Thích ứng cơ chế đào quặng trong `MiningMechanic`
**Files:**
- Modify: `HaoHanLunar/src/main/java/vn/haohan/lunar/features/MiningMechanic.java:68-80`
- Test: `HaoHanLunar/src/test/java/vn/haohan/lunar/block/LunarCustomBlockAdaptationTest.java`

- [ ] **Step 1: Viết test cho `isLookingAtLunarOre` với Custom Block Barrier**
- [ ] **Step 2: Chạy test để xác nhận FAIL**
- [ ] **Step 3: Cập nhật `MiningMechanic.java` hỗ trợ kiểm tra qua `CustomBlockService`**
  Thêm kiểm tra `Material.BARRIER` + `CustomBlockService.getCustomBlockId(target)` thuộc `LUNAR_ORE_BLOCK_IDS`, giữ nguyên fallback NoteBlock.
- [ ] **Step 4: Chạy test để xác nhận PASS**
- [ ] **Step 5: Commit**
  `git commit -m "feat(mining): support custom block barrier detection in MiningMechanic"`

---

### Task 4: Thích ứng module Dò Quặng của Robot 4 Chân trong `LunarRobotEntity`
**Files:**
- Modify: `HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotEntity.java:706-728, 1400-1406`
- Test: `HaoHanLunar/src/test/java/vn/haohan/lunar/block/LunarCustomBlockAdaptationTest.java`

- [ ] **Step 1: Viết test cho việc dò quặng Barrier của Robot**
- [ ] **Step 2: Chạy test để xác nhận FAIL**
- [ ] **Step 3: Cập nhật `handleOreScanTask` trong `LunarRobotEntity.java`**
  Thêm kiểm tra `Material.BARRIER` + `CustomBlockService` để nhận biết quặng Mặt Trăng phổ thông và quặng hiếm.
- [ ] **Step 4: Chạy test để xác nhận PASS**
- [ ] **Step 5: Commit**
  `git commit -m "feat(robot): detect barrier-based custom ores in ore scan module"`

---

### Task 5: Kiểm tra tổng thể & Xác minh tương thích
**Files:**
- All modified files

- [ ] **Step 1: Chạy toàn bộ test suite của HaoHanLunar**
  Chạy: `./gradlew test`
  Kỳ vọng: Toàn bộ bài test đều PASS.
- [ ] **Step 2: Chạy validate nội dung schema (ContentLintTool)**
  Chạy: `./gradlew validateContent`
  Kỳ vọng: PASS không có lỗi schema.
- [ ] **Step 3: Commit hoàn thiện & Tổng kết**
  `git commit -m "chore(block): complete custom block adaptation for HaoHanLunar"`

---

## Verification Plan

### Automated Tests
- `./gradlew test` (Chạy toàn bộ unit test bao gồm các test mới về custom block)
- `./gradlew validateContent` (Kiểm tra linting content schemas)

### Manual Verification
- Đặt quặng Mặt Trăng trong game: xác nhận khối vật lý là Barrier, client hiển thị model quặng mượt mà không rung lắc (anti z-fighting).
- Cầm cúp Netherite đào quặng Mặt Trăng: hiệu ứng `slowMining` được áp dụng chính xác; khi vỡ khối, rơi ra item quặng thô.
- Kích hoạt Robot 4 Chân ở chế độ `RobotTask.ORE_SCAN`: Robot phát xung radar và báo phát hiện quặng chính xác khi có quặng Mặt Trăng dạng Barrier xung quanh.
