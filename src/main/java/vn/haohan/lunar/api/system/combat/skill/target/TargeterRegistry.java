package vn.haohan.lunar.api.system.combat.skill.target;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Registry and safe built-in targeters for the MVP. */
public final class TargeterRegistry {

    private final Map<String, Targeter> targeters = new LinkedHashMap<>();

    public TargeterRegistry() {
        registerBuiltins();
    }

    public synchronized void register(String id, Targeter targeter) {
        String normalized = normalize(id);
        Objects.requireNonNull(targeter, "Targeter must not be null");
        if (targeters.putIfAbsent(normalized, targeter) != null) {
            throw new IllegalArgumentException("Targeter ID already registered: " + normalized);
        }
    }

    public Optional<Targeter> get(String id) {
        return Optional.ofNullable(targeters.get(normalize(id)));
    }

    public List<TargetRef> resolve(String id, TargeterContext context) {
        Objects.requireNonNull(context, "Targeter context must not be null");
        return get(id).map(targeter -> immutable(targeter.resolve(context))).orElseGet(List::of);
    }

    public Map<String, Targeter> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(targeters));
    }

    private void registerBuiltins() {
        register("self", context -> validEntity(context.caster())
                ? List.of(TargetRef.entity(context.caster())) : List.of());
        register("target", context -> context.targetOptional()
                .filter(target -> validEntity(target) && sameWorld(context, target))
                .map(target -> List.of(TargetRef.entity(target))).orElseGet(List::of));
        register("location", context -> List.of(TargetRef.location(context.origin())));
        register("players_in_radius", context -> nearby(context,
                entity -> entity instanceof Player player && player.getGameMode() != GameMode.SPECTATOR));
        register("living_entities_in_radius", context -> nearby(context, entity -> entity instanceof LivingEntity));
        register("random_player", context -> {
            List<TargetRef> players = nearby(context,
                    entity -> entity instanceof Player player && player.getGameMode() != GameMode.SPECTATOR);
            return players.isEmpty() ? List.of() : List.of(players.get(ThreadLocalRandom.current().nextInt(players.size())));
        });
    }

    private static List<TargetRef> nearby(TargeterContext context, Predicate<Entity> filter) {
        List<TargetRef> results = new ArrayList<>();
        for (Entity entity : context.world().getNearbyEntities(context.origin(), context.radius(), context.radius(), context.radius(),
                candidate -> validEntity(candidate) && sameWorld(context, candidate) && filter.test(candidate))) {
            if (results.size() >= context.maxResults()) {
                break;
            }
            results.add(TargetRef.entity(entity));
        }
        return immutable(results);
    }

    private static boolean validEntity(Entity entity) {
        return entity != null && entity.isValid() && !(entity instanceof LivingEntity living && living.isDead());
    }

    private static boolean sameWorld(TargeterContext context, Entity entity) {
        return entity.getWorld() != null && entity.getWorld().equals(context.world());
    }

    private static List<TargetRef> immutable(List<TargetRef> refs) {
        return List.copyOf(refs == null ? List.of() : refs);
    }

    private static String normalize(String id) {
        Objects.requireNonNull(id, "Targeter ID must not be null");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Targeter ID must not be blank");
        }
        return normalized;
    }
}
