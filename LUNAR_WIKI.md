# 🌙 LUNAR DIMENSION WIKI (`haohan:lunar`)

Wiki chi tiết về các vật phẩm đặc biệt (custom items) và cơ chế (mechanics) trên chiều không gian Mặt Trăng (Lunar).

---

## II. CƠ CHẾ VẬT LÝ & TRỌNG LỰC (PHYSICS & GRAVITY)
Mặt Trăng mô phỏng môi trường lực hấp dẫn cực kỳ yếu (Low-Gravity) thông qua hai hệ thống kiểm soát:

*   **Vòng lặp vật lý:** [physic.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/physic/physic.mcfunction)

### 1. Trọng lực thực thể sống (Players & Mobs)
Khi bất kỳ thực thể sống nào bước vào Mặt Trăng, các thuộc tính chuyển động sẽ thay đổi:
*   **Áp dụng thuộc tính:** [apply_attributes.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/attribute/apply_attributes.mcfunction)
    *   **Trọng lực thực thể (`minecraft:gravity`):** Đặt thành **`0.013256`** (bằng **16.57%** trọng lực thông thường `0.08`, tương đương tỉ lệ trọng lực Mặt Trăng thực tế).
    *   **Độ cao rơi an toàn (`minecraft:safe_fall_distance`):** Tăng lên **`18` blocks** (mặc định là 3).
    *   **Hệ số sát thương rơi (`minecraft:fall_damage_multiplier`):** Giảm còn **`0.2`** (chỉ nhận 20% sát thương rơi).
    *   **Lực đẩy lùi (`minecraft:attack_knockback`):** Tăng lên **`0.75`** (mục tiêu bị đẩy bay xa hơn do trọng lực yếu).
    *   **Tốc độ đập block (`minecraft:block_break_speed`):** Giảm còn **`0.8`** (đào chậm hơn 20% do thiếu lực tì trong môi trường phi trọng lực).
*   **Khôi phục thuộc tính:** [reset_attributes.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/attribute/reset_attributes.mcfunction) - Khi rời khỏi Mặt Trăng, tất cả các thuộc tính trên sẽ được trả về mặc định của Vanilla và xóa tag `hh_lunar_physic`.

### 2. Trọng lực vật thể rơi (Items & Falling Blocks)
Để các vật phẩm rơi tự do và các block chịu trọng lực (cát, sỏi...) rơi chậm tương tự thực thể sống:
*   **Nhóm thực thể chịu ảnh hưởng:** [lunar_gravity.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/tags/entity_type/lunar_gravity.json) (chỉ áp dụng cho `minecraft:item` và `minecraft:falling_block`).
*   **Tính toán trọng lực rơi:** [apply_falling_gravity.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/physic/apply_falling_gravity.mcfunction)
    *   Mỗi tick, nếu vật thể không chạm đất (`OnGround: 0b`), hệ thống sẽ cộng thêm **`0.033372`** vào chuyển động Y để giảm bớt lực rơi tự do do game mặc định, đưa lực rơi thực tế về đúng tỉ lệ **16.57%**.

---

## III. HỆ THỐNG OXY & KHÔNG KHÍ (OXYGEN SYSTEM)
Người chơi không thể thở tự nhiên trên Mặt Trăng và cần hệ thống cung cấp Oxy dự phòng.

*   **Vòng lặp kiểm soát Oxy:** [oxygen.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/environment/oxygen.mcfunction)

### 1. Chỉ số Oxy cơ bản (Base Oxygen)
*   Khi vào Mặt Trăng, người chơi nhận tag `hh_lunar_oxygen` và được cấp **600 đơn vị Oxy** (tương đương 10 bong bóng trên giao diện Actionbar, thở được 30 giây).
*   Lượng Oxy này giảm 1 đơn vị mỗi tick trừ khi người chơi:
    *   Đang đứng trong trạm an toàn.
    *   Có một bình Oxy dự phòng đang được kích hoạt hoạt động.
*   **Sát thương ngạt thở:** Khi Oxy về `<=` `0`, người chơi nhận **1 HP (nửa tim) sát thương ngạt nước (`minecraft:drown`)** mỗi **20 ticks (1 giây)**.

### 2. Trạm an toàn phục hồi Oxy (Safe Regen Areas)
Khi ở trong phạm vi cấu trúc an toàn, người chơi tự động phục hồi Oxy và không mất Oxy từ bình:
*   **Trạm dừng chân (Rest Base):** [regen_rest_base.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/environment/regen_rest_base.mcfunction) (hồi **+100 Oxy** mỗi 3 giây).
    *   *Điều kiện kiểm tra:* [in_rest_base.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/predicate/in_rest_base.json)
*   **Trạm vũ trụ (Space Station):** [regen_space_station.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/environment/regen_space_station.mcfunction) (hồi **+150 Oxy** mỗi 2 giây).
    *   *Điều kiện kiểm tra:* [in_space_station.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/predicate/in_space_station.json)
*   *Lưu ý:* Khi vào vùng an toàn, bình Oxy đang đeo sẽ tạm thời tự động tắt (ngừng tiêu hao) bằng cách đặt trạng thái active về 0.
*   **Khôi phục trạng thái khi ra khỏi Mặt Trăng:** [reset_oxygen.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/environment/reset_oxygen.mcfunction)

### 3. Hiển thị Oxy trên Actionbar
*   **Bộ phân phối giao diện:** [oxygen_display.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/environment/oxygen_display.mcfunction) (phát âm thanh `minecraft:block.bubble_column.bubble_pop` mỗi khi mất 1 bong bóng Oxy).
*   **Giao diện thường:** [oxygen_display_normal.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/environment/oxygen_display_normal.mcfunction) - Hiển thị 10 bong bóng xanh lam `●` và xám `○`.
*   **Giao diện khi đeo bình Oxy:** [oxygen_display_tank.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/environment/oxygen_display_tank.mcfunction) - Hiển thị bong bóng kèm theo biểu tượng pin `🔋` và số phần trăm Oxy còn lại của bình (Màu xanh lá khi > 50%, màu vàng từ 26% - 50%, màu đỏ khi <= 25%).

### 4. Cơ chế sạc lại bình Oxy (Charging)
Nếu người chơi đứng trong vùng an toàn (Rest Base/Space Station) và cầm bình Oxy đã sử dụng (bị hỏng độ bền) trên tay chính:
*   **Tính toán sạc:** [tank_charge.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/environment/tank_charge.mcfunction)
    *   **Bình nhỏ:** Nạp đầy trong **5 giây** (100 ticks). Hiển thị: `⚡ Đang nạp bình oxy nhỏ... X%`.
    *   **Bình vừa:** Nạp đầy trong **10 giây** (200 ticks). Hiển thị: `⚡ Đang nạp bình oxy vừa... X%`.
    *   **Bình lớn:** Nạp đầy trong **16 giây** (320 ticks). Hiển thị: `⚡ Đang nạp bình oxy lớn... X%`.
*   **Item Modifier nạp đầy:** [repair_tank.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/item_modifier/repair_tank.json) (đặt thuộc tính `"minecraft:damage": 0` để sửa đầy bình).
*   Khi nạp xong, phát âm thanh `minecraft:block.beacon.power_select` và hiển thị chữ: `🔋 Bình oxy đã được nạp xong!`.

---

## IV. CÁC CUSTOM ITEM (VẬT PHẨM ĐẶC BIỆT)

### 1. Bộ trang phục phi hành gia (Spacesuit)
*   **Vật phẩm:**
    *   Mũ: [helmet.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/spacesuit/helmet.json)
    *   Giáp ngực: [chestplate.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/spacesuit/chestplate.json)
    *   Quần: [leggings.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/spacesuit/leggings.json)
    *   Ủng: [boots.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/spacesuit/boots.json)
*   **Tính năng:** Chế tạo từ phôi Netherite với model hiển thị đặc thù.
*   **Điều kiện bắt buộc:** Người chơi **phải mặc đủ cả 4 món** trên người mới có thể sử dụng bình Oxy.
    *   *Bộ kiểm tra điều kiện mặc giáp:* [wears_spacesuit.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/predicate/wears_spacesuit.json)

### 2. Các loại bình Oxy dự phòng (Oxygen Tanks)
Cho phép người chơi di chuyển tự do bên ngoài vùng an toàn mà không bị ngạt thở.
*   **Đo lường tiêu hao bình Oxy:** [oxygen_tank.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/item/oxygen_tank/oxygen_tank.mcfunction) (tiêu thụ 1 đơn vị điện tích bình mỗi tick để tiếp Oxy vào cơ thể).
*   **Rút Oxy từ bình vào người chơi:** [drain.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/item/oxygen_tank/drain.mcfunction)
*   **Cơ chế truyền Oxy:** [refill_transfer.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/item/oxygen_tank/refill_transfer.mcfunction) (giới hạn chuyển tối đa 10 Oxy/tick từ bình vào cơ thể).
*   **Kích hoạt bình (Chuột phải):** [activate.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/item/oxygen_tank/activate.mcfunction)
    *   *Hoạt động:* Người chơi phải mặc đủ Spacesuit, đứng bên ngoài vùng an toàn trên Mặt Trăng. Nhấn chuột phải sẽ rút toàn bộ Oxy trong bình chứa ở tay chính nạp vào người, đồng thời chuyển trạng thái bình trên tay thành trống rỗng (độ bền giảm tối đa).
    *   *Giới hạn:* Không thể kích hoạt nếu bình còn dưới 15% dung lượng (Small còn dưới 226, Medium dưới 451, Large dưới 1021).
*   **Vật phẩm bình Oxy:**
    *   **Bình Oxy Nhỏ (Dung tích 1500):** [oxygen_tank_small.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/items/oxygen_tank_small.json)
    *   **Bình Oxy Vừa (Dung tích 3000):** [oxygen_tank_medium.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/items/oxygen_tank_medium.json)
    *   **Bình Oxy Lớn (Dung tích 6800):** [oxygen_tank_large.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/items/oxygen_tank_large.json)

### 3. Nguyên liệu đặc thù Mặt Trăng
Nguyên liệu dùng trong chế tạo các máy móc hoặc giáp phi hành gia.
*   **Aero Compound:** [aero_compound.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/items/aero_compound.json) - Tạo trên nền `minecraft:knowledge_book` với model `haohan:aero_compound`.
*   **Steel Ingot (Phôi Thép):** [steel_ingot.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/items/steel_ingot.json) - Tạo trên nền `minecraft:knowledge_book` với model `haohan:steel_ingot`.

### 4. Đĩa nhạc custom HaoHanSMP
*   **Vật phẩm:** [i_really_want_to_stay_at_your_house.json (Loot Table)](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/music_disc/i_really_want_to_stay_at_your_house.json)
*   **Cấu hình bài hát:** [i_really_want_to_stay_at_your_house.json (Jukebox Song)](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/jukebox_song/i_really_want_to_stay_at_your_house.json)
    *   *Tên đĩa nhạc:* Đĩa nhạc HaoHanSMP (Màu vàng gold)
    *   *Bài hát:* Lunity - I Really Want to Stay at Your House (Acoustic Cover)

---

## V. CƠ CHẾ ĐÀO BLOCK CUSTOM (MINING MECHANICS)

### 1. Quặng Anorthosite Ore
*   **Vật phẩm & cấu trúc block:** [anorthosite_ore.json](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/loot_table/blocks/anorthosite_ore.json)
    *   *Mô tả:* Khối quặng đặc trưng của Mặt Trăng, được tạo hình từ Note Block có block state `note=24`, `instrument=pling`, `powered=true`.

### 2. Ràng buộc khai thác (Mining Speed Penalty)
Do cấu trúc quặng Mặt Trăng cực kỳ cứng, tốc độ phá khối bị giới hạn nghiêm ngặt:
*   **Vòng lặp giám sát đào quặng:** [mining_tick.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/block/anorthosite_ore/mining_tick.mcfunction)
*   **Kiểm tra hướng nhìn người chơi:** [check_mining.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/block/anorthosite_ore/check_mining.mcfunction)
*   **Raycast dò quặng:** [raycast.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/block/anorthosite_ore/raycast.mcfunction) (dò tối đa 5 blocks trước mắt người chơi).
*   **Tốc độ khai thác tùy chỉnh:**
    *   **Đào không có Cúp Netherite:** [apply_no_mining.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/block/anorthosite_ore/apply_no_mining.mcfunction) (giảm tốc độ đi `-100%` qua modifier `haohan:no_mining` khiến block **hoàn toàn unbreakable**).
    *   **Đào bằng Cúp Netherite:** [apply_slow_mining.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/block/anorthosite_ore/apply_slow_mining.mcfunction) (giảm tốc độ đi `-97.4%` qua modifier `haohan:slow_mining`, giúp người chơi có thể phá block nhưng rất chậm).
    *   **Reset tốc độ khi không nhìn quặng:** [reset_mining_attributes.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/block/anorthosite_ore/reset_mining_attributes.mcfunction) (gỡ bỏ các modifier làm chậm đào block).

---

## VI. HIỆU ỨNG HÌNH ẢNH & BIOME (VISUAL EFFECTS)

### 1. Hiệu ứng tiêu đề khi đặt chân lên Mặt Trăng
*   **Kiểm tra và kích hoạt:** [title.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/effect/title.mcfunction)
*   **Hiện Title:** [show_title.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/effect/show_title.mcfunction) (hiển thị Title lớn `🌙` và Subtitle `ᴍặᴛ ᴛʀăɴɢ`, kèm âm thanh tiếng sấm trident gầm rú).

### 2. Các hạt hiệu ứng lơ lửng theo từng Biome
Datapack chạy vòng lặp phát hạt bụi lơ lửng tăng trải nghiệm không gian:
*   **Đo lường thời gian chạy hạt:** [particle_effect.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/effect/particle_effect.mcfunction) (chạy 8 ticks một lần).
*   **Tạo hạt cụ thể theo Biome:** [particle_player.mcfunction](file:///home/paithon/Projects/HaoHanSMP/HaoHanSMP-datapack/data/haohan/function/lunar/effect/particle_player.mcfunction)
    *   **Lunar Terrae:** Spawns hạt `minecraft:firefly`.
    *   **Lunar Maria:** Spawns hạt `minecraft:dust` màu đen.
    *   **Lunar Craters:** Spawns hạt `minecraft:glow` màu vàng.
    *   **Lunar Crystal Craters:** Spawns hạt `minecraft:dust` màu tím thạch anh và trắng.
    *   **Lunar Giant Crystals:** Spawns hạt `minecraft:dust` màu tím đậm, hồng magenta và trắng.
    *   **Lunar Giant Crystal Outskirts:** Spawns hạt `minecraft:dust` màu xanh dương nhạt, xanh cyan và trắng.
