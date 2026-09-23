package vn.haohan.lunar.core.system.item;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.command.LunarMobCommand;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Phase9CustomItemAndSkillTest {

    private ItemDefinitionRegistry registry;
    private ItemCooldownManager cooldownManager;
    private ItemSkillRuntime runtime;

    @BeforeEach
    void setUp() {
        registry = new ItemDefinitionRegistry();
        cooldownManager = new ItemCooldownManager();
        runtime = new ItemSkillRuntime(registry, cooldownManager);
    }

    @Test
    void testLoadMythicItemDefinitionFromYamlMap() {
        Map<String, Object> yaml = Map.of(
                "Material", "NETHERITE_SWORD",
                "Display", "<gradient:#ff4500:#8a2be2><b>Lunar Eclipse Blade</b></gradient>",
                "Lore", List.of(
                        "<gray>Thanh kiáº¿m rÃ¨n tá»« máº£nh vá»¡ Máº·t TrÄƒng.",
                        "<yellow>Ká»¹ nÄƒng chá»§ Ä‘á»™ng: <gold>Nguyá»‡t Tráº£m (Chuá»™t pháº£i)"
                ),
                "CustomModelData", 10501,
                "Rarity", "EPIC",
                "Unbreakable", true,
                "Enchantments", List.of("SHARPNESS 6", "UNBREAKING 3"),
                "Attributes", Map.of("MAIN_HAND", Map.of("GENERIC_ATTACK_DAMAGE", 14.5, "GENERIC_ATTACK_SPEED", 1.6)),
                "ItemFlags", List.of("HIDE_ATTRIBUTES", "HIDE_ENCHANTS"),
                "Skills", List.of(
                        "skill{s=MoonlightSlash;cd=60} @TargetLocation ~onUse",
                        "skill{s=LunarBleed;cd=20} @Target ~onDamage"
                )
        );

        MythicItemDefinition def = registry.loadFromMap("LUNAR_ECLIPSE_BLADE", yaml);

        assertNotNull(def);
        assertEquals("LUNAR_ECLIPSE_BLADE", def.id());
        assertEquals(Material.NETHERITE_SWORD, def.material());
        assertTrue(def.displayName().contains("Lunar Eclipse Blade"));
        assertEquals(2, def.lore().size());
        assertEquals(10501, def.customModelData());
        assertEquals("EPIC", def.rarity());
        assertTrue(def.unbreakable());
        assertEquals(6, def.enchantments().get("SHARPNESS"));
        assertEquals(3, def.enchantments().get("UNBREAKING"));
        assertEquals(14.5, def.attributes().get("MAIN_HAND").get("GENERIC_ATTACK_DAMAGE"));
        assertEquals(2, def.skills().size());

        ItemSkillBinding skill1 = def.skills().get(0);
        assertEquals("MoonlightSlash", skill1.skillId());
        assertEquals(ItemSkillTrigger.ON_USE, skill1.trigger());
        assertEquals(60L, skill1.cooldownTicks());

        ItemSkillBinding skill2 = def.skills().get(1);
        assertEquals("LunarBleed", skill2.skillId());
        assertEquals(ItemSkillTrigger.ON_DAMAGE, skill2.trigger());
        assertEquals(20L, skill2.cooldownTicks());
    }

    @Test
    void testItemPdcAndBuilderFallback() {
        Map<String, Object> pdcMap = new HashMap<>();
        PersistentDataContainer mockPdc = mockPdc(pdcMap);
        ItemMeta mockMeta = mockItemMeta(mockPdc);
        ItemStack mockStack = mockItemStack(mockMeta);

        registry.setTestItemBuilder((def, amount) -> {
            ItemDefinitionRegistry.applyComponentsToMeta(mockStack, def);
            return mockStack;
        });

        MythicItemDefinition def = MythicItemDefinition.builder("SOLAR_BOW")
                .material(Material.BOW)
                .displayName("<gold>Solar Bow")
                .rarity("LEGENDARY")
                .build();
        registry.register(def);

        Optional<ItemStack> built = registry.buildItemStack("SOLAR_BOW", 1);
        assertTrue(built.isPresent());

        // Verify PDC
        assertTrue(ItemDefinitionRegistry.isMythicItem(built.get()));
        assertEquals("SOLAR_BOW", ItemDefinitionRegistry.getItemId(built.get()).orElse(null));
        assertEquals("SOLAR_BOW", pdcMap.get(ItemDefinitionRegistry.KEY_ITEM_ID.toString()));
        assertEquals("LEGENDARY", pdcMap.get(ItemDefinitionRegistry.KEY_ITEM_RARITY.toString()));
    }

    @Test
    void testItemCooldownManager() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        String item = "LUNAR_BLADE";

        assertFalse(cooldownManager.isOnCooldown(player1, item, 100L));
        assertEquals(0L, cooldownManager.getRemainingTicks(player1, item, 100L));

        // Set cooldown 60 ticks starting at tick 100 -> expires at 160
        cooldownManager.setCooldown(player1, item, 60L, 100L);

        // Player 1 is on cooldown, Player 2 is NOT
        assertTrue(cooldownManager.isOnCooldown(player1, item, 120L));
        assertEquals(40L, cooldownManager.getRemainingTicks(player1, item, 120L));
        assertFalse(cooldownManager.isOnCooldown(player2, item, 120L));

        // At tick 160 -> exactly expired
        assertFalse(cooldownManager.isOnCooldown(player1, item, 160L));
        assertEquals(0L, cooldownManager.getRemainingTicks(player1, item, 160L));

        // Cleanup
        cooldownManager.cleanup(200L);
        assertEquals(0, cooldownManager.activePlayerCount());
    }

    @Test
    void testItemSkillTriggerAndExecutionWithCooldown() {
        MythicItemDefinition def = MythicItemDefinition.builder("FROST_WAND")
                .material(Material.BLAZE_ROD)
                .addSkill(new ItemSkillBinding("Blizzard", ItemSkillTrigger.ON_USE, 40L))
                .build();
        registry.register(def);

        Map<String, Object> pdcMap = new HashMap<>();
        pdcMap.put(ItemDefinitionRegistry.KEY_ITEM_ID.toString(), "FROST_WAND");
        PersistentDataContainer mockPdc = mockPdc(pdcMap);
        ItemMeta mockMeta = mockItemMeta(mockPdc);
        ItemStack wand = mockItemStack(mockMeta);

        World mockWorld = mockWorld("lunar");
        Player player = mockPlayer("IceMage", new Location(mockWorld, 0, 64, 0));

        AtomicInteger invokeCount = new AtomicInteger(0);
        AtomicReference<String> executedSkill = new AtomicReference<>();
        runtime.setSkillInvoker((caster, skillId, target) -> {
            invokeCount.incrementAndGet();
            executedSkill.set(skillId);
        });

        // 1. First trigger at tick 100 -> Success
        boolean firstCast = runtime.triggerItemSkill(player, wand, ItemSkillTrigger.ON_USE, null, null, 100L);
        assertTrue(firstCast);
        assertEquals(1, invokeCount.get());
        assertEquals("Blizzard", executedSkill.get());
        assertTrue(cooldownManager.isOnCooldown(player.getUniqueId(), "FROST_WAND", 100L));

        // 2. Second trigger at tick 120 (before 140 expiry) -> Blocked by Cooldown!
        boolean secondCast = runtime.triggerItemSkill(player, wand, ItemSkillTrigger.ON_USE, null, null, 120L);
        assertFalse(secondCast);
        assertEquals(1, invokeCount.get()); // Did not increase

        // 3. Third trigger at tick 150 (after cooldown) -> Success
        boolean thirdCast = runtime.triggerItemSkill(player, wand, ItemSkillTrigger.ON_USE, null, null, 150L);
        assertTrue(thirdCast);
        assertEquals(2, invokeCount.get());
    }

    @Test
    void testLunarMobCommandItemGive() {
        MythicItemDefinition def = MythicItemDefinition.builder("TEST_SWORD")
                .material(Material.DIAMOND_SWORD)
                .displayName("<red>Test Sword")
                .build();
        registry.register(def);

        Map<String, Object> pdcMap = new HashMap<>();
        PersistentDataContainer mockPdc = mockPdc(pdcMap);
        ItemMeta mockMeta = mockItemMeta(mockPdc);
        ItemStack swordItem = mockItemStack(mockMeta);

        registry.setTestItemBuilder((d, amt) -> swordItem);

        World mockWorld = mockWorld("lunar");
        Player targetPlayer = mockPlayer("Receiver", new Location(mockWorld, 0, 64, 0));

        List<String> messages = new ArrayList<>();
        CommandSender adminSender = mockSender(messages);

        LunarMobManager mobManager = new LunarMobManager(e -> {});
        MobDefinitionRegistry mobDefs = new MobDefinitionRegistry();
        LunarMobCommand cmd = new LunarMobCommand(mobDefs, mobManager, (p, d) -> true, () -> null, (m, s) -> {});
        cmd.setItemRegistry(registry);
        cmd.setPlayerResolver(name -> name.equals("Receiver") ? targetPlayer : null);

        // Execute: /lunarmob item give Receiver TEST_SWORD 2
        boolean handled = cmd.onCommand(adminSender, null, "lunarmob",
                new String[]{"item", "give", "Receiver", "TEST_SWORD", "2"});

        assertTrue(handled);
        assertTrue(messages.stream().anyMatch(m -> m.contains("Gave 2x") && m.contains("TEST_SWORD")));

        // Tab completion checks
        List<String> tab1 = cmd.onTabComplete(adminSender, null, "lunarmob", new String[]{"i"});
        assertTrue(tab1.contains("item"));

        List<String> tab2 = cmd.onTabComplete(adminSender, null, "lunarmob", new String[]{"item", ""});
        assertTrue(tab2.contains("give"));

        List<String> tab4 = cmd.onTabComplete(adminSender, null, "lunarmob", new String[]{"item", "give", "Receiver", ""});
        assertTrue(tab4.contains("TEST_SWORD"));
    }

    // --- Headless Mocks ---

    public static class MockItemStack extends ItemStack {
        ItemMeta meta;
        public void setMeta(ItemMeta meta) { this.meta = meta; }
        @Override public boolean hasItemMeta() { return meta != null; }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public boolean setItemMeta(ItemMeta itemMeta) { this.meta = itemMeta; return true; }
        @Override public int getAmount() { return 1; }
        @Override public ItemStack clone() { return this; }
    }

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }

    private static Player mockPlayer(String name, Location loc) {
        UUID uuid = UUID.randomUUID();
        PlayerInventory inv = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addItem")) return new HashMap<Integer, ItemStack>();
                    return null;
                });

        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getInventory")) return inv;
                    if (method.getName().equals("sendMessage")) return null;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private static CommandSender mockSender(List<String> capturedMessages) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return true;
                    if (method.getName().equals("sendMessage")) {
                        capturedMessages.add(String.valueOf(args[0]));
                        return null;
                    }
                    return null;
                });
    }

    private static PersistentDataContainer mockPdc(Map<String, Object> storage) {
        return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("set")) {
                        storage.put(args[0].toString(), args[2]);
                        return null;
                    }
                    if (method.getName().equals("get")) {
                        return storage.get(args[0].toString());
                    }
                    if (method.getName().equals("has")) {
                        return storage.containsKey(args[0].toString());
                    }
                    return null;
                });
    }

    private static ItemMeta mockItemMeta(PersistentDataContainer pdc) {
        return (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),
                new Class<?>[]{ItemMeta.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) return pdc;
                    if (method.getName().equals("displayName")) return null;
                    if (method.getName().equals("lore")) return null;
                    if (method.getName().equals("setCustomModelData")) return null;
                    if (method.getName().equals("setUnbreakable")) return null;
                    if (method.getName().equals("addItemFlags")) return null;
                    return null;
                });
    }

    private static ItemStack mockItemStack(ItemMeta meta) {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            MockItemStack stack = (MockItemStack) unsafe.allocateInstance(MockItemStack.class);
            stack.setMeta(meta);
            return stack;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
