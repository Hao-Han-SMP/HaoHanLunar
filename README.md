<div align="center">

# HaoHanLunar

Plugin cơ chế chiều không gian Mặt Trăng và Custom Item cho Minecraft Paper Server, tích hợp với HaoHanItemManager API.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)

Ngôn ngữ: Tiếng Việt | [Wiki Hướng Dẫn Chi Tiết](LUNAR_WIKI.md)

</div>

## Tổng quan

`HaoHanLunar` là plugin Minecraft dành cho HaoHan SMP. Plugin mô phỏng môi trường chiều không gian Mặt Trăng (`haohan:lunar`) với các cơ chế vật lý, hệ thống oxy, khai thác quặng và hiệu ứng hình ảnh đặc trưng. Plugin phụ thuộc vào `HaoHanItemManager` để đăng ký và quản lý custom item của Mặt Trăng.

### Mục tiêu chính

- Mô phỏng môi trường trọng lực thấp (Low-Gravity) chính xác theo tỉ lệ thực tế Mặt Trăng (~16.57%).
- Hệ thống Oxy đầy đủ: chỉ số Oxy cơ bản, bình dự phòng, trạm an toàn và cơ chế sạc.
- Đăng ký Custom Item đặc trưng Mặt Trăng qua `HaoHanItemManager` API (Spacesuit, Oxygen Tanks, Nguyên liệu, v.v).
- Cơ chế khai thác quặng `Anorthosite Ore` với tốc độ đào tùy chỉnh theo công cụ.
- Hiệu ứng hình ảnh theo Biome (hạt lơ lửng, tiêu đề chào mừng, âm thanh).

## Công nghệ sử dụng

| Toolkit | Vai trò |
| --- | --- |
| Paper API (1.21.11) | Nền tảng API chính để phát triển Paper plugin (sử dụng format `paper-plugin.yml`). |
| Java 21 | Ngôn ngữ và runtime chính của plugin. |
| Maven | Quản lý dependency và build file `.jar`. |
| HaoHanItemManager API | Đăng ký và quản lý toàn bộ custom item của Mặt Trăng qua `ItemDefinition` API. |
| Bukkit Attribute API | Áp dụng modifier trọng lực, tốc độ rơi và tốc độ đào block theo thời gian thực. |
| Adventure API | Hiển thị Actionbar Oxy, Title chào mừng và các thông báo trạng thái. |

## Thành phần dự án

| Thành phần | Mô tả |
| --- | --- |
| `HaoHanLunarPlugin` | Plugin chính, khởi tạo và điều phối tất cả cơ chế. |
| `GravityMechanic` | Xử lý trọng lực thấp cho người chơi, mob, item rơi và falling block. |
| `OxygenMechanic` | Quản lý chỉ số Oxy, bình Oxy, vùng an toàn và cơ chế sạc bình. |
| `MiningMechanic` | Kiểm soát tốc độ đào quặng Anorthosite Ore theo công cụ sử dụng. |
| `VisualMechanic` | Phát sinh hạt hiệu ứng theo Biome và tiêu đề khi đặt chân lên Mặt Trăng. |
| `LunarItems` | Đăng ký toàn bộ custom item của Mặt Trăng vào `HaoHanItemManager`. |
| `OxygenTankBehavior` | Behavior xử lý logic kích hoạt bình Oxy (chuột phải). |
| `PlayerLunarDataManager` | Quản lý dữ liệu trạng thái Oxy của từng người chơi trong bộ nhớ. |
| `LUNAR_WIKI.md` | Wiki chi tiết về tất cả cơ chế, vật phẩm và cấu hình của Mặt Trăng. |

## Yêu cầu

- Minecraft server chạy Paper hoặc Purpur 1.21.11.
- Java 21 trở lên.
- Plugin `HaoHanItemManager` phải được cài đặt và load **trước** plugin này.
- Maven 3.9 trở lên nếu cần build từ mã nguồn.

## Cài đặt

1. Đảm bảo `HaoHanItemManager` đã được cài đặt trong thư mục `plugins/`.
2. Build hoặc tải file `HaoHanLunar-1.0.0.jar`.
3. Copy file `.jar` vào thư mục `plugins/` của server.
4. Khởi động server.
5. Plugin sẽ tự động đăng ký toàn bộ custom item của Mặt Trăng vào `HaoHanItemManager`.

## Build từ mã nguồn

Chạy lệnh sau tại thư mục gốc của dự án:

```bash
mvn clean package
```

File `.jar` sau khi build nằm trong thư mục `target/HaoHanLunar-1.0.0.jar`.

> **Lưu ý:** Cần có `HaoHanItemManager-1.0.0.jar` trong Maven local repository (chạy `mvn install` ở dự án đó trước).

## Custom Item đã đăng ký

Tất cả item được đăng ký dưới namespace `haohan:`.

| ID Item | Tên | Loại | Mô tả |
| --- | --- | --- | --- |
| `haohan:spacesuit_helmet` | Spacesuit Helmet | `ARMOR` | Mũ phi hành gia, model tùy chỉnh trên nền Netherite Helmet. |
| `haohan:spacesuit_chestplate` | Spacesuit Chestplate | `ARMOR` | Giáp ngực phi hành gia, model tùy chỉnh trên nền Netherite Chestplate. |
| `haohan:spacesuit_leggings` | Spacesuit Leggings | `ARMOR` | Quần phi hành gia, model tùy chỉnh trên nền Netherite Leggings. |
| `haohan:spacesuit_boots` | Spacesuit Boots | `ARMOR` | Ủng phi hành gia, model tùy chỉnh trên nền Netherite Boots. |
| `haohan:oxygen_tank_small` | Bình Oxy Nhỏ | `SPECIAL` | Dung tích 1500 đơn vị. Chuột phải để kích hoạt nạp Oxy. |
| `haohan:oxygen_tank_medium` | Bình Oxy Vừa | `SPECIAL` | Dung tích 3000 đơn vị. Chuột phải để kích hoạt nạp Oxy. |
| `haohan:oxygen_tank_large` | Bình Oxy Lớn | `SPECIAL` | Dung tích 6800 đơn vị. Chuột phải để kích hoạt nạp Oxy. |
| `haohan:aero_compound` | Aero Compound | `MATERIAL` | Nguyên liệu đặc thù Mặt Trăng, dùng để chế tạo. |
| `haohan:steel_ingot` | Steel Ingot | `MATERIAL` | Phôi Thép, nguyên liệu chế tạo giáp phi hành gia. |
| `haohan:i_really_want_to_stay_at_your_house` | Đĩa nhạc HaoHanSMP | `SPECIAL` | Đĩa nhạc vàng, phát bài Lunity - I Really Want to Stay at Your House. |
| `haohan:anorthosite_ore` | Anorthosite Ore | `SPECIAL` | Quặng đặc trưng Mặt Trăng, dựa trên Note Block custom block state. |

## Cơ chế Trọng lực (GravityMechanic)

Khi người chơi hoặc mob bước vào chiều Mặt Trăng, các thuộc tính sau được áp dụng:

| Thuộc tính | Giá trị | Mô tả |
| --- | --- | --- |
| `minecraft:gravity` | `0.013256` | ~16.57% trọng lực thông thường (tỉ lệ thực tế Mặt Trăng). |
| `minecraft:safe_fall_distance` | `18` blocks | Tăng từ mặc định 3 blocks. |
| `minecraft:fall_damage_multiplier` | `0.2` | Chỉ nhận 20% sát thương rơi. |
| `minecraft:attack_knockback` | `0.75` | Knockback mạnh hơn do trọng lực yếu. |
| `minecraft:block_break_speed` | `0.8` | Đào chậm hơn 20% trong môi trường phi trọng lực. |

Item rơi (`minecraft:item`) và Falling Block (`minecraft:falling_block`) cũng được áp dụng lực bù trọng lực mỗi tick để đạt tỉ lệ rơi chính xác.

## Cơ chế Oxy (OxygenMechanic)

| Thành phần | Chi tiết |
| --- | --- |
| Oxy cơ bản | 600 đơn vị khi vào Mặt Trăng (tương đương 30 giây). |
| Tiêu hao | -1 đơn vị/tick khi không ở vùng an toàn và không có bình đang hoạt động. |
| Ngạt thở | Khi Oxy ≤ 0, nhận 1 HP sát thương `drown` mỗi giây. |
| Trạm dừng chân | Phục hồi +100 Oxy mỗi 3 giây. Bình tự động tắt khi trong vùng. |
| Trạm vũ trụ | Phục hồi +150 Oxy mỗi 2 giây. Bình tự động tắt khi trong vùng. |
| Sạc bình nhỏ | Nạp đầy trong **5 giây** khi đứng trong vùng an toàn. |
| Sạc bình vừa | Nạp đầy trong **10 giây** khi đứng trong vùng an toàn. |
| Sạc bình lớn | Nạp đầy trong **16 giây** khi đứng trong vùng an toàn. |
| Hiển thị | Actionbar hiển thị 10 bong bóng `●/○`; khi có bình hiển thị thêm `🔋 X%`. |

**Yêu cầu sử dụng bình Oxy:** Người chơi phải mặc đủ cả 4 món Spacesuit và đứng bên ngoài vùng an toàn.

## Cơ chế Khai thác (MiningMechanic)

Quặng `Anorthosite Ore` có cơ chế tốc độ đào riêng:

| Trường hợp | Hiệu ứng |
| --- | --- |
| Không dùng Cúp Netherite | Block **hoàn toàn không thể phá** (giảm `-100%` tốc độ đào). |
| Dùng Cúp Netherite | Có thể phá nhưng rất chậm (giảm `-97.4%` tốc độ đào). |
| Không nhìn vào quặng | Modifier bị gỡ bỏ, tốc độ về bình thường. |

Hệ thống sử dụng Raycast dò tối đa **5 blocks** phía trước mặt người chơi mỗi tick.

## Cơ chế Hiệu ứng (VisualMechanic)

| Biome | Hạt hiệu ứng |
| --- | --- |
| Lunar Terrae | `minecraft:firefly` |
| Lunar Maria | `minecraft:dust` màu đen |
| Lunar Craters | `minecraft:glow` màu vàng |
| Lunar Crystal Craters | `minecraft:dust` màu tím thạch anh và trắng |
| Lunar Giant Crystals | `minecraft:dust` màu tím đậm, hồng magenta và trắng |
| Lunar Giant Crystal Outskirts | `minecraft:dust` màu xanh dương nhạt, xanh cyan và trắng |

Khi lần đầu đặt chân lên Mặt Trăng, người chơi nhận Title `🌙` và Subtitle `ᴍặᴛ ᴛʀăɴɢ` kèm âm thanh trident.

## Phụ thuộc Plugin (paper-plugin.yml)

```yaml
dependencies:
  server:
    HaoHanItemManager:
      load: BEFORE
      required: true
```

Chi tiết đầy đủ về tất cả cơ chế, vật phẩm và cấu hình có tại **[LUNAR_WIKI.md](LUNAR_WIKI.md)**.
