# Spec Thiết Kế: Tính Năng Ngắm Mặt Trăng & Thành Tựu Lunar

## 1. Mục tiêu & Yêu cầu
Triển khai tính năng theo dõi góc nhìn của người chơi hướng về phía Mặt Trăng khi ở Overworld ban đêm. Tích hợp trao 2 thành tựu tùy chỉnh trong Datapack tích hợp (`src/main/resources/datapack`):
1. **Ngắm nhìn chú cuội** (`look_at_moon_cuoi`):
   - Loại: Thành tựu nhẹ (`task` frame).
   - Điều kiện: Đứng yên (vị trí tọa độ x, y, z không dịch chuyển đáng kể, sai số < 0.1 block) và nhìn thẳng về phía Mặt Trăng trong tối thiểu 10 giây (200 ticks liên tục).
2. **Cái đó là gì vậy?** (`look_at_moon_spyglass`):
   - Loại: Thành tựu lớn (`challenge` / `goal` frame).
   - Điều kiện: Sử dụng ống nhòm vanilla (`minecraft:spyglass`), đang zoom ngắm và hướng tâm ngắm về phía Mặt Trăng trong tối thiểu 0.75 giây (15 ticks liên tục).

## 2. Phạm vi & Ràng buộc (Constraints)
- **Thế giới**: Chỉ kích hoạt ở Overworld (`world.getEnvironment() == World.Environment.NORMAL` và không phải dimension phụ/lunar).
- **Thời gian**: Ban đêm khi mặt trăng xuất hiện trên đường chân trời (khoảng tick 12500 đến 23500 trong chu kỳ 24000 ticks của Minecraft).
- **Tầm nhìn (Line of Sight & Weather)**:
  - Trời không có mưa hoặc bão lớn (`!world.hasStorm()`).
  - Không bị mái nhà, hang động hay khối block cản trở đường ngắm lên bầu trời (`world.getHighestBlockYAt(player.getLocation()) <= player.getEyeLocation().getY()`).
- **Góc ngắm (Direction to Moon)**:
  - Trong Minecraft, Mặt Trăng và Mặt Trời chuyển động theo trục Đông - Tây (East-West axis).
  - Vị trí góc của Mặt Trăng trên bầu trời được tính chính xác từ `world.getTime()`:
    - Thiên đỉnh mặt trăng lúc nửa đêm (~18000 tick, góc chiếu đỉnh đầu pitch ~ -90 độ).
    - Lúc mọc (Đông ~12000-13000 tick) và lặn (Tây ~23000-24000 tick).
  - Vector hướng tới mặt trăng: góc lệch giữa `player.getEyeLocation().getDirection()` và `moonDirectionVector` có góc $\theta \le \theta_{threshold}$ (ngưỡng chấp nhận khoảng ~12 - 15 độ đối với mắt thường, và ~6 - 8 độ đối với spyglass).
- **Kính ngắm**: Sử dụng đúng `minecraft:spyglass` theo câu trả lời của user.
- **Tiêu chí Ponytail (Lazy & Tối Giản)**:
  - Tận dụng `runTaskTimer` lặp định kỳ (ví dụ mỗi 5 ticks = 0.25s) để kiểm tra các người chơi online ở Overworld.
  - Sử dụng tiêu chí trigger advancement dạng `minecraft:impossible` trong file JSON advancement, plugin sẽ gọi `player.getAdvancementProgress(advancement).awardCriteria("impossible")` khi thỏa mãn. Cách này đơn giản, chuẩn Paper API, không cần viết trigger nbt/predicate phức tạp trong datapack.

## 3. Kiến trúc Datapack & Plugin
### 3.1. Datapack
Tạo 2 file advancement trong thư mục `src/main/resources/datapack/data/haohan/advancement/lunar/`:
- `moon_cuoi.json`:
  - Parent: `haohan:lunar/root`
  - Title: Ngắm nhìn chú cuội
  - Frame: `task`
  - Criteria: `impossible` (trigger `minecraft:impossible`)
- `moon_what_is_that.json`:
  - Parent: `haohan:lunar/moon_cuoi`
  - Title: Cái đó là gì vậy?
  - Frame: `challenge`
  - Criteria: `impossible` (trigger `minecraft:impossible`)

### 3.2. Plugin
- Thêm `MoonGazingMechanic` implement `Listener, IService`:
  - Đăng ký vào hệ thống Service của plugin (`HaoHanLunarPlugin`).
  - Quản lý bộ đếm thời gian nhìn mặt trăng cho từng người chơi:
    - `Map<UUID, Integer> stillLookingTicks`
    - `Map<UUID, Integer> spyglassLookingTicks`
    - `Map<UUID, Location> lastLocations`
  - Tự động huỷ hoặc xóa khi người chơi offline / đổi thế giới / di chuyển.
- Unit test với JUnit 5 & MockBukkit kiểm tra tính toán góc nhìn mặt trăng và logic đếm thời gian.

## 4. Kế hoạch xác thực (Verification)
- Chạy unit tests: `./gradlew test`.
- Kiểm tra tính hợp lệ của cú pháp JSON Datapack.
