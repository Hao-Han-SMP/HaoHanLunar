package vn.haohan.engine.api.system.combat.skill.target;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.engine.api.system.combat.skill.targeter.TargeterRegistry;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Registry and safe built-in targeters for basic skill targets (self, target, players_in_radius, etc.).
 * Can operate standalone or bridge registrations to the unified {@link TargeterRegistry}.
 */
public class BasicTargeterRegistry {

    private final Map<String, ITargeter> targeters = new LinkedHashMap<>();
    private final TargeterRegistry unifiedTargeterRegistry;

    public BasicTargeterRegistry() {
        this(null);
    }

    public BasicTargeterRegistry(TargeterRegistry unifiedTargeterRegistry) {
        this.unifiedTargeterRegistry = unifiedTargeterRegistry;
        registerBuiltins();
    }

    public synchronized void register(String id, ITargeter targeter) {
        String normalized = normalize(id);
        Objects.requireNonNull(targeter, "ITargeter must not be null");
        if (targeters.putIfAbsent(normalized, targeter) != null) {
            throw new IllegalArgumentException("ITargeter ID already registered: " + normalized);
        }

        // Bridge registration to unified registry if available
        if (unifiedTargeterRegistry != null) {
            unifiedTargeterRegistry.registerEntityTargeter(normalized, (castContext, params) -> {
                TargeterContext ctx = new TargeterContext(
                        castContext.caster().entity(),
                        castContext.triggerEntity() instanceof LivingEntity living ? living : null,
                        castContext.origin() != null ? castContext.origin() : castContext.caster().entity().getLocation(),
                        32.0,
                        64
                );
                List<TargetRef> resolved = targeter.resolve(ctx);
                List<LivingEntity> result = new ArrayList<>();
                for (TargetRef ref : resolved) {
                    if (ref.entity() instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                        result.add(living);
                    }
                }
                return result;
            });
        }
    }

    public Optional<ITargeter> get(String id) {
        String key = normalize(id);
        ITargeter targeter = targeters.get(key);
        if (targeter == null) {
            if ("pir".equals(key)) return Optional.ofNullable(targeters.get("players_in_radius"));
            if ("eir".equals(key)) return Optional.ofNullable(targeters.get("living_entities_in_radius"));
        }
        return Optional.ofNullable(targeter);
    }

    public List<TargetRef> resolve(String id, TargeterContext context) {
        Objects.requireNonNull(context, "ITargeter context must not be null");
        return get(id).map(targeter -> immutable(targeter.resolve(context))).orElseGet(List::of);
    }

    public Map<String, ITargeter> snapshot() {
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
        Objects.requireNonNull(id, "ITargeter ID must not be null");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ITargeter ID must not be blank");
        }
        return normalized;
    }
}
