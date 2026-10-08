# Standardize Custom Block Material Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize custom block item representation to `Material.BARRIER` across `HaoHanItemCore` and `HaoHanLunar`, introduce `ItemType.BLOCK`, update `/im browse` indexing and sample custom blocks, and enable survival placement for barrier-based custom block items.

**Architecture:** Custom block items are uniformly defined with `Material.BARRIER` and `ItemType.BLOCK`. `ItemCategory.CUSTOM_BLOCKS` cleanly indexes them in `/im browse`. Survival mode right-click placement of `Material.BARRIER` custom block items is handled via `PlayerInteractEvent` in `CustomBlockEventListener`, invoking `BlockPlaceEvent` and `CustomBlockService.placeCustomBlock()`.

**Tech Stack:** PaperMC 1.21.4 API, PacketEvents 2.7.0, JUnit 5, Mockito.

**Spec:** User request to update custom block items in `/im browse` and standardize on 1 primary material type (`Material.BARRIER`) following the new Virtual BlockDisplay architecture.

## Global Constraints
- Follow **Ponytail** principles: minimal diffs, zero speculative abstractions, reuse existing Bukkit/Paper APIs.
- Physical in-world block and inventory item representation standardized to `Material.BARRIER`.
- Backward compatibility: existing custom block properties (`custom_block_id`, `custom_block_model`, `custom_block_drop`) remain intact.
- Both repositories (`HaoHanItemCore` and `HaoHanLunar`) must pass `./gradlew test`.

## Review Focus
- **Survival Placement Failure:** Vanilla client suppresses block placement packets for `Material.BARRIER` in survival mode. Solved by handling `PlayerInteractEvent` with `Action.RIGHT_CLICK_BLOCK`.
- **Category Overlap in `/im browse`:** Custom block items previously configured with `ItemType.SPECIAL` appeared under both `SPECIAL` and `CUSTOM_BLOCKS`. Solved by introducing `ItemType.BLOCK` and strictly partitioning `SPECIAL`.
- **Missing Custom Blocks in `/im browse`:** `HaoHanItemCore`'s `example.yml` contained no custom block items. Solved by providing standard `example:copper_casing` and `example:ruby_ore`.
- **Existing Lunar Ore Compatibility:** Lunar mining mechanic and quadruped robot scans require `Material.BARRIER` ores to be seamlessly recognized.
- **Null Safety on Block Interactivity:** Right-clicking existing custom blocks or invalid faces must not cause exceptions.

---

### Task 1: Add `ItemType.BLOCK` and update `ItemCategory` in `HaoHanItemCore`

**Files:**
- Modify: `/home/paithon/Projects/HaoHanSMP/HaoHanItemCore/src/main/java/vn/haohan/itemcore/api/item/ItemType.java`
- Modify: `/home/paithon/Projects/HaoHanSMP/HaoHanItemCore/src/main/java/vn/haohan/itemcore/api/item/ItemCategory.java`
- Test: `/home/paithon/Projects/HaoHanSMP/HaoHanItemCore/src/test/java/vn/haohan/itemcore/ItemFilterTest.java`

**Interfaces:**
- Consumes: `ItemType`, `ItemDefinition`, `Material.BARRIER`
- Produces: `ItemType.BLOCK`, updated `ItemCategory.CUSTOM_BLOCKS.matches()`

- [ ] **Step 1: Write the failing test in `ItemFilterTest.java`**

Add tests verifying:
1. `ItemType.BLOCK` item matches `ItemCategory.CUSTOM_BLOCKS`.
2. `ItemType.BLOCK` item does NOT match `ItemCategory.SPECIAL`.
3. `Material.BARRIER` item with `custom_block_id` matches `ItemCategory.CUSTOM_BLOCKS`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests vn.haohan.itemcore.ItemFilterTest` in `HaoHanItemCore`.
Expected: Compilation failure or assertion failure due to missing `ItemType.BLOCK`.

- [ ] **Step 3: Implement `ItemType.BLOCK` and `ItemCategory` matching**

1. In `ItemType.java`:
```java
public enum ItemType {
    MATERIAL,
    TOOL,
    WEAPON,
    ARMOR,
    FOOD,
    MACHINE_COMPONENT,
    CURRENCY,
    BLOCK,
    SPECIAL
}
```
2. In `ItemCategory.java`:
Update `matches`:
```java
case CUSTOM_BLOCKS -> type == ItemType.BLOCK || isCustomBlock(def, mat, matName);
```
And in `isCustomBlock`:
```java
private static boolean isCustomBlock(ItemDefinition def, Material mat, String matName) {
    if (def.getType() == ItemType.BLOCK) {
        return true;
    }
    if (mat == Material.BARRIER) {
        return true;
    }
    return def.getProperties().containsKey("custom_block_id") ||
           def.getProperties().containsKey("custom_block_model") ||
           def.getProperties().containsKey("custom_block_data") ||
           def.getProperties().containsKey("is_block") ||
           def.getProperties().containsKey("block_type") ||
           def.getProperties().containsKey("custom_block_drop");
}
```
And isolate `SPECIAL`:
```java
case SPECIAL -> (type == ItemType.SPECIAL || (!isTool(def, type, matName) && !isWeapon(def, type, matName) && !isArmor(def, type, matName))) && !isCustomBlock(def, mat, matName);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests vn.haohan.itemcore.ItemFilterTest` in `HaoHanItemCore`.
Expected: PASS.

---

### Task 2: Implement Survival Placement for `Material.BARRIER` Custom Blocks

**Files:**
- Modify: `/home/paithon/Projects/HaoHanSMP/HaoHanItemCore/src/main/java/vn/haohan/itemcore/internal/block/CustomBlockEventListener.java`

**Interfaces:**
- Consumes: `PlayerInteractEvent`, `CustomBlockService.placeCustomBlock(Location, String)`
- Produces: Seamless placement of `Material.BARRIER` custom block items in survival/adventure mode.

- [ ] **Step 1: Update `CustomBlockEventListener.onPlayerInteract`**

In `CustomBlockEventListener.java`:
Handle `PlayerInteractEvent`:
When `event.getAction() == Action.RIGHT_CLICK_BLOCK`:
1. Check if the held item has `Material.BARRIER` and has `itemIdKey` in PDC resolving to a `customBlockId`.
2. If player is in `GameMode.SURVIVAL` or `GameMode.ADVENTURE`:
   - Calculate target block location (`clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace())`).
   - Validate target block is replaceable or empty, and within world min/max height limits.
   - Fire a new `BlockPlaceEvent(target, target.getState(), clicked, item, player, true, event.getHand())`.
   - If not cancelled:
     - Set `target.setType(Material.BARRIER, false)`.
     - Call `customBlockService.placeCustomBlock(target.getLocation(), customBlockId)`.
     - Decrement hand item stack by 1.
     - Call `player.swingHand(event.getHand())`.
     - Play placement sound `target.getWorld().playSound(target.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 0.8f)`.
     - Cancel the interact event to prevent any unwanted interactions.

- [ ] **Step 2: Run all tests in `HaoHanItemCore`**

Run: `./gradlew test` in `HaoHanItemCore`.
Expected: All tests pass.

---

### Task 3: Add Sample Custom Blocks in `HaoHanItemCore` and Publish

**Files:**
- Modify: `/home/paithon/Projects/HaoHanSMP/HaoHanItemCore/src/main/resources/items/example.yml`

**Interfaces:**
- Produces: Built-in custom blocks displayed when opening `/im browse` -> "Custom Block".

- [ ] **Step 1: Add custom block items in `example.yml`**

Add `example:copper_casing` and `example:ruby_ore` using `material: BARRIER`, `type: BLOCK`, with `custom_block_id`, `custom_block_model`, and `custom_block_drop`.

- [ ] **Step 2: Run test suite in `HaoHanItemCore`**

Run: `./gradlew test` in `HaoHanItemCore`.
Expected: PASS.

- [ ] **Step 3: Publish to Maven Local**

Run: `./gradlew publishToMavenLocal` in `HaoHanItemCore`.
Expected: BUILD SUCCESSFUL.

---

### Task 4: Standardize Lunar Custom Block Ores to `Material.BARRIER` in `HaoHanLunar`

**Files:**
- Modify: `/home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/main/java/vn/haohan/lunar/item/LunarItems.java`
- Modify: `/home/paithon/Projects/HaoHanSMP/HaoHanLunar/src/test/java/vn/haohan/lunar/block/LunarCustomBlockAdaptationTest.java`

**Interfaces:**
- Consumes: `ItemType.BLOCK`, `Material.BARRIER`, `CustomBlockService`
- Produces: Standardized Lunar ore items with `material: BARRIER` and `type: BLOCK`.

- [ ] **Step 1: Update Lunar ore definitions in `LunarItems.java`**

Change the 4 ores:
- `haohan:anorthosite_ore`
- `haohan:ilmenite_ore`
- `haohan:pyroxene_ore`
- `haohan:kreep_basalt`
From:
```java
.material(Material.NOTE_BLOCK)
.type(ItemType.SPECIAL)
```
To:
```java
.material(Material.BARRIER)
.type(ItemType.BLOCK)
```

- [ ] **Step 2: Update assertions in `LunarCustomBlockAdaptationTest.java`**

Verify in `testLunarOresHaveCustomBlockProperties`:
- `assertEquals(Material.BARRIER, def.getMaterial(), oreId + " must use Material.BARRIER");`
- `assertEquals(ItemType.BLOCK, def.getType(), oreId + " must use ItemType.BLOCK");`
- `assertTrue(ItemCategory.CUSTOM_BLOCKS.matches(def), oreId + " must match ItemCategory.CUSTOM_BLOCKS");`

- [ ] **Step 3: Run test suite in `HaoHanLunar`**

Run: `./gradlew test` in `HaoHanLunar`.
Run: `./gradlew validateContent` in `HaoHanLunar`.
Expected: PASS.

- [ ] **Step 4: Commit changes in both repositories**

In `HaoHanItemCore`:
```bash
git add .
git commit -m "feat(block): standardize custom block material to BARRIER and add ItemType.BLOCK"
```
In `HaoHanLunar`:
```bash
git add .
git commit -m "feat(lunar): standardize lunar ores to Material.BARRIER and ItemType.BLOCK"
```
