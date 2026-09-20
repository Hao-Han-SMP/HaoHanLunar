package vn.haohan.lunar.api.v1.event;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.combat.DamageType;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicEventApiTest {

    private static class DummyItemStack extends ItemStack {
        private final Material mat;
        private final int count;

        public DummyItemStack(Material mat, int count) {
            this.mat = mat;
            this.count = count;
        }

        @Override
        public Material getType() {
            return mat;
        }

        @Override
        public int getAmount() {
            return count;
        }

        @Override
        public ItemStack clone() {
            return new DummyItemStack(mat, count);
        }
    }

    @Test
    void testLunarMobSpawnEvent() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 10.0, 64.0, 20.0);
        ActiveLunarMob mob = createMockMob("lunar_skeleton", loc);

        LunarMobSpawnEvent event = new LunarMobSpawnEvent(mob, loc, "spawner_1");
        assertEquals(mob, event.mob());
        assertEquals(10.0, event.location().getX());
        assertEquals("spawner_1", event.spawnInstanceId());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertNotNull(LunarMobSpawnEvent.getHandlerList());
    }

    @Test
    void testLunarMobDeathEvent() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0.0, 64.0, 0.0);
        ActiveLunarMob mob = createMockMob("lunar_boss", loc);
        Player killer = mockPlayer("PlayerKiller");

        ItemStack mockItem = new DummyItemStack(Material.DIAMOND, 3);
        List<ItemStack> drops = new ArrayList<>();
        drops.add(mockItem);

        LunarMobDeathEvent event = new LunarMobDeathEvent(mob, killer, drops, 2500.0);
        assertEquals(mob, event.mob());
        assertTrue(event.killer().isPresent());
        assertEquals(killer, event.killer().get());
        assertEquals(1, event.drops().size());
        assertEquals(2500.0, event.totalDamage());

        // Can modify drop list
        event.drops().clear();
        assertTrue(event.drops().isEmpty());
    }

    @Test
    void testLunarSkillPreAndPostCastEvent() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0.0, 64.0, 0.0);
        ActiveLunarMob caster = createMockMob("mage", loc);
        LivingEntity target1 = mockLiving(loc);
        LivingEntity target2 = mockLiving(loc);

        SkillDefinition skill = new SkillDefinition("fireball", Set.of(SkillTrigger.ON_TIMER), 20L);

        // PreCast
        LunarSkillPreCastEvent preEvent = new LunarSkillPreCastEvent(caster, skill, target1, 1.0);
        assertEquals(caster, preEvent.caster());
        assertEquals(skill, preEvent.skill());
        assertEquals(target1, preEvent.target().orElse(null));
        assertEquals(1.0, preEvent.power());

        // Modify target and power
        preEvent.setTarget(target2);
        preEvent.setPower(2.5);
        preEvent.setCancelled(true);

        assertEquals(target2, preEvent.target().orElse(null));
        assertEquals(2.5, preEvent.power());
        assertTrue(preEvent.isCancelled());

        // PostCast
        LunarSkillPostCastEvent postEvent = new LunarSkillPostCastEvent(caster, skill, target2, true);
        assertEquals(caster, postEvent.caster());
        assertEquals(skill, postEvent.skill());
        assertTrue(postEvent.success());
    }

    @Test
    void testLunarMobDamageEvent() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0.0, 64.0, 0.0);
        ActiveLunarMob victim = createMockMob("boss", loc);
        Player attacker = mockPlayer("Attacker1");

        LunarMobDamageEvent event = new LunarMobDamageEvent(victim, attacker, DamageType.MAGICAL, DamageCause.ENTITY_ATTACK, 150.0);
        assertEquals(victim, event.victim());
        assertEquals(attacker, event.source().orElse(null));
        assertEquals(DamageType.MAGICAL, event.damageType());
        assertEquals(DamageCause.ENTITY_ATTACK, event.cause());
        assertEquals(150.0, event.damage());

        // Modify damage and cancel
        event.setDamage(75.0);
        assertEquals(75.0, event.damage());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    void testLunarMobPhaseChangeEvent() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0.0, 64.0, 0.0);
        ActiveLunarMob mob = createMockMob("phase_boss", loc);

        LunarMobPhaseChangeEvent event = new LunarMobPhaseChangeEvent(mob, 1, 2);
        assertEquals(mob, event.mob());
        assertEquals(1, event.previousPhase());
        assertEquals(2, event.newPhase());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    void testLunarLootGenerateEvent() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0.0, 64.0, 0.0);
        ActiveLunarMob mob = createMockMob("loot_boss", loc);
        Player recipient = mockPlayer("LuckyPlayer");

        List<ItemStack> drops = new ArrayList<>();
        drops.add(new DummyItemStack(Material.NETHERITE_INGOT, 1));
        LunarLootGenerateEvent event = new LunarLootGenerateEvent(mob, recipient, drops);

        assertEquals(mob, event.mob());
        assertEquals(recipient, event.recipient().orElse(null));
        assertEquals(1, event.drops().size());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    // --- Helpers ---

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }

    private static LivingEntity mockLiving(Location loc) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private static Player mockPlayer(String name) {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private static ActiveLunarMob createMockMob(String mobId, Location loc) {
        LivingEntity entity = mockLiving(loc);
        MobDefinition def = new MobDefinition(new MobDefinitionId(mobId), EntityType.ZOMBIE, mobId, null,
                Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, def, new LunarMobIdentity(mobId, "1.0.0"));
    }
}
