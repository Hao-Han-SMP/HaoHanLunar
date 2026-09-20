# HaoHanLunar — Plan 2: mở rộng tính năng MythicMobs và khả năng hỗ trợ

## 1. Mục tiêu

`plan.md` đã xác lập nền tảng cốt lõi: Registry, YAML loader, PDC identity, mob lifecycle, skill runtime MVP, Warden migration, loot và spawner cơ bản. `plan2.md` mở rộng engine toàn diện theo các hệ thống chuyên sâu và battle-tested của **MythicMobs 5.13.0**:

- Cơ chế skill tổng hợp (Composite skills: parallel, sequence, subskills, cancellation token).
- Aura components đa dạng, Projectile framework (bullet display, block, arrow, bouncy, homing).
- Phase state machine, Threat table, Immunity table và Crowd-Control states.
- Pins và Pin Regions (hệ thống bounding arena, đa giác không gian, enter/exit bounds triggers).
- Spawner nâng cao (cluster generation, replacement rules, leash behavior, spawn reason filtering).
- Scoped variables, safe expression evaluator (exp4j), typed persistence và placeholder registry.
- Loot context nâng cao, Pity manager, per-player client-side drops, và damage contribution weighting.
- AI Goal & Target Selectors thông qua Paper 1.21.1 Mob Goals API (`Mob#getPathfinder()`).
- Custom Stat Registry (Damage, Armor, Crit, Lifesteal, Cooldown Reduction, Luck).
- Tooling, tracing, debugger, schema JSON và content creator workflows.
- Khả năng tương thích nền tảng Paper 1.21.1, Java 21, hướng tới Folia scheduler và ModelEngine bridge.

Không triển khai mù quáng toàn bộ chức năng của MythicMobs. Mọi tính năng mở rộng đều phải phục vụ nhu cầu thế giới Mặt Trăng của HaoHan SMP, kiểm soát chặt chẽ ngân sách hiệu năng (performance budget) và có thể kiểm thử độc lập.

## 2. Nguyên tắc mở rộng

- API public phải ổn định theo version (`vn.haohan.lunar.api.v1`).
- Giữ vững tính độc lập: Skill mechanics không được hard-code phụ thuộc vào riêng Lunar Warden.
- Mọi tác vụ async chỉ xử lý dữ liệu thuần; tương tác với Bukkit API, ModelEngine, hoặc entity state phải chạy trên main thread (hoặc entity scheduler phù hợp).
- Mọi kỹ năng và hiệu ứng phải có `max-targets`, `max-duration`, `max-range` và cơ chế hủy bỏ (`cancellation token`) rõ ràng.
- Tuyệt đối không cho phép YAML thực thi mã Java tự do, arbitrary reflection, hoặc command ngoài danh sách allow-list.
- Các plugin liên kết (ModelEngine, HaoHanItemCore, PlaceholderAPI, WorldGuard) là soft-dependency; nếu thiếu plugin thì engine vẫn hoạt động an toàn và fallback No-op.
- Mọi tính năng mới phải có tính quan sát (observability): debug trace, reason code hoặc metric tương ứng.

## 3. Bảng phạm vi tương đương MythicMobs ưu tiên

| Nhóm | Phạm vi plan2 theo MythicMobs 5.13.0 | Ưu tiên |
|---|---|---|
| Advanced combat | Immunity table, damage pipeline, damage modifiers, knockback, CC states | P1 |
| Advanced skills | Composite nodes, Aura components, Projectile/Bullets, Geometric targeters | P2 |
| Mob persistence & options | Mob option matrix (18+ options), level stat scaling, chunk/restart persistence | P3 |
| Drops & Pity | Per-player drops, PityManager, drop tables luck/bias modifiers | P4 |
| Advanced spawning | Cluster/Regional generators, spawn reason filter, fixed spawner leash | P5 |
| Integrations & API | ModelEngine adapter, HaoHanItemCore provider, PAPI, WorldGuard, Public API | P6 |
| Creator tools & Trace | Validate command, Skill tracer, Performance metrics, JSON Schema | P7 |
| Boss presentation | Bossbar, custom nameplates, speech bubble, audio sequence, display entities | P8 |
| Extended features | Items, Mounts, Environment, Signals, Safe Math, Packs, AI, Pins, Stats | P9–P28 |

---

# P1 — Combat runtime nâng cao

## P1-1. Damage pipeline và event context

**Mục tiêu:** Chuẩn hóa toàn bộ sát thương custom thông qua một pipeline tập trung trước khi tính toán immunity, modifier và trigger.

**Chi tiết kiến trúc theo MythicMobs:**
- `DamageContext`: Chứa `attacker`, `victim`, `cause` (DamageCause), `damageType` (PHYSICAL, MAGICAL, TRUE), `baseDamage`, `finalDamage`, `skillMetadata`, `isCritical`, `ignoreArmor`, `ignoreAbsoption`, `cancellationToken`.
- `DamagePipeline`: Thực hiện chuỗi bộ lọc theo thứ tự:
  1. *Pre-check*: Kiểm tra invulnerable, god-mode, bypass protection.
  2. *Modifiers*: Áp dụng Mob DamageModifiers (theo DamageCause và attacker EntityType).
  3. *Immunity*: Kiểm tra ImmunityTable của victim.
  4. *Stat mitigation*: Tính toán giảm trừ từ Armor / Magic Resistance (nếu không phải TRUE damage).
  5. *Execution*: Gọi Bukkit `victim.damage(amount, attacker)` với cờ `isUsingDamageSkill` bật sẵn để chống vòng lặp đệ quy.
  6. *Post-damage*: Cập nhật DamageTracker, ThreatTable và kích hoạt trigger `onDamaged` / `onAttack`.

**Prompt triển khai:**

> Tạo `DamageContext`, `DamagePipeline` và các hook trước/sau khi gây sát thương cho ActiveLunarMob dựa trên quy trình xử lý sát thương của MythicMobs. DamageContext phải lưu trữ đầy đủ attacker, victim, DamageCause, DamageType (PHYSICAL, MAGICAL, TRUE), baseDamage, finalDamage, skill ID, ignoreArmor và cờ recursion guard. Tích hợp chặt chẽ với EntityDamageByEntityEvent của Paper nhưng không phá vỡ logic vanilla. Áp dụng tuần tự các bước: kiểm tra miễn nhiễm, nhân hệ số sát thương, giảm trừ giáp và trừ máu thực tế. Viết unit test cho: hủy bỏ sát thương, giảm trừ giáp, sát thương chuẩn (true damage) và chống vòng lặp đệ quy khi skill gây sát thương lồng nhau.

**Tiêu chí nghiệm thu:** Toàn bộ skill gây sát thương đều đi qua cùng một pipeline; không xảy ra hiện tượng double-damage; chặn 100% stack overflow khi gọi skill vòng tròn.

## P1-2. Damage modifier và immunity table

**Mục tiêu:** Hỗ trợ cấu hình hệ số sát thương và bảng miễn nhiễm tạm thời cho mob.

**Chi tiết kiến trúc theo MythicMobs:**
- **DamageModifiers trong Mob YAML**:
  ```yaml
  DamageModifiers:
  - FIRE 0.5        # Giảm 50% sát thương lửa
  - FALL 0.0        # Miễn nhiễm 100% sát thương rơi
  - PROJECTILE 1.5  # Nhận 150% sát thương từ cung tên
  EntityDamageModifiers:
  - PLAYER 1.0
  - IRON_GOLEM 0.2
  ```
- **`ImmunityTable`**:
  * Quản lý danh sách các loại sát thương hoặc skill ID đang bị miễn nhiễm tạm thời kèm tick timeout (`hasImmunity(cause/skill)`).
  * Hỗ trợ mechanic `immunity{type=PROJECTILE;duration=100}` hoặc `immunity{skill=ALL;duration=60}` (ví dụ lúc boss đang gồng chiêu).
  * Khi chặn sát thương thành công, log lý do debug và phát hiệu ứng phản đòn (nếu có cấu hình).

**Prompt triển khai:**

> Triển khai hệ thống Damage Modifiers và ImmunityTable cho ActiveLunarMob theo mô hình MythicMobs. Hỗ trợ cấu hình tỷ lệ sát thương theo DamageCause và EntityType từ YAML. Xây dựng ImmunityTable quản lý các miễn nhiễm sát thương có thời hạn theo server tick. Cung cấp mechanic `immunity` cho phép boss miễn nhiễm sát thương trong lúc vận chiêu. Khi miễn nhiễm kích hoạt, hủy sát thương trước khi gọi Bukkit event và ghi nhận lý do vào DamageContext. Viết test cho: giảm sát thương lửa, miễn nhiễm rơi, nhân sát thương cung và timeout của bảng miễn nhiễm.

**Tiêu chí nghiệm thu:** Sát thương bị nhân hệ số hoặc triệt tiêu chính xác theo cấu hình; immunity tự động hết hiệu lực khi hết tick.

## P1-3. Threat table hoàn chỉnh

**Mục tiêu:** Nâng cấp hệ thống bảng thù hận (Threat Table) với đầy đủ các thao tác taunt, threat decay và target change event.

**Chi tiết kiến trúc theo MythicMobs:**
- Threat sources:
  * Sát thương nhận vào (1 damage = 1 threat point).
  * Heal cho đồng minh gần boss (1 heal = 0.5 threat point).
  * Proximity threat: người chơi đứng quá gần boss trong thời gian dài.
  * `taunt{amount=X}`: Cưỡng chế tăng điểm threat cao nhất cho mục tiêu chỉ định trong thời gian T tick.
- Threat decay & Cleanup:
  * Điểm threat tự động giảm theo tỷ lệ cấu hình nếu mục tiêu chạy trốn hoặc không tương tác sau N tick (`threatDecayPerSecond`).
  * Xóa mục tiêu khỏi bảng khi mục tiêu chết, chuyển thế giới hoặc logout.
- Event: Bắn `MobTargetChangeEvent` (Cancellable) khi boss chuẩn bị chuyển sang mục tiêu mới.

**Prompt triển khai:**

> Mở rộng hệ thống Threat Table cho ActiveLunarMob với các tính năng: cộng điểm theo damage, heal đồng đội, khoảng cách gần, và mechanic taunt (cưỡng chế đổi target). Triển khai cơ chế suy giảm threat theo thời gian (decay) khi mục tiêu chạy xa hoặc không gây sát thương. Phát ra sự kiện `MobTargetChangeEvent` (cancellable) khi boss đổi target. Tự động dọn dẹp mục tiêu khi người chơi chết, thoát game hoặc chuyển world. Viết unit test cho: tính toán điểm thù hận, thứ tự ưu tiên, taunt override, và dọn dẹp khi player disconnect.

**Tiêu chí nghiệm thu:** Boss ưu tiên đánh người chơi có threat cao nhất; cơ chế taunt hoạt động tức thì; không còn rò rỉ UUID khi người chơi logout.

## P1-4. Crowd-control state

**Mục tiêu:** Cung cấp hệ thống hiệu ứng khống chế (Crowd-Control) chuẩn mực: Stun, Root, Silence, Disarm, Invulnerable, Slow, Fear.

**Chi tiết kiến trúc theo MythicMobs:**
- `CCState`: Enum gồm `STUN` (không di chuyển, không đánh), `ROOT` (không di chuyển, vẫn đánh được), `SILENCE` (không cast được skill), `DISARM` (không đánh thường được), `INVULNERABLE` (bất tử), `SLOW`, `FEAR` (bị hoảng loạn chạy ra xa).
- Quản lý trên `ActiveLunarMob`: Mỗi state có `durationTicks`, `sourceEntity`, và `priority`.
- Khi bị STUN/ROOT: can thiệp tạm thời vào movement speed attribute hoặc Paper Pathfinder goal, không sửa đổi AI gốc vĩnh viễn.
- Tự động hoàn trả thuộc tính ban đầu khi hết thời gian hoặc mob chết.

**Prompt triển khai:**

> Xây dựng hệ thống hiệu ứng khống chế (Crowd-Control State) cho ActiveLunarMob gồm: STUN, ROOT, SILENCE, DISARM, INVULNERABLE và FEAR. Mỗi hiệu ứng có thời lượng (duration ticks), nguồn gây ra và mức độ ưu tiên. Triển khai controller can thiệp tạm thời vào tốc độ di chuyển và khóa kỹ năng trong thời gian khống chế; tuyệt đối không ghi đè làm mất AI gốc của entity. Tự động phục hồi trạng thái khi hết hạn hoặc khi entity chết. Viết unit test cho: chồng hiệu ứng (stacking), làm mới thời gian (refresh), miễn nhiễm CC và dọn dẹp khi hết hạn.

**Tiêu chí nghiệm thu:** Mob bị Stun không thể di chuyển hoặc dùng skill; khi hết stun lập tức hoạt động lại bình thường.

## P1-5. Phase transition nâng cao

**Mục tiêu:** Mở rộng Phase Machine hỗ trợ điều kiện chuyển phase phức tạp, enter/exit skills và bảo vệ chống lặp vô hạn.

**Chi tiết kiến trúc theo MythicMobs:**
- Hỗ trợ điều kiện chuyển phase đa dạng:
  * Ngưỡng máu (`health < 50%`).
  * Thời gian sống (`aliveTime > 1200 ticks`).
  * Số lượng target (`targetCount >= 5`).
  * Biến số (`<caster.var.phase_ready> == true`).
  * Tín hiệu bên ngoài (`~onSignal:FORCE_PHASE_2`).
- Mỗi Phase có:
  * `onEnterSkills`: Danh sách skill thi triển ngay khi vừa bước vào phase (invulnerable tạm thời, gầm rú, hất tung, hồi máu, triệu hồi quái phụ).
  * `onExitSkills`: Kỹ năng thi triển khi rời khỏi phase.
  * Cờ `onceOnly: true`: Ngăn chặn kích hoạt lại phase cũ nếu máu boss hồi phục trở lại.
  * Cooldown chuyển phase (chống boss bị đổi phase nhiều lần trong cùng một tick).

**Prompt triển khai:**

> Mở rộng hệ thống quản lý phase của ActiveLunarMob hỗ trợ chuyển đổi trạng thái nâng cao theo mô hình MythicMobs. Cho phép định nghĩa phase chuyển tiếp dựa trên điều kiện: máu, thời gian sống, số lượng người chơi xung quanh, biến số và signal. Mỗi phase hỗ trợ khai báo onEnterSkills và onExitSkills kèm cờ onceOnly để không lặp lại khi máu dao động. Tích hợp cơ chế cooldown chuyển phase để chống tình trạng boss chuyển đổi liên tục trong cùng 1 tick. Viết unit test mô phỏng kịch bản boss chuyển từ phase 1 -> 2 -> 3 kèm enter skill và kiểm tra an toàn khi reload config lúc đang ở phase 2.

**Tiêu chí nghiệm thu:** Boss chuyển phase chuẩn xác kèm hiệu ứng vào/ra; không bị giật lag đổi phase liên tục khi máu chạm ngưỡng.

---

# P2 — Skill và targeter nâng cao

## P2-1. Composite skill framework

**Mục tiêu:** Xây dựng hệ thống cây kỹ năng tổng hợp (Composite Skill Nodes) cho phép điều khiển luồng thực thi phức tạp: `sequence`, `parallel`, `random`, `chance`, `repeat_until`, `on_success`, `on_fail`, `cancel`.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa mô hình `MetaSkillMechanic` của MythicMobs 5.13.0:
  * Mỗi subskill nhận một bản sao sâu (deep clone) của `SkillCastContext` (`context.deepClone()`), giữ nguyên target snapshot và tham số runtime.
  * Truyền biến số cục bộ và tham số động thông qua `context.getParameters()`.
  * Hỗ trợ token hủy bỏ (`AtomicBoolean cancellationToken`): Nếu node cha bị hủy hoặc boss chết, toàn bộ các node con đang chờ trong scheduler sẽ lập tức dừng lại.
  * Giới hạn độ sâu đệ quy tối đa (max depth = 5) và thời lượng tối đa cho một chuỗi skill (max duration = 600 ticks) để chống treo thread.

**Prompt triển khai:**

> Xây dựng hệ thống Composite Skill Nodes cho HaoHanLunar theo kiến trúc MetaSkill của MythicMobs: hỗ trợ các cấu trúc điều khiển `sequence` (tuần tự), `parallel` (đồng thời), `random` (chọn ngẫu nhiên một nhánh), `chance` (theo xác suất), `repeat_until` (lặp tới khi thỏa mãn điều kiện), `on_success` và `on_fail`. Mỗi node con thực thi trên deep clone của SkillCastContext và nhận tham số truyền vào qua map parameters. Tích hợp AtomicBoolean cancellation token xuyên suốt toàn bộ cây skill: khi node cha hủy hoặc caster chết, mọi sub-action lập tức ngừng thực thi. Khống chế độ sâu đệ quy tối đa 5 tầng. Viết unit test cho: thứ tự thực thi của sequence, phân nhánh random, truyền tham số và cơ chế hủy ngang an toàn.

**Tiêu chí nghiệm thu:** Các chuỗi kỹ năng lồng nhau chạy đúng thứ tự; hủy skill cha dọn dẹp sạch sẽ toàn bộ các nhánh con đang đợi.

## P2-2. Aura system và Aura Components

**Mục tiêu:** Xây dựng hệ thống hiệu ứng hào quang (Aura) gắn trên entity hoặc vị trí không gian với các component mô-đun hóa cao.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa mô hình `AuraRegistry` và `AuraComponent` từ MythicMobs 5.13.0:
  * Không tạo Bukkit task riêng cho từng aura; toàn bộ aura trên server được quản lý và tick tập trung bởi một `AuraScheduler` duy nhất.
  * Hỗ trợ gán lên: `LivingEntity`, tọa độ `Location`, hoặc gắn trực tiếp vào xương của ModelEngine (`MEGAttachment`).
  * Vòng đời Aura: `onStart`, `onTick` (mỗi N tick), `onHit` (khi mục tiêu bị đánh), `onDamaged` (khi mục tiêu bị sát thương), `onExpire` (khi hết thời gian).
  * **Aura Components** có thể ghép linh hoạt:
    * `FearAuraComponent`: Làm hoảng loạn mục tiêu đứng trong vùng.
    * `GlowAuraComponent`: Làm mục tiêu phát sáng quang học.
    * `FlyAuraComponent`: Cho phép bay trong vùng hào quang.
    * `StatAuraComponent`: Tăng/giảm chỉ số (giáp, damage, tốc độ) cho ai đứng trong vùng.
    * `OnAttackAuraComponent` / `OnDamagedAuraComponent`: Phản đòn hoặc thêm hiệu ứng khi giao tranh.
  * Quản lý Aura Stacks: `maxStacks`, chế độ gộp (`REFRESH`, `ADD_STACK`, `IGNORE`).

**Prompt triển khai:**

> Triển khai Aura System hoàn chỉnh cho HaoHanLunar dựa trên kiến trúc AuraRegistry và AuraComponent của MythicMobs 5.13.0. Quản lý toàn bộ aura qua một AuraScheduler trung tâm. Hỗ trợ gắn aura lên LivingEntity hoặc Location với các sự kiện vòng đời: onStart, onTick, onHit, onDamaged, onExpire. Xây dựng các AuraComponent mô-đun: StatAuraComponent (cộng/trừ chỉ số), GlowAuraComponent, FearAuraComponent và OnAttackAuraComponent. Hỗ trợ cơ chế Aura Stacks (giới hạn maxStacks, chính sách refresh hoặc cộng dồn). Tự động dọn dẹp aura khi mục tiêu chết hoặc chunk unload. Viết unit test cho: aura tick định kỳ, cộng dồn stacks, kích hoạt onDamaged component và dọn dẹp sạch sẽ khi hết hạn.

**Tiêu chí nghiệm thu:** 50 mob bật aura đồng thời không tạo 50 runnable; hiệu ứng hào quang phản hồi tức thì với sát thương và di chuyển.

## P2-3. Projectile và Bullet framework

**Mục tiêu:** Xây dựng khung đạn đạo (Projectile) hỗ trợ cả đạn mô phỏng vật lý và đạn hiển thị bằng Packet / Display Entity.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa mô hình `ProjectileMechanic` và `BulletMechanic` của MythicMobs 5.13.0:
  * **Các loại Bullet (`BulletType`)**:
    * `DISPLAY_ITEM` / `DISPLAY_BLOCK`: Dùng `ItemDisplay` hoặc `BlockDisplay` (Minecraft 1.21) cho hiệu ứng xoay và kích thước mượt mà.
    * `ARMOR_STAND`: Mô hình giáp tàng hình mang item trên đầu.
    * `ARROW`: Bắn mũi tên vanilla Paper.
    * `PARTICLE_ONLY`: Đạn chỉ là vệt hạt particle không sinh entity.
  * **Thuộc tính đường đạn**:
    * `velocity`, `gravity`, `maxDistance`, `maxLifetimeTicks`, `hitboxRadius`.
    * `raytrace`: Dùng Raytrace ray-box intersection mỗi bước di chuyển để **chống hiện tượng đạn bay xuyên qua người mục tiêu khi tốc độ quá nhanh (tunneling)**.
    * `surfaceMode`: `DETONATE` (nổ khi chạm đất), `BOUNCE` (nảy lên bề mặt khối theo vector phản xạ góc tới), `PIERCE` (xuyên qua entity), `SLIDE` (trượt dọc sàn).
    * `homing`: Đạn đuổi mục tiêu với góc ngoặt tối đa mỗi tick (`turnRate`).
  * Sự kiện callback: `onTick`, `onHitEntity`, `onHitBlock`, `onEnd`.

**Prompt triển khai:**

> Xây dựng Projectile & Bullet Framework cho HaoHanLunar theo mô hình MythicMobs 5.13.0. Quản lý đạn tập trung qua ProjectileTracker. Hỗ trợ các kiểu đạn: DISPLAY_ITEM/DISPLAY_BLOCK (dùng Display Entity 1.21), ARMOR_STAND và PARTICLE_ONLY. Triển khai thuật toán raytrace ray-box intersection giữa vị trí cũ và vị trí mới để chống hoàn toàn lỗi tunneling khi đạn bay tốc độ cao. Cung cấp các thuộc tính: gravity, velocity, maxDistance, maxLifetime, piercing, và tính năng nảy bề mặt (bounce physics). Hỗ trợ đạn tầm nhiệt (homing) điều chỉnh hướng đuổi theo target theo hệ số turnRate. Callback đầy đủ onTick, onHitEntity, onHitBlock, onEnd. Viết unit test cho: va chạm raytrace, xuyên mục tiêu, nảy khi đập tường và tự hủy khi vượt quá maxDistance.

**Tiêu chí nghiệm thu:** Đạn bay vận tốc cao không xuyên thủng tường hay người; Display Entity được dọn dẹp triệt để khi đạn chạm đích.

## P2-4. Chain, Beam, Orbital và Slash mechanics

**Mục tiêu:** Triển khai các hiệu ứng chiến đấu đặc trưng nâng cao: đạn nảy chuyền (Chain), tia quét liên tục (Beam), đạn xoay quanh thân (Orbital) và chém vệt sáng (Slash).

**Chi tiết kiến trúc theo MythicMobs:**
- `ChainMechanic`: Bắn từ caster sang mục tiêu 1, sau đó tự động tìm mục tiêu hợp lệ gần nhất trong bán kính R để nảy tiếp tối đa N lần (`maxBounces`).
- `BeamMechanic`: Chiếu tia laser/hạt tức thời hoặc duy trì trong T tick giữa caster và target/location, gây sát thương cho bất kỳ ai chạm vào đường tia.
- `OrbitalMechanic`: Triệu hồi N viên đạn/vật thể xoay quanh caster theo bán kính R, vận tốc góc omega, tồn tại trong T tick; va chạm vào kẻ thù sẽ nổ và gây sát thương.
- `SlashMechanic`: Phát animation chém ModelEngine đồng thời quét một hình cánh cung phía trước mặt caster để hất tung và gây sát thương diện rộng.

**Prompt triển khai:**

> Implement các mechanic chiến đấu nâng cao cho HaoHanLunar dựa trên Projectile và Particle framework: Chain (sét/đạn nảy chuyền giữa nhiều mục tiêu kèm suy giảm sát thương mỗi lần nảy), Beam (tia laser chiếu thẳng kiểm tra va chạm raytrace dọc đường chiếu), Orbital (đạn hoặc hạt bay theo quỹ đạo tròn xoay quanh thân caster) và Slash (quét chém hình quạt phía trước). Mọi hiệu ứng phải có giới hạn ngân sách hạt particle mỗi tick, trần số lượng mục tiêu và tự hủy an toàn khi caster chết. Viết unit test và benchmark mô phỏng 50 orbital effect đồng thời kiểm tra tính ổn định tick-time.

**Tiêu chí nghiệm thu:** Hiệu ứng nảy chuyền không bị lặp lại mục tiêu cũ; tia beam tính va chạm chuẩn xác từng voxel; không drop TPS khi dùng nhiều orbital.

## P2-5. Targeter hình học và Audiences

**Mục tiêu:** Bổ sung các bộ chọn mục tiêu hình học chính xác (Cone, Line, Ring, Sphere, Cylinder, Behind, InFront) và phân tách khái niệm Audiences cho hiệu ứng.

**Chi tiết kiến trúc theo MythicMobs:**
- **Targeter hình học**:
  * `@Cone{angle=X;r=Y}`: Quạt hình nón phía trước mặt caster theo góc mở angle (độ) và bán kính Y block (dùng dot product vector hướng nhìn).
  * `@Line{length=X;width=Y}`: Đoạn thẳng phía trước mặt.
  * `@Ring{radius=X;width=Y}`: Vành khăn xung quanh caster hoặc vị trí mục tiêu.
  * `@Sphere{r=X}`: Khối cầu 3D.
  * `@Behind{r=X}`: Phía sau lưng caster.
  * `@InFront{r=X}`: Phía trước mặt caster.
  * `@HighestBlock`: Tọa độ khối cao nhất tại vị trí target (phục vụ sét đánh từ trời xuống).
- **SkillAudience (phân biệt Targeter và Audience)**:
  * Trong MythicMobs 5.13.0, *Targeter* quyết định ai chịu ảnh hưởng của gameplay (nhận damage, nhận hiệu ứng), còn *Audience* quyết định ai nhìn thấy hạt particle, nghe thấy âm thanh, hoặc nhận tin nhắn (để tối ưu hóa packet và trải nghiệm người chơi).
  * `@Audience{r=32}`: Chỉ gửi sound/particle cho người chơi trong bán kính 32 block thay vì phát ra toàn server.

**Prompt triển khai:**

> Xây dựng hệ thống Targeter hình học và SkillAudience cho HaoHanLunar theo mô hình MythicMobs 5.13.0. Triển khai các targeter toán học: @Cone (quạt hình nón theo góc mở và bán kính), @Line, @Ring, @Sphere, @Behind, @InFront, @HighestBlock. Phân tách rõ ràng SkillAudience dành riêng cho việc phân phối packet âm thanh, hiệu ứng hạt, bossbar và tin nhắn (ví dụ @Audience{r=32}) để không gửi packet dư thừa ra toàn server. Mọi phép tính vector phải dùng tọa độ tương đối an toàn và loại bỏ Spectator/Creative. Viết unit test kiểm tra: tính toán góc cone chính xác, entity đứng sau lưng bị bắt bởi @Behind và không bị bắt bởi @Cone.

**Tiêu chí nghiệm thu:** Góc nón @Cone quét trúng 100% mục tiêu nằm trong góc mở và loại bỏ mục tiêu ngoài góc; SkillAudience gửi gói tin chính xác cho người chơi trong tầm nghe/nhìn.

## P2-6. Condition nâng cao

**Mục tiêu:** Mở rộng bộ điều kiện kiểm tra toàn diện: DamageCause, Biome, Structure, Equipment, Permission, TargetCount, TimeAlive, BlockType.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa các điều kiện phổ biến từ MythicMobs:
  * `damagecause{cause=FIRE/PROJECTILE/...}`
  * `biome{b=BIOME_KEY}`
  * `structure{s=STRUCTURE_KEY}` (Paper 1.21 Structure API)
  * `holding{item=ITEM_ID;slot=HAND/OFF_HAND}`
  * `wearing{item=ITEM_ID;slot=HEAD/...}`
  * `targetcount{count=range}` (ví dụ: `>3`, `<=1`)
  * `timealive{ticks=range}`
  * `blocktype{m=MATERIAL}`
  * `lunarphase{phase=FULL_MOON/...}`
  * `altitude{y=range}` (ví dụ: `>100`, `<50`)
- Validator tham số chặt chẽ: cảnh báo ngay khi parse YAML nếu truyền tên Biome hoặc DamageCause không tồn tại.

**Prompt triển khai:**

> Bổ sung bộ Condition nâng cao cho HaoHanLunar: damagecause, biome, structure, holding, wearing, targetcount, timealive, blocktype, altitude và lunarphase. Mỗi condition phải xác thực tên enum và cú pháp giá trị ngay khi load cấu hình (không throw Exception lúc combat). Hỗ trợ kiểm tra linh hoạt trên Caster, Target entity hoặc Location mục tiêu. Viết unit test ma trận cho từng condition kiểm tra cả kịch bản thỏa mãn, không thỏa mãn và dữ liệu đầu vào không hợp lệ.

**Tiêu chí nghiệm thu:** Toàn bộ điều kiện nâng cao hoạt động đúng chuẩn; sai tên enum được phát hiện và báo lỗi ngay tại bước validate.

---

# P3 — Mob definition, equipment và persistence

## P3-1. Equipment và loadout provider

**Mục tiêu:** Gắn trang bị vũ khí và giáp cho custom mob từ Material vanilla hoặc HaoHanItemCore ID với tỉ lệ rơi và thuộc tính tùy chọn.

**Chi tiết kiến trúc theo MythicMobs:**
- `MobEquipmentDefinition`:
  * Các slot: `MAIN_HAND`, `OFF_HAND`, `HEAD`, `CHEST`, `LEGS`, `FEET`.
  * Mỗi slot có: `itemId` (query qua `ItemProvider`), `dropChance` (0.0 đến 1.0), `unbreakable` (boolean).
- Khi mob spawn: tự động trang bị vật phẩm vào LivingEntity `EntityEquipment`.
- Khi mob chết: chỉ rơi trang bị nếu thỏa mãn `dropChance` và không bị chặn bởi option `preventOtherDrops`.

**Prompt triển khai:**

> Xây dựng hệ thống Equipment và Loadout Provider cho HaoHanLunar. Cho phép khai báo trang bị 6 slot (MainHand, OffHand, Head, Chest, Legs, Feet) trong MobDefinition từ ID của HaoHanItemCore hoặc Bukkit Material. Hỗ trợ thuộc tính tỷ lệ rơi khi chết (dropChance). Tích hợp an toàn với ItemProvider; nếu thiếu item hoặc dependency chưa sẵn sàng, ghi nhận cảnh báo và trang bị fallback không làm gián đoạn spawn mob. Viết unit test cho: gắn trang bị chính xác, roll tỷ lệ rơi khi mob chết và xử lý khi item provider trả về null/empty.

**Tiêu chí nghiệm thu:** Mob sinh ra cầm đúng trang bị cấu hình; tỉ lệ rơi trang bị khi chết tuân thủ đúng dropChance.

## P3-2. Ma trận Mob Options đầy đủ

**Mục tiêu:** Hiện thực hóa đầy đủ ma trận các tùy chọn bảo vệ và hành vi của mob từ `MythicMob.class` của MythicMobs 5.13.0.

**Chi tiết kiến trúc theo MythicMobs:**
Danh sách 18+ options bắt buộc trong `MobOptionDefinition`:
1. `preventOtherDrops` (boolean): Chặn toàn bộ loot và EXP vanilla khi mob chết (chỉ rơi từ DropTable của plugin).
2. `preventRandomEquipment` (boolean): Chặn vanilla tự động mặc giáp ngẫu nhiên khi mob sinh ra.
3. `preventSunburn` (boolean): Chặn Zombie/Skeleton bị cháy nắng ban ngày.
4. `preventKnockback` (boolean): Kháng 100% bật lùi từ mọi đòn đánh.
5. `preventLeashing` (boolean): Không cho phép người chơi dùng dây dắt (lead).
6. `preventRename` (boolean): Không cho phép dùng Name Tag đổi tên mob.
7. `preventEndermanTeleport` (boolean): Không cho Enderman tự dịch chuyển khi trúng đòn hoặc gặp nước.
8. `preventItemPickup` (boolean): Không cho mob nhặt vật phẩm rơi trên đất.
9. `preventSilverfishInfection` (boolean): Không cho chui vào block đá.
10. `preventExploding` (boolean): Không cho Creeper tự nổ phá block.
11. `preventMobKillDrops` (boolean): Không sinh loot nếu mob bị giết bởi mob khác thay vì player.
12. `preventTransformation` (boolean): Chặn biến đổi (Zombie -> Drowned, Piglin -> Zombified, Villager -> Witch).
13. `preventMounts` (boolean): Chặn entity khác cưỡi lên mob.
14. `passthroughDamage` (boolean): Cho phép sát thương xuyên thấu qua các hitbox phụ.
15. `applyInvisibility` (boolean): Mob vô hình (dành cho boss ẩn thân hoặc gắn model ModelEngine).
16. `preventVanillaDamage` (boolean): Chặn toàn bộ sát thương vanilla (chỉ nhận sát thương từ skill custom).
17. `despawnMode` (DESPAWN / PERSISTENT): Chính sách tự biến mất khi không có người chơi.
18. `noDamageTicks` (int): Thời gian bất tử chớp nhoáng giữa 2 đòn đánh (mặc định vanilla là 20 tick; boss có thể giảm xuống 0 để nhận combo liên tục).

**Prompt triển khai:**

> Hiện thực hóa toàn diện ma trận Mob Options của MythicMobs 5.13.0 cho ActiveLunarMob. Bao gồm 18 options: preventOtherDrops, preventRandomEquipment, preventSunburn, preventKnockback, preventLeashing, preventRename, preventEndermanTeleport, preventItemPickup, preventSilverfishInfection, preventExploding, preventMobKillDrops, preventTransformation, preventMounts, passthroughDamage, applyInvisibility, preventVanillaDamage, despawnMode, và noDamageTicks. Mỗi option được quản lý qua các Paper event listener chuyên biệt, chỉ áp dụng cho LivingEntity có LunarMobIdentity. Viết unit/integration test cho từng option: chống cháy nắng, chống nổ phá block, chống nhặt đồ và chỉnh sửa noDamageTicks.

**Tiêu chí nghiệm thu:** Mọi tùy chọn bảo vệ hoạt động chính xác 100%; không ảnh hưởng đến bất kỳ entity vanilla nào khác trên server.

## P3-3. Mob level scaling và Stats công thức

**Mục tiêu:** Tự động tăng máu, sát thương, giáp và sức mạnh theo Level của mob thông qua bộ công thức toán học an toàn.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa mô hình Level Scaling của MythicMobs:
  * `Health = BaseHealth + (Level - 1) * PerLevelHealth`
  * `Damage = BaseDamage + (Level - 1) * PerLevelDamage`
  * `Armor = BaseArmor + (Level - 1) * PerLevelArmor`
  * `Power = BasePower + (Level - 1) * PerLevelPower`
- Level được lưu trong `haohan:mob_level` (PDC).
- Cho phép tính toán Level ngẫu nhiên theo khoảng (ví dụ: `Level: 5-10`) hoặc theo khoảng cách tới Spawn Point thế giới (`WorldScaling`).
- Không dùng JavaScript/Nashorn nguy hiểm; chỉ dùng công thức tuyến tính hoặc bộ giải mã toán học an toàn (Safe Math Evaluator).

**Prompt triển khai:**

> Xây dựng hệ thống Mob Level Scaling cho HaoHanLunar theo mô hình MythicMobs. Hỗ trợ cấu hình chỉ số tăng trưởng theo level: PerLevelHealth, PerLevelDamage, PerLevelArmor, PerLevelPower. Cho phép định nghĩa khoảng level ngẫu nhiên khi spawn (ví dụ 10-15) và lưu trữ giá trị level vào PersistentDataContainer. Áp dụng chỉ số thực tế sau khi tính toán level vào attribute của LivingEntity. Cung cấp placeholder hiển thị level của mob. Viết unit test cho: tính toán chỉ số chính xác theo level, scale máu tối đa khi spawn và kiểm tra an toàn chống tràn số khi level cực lớn.

**Tiêu chí nghiệm thu:** Mob level cao có lượng máu và sát thương tăng tương ứng theo cấu hình; Level lưu bền vững trong PDC.

## P3-4. Persistence qua restart và chunk unload

**Mục tiêu:** Khôi phục nguyên vẹn trạng thái của ActiveLunarMob khi chunk được tải lại hoặc sau khi server restart.

**Chi tiết kiến trúc theo MythicMobs:**
- Khi chunk unload:
  * Nếu mob có `despawnMode == PERSISTENT`: Lưu trữ toàn bộ runtime state vào PDC (`health`, `stance`, `level`, `variables`) và gỡ mob khỏi active memory.
  * Nếu `despawnMode == DESPAWN`: Xóa entity, không lưu trữ.
- Khi chunk load:
  * Lắng nghe `ChunkLoadEvent` hoặc `EntityAddToWorldEvent`.
  * Đọc `haohan:mob_id` từ PDC -> Tra cứu `MobDefinition` từ registry.
  * Nếu definition còn tồn tại: Tái tạo `ActiveLunarMob`, khôi phục stance, variables và gắn lại model ModelEngine.
  * Nếu definition đã bị xóa khỏi config (Orphan Entity): Áp dụng chính sách `orphanPolicy` (mặc định xóa entity để tránh lỗi game).

**Prompt triển khai:**

> Xây dựng cơ chế lưu trữ và khôi phục trạng thái bền vững (Persistence) cho ActiveLunarMob qua chu kỳ restart và chunk lifecycle. Khi chunk unload, nếu mob được cấu hình persistent, lưu trữ an toàn các trạng thái: máu hiện tại, stance, level và scoped variables vào PDC rồi unregister khỏi memory. Khi chunk load lại, đọc PDC để tái cấu trúc ActiveLunarMob, khôi phục model ModelEngine và đăng ký lại vào LunarMobManager. Xử lý trường hợp mob mồ côi (khi mob definition đã bị admin xóa khỏi YAML). Viết unit test mô phỏng chu trình: spawn -> đổi stance -> unload chunk -> load chunk -> kiểm tra stance và máu được bảo toàn.

**Tiêu chí nghiệm thu:** Khởi động lại server không làm biến mất boss persistent; boss giữ nguyên lượng máu và phase trước khi tắt server.

---

# P4 — Loot, variable và context nâng cao

## P4-1. Per-player drops và Loot Context mở rộng

**Mục tiêu:** Mở rộng hệ thống phần thưởng rơi hỗ trợ chia thưởng riêng cho từng người chơi (Per-player instanced loot) và hiệu ứng rơi đồ.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa các tính năng rơi đồ hiện đại của MythicMobs 5.13.0:
  * `DropsPerPlayer: true`: Mỗi người chơi đạt đủ điều kiện sát thương tối thiểu (`DropsPerPlayerRequiredDamagePercent`, ví dụ >= 5%) sẽ nhận được một túi đồ rơi riêng biệt (LootBag riêng).
  * `DropsDoLootsplosion: true`: Bắn vật phẩm tung tóe theo hình vòng cung từ tâm xác boss với vận tốc ngẫu nhiên.
  * `DropsGlowByDefault: true`: Vật phẩm rơi phát sáng quang học viền màu (Glowing item).
  * `DropsHaveBeamByDefault: true`: Tạo cột sáng chọc trời (Beacon beam) phía trên vật phẩm hiếm.
  * Drop Table Modifiers: Hỗ trợ `BonusLuckItems` (tăng số lượng item rơi theo chỉ số may mắn Luck của player) và `BonusLevelItems` (tăng theo level của mob).

**Prompt triển khai:**

> Nâng cấp Drop Table Engine của HaoHanLunar với các tính năng rơi đồ chuyên sâu từ MythicMobs 5.13.0: hỗ trợ DropsPerPlayer (sinh bảng loot riêng cho từng người chơi đạt điều kiện sát thương tối thiểu), DropsDoLootsplosion (bắn vật phẩm tung tóe từ xác boss), vật phẩm phát sáng (Glow) và tạo hiệu ứng cột sáng chùm tia (Beacon beam) cho đồ hiếm. Tích hợp chỉ số Luck của người chơi để tăng số lượng item rơi (BonusLuckItems). Viết unit test cho: chia loot độc lập cho 3 người chơi cùng đánh boss, kiểm tra điều kiện sát thương tối thiểu và tính toán bonus item theo chỉ số may mắn.

**Tiêu chí nghiệm thu:** Người chơi tham gia đánh boss nhận được phần thưởng công bằng tương xứng với sát thương đóng góp; hiệu ứng rơi đồ đẹp mắt và không gây lag.

## P4-2. Pity system và Drop history

**Mục tiêu:** Xây dựng hệ thống bảo hiểm rớt đồ (Pity System) đảm bảo người chơi chắc chắn nhận được vật phẩm hiếm sau một số lần săn boss nhất định.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa mô hình `DefaultPityManager` của MythicMobs:
  * Theo dõi số lần săn mob không rớt đồ hiếm theo cặp: `(PlayerUUID, MobId / DropTableId)`.
  * Mỗi khi boss chết và người chơi không nhận được item mục tiêu: `pityCounter++`.
  * Khi `pityCounter >= maxPity`: Tỉ lệ rớt của item mục tiêu cưỡng chế thành 100% (Guaranteed Drop), sau đó reset `pityCounter = 0`.
  * Dữ liệu Pity được lưu trữ bất đồng bộ (async queue) xuống database/file định kỳ, không làm gián đoạn combat tick.

**Prompt triển khai:**

> Xây dựng hệ thống Pity Manager (Bảo hiểm rơi đồ) cho HaoHanLunar theo kiến trúc DefaultPityManager của MythicMobs. Theo dõi số lần tiêu diệt boss không nhận được vật phẩm hiếm của từng người chơi theo UUID. Khi số lần săn đạt ngưỡng trần (max pity), đảm bảo 100% tỷ lệ rớt vật phẩm trong lần tiêu diệt tiếp theo và tự động reset bộ đếm về 0. Quá trình lưu trữ dữ liệu pity phải chạy bất đồng bộ ngoài main thread, có cơ chế ghi atomic chống mất dữ liệu khi server crash. Viết unit test cho: tăng bộ đếm pity, kích hoạt guaranteed drop tại ngưỡng trần và kiểm tra reset bộ đếm.

**Tiêu chí nghiệm thu:** Người chơi chạm ngưỡng pity chắc chắn nhận được đồ hiếm; bộ đếm pity hoạt động chính xác và không gây gián đoạn combat tick.

## P4-3. Scoped Variables và Typed Persistence

**Mục tiêu:** Cung cấp hệ thống biến số hoàn chỉnh với 4 phạm vi (GLOBAL, CASTER, TARGET/PLAYER, CAST) và hỗ trợ lưu trữ bền vững.

**Chi tiết kiến trúc theo MythicMobs:**
- Kiểu dữ liệu hỗ trợ: `INT`, `FLOAT`, `DOUBLE`, `STRING`, `BOOLEAN`, `LOCATION`.
- Các thao tác mechanic trên biến:
  * `setvariable{var=X;val=Y;type=INT;scope=CASTER}`
  * `variableadd{var=X;val=1;scope=CASTER}`
  * `variablesubtract{var=X;val=1;scope=CASTER}`
  * `variablemath{var=X;equation="<caster.var.X> * 1.5";scope=CASTER}`
- Cờ `save: true`: Tự động lưu biến vào PDC của mob hoặc player để không bị mất khi reload hoặc restart server.

**Prompt triển khai:**

> Nâng cấp hệ thống Scoped Variables cho HaoHanLunar hỗ trợ đầy đủ các kiểu dữ liệu: INT, FLOAT, DOUBLE, STRING, BOOLEAN và LOCATION trên 4 phạm vi: GLOBAL, CASTER, TARGET, CAST. Cung cấp các mechanic thao tác biến: setvariable, variableadd, variablesubtract, variablemath (tính toán biểu thức an toàn) và variableunset. Hỗ trợ cờ persistence cho phép lưu biến của mob và player vào PersistentDataContainer. Viết unit test cho: cộng trừ biến số, biến đổi kiểu dữ liệu, tính toán biểu thức và khôi phục biến sau khi load lại PDC.

**Tiêu chí nghiệm thu:** Biến số được cô lập chuẩn xác theo scope; các phép toán số học chạy ổn định; biến persistent tồn tại qua restart.

## P4-4. Placeholder provider và Expression Evaluator

**Mục tiêu:** Mở rộng bộ phân giải placeholder nội bộ và tích hợp bộ đánh giá toán học an toàn (exp4j) không dùng reflection.

**Chi tiết kiến trúc theo MythicMobs:**
- Bộ placeholder tiêu chuẩn của MythicMobs:
  * Mob data: `<mob.id>`, `<mob.name>`, `<mob.uuid>`, `<mob.health>`, `<mob.max_health>`, `<mob.stance>`, `<mob.level>`, `<mob.power>`, `<mob.threat.top>`.
  * Target data: `<target.name>`, `<target.uuid>`, `<target.health>`, `<target.distance>`, `<target.threat>`.
  * Variable placeholders: `<caster.var.<name>>`, `<target.var.<name>>`, `<global.var.<name>>`.
  * Expression placeholder: `<skill.calc.<equation>>` (ví dụ: `<skill.calc.<mob.health> * 0.1>`).
- **Safe Math Engine**: Dùng thư viện `exp4j` hoặc AST parser thuần Java để đánh giá biểu thức toán học. Tuyệt đối cấm dùng JavaScript Engine (Nashorn/GraalVM) để chống lỗ hổng bảo mật RCE.

**Prompt triển khai:**

> Xây dựng Placeholder Provider và Safe Expression Evaluator cho HaoHanLunar. Hỗ trợ đầy đủ các placeholder cốt lõi từ MythicMobs: mob info, target info, scoped variables và biểu thức toán học <skill.calc.equation>. Sử dụng exp4j hoặc tokenizer an toàn để tính toán biểu thức; cấm tuyệt đối việc sử dụng reflection, script engine hoặc class loading động. Có cơ chế chặn đệ quy vô hạn khi placeholder lồng nhau và rate-limit cảnh báo. Viết unit test cho: phân giải toàn bộ các placeholder mẫu, tính toán biểu thức toán phức tạp và từ chối biểu thức chứa ký tự độc hại.

**Tiêu chí nghiệm thu:** Phân giải placeholder mượt mà trong combat; biểu thức toán học tính toán chính xác; an toàn tuyệt đối trước mọi nguy cơ inject mã độc.

---

# P5 — Spawner và world rules nâng cao

## P5-1. Pack và Cluster Spawner

**Mục tiêu:** Nâng cấp hệ thống sinh quái hỗ trợ sinh theo bầy đàn (Packs/Clusters) và phân bố ngẫu nhiên quanh người chơi.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa các kiểu sinh quái `GeneratorType` của MythicMobs:
  * `ClusterGenerator`: Chọn một tâm ngẫu nhiên hợp lệ và sinh một bầy gồm 1 con đầu đàn (Leader/Alpha) kèm 2-5 quái tùy tùng (Minions/Pack).
  * `RegionalClusterGenerator`: Sinh bầy quái theo vùng địa lý hoặc quần xã chỉ định.
- Quản lý bầy đàn: Các quái trong cùng bầy tự động liên kết quan hệ Parent-Child (`parentUUID`), hỗ trợ hiệu ứng cùng tấn công mục tiêu khi con đầu đàn bị đánh (Pack Aggro).

**Prompt triển khai:**

> Triển khai hệ thống Pack & Cluster Spawning cho HaoHanLunar theo mô hình ClusterGenerator của MythicMobs. Cho phép cấu hình sinh quái theo bầy gồm quái đầu đàn và số lượng tùy tùng ngẫu nhiên. Tự động thiết lập quan hệ Parent-Child giữa các thành viên trong bầy. Triển khai cơ chế Pack Aggro: khi bất kỳ con nào trong bầy nhận sát thương, phát tín hiệu signal cho toàn bộ các con còn lại cùng chuyển mục tiêu sang kẻ tấn công. Đảm bảo toàn bộ bầy sinh ra tuân thủ giới hạn mob cap cục bộ. Viết unit test cho: thuật toán chọn vị trí cluster, gán parent UUID và lan truyền thù hận trong đàn.

**Tiêu chí nghiệm thu:** Quái xuất hiện tự nhiên theo bầy đàn; cả bầy cùng phối hợp tấn công khi bị kích động; không sinh quá mob cap của chunk.

## P5-2. Spawn Replacement Policy và Reason Filtering

**Mục tiêu:** Bắt các sự kiện sinh quái tự nhiên của Minecraft để thay thế bằng custom mob theo điều kiện khắt khe.

**Chi tiết kiến trúc theo MythicMobs:**
- Lắng nghe `CreatureSpawnEvent` của Paper:
  * Hỗ trợ lọc theo `SpawnReason`: `NATURAL`, `SPAWNER`, `CHUNK_GEN`, `PATROL`, `RAID`, `REINFORCEMENTS`. Mặc định bỏ qua các spawn reason nhân tạo (CUSTOM, SPAWN_EGG, COMMAND, BREEDING).
  * Action `REPLACE`: Xóa entity vanilla ban đầu và spawn custom mob của HaoHanLunar tại đúng tọa độ, giữ nguyên vận tốc và hướng nhìn.
  * Action `DENY`: Hủy bỏ hoàn toàn việc sinh quái vanilla tại khu vực quy định (dùng để cấm quái thường xuất hiện trong vùng đất boss Mặt Trăng).

**Prompt triển khai:**

> Xây dựng hệ thống Spawn Replacement Policy cho HaoHanLunar theo mô hình MythicMobs RandomSpawner Action. Lắng nghe CreatureSpawnEvent, lọc chính xác theo SpawnReason (ưu tiên NATURAL và CHUNK_GEN). Khi rule REPLACE kích hoạt, hủy hoặc xóa entity vanilla ngay lập tức và spawn ActiveLunarMob tương ứng, kế thừa hướng nhìn yaw/pitch. Hỗ trợ action DENY để ngăn chặn triệt để mob vanilla sinh ra tại dimension Mặt Trăng. Toàn bộ quá trình phải tôn trọng Paper Mob Cap. Viết unit test cho: lọc spawn reason, kịch bản thay thế entity và kịch bản từ chối spawn.

**Tiêu chí nghiệm thu:** Quái vanilla được thay thế mượt mà bằng custom mob; không sinh lặp vô hạn do sự kiện spawn mới; cấm triệt để quái thường tại vùng cấm.

## P5-3. Fixed Spawner Leashing và hồi phục

**Mục tiêu:** Hoàn thiện cơ chế dắt dây (Leashing) và hồi máu của Spawner cố định khi quái bị kéo đi quá xa vị trí ban đầu.

**Chi tiết kiến trúc theo MythicMobs:**
- Trên `MythicSpawner`:
  * Mỗi chu kỳ tick, kiểm tra khoảng cách của toàn bộ các mob được spawner quản lý (`trackedMobs`) tới tọa độ của spawner.
  * Nếu `distance > leashRange`:
    * Hủy bỏ mục tiêu hiện tại của mob (`mob.setTarget(null)`).
    * Xóa sạch ThreatTable (`threatTable.clear()`).
    * Nếu `healOnLeash == true`: Hồi phục 100% máu cho mob.
    * Dịch chuyển (teleport) mob trở về ngay vị trí spawner kèm hiệu ứng particle/sound.

**Prompt triển khai:**

> Hoàn thiện cơ chế Leashing (Dắt dây hồi phục) cho Fixed Spawner theo chuẩn MythicSpawner của MythicMobs. Định kỳ kiểm tra khoảng cách của các mob được spawner quản lý. Khi mob vượt quá bán kính leashRange: xóa toàn bộ điểm thù hận trong ThreatTable, hủy target của mob, hồi đầy 100% máu nếu bật healOnLeash và dịch chuyển mob trở lại bệ spawn kèm hiệu ứng. Đảm bảo quá trình kiểm tra chạy trên scheduler trung tâm không gây lag. Viết unit test cho: phát hiện mob vượt leashRange, reset thù hận và teleport hồi phục an toàn.

**Tiêu chí nghiệm thu:** Người chơi không thể dụ boss đi quá xa đấu trường để ăn gian (exploit); boss tự động quay về bệ và hồi máu khi bị kéo quá tầm.

---

# P6 — Integration và Public API

## P6-1. Public Event API (`vn.haohan.lunar.api.v1`)

**Mục tiêu:** Cung cấp bộ API và sự kiện công khai chuẩn mực cho các plugin khác trên server hook vào.

**Chi tiết kiến trúc:**
- Package: `vn.haohan.lunar.api.v1.event`
- Các event cốt lõi:
  * `MobSpawnEvent` (Cancellable)
  * `MobDeathEvent` (Lấy được killer, damage record, drop table)
  * `SkillPreCastEvent` (Cancellable, thay đổi được power hoặc target)
  * `SkillPostCastEvent`
  * `MobDamageEvent` (Cancellable, can thiệp DamageContext)
  * `MobPhaseChangeEvent` (Cancellable)
  * `LootGenerateEvent` (Can thiệp danh sách item rơi)

**Prompt triển khai:**

> Xây dựng bộ Public Event API cho HaoHanLunar tại package `vn.haohan.lunar.api.v1.event`. Định nghĩa các sự kiện Bukkit chuẩn: LunarMobSpawnEvent, LunarMobDeathEvent, LunarSkillPreCastEvent, LunarSkillPostCastEvent, LunarMobDamageEvent, LunarMobPhaseChangeEvent và LunarLootGenerateEvent. Các sự kiện cần thiết phải triển khai Cancellable và cung cấp các getter bất biến an toàn. Viết tài liệu Javadoc chi tiết cho từng event và viết test kiểm tra listener bên ngoài có thể bắt và hủy bỏ sự kiện chính xác.

**Tiêu chí nghiệm thu:** Các plugin khác có thể can thiệp vào vòng đời mob và skill thông qua Event API mà không cần truy cập class nội bộ.

## P6-2. HaoHanItemCore Provider

**Mục tiêu:** Chuẩn hóa việc phân giải vật phẩm custom thông qua interface `ItemProvider` độc lập.

**Chi tiết:**
- Tạo interface `ItemProvider` với phương thức `resolveItem(String id, int amount, ItemContext ctx): Optional<ItemStack>`.
- Triển khai `HaoHanItemCoreBridge`: Truy vấn item từ API của HaoHanItemCore nếu plugin này có mặt trên server.
- Fallback an toàn: Nếu không tìm thấy hoặc plugin vắng mặt, tra cứu theo enum `Material` vanilla.

**Prompt triển khai:**

> Xây dựng `ItemProvider` interface và triển khai `HaoHanItemCoreBridge`. Bridge thực hiện truy vấn ItemStack từ HaoHanItemCore dựa trên custom item ID, sao chép an toàn và áp dụng số lượng yêu cầu. Nếu HaoHanItemCore chưa load hoặc không tìm thấy ID, fallback tra cứu theo Bukkit Material. Tuyệt đối không để code combat hoặc drop table gọi trực tiếp static API của HaoHanItemCore. Viết unit test với fake ItemProvider kiểm tra tra cứu thành công, fallback an toàn và xử lý ngoại lệ khi thiếu plugin.

**Tiêu chí nghiệm thu:** Toàn bộ việc sinh item drop và trang bị đều thông qua ItemProvider; plugin khởi động bình thường ngay cả khi không có HaoHanItemCore.

---

# P7 — Tooling, debug và content creator support

## P7-1. Config Validator Command

**Mục tiêu:** Cung cấp lệnh `/lunarmob validate` kiểm tra toàn bộ file cấu hình mà không cần khởi động lại hoặc reload server.

**Prompt triển khai:**

> Xây dựng subcommand `/lunarmob validate [mobs|skills|drops|spawners]` cho admin và người tạo nội dung. Lệnh thực hiện duyệt toàn bộ file YAML trong thư mục tương ứng, kiểm tra cú pháp, trường bắt buộc, kiểu dữ liệu, liên kết ID chéo và in ra báo cáo tổng hợp trực quan trong chat: danh sách file hợp lệ, danh sách warning và danh sách error kèm số dòng cụ thể. Lệnh là pure validator: không làm thay đổi hay tráo đổi registry đang chạy. Viết unit test kiểm tra output format của lệnh khi quét thư mục có cấu hình hỏng.

**Tiêu chí nghiệm thu:** Admin có thể kiểm tra cấu hình mới viết ngay trong game; phát hiện 100% lỗi cú pháp và liên kết thiếu trước khi áp dụng reload.

## P7-2. Skill Tracer và Performance Metrics

**Mục tiêu:** Cung cấp công cụ theo dõi luồng thực thi kỹ năng (`/lunarmob trace`) và giám sát hiệu năng theo thời gian thực.

**Chi tiết kiến trúc theo MythicMobs:**
- Kế thừa cơ chế Debug Trace của MythicMobs:
  * Khi bật trace trên một mob (`/lunarmob trace <uuid|target> on`): Mỗi tick skill thi triển sẽ in ra chi tiết: Tên trigger, kết quả kiểm tra từng condition (PASS / FAIL), danh sách target tìm được, thời gian thực thi của từng mechanic và sát thương thực tế gây ra.
  * Tự động tắt sau 60 giây để không gây ngập log server.
- Đo lường Metrics: Đo thời gian thực thi (p95 tick time) của SkillScheduler, ProjectileTracker và SpawnerManager.

**Prompt triển khai:**

> Xây dựng hệ thống Skill Tracer và Performance Metrics cho HaoHanLunar. Cho phép admin bật theo dõi chi tiết trên một mob cụ thể qua lệnh `/lunarmob trace`. Khi bật, ghi nhận chi tiết: trigger kích hoạt, kết quả từng condition, số lượng target, thời gian chạy của mechanic và sát thương cuối cùng. Tự động tắt trace sau 60 giây. Đo đạc và ghi nhận thời gian thực thi p95 của SkillScheduler mỗi 100 tick để cảnh báo sớm tình trạng tụt TPS. Viết unit test cho: ghi trace chính xác, tự động timeout dọn dẹp và xuất báo cáo metrics.

**Tiêu chí nghiệm thu:** Người tạo kỹ năng dễ dàng tìm ra nguyên nhân vì sao skill không cast hoặc chọn sai mục tiêu; hệ thống tự động cảnh báo khi có skill tiêu tốn quá nhiều CPU.

---

# P8 — Boss presentation và visual layer

## P8-1. Multi BossBar và Custom Nameplate

**Mục tiêu:** Hỗ trợ hiển thị nhiều BossBar độc lập (Multiple Named BossBars), thanh niệm chiêu (CastBar) và Nameplate tùy biến.

**Chi tiết kiến trúc theo MythicMobs:**
- Trên `ActiveLunarMob`: Quản lý `Map<String, BossBar> bossBars`.
  * Hỗ trợ nhiều thanh BossBar đồng thời (ví dụ: thanh Máu chính, thanh Giáp ảo Shield, thanh Nộ khí).
  * Điều khiển linh hoạt qua mechanic: `barcreate`, `barset{bar=X;value=Y}`, `barremove`.
  * Hiển thị tự động theo bán kính người chơi xung quanh (`bossBarRangeSquared`).
- Nameplate động hiển thị tên, level và phần trăm máu bằng MiniMessage format.

**Prompt triển khai:**

> Xây dựng hệ thống Multi-BossBar và Custom Nameplate cho ActiveLunarMob theo mô hình MythicMobs 5.13.0. Cho phép một boss sở hữu nhiều thanh BossBar độc lập (thanh máu, thanh khiên, thanh nộ) với màu sắc và kiểu dáng tùy chỉnh qua Adventure API. Cung cấp các mechanic barcreate, barset, barremove để kỹ năng có thể điều khiển trực tiếp giá trị của thanh bar. Tự động hiển thị/ẩn thanh bar khi người chơi đi vào/ra khỏi bán kính quan sát. Dọn dẹp sạch sẽ toàn bộ thanh bar khi boss chết hoặc despawn. Viết unit test cho: tạo nhiều thanh bar, cập nhật tiến trình và dọn dẹp khi entity bị xóa.

**Tiêu chí nghiệm thu:** Boss có thể hiển thị thanh máu kết hợp thanh nộ/khiên mượt mà; người chơi ra xa tự động ẩn bossbar; không để lại bossbar rác khi boss chết.

---

# Backlog feature-parity bổ sung (P9 – P28)

## P19 — AI, pathfinding và Paper Mob Goals API

### P19-1. Paper Mob Goals API integration

> Triển khai hệ thống AI Goal Selectors và Target Selectors cho ActiveLunarMob dựa trên **Paper 1.21.1 Mob Goals API (`Mob#getPathfinder()`)**. Khai báo trong YAML theo mô hình MythicMobs:
> ```yaml
> AIGoalSelectors:
> - clear
> - movetotarget 1.2
> - lookatplayers
> - patrol 1.0
> AITargetSelectors:
> - clear
> - targetplayers
> - targetdamagers
> ```
> Sử dụng các Paper VanillaGoal và CustomGoal thuần Java; tuyệt đối không gọi mã NMS nguy hiểm để bảo đảm an toàn khi Paper cập nhật.

### P19-2. Navigation safety và Anti-Stuck

> Triển khai cơ chế chống kẹt đường (Anti-Stuck): Khi mob có mục tiêu nhưng không thể di chuyển trong 100 tick liên tiếp (do địa hình hoặc chướng ngại vật), tự động kích hoạt hành động gỡ kẹt: nhảy qua chướng ngại, phá hủy khối mềm (nếu được phép) hoặc teleport khoảng cách ngắn về phía mục tiêu.

## P20 — Pins và Pin Regions System (Đấu trường không gian)

### P20-1. PinManager và PinRegion

> Xây dựng hệ thống Pins và Pin Regions hoàn chỉnh theo kiến trúc `io.lumine.mythic.core.skills.pins` của MythicMobs 5.13.0:
> - `SinglePin`: Điểm neo không gian 3D xác định (`World, x, y, z`).
> - `PinRegion`: Vùng đa giác không gian 3D được tạo thành từ tập hợp nhiều Pin (dùng để xác định đấu trường boss arena).
> - Lệnh quản trị: `/lunarmob pins wand` (công cụ chọn điểm bằng gậy), `/lunarmob pins add <name>`, `/lunarmob pins create_region <name> <pin1> <pin2>...`.

### P20-2. Targeters, Conditions và Triggers theo Pin

> Tích hợp Pin System vào Skill Runtime:
> - **Targeters**:
>   * `@BlocksInPinRegion{region=ARENA_1}`: Chọn toàn bộ block trong vùng đấu trường (dùng cho hiệu ứng sàn đấu đổi màu, sàn bốc cháy).
>   * `@EntitiesNearPin{pin=CENTER;r=10}`: Chọn entity quanh điểm neo trung tâm.
> - **Conditions**:
>   * `inpinregion{region=ARENA_1}`: Kiểm tra mục tiêu có đang đứng trong đấu trường không.
>   * `distancefrompin{pin=CENTER;d=<20}`: Kiểm tra khoảng cách tới điểm neo.
> - **Triggers**:
>   * `~onEnterBounds:<region>`: Kích hoạt khi người chơi bước vào vùng đấu trường (bắt đầu nhạc boss, đóng cửa đấu trường).
>   * `~onExitBounds:<region>`: Kích hoạt khi người chơi tìm cách chạy trốn khỏi đấu trường.

## P21 — Custom Stat Registry và Stat Modifiers

### P21-1. Stat Registry và Core Stat Types

> Xây dựng hệ thống chỉ số chiến đấu chuyên sâu cho ActiveLunarMob theo mô hình `io.lumine.mythic.core.skills.stats` của MythicMobs 5.13.0:
> - Định nghĩa các `StatType`: `DAMAGE`, `ARMOR`, `CRIT_CHANCE`, `CRIT_DAMAGE`, `LIFESTEAL`, `COOLDOWN_REDUCTION`, `LUCK`, `KNOCKBACK_RESISTANCE`.
> - Cơ chế Modifier: Hỗ trợ 3 phép toán biến đổi: `FLAT` (+10 giáp), `PERCENT_ADD` (+20% damage), `PERCENT_MULT` (*1.15).
> - Tích hợp `StatSnapshot` vào `DamagePipeline` để tính toán sát thương chí mạng, hút máu và giảm hồi chiêu tự động.

## P22 — Folia Scheduler Seam và Concurrency

### P22-1. Scheduler Abstraction Seam

> Thiết kế lớp trừu tượng `LunarPlatformScheduler` phân tách rõ ràng giữa Paper Bukkit Scheduler truyền thống và Folia Regionized / Entity Scheduler (`runTimerSkills` vs `runTimerSkillsFolia`).
> - Mọi thao tác tác động lên entity phải được dispatch qua `entity.getScheduler().run(...)`.
> - Mọi thao tác tác động lên khối/vùng không gian phải được dispatch qua `Bukkit.getRegionScheduler().run(...)`.
> - Đảm bảo kiến trúc sẵn sàng tương thích hoàn hảo khi server chuyển đổi từ Paper sang Folia.

---

# Quy tắc sử dụng prompt

Khi giao việc cho agent/developer triển khai, luôn gửi nguyên văn prompt của **đúng một task**, kèm các chỉ thị bắt buộc:

1. Chỉ chỉnh sửa các file thuộc phạm vi của task được giao.
2. Viết unit/integration test đầy đủ kiểm chứng cho tính năng mới.
3. Chạy lệnh `gradlew.bat build` thành công trước khi báo cáo hoàn tất.
4. Tuyệt đối không tự ý thực hiện các task phía sau nếu chưa có yêu cầu.
