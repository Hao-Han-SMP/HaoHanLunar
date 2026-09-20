# HaoHanLunar — Plan xây dựng hệ thống Mob/Skill theo hướng MythicMobs

## 1. Phạm vi và nguyên tắc

Mục tiêu là xây một engine cấu hình bằng YAML cho mob, skill, targeter, condition, loot và spawner phục vụ nội dung Mặt Trăng. Thiết kế lấy cảm hứng từ MythicMobs 5.13.0 nhưng không sao chép mã nguồn, không phụ thuộc MythicMobs runtime và không cố triển khai toàn bộ số lượng mechanic/condition của MythicMobs.

Các ranh giới cần giữ:

- `HaoHanItemCore` tiếp tục sở hữu custom item và item ID.
- `ModelEngine` tiếp tục sở hữu model, hitbox model và animation.
- HaoHanLunar sở hữu vòng đời mob, combat runtime, skill orchestration, loot và spawn.
- Entity phải được nhận diện bằng `PersistentDataContainer`, không dựa vào custom name.
- Cấu hình lỗi không được làm mất registry đang chạy.
- Mọi task có thể triển khai và kiểm thử độc lập; không gộp nhiều hệ thống lớn vào một PR.

## 2. Trạng thái nền hiện tại

- `HaoHanLunarPlugin` đăng ký mechanic và command động.
- Lunar Warden đang được spawn bằng `WardenSpawner` và điều khiển bằng nhiều Java class riêng.
- Các skill hiện có nằm trong `mechanics/boss/warden/skills`.
- `WardenState`/`WardenBehavior` đang giữ state và AI theo kiểu hard-code.
- ModelEngine là dependency bắt buộc; SkyboxEngine là dependency tùy chọn.
- Chưa có registry YAML, schema validation, loot table tổng quát hay random spawn engine.

## 3. Thứ tự phụ thuộc

```text
P0-1/P0-2
   ↓
P0-3/P0-4/P0-5
   ↓
P1-1 → P1-2 → P1-3 → P1-4
   ↓             ↓
P1-5          P2-1/P2-2
   ↓             ↓
P2-3/P2-4 → P3-1/P3-2 → P4
```

Không bắt đầu migration Warden trước khi có registry, PDC identity và validator tối thiểu.

## 4. Definition of Done chung

Mỗi task chỉ được đánh dấu hoàn thành khi:

1. Code biên dịch bằng `gradlew.bat build`.
2. Có test phù hợp hoặc ghi rõ lý do không thể unit-test trực tiếp.
3. Không phá vỡ các test hiện có.
4. Có log lỗi dễ hiểu bằng tiếng Anh hoặc tiếng Việt thống nhất.
5. Không scan toàn bộ world mỗi tick nếu chưa có benchmark và giới hạn.
6. Cấu hình mẫu được cập nhật cùng code nếu task thay đổi public YAML.
7. Prompt thực hiện task phải được dùng như một task độc lập; agent không tự làm các task phía sau.

---

# P0 — Nền tảng an toàn và registry

## P0-1. Tạo domain model cho mob

**Mục tiêu:** Định nghĩa các object thuần dữ liệu, chưa spawn entity.

**File dự kiến:** `mob/MobDefinition.java`, `mob/MobAttributeDefinition.java`, `mob/MobOptionDefinition.java`, `mob/MobDefinitionId.java`.

**Prompt triển khai:**

> Trong repository HaoHanLunar, tạo domain model bất biến cho cấu hình custom mob. Bao gồm ID, entity type, display name, model ID tùy chọn, attributes, options, skill references, drop table reference và tags. Dùng Java 21, null-safety rõ ràng và collection immutable. Chưa viết YAML parser, chưa spawn Bukkit entity. Tạo unit test kiểm tra equality, default value và dữ liệu thiếu bắt buộc. Giữ tương thích với Paper 1.21.11.

**Tiêu chí nghiệm thu:** Model không phụ thuộc Bukkit runtime ngoài enum/type cần thiết; test chạy được.

## P0-2. Tạo YAML loading và validation framework

**Mục tiêu:** Đọc file cấu hình theo thư mục và trả về báo cáo lỗi có dòng/path.

**File dự kiến:** `config/LunarYamlLoader.java`, `config/ConfigValidationReport.java`, `config/ConfigLoadException.java`.

**Prompt triển khai:**

> Xây framework load YAML cho các thư mục `mobs`, `skills`, `drops`, `spawners`. Loader phải hỗ trợ file không tồn tại, YAML sai cú pháp, ID trùng và field sai kiểu; lỗi phải chỉ ra file và path cấu hình. Implement validate-then-swap: nếu batch load thất bại thì giữ registry cũ. Không dùng reflection nguy hiểm và không log secret. Viết test cho YAML hợp lệ, lỗi cú pháp, ID trùng và rollback registry.

**Tiêu chí nghiệm thu:** Một file lỗi không làm plugin disable; registry cũ vẫn tồn tại sau reload thất bại.

## P0-3. Registry và lifecycle của mob definition

**Mục tiêu:** Đăng ký, tra cứu, reload và snapshot definition.

**Prompt triển khai:**

> Tạo `MobDefinitionRegistry` thread-safe với các thao tác register, get, contains, snapshot, replaceAll và clear. ID phải normalize lowercase và từ chối ký tự không hợp lệ. Kết nối registry với `LunarYamlLoader` nhưng chưa tạo entity. Thêm test concurrent read, duplicate ID, normalize ID và atomic replace.

**Tiêu chí nghiệm thu:** Reader không thấy registry ở trạng thái nửa cũ nửa mới khi reload.

## P0-4. Persistent identity cho active mob

**Mục tiêu:** Nhận diện custom mob bền vững qua restart/chunk load.

**Prompt triển khai:**

> Tạo `LunarMobIdentity` dùng PersistentDataContainer với namespace key ổn định: mob ID, engine version và optional spawn instance ID. Tạo utility đọc/ghi/kiểm tra identity trên LivingEntity. Không nhận diện bằng custom name hoặc class entity. Viết test mock phù hợp và test entity không có PDC data.

**Tiêu chí nghiệm thu:** Có thể lấy đúng mob ID sau đổi tên entity; dữ liệu không đè key của plugin khác.

## P0-5. Mob manager và cleanup

**Mục tiêu:** Quản lý active mob, chunk unload, death, disable.

**Prompt triển khai:**

> Tạo `ActiveLunarMob` và `LunarMobManager`. Manager map entity UUID → active state, đăng ký/unregister, tìm theo UUID/definition ID, cleanup invalid entity và cleanup toàn bộ khi plugin disable. Đăng ký listener cho death, chunk unload và plugin disable. Không scan toàn bộ world mỗi tick. Thêm test lifecycle và đảm bảo ModelEngine model được destroy khi entity bị cleanup.

**Tiêu chí nghiệm thu:** Không còn state mồ côi sau death, remove hoặc plugin disable.

---

# P1 — Skill runtime MVP

## P1-1. Skill definition, trigger và cooldown

**Prompt triển khai:**

> Tạo model `SkillDefinition`, `SkillTrigger`, `SkillCastContext` và `CooldownRegistry`. Hỗ trợ trigger `onSpawn`, `onTimer`, `onCombat`, `onDamaged`, `onDeath`, `onKill`, `onInteract`, `onSignal`. Cooldown dùng tick hoặc milliseconds nhất quán, không tạo task riêng cho mỗi mob nếu có thể dùng scheduler trung tâm. Viết test cooldown, reset khi entity chết và cast bị hủy.

## P1-2. Skill scheduler trung tâm

**Prompt triển khai:**

> Tạo scheduler/tick dispatcher cho skill với một Bukkit task trung tâm. Hỗ trợ delay, repeat giới hạn và cancellation theo entity UUID. Có budget thời gian mỗi tick và log cảnh báo khi vượt budget. Không chạy Bukkit API từ async thread. Viết test lifecycle cancellation; thêm benchmark đơn giản cho 100 active mobs.

## P1-3. Targeter MVP

**Prompt triển khai:**

> Implement targeter registry và các targeter: self, target, players_in_radius, living_entities_in_radius, location, random_player. Bắt buộc giới hạn radius, world và số kết quả để tránh lag. Targeter trả về snapshot immutable. Viết unit test bán kính, khác world, spectator và entity invalid.

## P1-4. Condition MVP

**Prompt triển khai:**

> Implement condition registry với target_within, health_below, health_above, world, line_of_sight, phase, cooldown_ready, tag và variable_compare. Condition không được mutate entity. Khi tham số sai phải trả validation error thay vì throw trong combat tick. Viết test cho từng condition và tổ hợp AND/OR/NOT.

## P1-5. Mechanics MVP

**Prompt triển khai:**

> Implement mechanic registry và các mechanic an toàn: damage, heal, potion, knockback, teleport, leap, particle, sound, message, bossbar, animation, summon và remove. Mỗi mechanic phải nhận `SkillCastContext`, kiểm tra target invalid, permission/world và có giới hạn amount/radius. Không cho phép command execution tùy ý trong MVP. Viết test parser và integration test cho ít nhất damage, delay, particle và summon.

## P1-6. YAML parser cho skill chain

**Prompt triển khai:**

> Parse skill chain từ YAML thành trigger + condition + targeter + mechanics. Hỗ trợ alias rõ ràng, tham số typed, `delay`, `repeat`, `cooldown` và cast skill khác. Error message phải chỉ ra skill ID, mechanic index và field lỗi. Tạo `ExampleSkills.yml` tối thiểu cho Warden và test parse/validation.

---

# P2 — Migration Lunar Warden

## P2-1. Khai báo Lunar Warden bằng YAML

**Prompt triển khai:**

> Tạo `src/main/resources/mobs/lunar_warden.yml` chứa type IRON_GOLEM, model ID, attributes, options, tags và skill references. Migrate các hằng số public từ WardenConstants sang config với default backward-compatible. Chưa xóa logic cũ cho đến khi test migration pass.

## P2-2. Adapter ModelEngine

**Prompt triển khai:**

> Tách logic ModelEngine khỏi WardenSpawner thành `ModelEngineMobAdapter`. Adapter nhận ActiveLunarMob và model ID từ MobDefinition, xử lý blueprint không tồn tại, create model, scale, hitbox, animation và destroy. Giữ message lỗi hiện tại nhưng đưa vào utility dùng chung. Viết test bằng fake adapter hoặc seam để không cần ModelEngine thật trong unit test.

## P2-3. Chuyển 10 Warden skill sang skill registry

**Prompt triển khai:**

> Đăng ký các skill hiện có: aerial slash combo, ground slam, celestial summon, shield block, shield block push, shield charge, shield sword slam, targeted light strike, thrust fling và pursuit. Ban đầu dùng Java-backed mechanic adapter để giữ hành vi y hệt; chỉ thay cách chọn/cast bằng SkillExecutor. Không thay đổi damage balance trong task này. Chạy regression test cho từng skill.

## P2-4. Phase/state machine

**Prompt triển khai:**

> Tạo `MobPhaseState` thay thế việc rải rác dùng WardenBehavior cho phần generic. Hỗ trợ phase ID, transition condition, enter/exit skill và cooldown reset tùy chọn. Giữ các behavior đặc thù của Warden trong adapter cho đến khi migration hoàn tất. Test transition theo health threshold và death cleanup.

## P2-5. Command quản trị mob

**Prompt triển khai:**

> Thêm command `/lunarmob spawn`, `kill`, `info`, `reload` và `signal` qua Paper command registration hiện có. Kiểm tra permission, tab completion, invalid ID và reload rollback. Đảm bảo các command cũ của Warden vẫn hoạt động trong một thời gian tương thích và ghi cảnh báo deprecation.

---

# P3 — Loot và spawning

## P3-1. Drop table engine

**Prompt triển khai:**

> Tạo `DropTableDefinition`, `DropEntry`, chance, amount range, condition và nested table. Item phải resolve qua HaoHanItemCore trước, sau đó mới fallback Material whitelist. Không cho phép arbitrary NBT/command trong MVP. Hook vào death event và test chance deterministic bằng seeded random.

## P3-2. Damage contribution

**Prompt triển khai:**

> Theo dõi damage contribution theo UUID cho ActiveLunarMob, chống duplicate event và xử lý projectile/shooter. Khi mob chết, tạo immutable contribution snapshot để loot condition dùng. Xóa data khi cleanup. Test melee, projectile, damage sau khi player rời server và minimum contribution threshold.

## P3-3. Fixed spawner

**Prompt triển khai:**

> Tạo `SpawnerDefinition` và `LunarSpawnerManager` hỗ trợ location/region, mob ID, cooldown, max alive, activation radius, respawn delay và world whitelist. Scheduler phải dùng một task trung tâm, không tạo task vô hạn. Thêm command list/enable/disable và test cap/cooldown.

## P3-4. Random spawn có giới hạn

**Prompt triển khai:**

> Implement random spawn rule cho dimension haohan:lunar với chance, biome, Y range, player distance, cap, priority và action ADD/REPLACE. Tôn trọng Paper mob cap và giới hạn số attempt mỗi tick. Tắt mặc định trong config. Thêm metrics/log để biết số attempt, success và skip vì cap.

---

# P4 — Variables, threat và nội dung mở rộng

## P4-1. Variable scope và placeholder

**Prompt triển khai:**

> Tạo variable store với scope mob, player, cast và global; hỗ trợ number, boolean, string với TTL tùy chọn. Chỉ expose placeholder allow-list như mob.name, mob.health, mob.phase, target.name. Chống memory leak khi player/entity rời server. Viết test scope và cleanup.

## P4-2. Threat table

**Prompt triển khai:**

> Tạo threat table cho ActiveLunarMob với add, reduce, clear, top target và target switch cooldown. Tích hợp damage contribution nhưng giữ hai khái niệm độc lập. Có filter player/world/range và fallback target vanilla. Benchmark với 100 mob và 20 target.

## P4-3. Config reload an toàn và version migration

**Prompt triển khai:**

> Thêm `config-version`, migration hook và `/lunarmob reload`. Reload phải parse toàn bộ file ngoài main thread chỉ khi dữ liệu không chạm Bukkit API, sau đó apply snapshot trên main thread. Nếu một file lỗi, rollback toàn bộ batch. Viết test version cũ, version mới và rollback.

## P4-4. Documentation và sample content

**Prompt triển khai:**

> Cập nhật README với schema YAML, lifecycle, command, permission, giới hạn mechanic và ví dụ tạo một mob mới. Thêm sample mob/skill/drop/spawner vào resources. Mọi field public phải có comment hoặc documentation tương ứng.

---

# 5. Danh sách tính năng cố ý để sau

Các tính năng sau không nằm trong MVP vì chi phí hoặc rủi ro cao:

- GUI editor giống MythicMobs.
- Cinematic camera và packet-only client effects.
- Disguise system riêng.
- Arbitrary command mechanic.
- Full scripting language.
- Mob replacement cho mọi vanilla spawn reason.
- Per-player clientside loot/hologram phức tạp.
- Hàng trăm mechanic/condition tổng quát như MythicMobs.

Chỉ mở các mục này sau khi đã có profiling, use case cụ thể và test server thực tế.

# 6. Quy tắc sử dụng prompt

Khi giao cho agent/developer, luôn gửi nguyên prompt của đúng một task, kèm yêu cầu:

```text
Chỉ thực hiện task được nêu. Không tự triển khai task kế tiếp.
Trước khi sửa, đọc các file liên quan và kiểm tra thay đổi hiện tại.
Sau khi sửa, chạy test/build phù hợp và báo cáo file đã thay đổi, test đã chạy,
rủi ro còn lại và đề xuất task tiếp theo trong plan.md.
Không dùng git reset --hard, không xóa thay đổi của người khác.
```

Mỗi task nên là một commit/PR nhỏ. Thứ tự khuyến nghị: P0 hoàn tất trước, sau đó P1; P2 chỉ bắt đầu khi P1 có test; P3/P4 triển khai độc lập sau khi Warden migration ổn định.

# 7. Tiêu chí hoàn thành toàn bộ dự án

- Có thể tạo một mob mới chỉ bằng YAML + skill/drop config.
- Lunar Warden chạy bằng registry/skill runtime, không phụ thuộc đường spawn hard-code.
- Reload cấu hình không làm mất mob đang active và rollback được khi lỗi.
- Có loot table, damage contribution và fixed spawner hoạt động.
- Random spawn mặc định tắt và có cap/budget rõ ràng.
- `gradlew.bat build` pass; test bao phủ parser, registry, cooldown, cleanup, skill, loot và spawner.
- README có hướng dẫn cho người làm content mà không cần sửa Java cho mỗi mob mới.
