# Robot Battery & MagSafe Charger Rewrite Implementation Plan

Rewrite the robot battery system and charger mechanic in `HaoHanLunar`:
1. Single battery model (nominal 35,000 EU) with exponential health decay and charge cycle tracking on the robot dashboard.
2. Replacement of the old charger with MagSafe `robot_charger` (formerly test item `hologram_projector`), with 4 visual states (Red, Yellow, Cyan, Blue), single fuel source `raw_anorthosite` (+3500 EU / 4s), and autonomous robot docking with sit animation.

## Proposed Architecture & Design Decisions

### 1. Battery & Health Mechanics
- **Item ID**: `haohan:robot_battery_small` (Display name: `§b§lPin Robot`, reuse existing model & CustomModelData). Remove `medium` and `large` tiers.
- **Nominal Capacity**: $C_0 = 35,000\text{ EU}$.
- **Health Parameter**: $H \in [0.0, 100.0]\%$ (default $100.0\%$).
- **Effective Capacity Formula (Exponential Decay)**:
  $$C_{\text{eff}} = \max\left(1000, \text{round}\left(C_0 \times \left(\frac{H}{100.0}\right)^2\right)\right)$$
  - At $100\%$ health: $C_{\text{eff}} = 35,000\text{ EU}$
  - At $90\%$ health: $C_{\text{eff}} \approx 28,350\text{ EU}$
  - At $70\%$ health: $C_{\text{eff}} \approx 17,150\text{ EU}$
  - At $50\%$ health: $C_{\text{eff}} \approx 8,750\text{ EU}$
- **Charge Cycle Tracking**:
  - `chargeCycles` tracked in `LunarRobotData` and item PDC (`haohan:battery_cycles`).
  - Completed charging session increments cycles by $+1$, and degrades health: $H = \max(5.0, H - 0.5)$.
  - Exposed in Robot Dashboard UI (Slot 3 card and Detailed Statistics panel).

### 2. MagSafe Charger (`haohan:robot_charger`)
- **Item & Block Identity**:
  - Craftable / Droppable item: `haohan:robot_charger` (Display Name: `§9§lTrạm Sạc MagSafe Robot`, Material: `BARRIER`, CMD: `6206` or `6210`).
  - State Models (ItemDisplay):
    - `haohan:robot_charger_red` (CMD 6207): **RED** - Trạm không khả dụng (Fuel buffer = 0).
    - `haohan:robot_charger` / `haohan:robot_charger_blue` (CMD 6206/6210): **BLUE** - Khả dụng (Fuel > 0, sẵn sàng chờ robot).
    - `haohan:robot_charger_yellow` (CMD 6208): **YELLOW** - Đang có robot ngồi sạc.
    - `haohan:robot_charger_cyan` (CMD 6209): **CYAN** - Robot đã sạc đầy xong.
- **Fuel System**:
  - STRICTLY ONLY `haohan:raw_anorthosite`.
  - Rate: $+3,500\text{ EU}$ over 4 seconds ($875\text{ EU/s}$) per raw anorthosite.
  - Quick interaction: Right-click charger with `haohan:raw_anorthosite` adds to fuel queue / buffer.
- **Physical Clearance & Spatial Protection ($3 \times 3 \times 2$)**:
  - **Pre-placement Clearance**: Placing the station requires a completely clear $3 \times 3 \times 2$ bounding volume (centered at $(X_0, Y_0, Z_0)$ where $X \in [X_0-1, X_0+1]$, $Z \in [Z_0-1, Z_0+1]$, and $Y \in [Y_0, Y_0+1]$). If any block in this volume is non-air/non-replaceable, cancel placement and alert the player.
  - **In-use Protection Zone**: Whenever a charger station exists, any attempt by a player to place a block within its $3 \times 3 \times 2$ perimeter is cancelled via `BlockPlaceEvent`, ensuring the robot always has an unobstructed docking and sitting space.
- **Autonomous Robot AI & MagSafe Docking**:
  - Robot scans for nearby active chargers within 16 blocks when energy $< C_{\text{eff}}$, not mounted, and not manually ordered to sit.
  - Pathfinds to charger; within 1.5 blocks, stops, faces charger, and triggers `"sit"` animation.
  - Sits and charges at random rate between $350 - 780\text{ EU/s}$.
  - When energy reaches $C_{\text{eff}}$, charger turns CYAN, cycle increments, robot stands up and returns to follow/idle.

---

## User Review Required

> [!IMPORTANT]
> The old charger item `haohan:battery_charger` and tiered batteries `haohan:robot_battery_medium` and `haohan:robot_battery_large` will be purged from recipes and active registrations. Existing items in player inventories will safely fall back to standard battery/charger definitions.

---

## Proposed Changes

### Package `vn.haohan.lunar.robot.battery`
#### [RobotBatteryUtil.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/battery/RobotBatteryUtil.java)
- Standardize on `haohan:robot_battery_small` (labeled `Pin Robot`), remove medium/large constants.
- Nominal capacity constant: `NOMINAL_CAPACITY = 35000`.
- PDC keys: `battery_energy`, `battery_health`, `battery_cycles`.
- Methods:
  - `calculateEffectiveCapacity(double health)`
  - `getBatteryHealth(ItemStack item)`
  - `setBatteryHealth(ItemStack item, double health)`
  - `getBatteryCycles(ItemStack item)`
  - `incrementBatteryCycles(ItemStack item)`
  - Updated `updateBatteryLore(...)` reflecting health %, cycles, and effective capacity.

---

### Package `vn.haohan.lunar.robot`
#### [LunarRobotData.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotData.java)
- Fields:
  - `batteryHealth` (default `100.0`)
  - `chargeCycles` (default `0`)
  - `chargingStationLocation` / `dockedCharging` boolean
- Update `getMaxEnergy()` to return `RobotBatteryUtil.calculateEffectiveCapacity(batteryHealth)` when battery installed.
- Save/load PDC keys: `robot_battery_health`, `robot_charge_cycles`.

#### [LunarRobotEntity.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/LunarRobotEntity.java)
- In autonomous movement loop:
  - Detect nearby available `robot_charger` stations (within 16 blocks).
  - MagSafe docking routine: navigate to charger, stop at 1.5m, play `"sit"` animation.
  - Tick charging from station buffer with random rate $350 - 780\text{ EU/s}$.
  - Completion logic: trigger cycle count, update health, play `"idle"`, stand up.

---

### Package `vn.haohan.lunar.charger`
#### [BatteryChargerStation.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/charger/BatteryChargerStation.java)
- Add station states: `ChargerState { RED, BLUE, YELLOW, CYAN }`.
- Track docked robot UUID.
- Raw anorthosite fuel queue / consumption ticker (+3500 EU / 4s).

#### [BatteryChargerMechanic.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/charger/BatteryChargerMechanic.java)
- Rename references and items from `hologram_projector` / `battery_charger` to `haohan:robot_charger`.
- Update state transitions and update ItemDisplay model item (`robot_charger_red`, `robot_charger`, `robot_charger_yellow`, `robot_charger_cyan`).
- Fuel handling: reject anything except `haohan:raw_anorthosite`.
- Quick-feeding: Right-click charger with `haohan:raw_anorthosite` adds to fuel.
- $3 \times 3 \times 2$ Space Enforcement:
  - Add helper `isAreaClearForStation(Location center)` to check $X \pm 1, Z \pm 1, Y$ to $Y + 1$.
  - In `onBlockPlace` (`BlockPlaceEvent`): check if placement coordinates intersect any registered station's $3 \times 3 \times 2$ bounding box. If so, cancel event with message `"§c§l[Trạm Sạc] §cKhông thể đặt khối trong vùng hoạt động 3x3x2 của Trạm Sạc!"`.

---

### Package `vn.haohan.lunar.item`
#### [LunarItems.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/item/LunarItems.java)
- Replace `haohan:hologram_projector*` definitions with `haohan:robot_charger`, `haohan:robot_charger_blue`, `haohan:robot_charger_red`, `haohan:robot_charger_yellow`, `haohan:robot_charger_cyan`.
- Register crafting recipe for `haohan:robot_charger` (giving blue projector item).
- Simplify battery recipe to single `haohan:robot_battery_small` ("Pin Robot", 35,000 EU).
- Remove recipes for medium/large batteries and old battery charger.

---

### Package `vn.haohan.lunar.robot.ui`
#### [LunarRobotDashboardUi.java](file:///home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/robot/ui/LunarRobotDashboardUi.java)
- Update Slot 3 battery display: Show "Pin Robot", Health %, and Energy / Effective Capacity.
- Update Detailed Stats panel: Add "Chu kỳ sạc: <cycles> lần" and "Sức khỏe pin: <health>%".

---

## Verification Plan

### Automated Tests
- `RobotBatteryAndChargerTest`:
  - `testBatteryNominalAndEffectiveCapacity()`: Verify 35,000 EU nominal and exponential degradation curve.
  - `testBatteryHealthDegradationAndCycles()`: Verify cycle incrementation and $-0.5\%$ degradation.
  - `testChargerFuelOnlyRawAnorthosite()`: Verify only `raw_anorthosite` is accepted and converts +3500 EU / 4s.
  - `testChargerSpaceClearanceAndProtection()`: Verify $3 \times 3 \times 2$ bounding volume validation and block obstruction rejection.
  - `testChargerFourStateTransitions()`: Verify Red -> Blue -> Yellow -> Cyan transitions based on fuel and docking.
  - `testRandomChargeRateBounds()`: Verify random rate generates values strictly between 350 and 780 EU.
- `./gradlew test`: Run entire test suite to ensure no regressions across dimension, bossbar, and combat modules.

### Manual Verification Steps
1. Craft `haohan:robot_charger` using new recipe.
2. Place charger in world -> verify initial state is 🔴 RED (empty fuel).
3. Right-click with `haohan:raw_anorthosite` -> verify fuel adds (+3500 EU / 4s) and state turns 🔵 BLUE.
4. Spawn/tame a Robot with energy < 35,000 EU near the charger -> verify robot walks to charger, sits down with animation, and state turns 🟡 YELLOW.
5. Verify random charging between 350-780 EU/s until full.
6. When full, verify state turns 🔷 CYAN, robot stands up, charge cycles increments on Dashboard, and health decreases appropriately.
