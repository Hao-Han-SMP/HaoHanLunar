package vn.haohan.lunar.core.mob.disguise;

import vn.haohan.lunar.api.mob.disguise.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class DisguiseSystemTest {

    private World createMockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });
    }

    private ActiveLunarMob createDummyMob() {
        World world = createMockWorld();
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, 0, 64, 0);

        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("getType")) return EntityType.ZOMBIE;
                    if (method.getName().equals("getHealth")) return 500.0;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });

        MobDefinition def = new MobDefinition(new MobDefinitionId("abyssal_stalker"), EntityType.ZOMBIE,
                "Abyssal Stalker", null, Map.of(), Map.of(), List.of(), null, Set.of());

        return new ActiveLunarMob(entity, def, new LunarMobIdentity("abyssal_stalker", "1"));
    }

    @Test
    @DisplayName("DisguiseManager applies PLAYER disguise and preserves ActiveLunarMob state")
    void testPlayerDisguisePreservation() {
        ActiveLunarMob mob = createDummyMob();
        mob.setStance("AGGRESSIVE");
        UUID originalUuid = mob.entityId();
        MobDefinitionId originalDefId = mob.definitionId();

        DisguiseManager manager = new DisguiseManager();
        AtomicBoolean applied = new AtomicBoolean(false);
        manager.setDisguiseApplier((m, d) -> applied.set(true));

        DisguiseData playerDisguise = DisguiseData.player("SteveInDisguise", "textureBase64", "sigBase64");
        manager.disguise(mob, playerDisguise);

        assertTrue(applied.get());
        assertTrue(mob.isDisguised());
        assertEquals(playerDisguise, mob.getDisguise());
        assertTrue(manager.isDisguised(originalUuid));
        assertEquals(playerDisguise, manager.getDisguise(originalUuid).orElse(null));

        // State verification: server-side hitbox, identity, stats intact
        assertEquals(originalUuid, mob.entityId());
        assertEquals(originalDefId, mob.definitionId());
        assertEquals("AGGRESSIVE", mob.stance());
        assertNotNull(mob.threatTable());
        assertNotNull(mob.crowdControl());
        assertNotNull(mob.immunityTable());
    }

    @Test
    @DisplayName("DisguiseManager undisguises mob and triggers remover callback")
    void testUndisguise() {
        ActiveLunarMob mob = createDummyMob();
        DisguiseManager manager = new DisguiseManager();
        AtomicBoolean removed = new AtomicBoolean(false);
        manager.setDisguiseRemover((m, d) -> removed.set(true));

        manager.disguise(mob, DisguiseData.mob(EntityType.IRON_GOLEM, "GolemDefender"));
        assertTrue(mob.isDisguised());

        manager.undisguise(mob);
        assertFalse(mob.isDisguised());
        assertNull(mob.getDisguise());
        assertFalse(manager.isDisguised(mob.entityId()));
        assertTrue(removed.get());
    }

    @Test
    @DisplayName("MechanicRegistry executes disguise and undisguise mechanics properly")
    void testDisguiseMechanicExecution() {
        ActiveLunarMob mob = createDummyMob();
        MechanicRegistry registry = new MechanicRegistry();

        SkillDefinition skill = new SkillDefinition("stealth_form", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext castContext = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 50L);
        MechanicContext mechContext = new MechanicContext(castContext, List.of());

        // Execute disguise mechanic
        MechanicResult disguiseResult = registry.execute("disguise", mechContext, Map.of(
                "type", "PLAYER",
                "name", "Infiltrator",
                "skin", "custom_skin_val",
                "sig", "custom_sig_val"
        ));

        assertTrue(disguiseResult.isSuccess(), "Disguise mechanic should succeed: " + disguiseResult.error());
        assertTrue(mob.isDisguised());
        DisguiseData disguise = mob.getDisguise();
        assertNotNull(disguise);
        assertEquals(DisguiseType.PLAYER, disguise.type());
        assertEquals("Infiltrator", disguise.displayName());
        assertEquals("custom_skin_val", disguise.skinTexture());
        assertEquals("custom_sig_val", disguise.skinSignature());

        // Execute undisguise mechanic
        MechanicResult undisguiseResult = registry.execute("undisguise", mechContext, Map.of());
        assertTrue(undisguiseResult.isSuccess(), "Undisguise mechanic should succeed: " + undisguiseResult.error());
        assertFalse(mob.isDisguised());
        assertNull(mob.getDisguise());
    }

    @Test
    @DisplayName("MechanicRegistry executes changeskin and changemodel mechanics properly")
    void testDynamicSkinAndModelSwapping() {
        ActiveLunarMob mob = createDummyMob();
        DisguiseManager manager = new DisguiseManager();
        MechanicRegistry registry = new MechanicRegistry();
        registry.setDisguiseManager(manager);

        SkillDefinition skill = new SkillDefinition("enrage_phase", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext castContext = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 50L);
        MechanicContext mechContext = new MechanicContext(castContext, List.of());

        // 1. Initial Disguise
        registry.execute("disguise", mechContext, Map.of(
                "type", "PLAYER",
                "name", "PhaseOneBoss",
                "skin", "normal_skin_base64"
        ));
        assertEquals("normal_skin_base64", mob.getDisguise().skinTexture());

        // 2. Dynamic Skin change (e.g. Enraged Red Skin)
        MechanicResult skinResult = registry.execute("changeskin", mechContext, Map.of(
                "skin", "enraged_red_skin_base64",
                "signature", "sig_red"
        ));
        assertTrue(skinResult.isSuccess());
        assertTrue(mob.isDisguised());
        assertEquals("enraged_red_skin_base64", mob.getDisguise().skinTexture());
        assertEquals("sig_red", mob.getDisguise().skinSignature());
        assertEquals("PhaseOneBoss", mob.getDisguise().displayName());

        // 3. Dynamic Model change (e.g. Phase 2 Demon Form)
        MechanicResult modelResult = registry.execute("changemodel", mechContext, Map.of(
                "model", "infernal_demon_lord",
                "state", "enraged"
        ));
        assertTrue(modelResult.isSuccess());
        assertEquals("infernal_demon_lord", mob.activeModelId());
        assertEquals("enraged", mob.activeModelState());
    }
}
