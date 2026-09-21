package vn.haohan.lunar.api.system.combat.skill.targeter;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.impl.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry and parser for Entity and Location targeters.
 * Supports Mythic-style inline syntax: {@code @PlayersInRadius{r=20;limit=3}}
 */
public final class TargeterRegistry {

    private static final Pattern INLINE_TARGETER_PATTERN = Pattern.compile("^@?([a-zA-Z0-9_-]+)(?:\\{(.*)\\})?$");

    private final Map<String, EntityTargeter> entityTargeters = new ConcurrentHashMap<>();
    private final Map<String, LocationTargeter> locationTargeters = new ConcurrentHashMap<>();

    public TargeterRegistry() {
        registerBuiltins();
    }

    public void registerEntityTargeter(String name, EntityTargeter targeter) {
        Objects.requireNonNull(targeter, "Targeter must not be null");
        String key = normalizeKey(name);
        entityTargeters.put(key, targeter);
    }

    public void registerLocationTargeter(String name, LocationTargeter targeter) {
        Objects.requireNonNull(targeter, "Targeter must not be null");
        String key = normalizeKey(name);
        locationTargeters.put(key, targeter);
    }

    public Optional<EntityTargeter> getEntityTargeter(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(entityTargeters.get(normalizeKey(name)));
    }

    public Optional<LocationTargeter> getLocationTargeter(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(locationTargeters.get(normalizeKey(name)));
    }

    public boolean hasTargeter(String name) {
        if (name == null || name.isBlank()) return false;
        String key = normalizeKey(name);
        return entityTargeters.containsKey(key) || locationTargeters.containsKey(key);
    }

    public boolean isEntityTargeter(String name) {
        if (name == null || name.isBlank()) return false;
        return entityTargeters.containsKey(normalizeKey(name));
    }

    public boolean isLocationTargeter(String name) {
        if (name == null || name.isBlank()) return false;
        return locationTargeters.containsKey(normalizeKey(name));
    }

    /**
     * Resolves living entities using an inline string definition (e.g. {@code @PlayersInRadius{r=15}}).
     */
    public Collection<LivingEntity> resolveEntities(String inlineTargeter, SkillCastContext context) {
        ParsedTargeterCall parsed = parse(inlineTargeter);
        EntityTargeter targeter = getEntityTargeter(parsed.targeterName()).orElse(null);
        if (targeter == null) {
            return List.of();
        }
        return targeter.resolve(context, parsed.parameters());
    }

    /**
     * Resolves locations using an inline string definition (e.g. {@code @Location{x=10;y=65;z=-20}}).
     */
    public Collection<Location> resolveLocations(String inlineTargeter, SkillCastContext context) {
        ParsedTargeterCall parsed = parse(inlineTargeter);
        LocationTargeter targeter = getLocationTargeter(parsed.targeterName()).orElse(null);
        if (targeter == null) {
            return List.of();
        }
        return targeter.resolve(context, parsed.parameters());
    }

    /**
     * Parses an inline targeter syntax into a targeter name and parameter map.
     * Examples:
     * - {@code @self} -> targeterName: "self", parameters: {}
     * - {@code @PlayersInRadius{r=20;limit=3}} -> targeterName: "playersinradius", parameters: {r=20, limit=3}
     */
    public ParsedTargeterCall parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedTargeterCall("self", Map.of());
        }

        String trimmed = raw.trim();
        Matcher matcher = INLINE_TARGETER_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return new ParsedTargeterCall(normalizeKey(trimmed), Map.of());
        }

        String name = normalizeKey(matcher.group(1));
        String paramsRaw = matcher.group(2);
        Map<String, Object> params = parseParameters(paramsRaw);
        return new ParsedTargeterCall(name, params);
    }

    public Map<String, EntityTargeter> entityTargetersSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entityTargeters));
    }

    public Map<String, LocationTargeter> locationTargetersSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(locationTargeters));
    }

    private void registerBuiltins() {
        // Entity targeters
        EntityTargeter self = new SelfTargeter();
        registerEntityTargeter("self", self);

        EntityTargeter target = new TargetTargeter();
        registerEntityTargeter("target", target);

        EntityTargeter trigger = new TriggerTargeter();
        registerEntityTargeter("trigger", trigger);

        EntityTargeter playersInRadius = new PlayersInRadiusTargeter();
        registerEntityTargeter("players_in_radius", playersInRadius);
        registerEntityTargeter("playersinradius", playersInRadius);
        registerEntityTargeter("pir", playersInRadius);

        EntityTargeter livingEntitiesInRadius = new LivingEntitiesInRadiusTargeter();
        registerEntityTargeter("living_entities_in_radius", livingEntitiesInRadius);
        registerEntityTargeter("livingentitiesinradius", livingEntitiesInRadius);
        registerEntityTargeter("eir", livingEntitiesInRadius);

        EntityTargeter randomPlayer = new RandomPlayerTargeter();
        registerEntityTargeter("random_player", randomPlayer);
        registerEntityTargeter("randomplayer", randomPlayer);

        EntityTargeter cone = new ConeTargeter();
        registerEntityTargeter("cone", cone);

        EntityTargeter ring = new RingTargeter();
        registerEntityTargeter("ring", ring);

        EntityTargeter line = new LineTargeter();
        registerEntityTargeter("line", line);

        EntityTargeter sphere = new SphereTargeter();
        registerEntityTargeter("sphere", sphere);

        EntityTargeter cylinder = new CylinderTargeter();
        registerEntityTargeter("cylinder", cylinder);

        EntityTargeter threat = new ThreatTableTargeter();
        registerEntityTargeter("threat_table_targets", threat);
        registerEntityTargeter("threattabletargets", threat);
        registerEntityTargeter("threattargets", threat);

        EntityTargeter threatTop = new ThreatTopTargeter();
        registerEntityTargeter("threat_top", threatTop);
        registerEntityTargeter("threattop", threatTop);
        registerEntityTargeter("threat_table_top", threatTop);
        registerEntityTargeter("threattabletop", threatTop);

        EntityTargeter behind = new BehindTargeter();
        registerEntityTargeter("behind", behind);

        EntityTargeter inFront = new InFrontTargeter();
        registerEntityTargeter("infront", inFront);
        registerEntityTargeter("in_front", inFront);

        EntityTargeter nearestPlayer = new NearestPlayerTargeter();
        registerEntityTargeter("nearest_player", nearestPlayer);
        registerEntityTargeter("nearestplayer", nearestPlayer);
        registerEntityTargeter("pirnearest", nearestPlayer);

        EntityTargeter audience = new AudienceTargeter();
        registerEntityTargeter("audience", audience);
        registerEntityTargeter("skillaudience", audience);

        // Location targeters
        LocationTargeter origin = new OriginTargeter();
        registerLocationTargeter("origin", origin);

        LocationTargeter location = new LocationTargeterImpl();
        registerLocationTargeter("location", location);

        LocationTargeter boneLocation = new BoneLocationTargeter();
        registerLocationTargeter("bonelocation", boneLocation);
        registerLocationTargeter("bone", boneLocation);

        LocationTargeter highestBlock = new HighestBlockTargeter();
        registerLocationTargeter("highest_block", highestBlock);
        registerLocationTargeter("highestblock", highestBlock);
        // P20 Pin Targeters
        EntityTargeter entitiesNearPin = new EntitiesNearPinTargeter();
        registerEntityTargeter("entities_near_pin", entitiesNearPin);
        registerEntityTargeter("entitiesnearpin", entitiesNearPin);

        LocationTargeter blocksInPinRegion = new BlocksInPinRegionTargeter();
        registerLocationTargeter("blocks_in_pin_region", blocksInPinRegion);
        registerLocationTargeter("blocksinpinregion", blocksInPinRegion);
    }

    public static String normalizeKey(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("@")) {
            s = s.substring(1);
        }
        return s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static Map<String, Object> parseParameters(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        String[] tokens = raw.split("[;,]");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String val = trimmed.substring(eq + 1).trim();
                map.put(key, tryParseNumber(val));
            } else {
                map.put(trimmed.toLowerCase(Locale.ROOT), true);
            }
        }
        return Map.copyOf(map);
    }

    private static Object tryParseNumber(String val) {
        try {
            if (val.contains(".")) {
                return Double.parseDouble(val);
            }
            return Long.parseLong(val);
        } catch (NumberFormatException ignored) {
            if ("true".equalsIgnoreCase(val)) return true;
            if ("false".equalsIgnoreCase(val)) return false;
            return val;
        }
    }

    public record ParsedTargeterCall(String targeterName, Map<String, Object> parameters) {
        public ParsedTargeterCall {
            Objects.requireNonNull(targeterName, "Targeter name must not be null");
            parameters = Map.copyOf(Objects.requireNonNull(parameters, "Parameters must not be null"));
        }
    }
}
