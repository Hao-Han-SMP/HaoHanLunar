package vn.haohan.lunar.core.mob.scaling;

import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;
import vn.haohan.lunar.core.mob.scaling.DynamicScalingDefinition;
import vn.haohan.lunar.core.mob.scaling.DynamicScalingResult;
import vn.haohan.lunar.core.mob.scaling.DynamicScalingService;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicScalingTest {

    @Test
    void scalesHealthWhen5PlayersParticipate() {
        DynamicScalingService service = new DynamicScalingService();
        DynamicScalingDefinition scalingDef = new DynamicScalingDefinition(
                true, 32.0, 0.25, 0.05, 0.02, 20, 1
        );

        MockLivingEntity mockEntity = new MockLivingEntity(100.0, 100.0);
        ActiveLunarMob mob = createMob(mockEntity, scalingDef);

        // 5 players = 1 baseline + 4 additional -> +100% health = 200.0 max health
        DynamicScalingResult result = service.applyScaling(mob, 5);

        assertEquals(2.0, result.healthMultiplier(), 1e-6);
        assertEquals(200.0, mockEntity.maxHealth, 1e-6);
        assertEquals(200.0, mockEntity.health, 1e-6);
        assertEquals(1.20, result.damageMultiplier(), 1e-6); // 1.0 + 4 * 0.05 = 1.20
        assertEquals(0.08, result.cooldownReduction(), 1e-6); // 4 * 0.02 = 0.08

        assertEquals(2.0, mob.currentHealthMultiplier(), 1e-6);
        assertEquals(1.20, mob.currentDamageMultiplier(), 1e-6);
        assertEquals(0.08, mob.dynamicCooldownReduction(), 1e-6);
        assertEquals(5, mob.lastTrackedPlayerCount());
    }

    @Test
    void maintainsExactHealthPercentageDuringScaling() {
        DynamicScalingService service = new DynamicScalingService();
        DynamicScalingDefinition scalingDef = new DynamicScalingDefinition(
                true, 32.0, 0.25, 0.05, 0.02, 20, 1
        );

        // Boss currently has 50/100 HP (50%)
        MockLivingEntity mockEntity = new MockLivingEntity(50.0, 100.0);
        ActiveLunarMob mob = createMob(mockEntity, scalingDef);

        // Scale up to 5 players (2x max HP)
        service.applyScaling(mob, 5);
        assertEquals(200.0, mockEntity.maxHealth, 1e-6);
        assertEquals(100.0, mockEntity.health, 1e-6); // exactly 50% of 200!

        // Scale down back to 1 player (1x max HP)
        service.applyScaling(mob, 1);
        assertEquals(100.0, mockEntity.maxHealth, 1e-6);
        assertEquals(50.0, mockEntity.health, 1e-6); // exactly 50% of 100!
    }

    @Test
    void ignoresCreativeAndSpectatorPlayers() {
        DynamicScalingService service = new DynamicScalingService();

        Player survivalPlayer = mockPlayer(GameMode.SURVIVAL, true, false);
        Player adventurePlayer = mockPlayer(GameMode.ADVENTURE, true, false);
        Player creativePlayer = mockPlayer(GameMode.CREATIVE, true, false);
        Player spectatorPlayer = mockPlayer(GameMode.SPECTATOR, true, false);
        Player deadSurvivalPlayer = mockPlayer(GameMode.SURVIVAL, true, true);

        assertTrue(service.isValidCombatant(survivalPlayer));
        assertTrue(service.isValidCombatant(adventurePlayer));
        assertFalse(service.isValidCombatant(creativePlayer));
        assertFalse(service.isValidCombatant(spectatorPlayer));
        assertFalse(service.isValidCombatant(deadSurvivalPlayer));
    }

    @Test
    void respectsMaxPlayersCeiling() {
        DynamicScalingService service = new DynamicScalingService();
        DynamicScalingDefinition scalingDef = new DynamicScalingDefinition(
                true, 32.0, 0.10, 0.02, 0.01, 10, 1
        );

        MockLivingEntity mockEntity = new MockLivingEntity(100.0, 100.0);
        ActiveLunarMob mob = createMob(mockEntity, scalingDef);

        // 100 players enter the arena, but capped at 10
        DynamicScalingResult result = service.applyScaling(mob, 100);

        assertEquals(10, result.effectivePlayers());
        // extra = 10 - 1 = 9 -> +90% health
        assertEquals(1.90, result.healthMultiplier(), 1e-6);
        assertEquals(190.0, mockEntity.maxHealth, 1e-6);
    }

    private static ActiveLunarMob createMob(MockLivingEntity mockEntity, DynamicScalingDefinition scaling) {
        MobDefinition definition = new MobDefinition(
                new MobDefinitionId("scaling_boss"),
                EntityType.IRON_GOLEM,
                "Scaling Boss",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                null,
                null,
                List.of(),
                scaling
        );
        return new ActiveLunarMob(mockEntity.proxy(), definition, new LunarMobIdentity("scaling_boss", "1"));
    }

    private static Player mockPlayer(GameMode mode, boolean valid, boolean dead) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGameMode" -> mode;
                    case "isValid" -> valid;
                    case "isDead" -> dead;
                    default -> null;
                }
        );
    }

    private static class MockLivingEntity {
        double health;
        double maxHealth;
        final UUID uuid = UUID.randomUUID();

        MockLivingEntity(double health, double maxHealth) {
            this.health = health;
            this.maxHealth = maxHealth;
        }

        LivingEntity proxy() {
            return (LivingEntity) Proxy.newProxyInstance(
                    LivingEntity.class.getClassLoader(),
                    new Class<?>[]{LivingEntity.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> uuid;
                        case "isDead" -> false;
                        case "isValid" -> true;
                        case "getHealth" -> health;
                        case "getMaxHealth" -> maxHealth;
                        case "setMaxHealth" -> {
                            maxHealth = (double) args[0];
                            yield null;
                        }
                        case "setHealth" -> {
                            health = (double) args[0];
                            yield null;
                        }
                        default -> null;
                    }
            );
        }
    }
}
