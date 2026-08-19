<div align="center">

# HaoHanLunar

Plugin cơ chế chiều không gian Mặt Trăng và Custom Item cho Minecraft Paper Server, tích hợp với HaoHanItemCore API và HaoHanSMP Datapack.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)

Ngôn ngữ: Tiếng Việt

</div>

## Tổng quan

`HaoHanLunar` là plugin Minecraft dành cho hệ thống HaoHan SMP. Plugin mô phỏng trọn vẹn môi trường chiều không gian Mặt Trăng (`haohan:lunar`) với các cơ chế vật lý trọng lực thấp, hệ thống Oxy, khai thác quặng Anorthosite và hiệu ứng hình ảnh sinh động. Plugin hoạt động dựa trên infrastructure của `HaoHanItemCore` để quản lý custom item/recipe và kết hợp với `HaoHanSMP-datapack` để định nghĩa chiều không gian và sinh cấu trúc thế giới.

### Mục tiêu chính

- Mô phỏng môi trường trọng lực thấp (Low-Gravity) chính xác theo tỉ lệ thực tế Mặt Trăng (~16.57%).
- Hệ thống Oxy hoàn chỉnh: chỉ số Oxy cơ bản, bình Oxy dự phòng, trạm an toàn và cơ chế sạc bình theo thời gian thực.
- Tích hợp đăng ký Custom Item đặc trưng Mặt Trăng (Spacesuit, Oxygen Tanks, Raw Anorthosite, Aero Compound) qua `HaoHanItemCore` API.
- Cơ chế khai thác quặng `Anorthosite Ore` giới hạn công cụ đào (chỉ Cúp Netherite mới có thể đào).
- Kết hợp với Datapack server để nhận diện vị trí trạm an toàn (`haohan:rest_base`, `haohan:space_station`) và sinh địa hình Mặt Trăng.
- Beacon đặt trong chiều `haohan:lunar` sẽ mở rộng nhanh thành vùng bảo vệ năng lượng nhiều lớp; người chơi trong vùng được duy trì Oxy.

## Công nghệ sử dụng

| Toolkit | Vai trò |
| --- | --- |
| Paper API (1.21.11) | Nền tảng API chính để phát triển Paper plugin (sử dụng format `paper-plugin.yml`). |
| Java 21 | Ngôn ngữ và runtime chính của plugin. |
| Maven | Quản lý dependency và build file `.jar`. |
| HaoHanItemCore API | Đăng ký và quản lý tập trung toàn bộ custom item/recipe của Mặt Trăng qua `ItemDefinition` API. |
| HaoHanSMP Datapack | Định nghĩa chiều không gian `haohan:lunar`, cấu trúc trạm dừng chân và dữ liệu sinh địa hình Mặt Trăng. |
| Bukkit Attribute API | Áp dụng modifier trọng lực, tốc độ rơi và tốc độ đào block theo thời gian thực. |
| Adventure API | Hiển thị Actionbar chỉ số Oxy, Title chào mừng và các thông báo trạng thái trực quan. |

## Thành phần dự án

| Thành phần | Mô tả |
| --- | --- |
| `HaoHanLunarPlugin` | Plugin chính, khởi tạo và điều phối tất cả cơ chế vật lý, sự kiện và liên kết API. |
| `GravityMechanic` | Xử lý trọng lực thấp cho người chơi, mob, item rơi và falling block khi ở trong thế giới Mặt Trăng. |
| `OxygenMechanic` | Quản lý chỉ số Oxy, kiểm tra vùng an toàn, sử dụng bình Oxy và sạc bình tự động. |
| `MiningMechanic` | Kiểm soát tốc độ đào quặng Anorthosite Ore theo công cụ sử dụng (Raycast dò block). |
| `VisualMechanic` | Phát sinh hạt hiệu ứng theo Biome và hiển thị tiêu đề chào mừng khi đến Mặt Trăng. |
| `BeaconShieldMechanic` | Quản lý vùng năng lượng động quanh beacon Mặt Trăng, animation mở rộng và các lớp particle bảo vệ. |
| `LunarItems` | Đăng ký toàn bộ custom item và công thức rèn (Smithing Recipes) vào `HaoHanItemCore`. |
| `OxygenTankBehavior` | Item Behavior xử lý logic khi người chơi kích hoạt bình Oxy (chuột phải). |
| `PlayerLunarDataManager` | Quản lý dữ liệu trạng thái Oxy và tiến trình sạc bình của từng người chơi trong bộ nhớ. |

## Yêu cầu hệ thống

- Minecraft server chạy Paper hoặc Purpur **1.21.11**.
- Java **21** trở lên.
- Plugin **`HaoHanItemCore`** phải được cài đặt trong thư mục `plugins/`.
- Datapack **`HaoHanSMP-datapack`** phải được cài đặt vào thư mục `datapacks/` của thế giới chính.
- Resourcepack **`HaoHanSMP-resourcepack`** (khuyên dùng) để hiển thị custom 3D model giáp phi hành gia và item textures.

---

## Cài đặt

Quá trình cài đặt hoàn chỉnh bao gồm **2 phần**: Cài đặt Datapack cho World và cài đặt Plugin cho Server.

### Bước 1: Cài đặt Datapack (`HaoHanSMP-datapack`)

Datapack chứa định nghĩa chiều không gian `haohan:lunar`, cấu trúc nhà trạm và dữ liệu sinh khối quặng.

1. Tải hoặc sao chép thư mục/file zip `HaoHanSMP-datapack`.
2. Di chuyển Datapack vào thư mục `datapacks/` của thế giới chính trên server:
   ```text
   <server_root>/world/datapacks/HaoHanSMP-datapack/
   ```
3. Khởi động server hoặc chạy lệnh `/reload` (nếu server đang chạy) để Minecraft nhận diện Datapack.
4. Kiểm tra Datapack đã hoạt động bằng lệnh:
   ```bash
   /datapack list
   ```
   *(Đảm bảo `file/HaoHanSMP-datapack` hiển thị trong danh sách enabled).*

### Bước 2: Cài đặt Plugin (`HaoHanLunar`)

1. Đảm bảo plugin **`HaoHanItemCore`** đã được đặt vào thư mục `plugins/` và khởi động thành công.
2. Build hoặc tải file `HaoHanLunar-1.0.0.jar`.
3. Copy file `HaoHanLunar-1.0.0.jar` vào thư mục `plugins/` của server.
4. Khởi động lại server.
5. Plugin sẽ tự động đăng ký toàn bộ custom item và recipe của Mặt Trăng vào `HaoHanItemCore`.

---

## Build từ mã nguồn

Chạy lệnh Maven tại thư mục gốc của dự án:

```bash
mvn clean package
```

File `.jar` sau khi biên dịch nằm tại `target/HaoHanLunar-1.0.0.jar`.

> **Lưu ý:** Dự án phụ thuộc vào `HaoHanItemCore`. Đảm bảo bạn đã biên dịch và cài đặt `HaoHanItemCore` vào Maven local repository (`mvn install` hoặc có trong classpath build).

---

## Custom Item đã đăng ký

Tất cả item được đăng ký dưới namespace `haohan:` thông qua `HaoHanItemCore` API:

| ID Item | Tên hiển thị | Loại | Mô tả |
| --- | --- | --- | --- |
| `haohan:spacesuit_helmet` | Mũ Phi Hành Gia | `ARMOR` | Mũ phi hành gia, model 3D tùy chỉnh trên nền Netherite Helmet. |
| `haohan:spacesuit_chestplate` | Giáp Ngực Phi Hành Gia | `ARMOR` | Giáp ngực phi hành gia, model 3D tùy chỉnh trên nền Netherite Chestplate. |
| `haohan:spacesuit_leggings` | Quần Phi Hành Gia | `ARMOR` | Quần phi hành gia, model 3D tùy chỉnh trên nền Netherite Leggings. |
| `haohan:spacesuit_boots` | Ủng Phi Hành Gia | `ARMOR` | Ủng phi hành gia, model 3D tùy chỉnh trên nền Netherite Boots. |
| `haohan:oxygen_tank_small` | Bình Oxy Nhỏ | `SPECIAL` | Dung tích 1500 đơn vị. Chuột phải để bật/tắt nạp Oxy. Sạc đầy trong 5s. |
| `haohan:oxygen_tank_medium` | Bình Oxy Vừa | `SPECIAL` | Dung tích 3000 đơn vị. Chuột phải để bật/tắt nạp Oxy. Sạc đầy trong 10s. |
| `haohan:oxygen_tank_large` | Bình Oxy Lớn | `SPECIAL` | Dung tích 6800 đơn vị. Chuột phải để bật/tắt nạp Oxy. Sạc đầy trong 16s. |
| `haohan:aero_compound` | Aero Compound | `MATERIAL` | Nguyên liệu đặc thù Mặt Trăng, dùng để nâng cấp bộ trang phục Spacesuit. |
| `haohan:steel_ingot` | Steel Ingot | `MATERIAL` | Phôi Thép, nguyên liệu chế tạo trang thiết bị. |
| `haohan:raw_anorthosite` | Raw Anorthosite | `MATERIAL` | Anorthosite thô rơi ra khi đào quặng Anorthosite Ore. |
| `haohan:anorthosite_ore` | Anorthosite Ore | `SPECIAL` | Quặng đặc trưng Mặt Trăng (Note Block state `note=24, instrument=pling`). |
| `haohan:raw_ilmenite` | Raw Ilmenite | `MATERIAL` | Ilmenite thô rơi ra khi đào quặng Ilmenite Ore. |
| `haohan:ilmenite_ore` | Ilmenite Ore | `SPECIAL` | Quặng Ilmenite Mặt Trăng (Note Block state `note=23, instrument=pling`). |
| `haohan:raw_pyroxene` | Raw Pyroxene | `MATERIAL` | Pyroxene thô rơi ra khi đào quặng Pyroxene Ore. |
| `haohan:pyroxene_debris` | Pyroxene Debris | `MATERIAL` | Mảnh vỡ tinh thể Pyroxene từ tầng sâu Mặt Trăng. |
| `haohan:pyroxene_ore` | Pyroxene Ore | `SPECIAL` | Quặng Pyroxene Mặt Trăng (Note Block state `note=22, instrument=pling`). |
| `haohan:kreep_dust` | KREEP Dust | `MATERIAL` | Bụi KREEP giàu kali, nguyên tố đất hiếm và phốt pho, rơi ra khi đào quặng KREEP Basalt. |
| `haohan:kreep_basalt` | KREEP Basalt | `SPECIAL` | Đá bazan Mặt Trăng giàu khoáng vật KREEP (Note Block state `note=21, instrument=pling`). |
| `haohan:i_really_want_to_stay_at_your_house` | Đĩa nhạc HaoHanSMP | `SPECIAL` | Đĩa nhạc custom, phát bài Lunity - I Really Want to Stay at Your House. |

---

## Chi tiết các cơ chế chính

### 1. Cơ chế Trọng lực (GravityMechanic)

Khi người chơi hoặc mob bước vào chiều không gian `haohan:lunar`, các chỉ số vật lý sau được áp dụng tự động qua Bukkit Attribute API:

| Thuộc tính | Giá trị | Mô tả |
| --- | --- | --- |
| `minecraft:gravity` | `0.013256` | ~16.57% trọng lực Trái Đất (tỉ lệ chuẩn thực tế của Mặt Trăng). |
| `minecraft:safe_fall_distance` | `18` blocks | Ngưỡng rơi an toàn (tăng từ mặc định 3 blocks lên 18 blocks). |
| `minecraft:fall_damage_multiplier` | `0.2` | Giảm 80% sát thương khi ngã. |
| `minecraft:attack_knockback` | `0.75` | Lực bật lùi khi bị tấn công tăng do trọng lực yếu. |
| `minecraft:block_break_speed` | `0.8` | Tốc độ đào giảm 20% trong môi trường phi trọng lực. |

Item rơi (`minecraft:item`) và Falling Block (`minecraft:falling_block`) được tính toán lực bù trọng lực mỗi tick để rơi chậm thực tế.

### 2. Cơ chế Oxy (OxygenMechanic)

- **Oxy cơ bản**: Người chơi có tối đa **600 đơn vị Oxy** (tương đương 30 giây thở tự nhiên).
- **Tiêu hao**: Giảm `-1 đơn vị/tick` khi ở ngoài vùng an toàn và không có bình Oxy hoạt động.
- **Ngạt thở**: Khi Oxy về `0`, người chơi nhận **1 HP (0.5 tim)** sát thương `drown` mỗi giây.
- **Trạm an toàn**:
  - **Trạm dừng chân (`haohan:rest_base`)**: Phục hồi `+100 Oxy` mỗi 3 giây.
  - **Trạm vũ trụ (`haohan:space_station`)**: Phục hồi `+150 Oxy` mỗi 2 giây.
- **Sạc bình Oxy**: Khi đứng trong vùng an toàn và cầm bình Oxy trên tay chính, bình sẽ tự động nạp điện/khí:
  - **Bình nhỏ**: Nạp đầy sau 100 ticks (5 giây).
  - **Bình vừa**: Nạp đầy sau 200 ticks (10 giây).
  - **Bình lớn**: Nạp đầy sau 320 ticks (16 giây).
- **Yêu cầu bình Oxy**: Người chơi phải **mặc đủ cả 4 món Spacesuit** thì bình Oxy mới kích hoạt nạp khí ngoài không gian.

Beacon năng lượng đặt trên Mặt Trăng tạo vùng Oxy bảo vệ. Vùng bắt đầu từ beacon và mở rộng nhanh đến bán kính cấu hình trong `config.yml`:

```yaml
beacon-shield:
  radius: 48.0
  expansion-speed: 3.0
  ring-points: 72
  display-height: 4.0
```

Resourcepack dùng các ô vuông `ItemDisplay` ghép thành vòng tròn; mỗi ô chạy qua 6 frame alpha để tạo animation border. Particle được xếp thành nhiều lớp: vòng biên xanh điện, lớp hạt trong suốt chạy dọc theo thành vùng và lớp lõi xanh nhấp nháy.

### 3. Cơ chế Khai thác Quặng (MiningMechanic)

Quặng `Anorthosite Ore` được kiểm soát tốc độ đào theo công cụ:

| Điều kiện đào | Hiệu ứng tốc độ | Trạng thái |
| --- | --- | --- |
| Cầm cúp khác (Gỗ, Đá, Sắt, Vàng, Kim Cương) | `-100%` tốc độ đào | **Khối không thể phá hủy**. |
| Cầm Cúp Netherite | `-97.4%` tốc độ đào | Cho phép đào nhưng tốc độ chậm. |
| Không nhìn vào Anorthosite Ore | Tháo bỏ modifier | Tốc độ đào trở lại bình thường. |

### 4. Cơ chế Hiệu ứng Visual (VisualMechanic)

Phát sinh các hạt hiệu ứng (Particle) tùy theo Biome Mặt Trăng:
- **Lunar Terrae**: `firefly`
- **Lunar Maria**: `dust` màu đen
- **Lunar Craters**: `glow` màu vàng
- **Lunar Crystal Craters**: `dust` màu tím thạch anh & trắng
- **Lunar Giant Crystals**: `dust` màu tím đậm, hồng magenta & trắng

Lần đầu chuyển sang chiều Mặt Trăng, người chơi sẽ nhận Title màn hình `🌙 ᴍặᴛ ᴛʀăɴɢ` kèm hiệu ứng âm thanh đặc trưng.

---

## Tích hợp Datapack & Dimension

Plugin liên kết chặt chẽ với **`HaoHanSMP-datapack`**:
- **Dimension Key**: `haohan:lunar`
- **Cấu trúc trạm an toàn**: `haohan:rest_base` và `haohan:space_station` (được nhận diện qua `Structure` Bounding Box của Minecraft).
- **Anorthosite Ore**: Khối `NOTE_BLOCK` với block state `note=24, instrument=pling, powered=true`.

---

## Phụ thuộc Plugin (`paper-plugin.yml`)

```yaml
name: HaoHanLunar
main: vn.haohan.lunar.HaoHanLunarPlugin
version: 1.0.0
api-version: '1.21'
dependencies:
  server:
    HaoHanItemCore:
      load: BEFORE
      required: true
```
