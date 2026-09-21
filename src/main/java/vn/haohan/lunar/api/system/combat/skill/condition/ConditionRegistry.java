package vn.haohan.lunar.api.system.combat.skill.condition;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraScheduler;
import vn.haohan.lunar.api.system.world.pin.PinManager;
import vn.haohan.lunar.api.system.world.pin.SinglePin;

import java.util.*;

/** Safe condition registry. Invalid parameters become validation results, never combat-tick exceptions. */
public final class ConditionRegistry {

    private final Map<String, Condition> conditions = new LinkedHashMap<>();
    private AuraScheduler auraScheduler;

    public ConditionRegistry() {
        registerBuiltins();
    }

    public static boolean evaluateComparison(double actual, String spec) {
        if (spec == null) return true;
        spec = spec.trim();
        try {
            if (spec.startsWith("<=")) return actual <= Double.parseDouble(spec.substring(2).trim());
            if (spec.startsWith("<")) return actual < Double.parseDouble(spec.substring(1).trim());
            if (spec.startsWith(">=")) return actual >= Double.parseDouble(spec.substring(2).trim());
            if (spec.startsWith(">")) return actual > Double.parseDouble(spec.substring(1).trim());
            if (spec.startsWith("==")) return actual == Double.parseDouble(spec.substring(2).trim());
            if (spec.startsWith("=")) return actual == Double.parseDouble(spec.substring(1).trim());
            return actual <= Double.parseDouble(spec);
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    public synchronized void register(String id, Condition condition) {
        String normalized = normalize(id);
        Objects.requireNonNull(condition, "Condition must not be null");
        if (conditions.putIfAbsent(normalized, condition) != null) {
            throw new IllegalArgumentException("Condition ID already registered: " + normalized);
        }
    }

    public Optional<Condition> get(String id) {
        return Optional.ofNullable(conditions.get(normalize(id)));
    }

    public ConditionResult evaluate(String id, ConditionContext context, Map<String, Object> parameters) {
        if (context == null) return ConditionResult.invalid("Condition context must not be null");
        if (parameters == null) return ConditionResult.invalid("Condition parameters must not be null");
        Condition condition;
        try {
            condition = get(id).orElse(null);
        } catch (RuntimeException exception) {
            return ConditionResult.invalid(exception.getMessage());
        }
        if (condition == null) return ConditionResult.invalid("Unknown condition: " + id);
        try {
            return ConditionResult.matched(condition.evaluate(context, Map.copyOf(parameters)));
        } catch (ConditionParameterException exception) {
            return ConditionResult.invalid(exception.getMessage());
        } catch (RuntimeException exception) {
            return ConditionResult.invalid("Invalid parameters for condition '" + id + "': " + exception.getMessage());
        }
    }

    public ConditionResult and(ConditionContext context, List<ConditionCall> calls) {
        return combine(context, calls, true);
    }

    public ConditionResult or(ConditionContext context, List<ConditionCall> calls) {
        return combine(context, calls, false);
    }

    public ConditionResult not(ConditionContext context, ConditionCall call) {
        ConditionResult result = evaluate(call.id(), context, call.parameters());
        return result.valid() ? ConditionResult.matched(!result.matched()) : result;
    }

    public Map<String, Condition> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(conditions));
    }

    private ConditionResult combine(ConditionContext context, List<ConditionCall> calls, boolean all) {
        if (calls == null || calls.isEmpty()) return ConditionResult.invalid("Condition group must not be empty");
        boolean result = all;
        for (ConditionCall call : calls) {
            if (call == null) return ConditionResult.invalid("Condition group contains null entry");
            ConditionResult evaluated = evaluate(call.id(), context, call.parameters());
            if (!evaluated.valid()) return evaluated;
            if (all && !evaluated.matched()) return ConditionResult.matched(false);
            if (!all && evaluated.matched()) return ConditionResult.matched(true);
            result = evaluated.matched();
        }
        return ConditionResult.matched(all || result);
    }

    private static ConditionParameterException invalid(String message) {
        return new ConditionParameterException(message);
    }

    private static String normalize(String id) {
        Objects.requireNonNull(id, "Condition ID must not be null");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("Condition ID must not be blank");
        return normalized;
    }

    private static boolean compare(Object actual, Object expected, String operator) {
        if (actual == null || expected == null) return false;
        int ordering;
        if (actual instanceof Number left && expected instanceof Number right) {
            ordering = Double.compare(left.doubleValue(), right.doubleValue());
        } else {
            ordering = String.valueOf(actual).compareTo(String.valueOf(expected));
        }
        return switch (operator.toLowerCase(Locale.ROOT)) {
            case "equals", "==", "=" -> Objects.equals(actual, expected) || String.valueOf(actual).equals(String.valueOf(expected));
            case "not_equals", "!=", "<>" -> !Objects.equals(actual, expected);
            case "greater", ">" -> ordering > 0;
            case "greater_or_equal", ">=" -> ordering >= 0;
            case "less", "<" -> ordering < 0;
            case "less_or_equal", "<=" -> ordering <= 0;
            default -> throw invalid("Unsupported comparison operator: " + operator);
        };
    }

    private static Entity requiredTarget(ConditionContext context) {
        if (context.target() == null) throw invalid("Target is required");
        return context.target();
    }

    private static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof String string) || string.isBlank()) throw invalid(key + " must be a non-blank string");
        return string.trim();
    }

    private static double number(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof Number number)) throw invalid(key + " must be numeric");
        return number.doubleValue();
    }

    public void setAuraScheduler(AuraScheduler auraScheduler) {
        this.auraScheduler = auraScheduler;
    }

    private void registerBuiltins() {
        register("target_within", (context, params) -> {
            Entity target = requiredTarget(context);
            double radius = number(params, "radius");
            if (radius < 0 || radius > 128 || !Double.isFinite(radius)) throw invalid("radius must be between 0 and 128");
            return target.isValid() && target.getWorld() != null && target.getWorld().equals(context.caster().getWorld())
                    && target.getLocation().distanceSquared(context.caster().getLocation()) <= radius * radius;
        });
        register("health_below", (context, params) -> context.subjectLivingEntity().getHealth() < number(params, "value"));
        register("health_above", (context, params) -> context.subjectLivingEntity().getHealth() > number(params, "value"));
        register("world", (context, params) -> {
            String expected = text(params, "value");
            return expected.equalsIgnoreCase(context.subjectLivingEntity().getWorld().getName())
                    || expected.equalsIgnoreCase(context.subjectLivingEntity().getWorld().getKey().toString());
        });
        register("line_of_sight", (context, params) -> {
            Entity target = requiredTarget(context);
            return target.isValid() && context.caster().hasLineOfSight(target);
        });
        register("phase", (context, params) -> context.phase() != null
                && context.phase().equalsIgnoreCase(text(params, "value")));
        register("cooldown_ready", (context, params) -> context.cooldowns()
                .isReady(context.casterId(), text(params, "skill"), context.currentTick()));
        register("tag", (context, params) -> {
            String tag = text(params, "value");
            Entity subject = context.target() == null ? context.caster() : context.target();
            return subject.getScoreboardTags().contains(tag);
        });
        register("variable_compare", (context, params) -> compare(
                context.variables().get(text(params, "variable")), params.get("value"), text(params, "operator")));

        // P2-6 Advanced Conditions
        register("damagecause", (context, params) -> {
            String expected = text(params, "cause");
            Object actual = context.variables().get("damage_cause");
            if (actual == null) actual = context.variables().get("cause");
            return actual != null && expected.equalsIgnoreCase(String.valueOf(actual).trim());
        });

        register("biome", (context, params) -> {
            String expected = text(params, "name");
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null || loc.getWorld() == null) return false;
            try {
                String biomeName = loc.getBlock().getBiome().name();
                return expected.equalsIgnoreCase(biomeName);
            } catch (Throwable ignored) {
                return false;
            }
        });

        register("structure", (context, params) -> {
            String expected = text(params, "name");
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null || loc.getWorld() == null) return false;
            try {
                return loc.getWorld().getName().toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            } catch (Throwable ignored) {
                return false;
            }
        });

        register("holding", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            EntityEquipment eq = subject.getEquipment();
            if (eq == null) return false;
            ItemStack item = eq.getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) return false;

            if (params.containsKey("material")) {
                String mat = text(params, "material");
                if (!item.getType().name().equalsIgnoreCase(mat)) {
                    return false;
                }
            }

            Object cmdRaw = params.get("custommodeldata");
            if (cmdRaw == null) cmdRaw = params.get("custom-model-data");
            if (cmdRaw == null) cmdRaw = params.get("model");
            if (cmdRaw instanceof Number n) {
                ItemMeta meta = item.getItemMeta();
                if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != n.intValue()) {
                    return false;
                }
            }
            return true;
        });

        register("wearing", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            EntityEquipment eq = subject.getEquipment();
            if (eq == null) return false;

            String slot = params.containsKey("slot") ? text(params, "slot").toUpperCase(Locale.ROOT) : "CHEST";
            ItemStack item = switch (slot) {
                case "HEAD", "HELMET" -> eq.getHelmet();
                case "CHEST", "CHESTPLATE" -> eq.getChestplate();
                case "LEGS", "LEGGINGS" -> eq.getLeggings();
                case "FEET", "BOOTS" -> eq.getBoots();
                case "OFF_HAND", "OFFHAND" -> eq.getItemInOffHand();
                default -> eq.getChestplate();
            };

            if (item == null || item.getType() == Material.AIR) return false;
            if (params.containsKey("material")) {
                String mat = text(params, "material");
                return item.getType().name().equalsIgnoreCase(mat);
            }
            return true;
        });

        register("targetcount", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null || loc.getWorld() == null) return false;

            double radius = params.containsKey("radius") ? number(params, "radius") : 16.0;
            double expected = number(params, "amount");
            String op = params.containsKey("compare") ? text(params, "compare") : ">=";

            int count = 0;
            try {
                for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                    if (entity instanceof LivingEntity living && living.isValid() && !living.isDead() && !living.getUniqueId().equals(subject.getUniqueId())) {
                        count++;
                    }
                }
            } catch (Throwable ignored) {}

            return compare(count, expected, op);
        });

        register("timealive", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            int ticksLived = subject.getTicksLived();
            double expected = number(params, "ticks");
            String op = params.containsKey("compare") ? text(params, "compare") : ">=";
            return compare(ticksLived, expected, op);
        });

        register("blocktype", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null || loc.getWorld() == null) return false;

            String mat = text(params, "material");
            try {
                Block under = loc.getBlock().getRelative(BlockFace.DOWN);
                Block at = loc.getBlock();
                return under.getType().name().equalsIgnoreCase(mat) || at.getType().name().equalsIgnoreCase(mat);
            } catch (Throwable ignored) {
                return false;
            }
        });

        register("lunarphase", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            World world = subject.getWorld();
            if (world == null) return false;

            long fullTime = world.getFullTime();
            int phaseIndex = (int) ((fullTime / 24000L) % 8L);
            if (phaseIndex < 0) phaseIndex += 8;

            String[] phaseNames = {
                    "FULL_MOON",
                    "WANING_GIBBOUS",
                    "THIRD_QUARTER",
                    "WANING_CRESCENT",
                    "NEW_MOON",
                    "WAXING_CRESCENT",
                    "FIRST_QUARTER",
                    "WAXING_GIBBOUS"
            };

            String expected = text(params, "phase").toUpperCase(Locale.ROOT);
            if (expected.equals(phaseNames[phaseIndex])) return true;
            if (expected.equals("LAST_QUARTER") && phaseIndex == 2) return true;
            try {
                return Integer.parseInt(expected) == phaseIndex;
            } catch (NumberFormatException ignored) {}
            return false;
        });

        register("altitude", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null) return false;

            double expected = number(params, "y");
            String op = params.containsKey("compare") ? text(params, "compare") : "<=";
            return compare(loc.getY(), expected, op);
        });

        register("skylight", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null || loc.getWorld() == null) return false;
            try {
                int light = loc.getBlock().getLightFromSky();
                double expected = number(params, "level");
                String op = params.containsKey("compare") ? text(params, "compare") : ">=";
                return compare(light, expected, op);
            } catch (Throwable ignored) {
                return false;
            }
        });

        register("underopensky", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null || loc.getWorld() == null) return false;
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            try {
                int highestY = loc.getWorld().getHighestBlockYAt(loc);
                boolean open = loc.getBlockY() >= highestY - 1;
                return open == expected;
            } catch (Throwable ignored) {
                return false;
            }
        });

        register("nightonly", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            World world = subject.getWorld();
            if (world == null) return false;
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            long time = world.getTime() % 24000L;
            if (time < 0) time += 24000L;
            boolean isNight = (time >= 13000L && time <= 23000L);
            return isNight == expected;
        });

        register("signal", (context, params) -> {
            String expected = text(params, "signal").toUpperCase(Locale.ROOT);
            Object actual = context.variables().get("signal");
            if (actual == null) actual = context.variables().get("sig");
            return actual != null && expected.equalsIgnoreCase(String.valueOf(actual).trim());
        });

        // P20 Conditions
        register("inpinregion", (context, params) -> {
            String regionName = params.containsKey("region") ? text(params, "region")
                    : text(params, "r");
            LivingEntity subject = context.target() instanceof LivingEntity le ? le : context.subjectLivingEntity();
            if (subject == null || subject.getLocation() == null) return false;
            return PinManager.get().isInsideRegion(regionName, subject.getLocation());
        });

        register("distancefrompin", (context, params) -> {
            String pinName = text(params, "pin");
            var pinOpt = PinManager.get().getPin(pinName);
            if (pinOpt.isEmpty()) return false;
            SinglePin pin = pinOpt.get();
            LivingEntity subject = context.target() instanceof LivingEntity le ? le : context.subjectLivingEntity();
            if (subject == null || subject.getLocation() == null) return false;
            double dist = pin.distance(subject.getLocation());
            String distSpec = params.containsKey("distance") ? String.valueOf(params.get("distance"))
                    : params.containsKey("d") ? String.valueOf(params.get("d")) : null;
            if (distSpec == null) return true;
            return evaluateComparison(dist, distSpec);
        });

        // --- MythicMobs Parity Conditions (Phase 2) ---

        ConditionEvaluator distanceEvaluator = (context, params) -> {
            Entity target = context.target();
            if (target == null) return false;
            Location cLoc = context.caster().getLocation();
            Location tLoc = target.getLocation();
            if (cLoc == null || tLoc == null) return false;
            if (cLoc.getWorld() != null && tLoc.getWorld() != null) {
                if (!Objects.equals(cLoc.getWorld().getName(), tLoc.getWorld().getName())) return false;
            }
            double dist;
            try {
                dist = cLoc.distance(tLoc);
            }
            catch (Throwable ignored) {
                double dx = cLoc.getX() - tLoc.getX();
                double dy = cLoc.getY() - tLoc.getY();
                double dz = cLoc.getZ() - tLoc.getZ();
                dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            String distSpec = params.containsKey("distance") ? String.valueOf(params.get("distance")) : params.containsKey("d") ? String.valueOf(params.get("d")) : params.containsKey("radius") ? "<=" + params.get("radius") : null;
            if (distSpec == null) return true;
            return evaluateComparison(dist, distSpec);
        };
        register("distance", distanceEvaluator);
        register("targetdistance", distanceEvaluator);

        register("health", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            String hSpec = params.containsKey("health") ? String.valueOf(params.get("health")) : params.containsKey("h") ? String.valueOf(params.get("h")) : params.containsKey("amount") ? String.valueOf(params.get("amount")) : null;
            if (hSpec == null) return true;
            hSpec = hSpec.trim();
            if (hSpec.endsWith("%")) {
                double maxHealth = 20.0;
                try {
                    maxHealth = subject.getMaxHealth();
                }
                catch (Throwable ignored) {
                }
                double percent = (subject.getHealth() / Math.max(0.1, maxHealth)) * 100.0;
                return evaluateComparison(percent, hSpec.substring(0, hSpec.length() - 1));
            }
            return evaluateComparison(subject.getHealth(), hSpec);
        });

        register("hasaura", (context, params) -> {
            if (auraScheduler == null) return false;
            String auraId = params.containsKey("aura") ? text(params, "aura") : params.containsKey("a") ? text(params, "a") : null;
            if (auraId == null) return false;
            LivingEntity subject = context.subjectLivingEntity();
            return auraScheduler.hasAura(subject.getUniqueId(), auraId);
        });

        register("moving", (context, params) -> {
            Entity subject = context.target() != null ? context.target() : context.caster();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            boolean isMoving = false;
            try {
                isMoving = subject.getVelocity().lengthSquared() > 0.001;
            }
            catch (Throwable ignored) {
            }
            return isMoving == expected;
        });

        register("sneaking", (context, params) -> {
            Entity subject = context.target() != null ? context.target() : context.caster();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            boolean isSneaking = (subject instanceof Player p) && p.isSneaking();
            return isSneaking == expected;
        });

        register("inwater", (context, params) -> {
            Entity subject = context.target() != null ? context.target() : context.caster();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            boolean inWater = false;
            try {
                inWater = subject.isInWater();
            }
            catch (Throwable ignored) {
            }
            return inWater == expected;
        });

        register("inlava", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            Location loc = subject.getLocation();
            if (loc == null || loc.getWorld() == null) return false;
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            boolean inLava = false;
            try {
                inLava = loc.getBlock().getType() == Material.LAVA;
            }
            catch (Throwable ignored) {
            }
            return inLava == expected;
        });

        register("onfire", (context, params) -> {
            Entity subject = context.target() != null ? context.target() : context.caster();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            boolean onFire = subject.getFireTicks() > 0;
            return onFire == expected;
        });

        register("haspotion", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            String typeStr = params.containsKey("type") ? text(params, "type") : params.containsKey("potion") ? text(params, "potion") : null;
            if (typeStr == null) return false;
            try {
                PotionEffectType type = PotionEffectType.getByName(typeStr.toUpperCase(Locale.ROOT));
                return type != null && subject.hasPotionEffect(type);
            }
            catch (Throwable ignored) {
                return false;
            }
        });

        register("isplayer", (context, params) -> {
            Entity subject = context.target() != null ? context.target() : context.caster();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            return (subject instanceof Player) == expected;
        });

        register("isdead", (context, params) -> {
            Entity subject = context.target() != null ? context.target() : context.caster();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            return (!subject.isValid() || subject.isDead()) == expected;
        });

        register("raining", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            boolean raining = subject.getWorld() != null && subject.getWorld().hasStorm();
            return raining == expected;
        });

        register("thundering", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            boolean thundering = subject.getWorld() != null && subject.getWorld().isThundering();
            return thundering == expected;
        });

        register("dayonly", (context, params) -> {
            LivingEntity subject = context.subjectLivingEntity();
            World world = subject.getWorld();
            if (world == null) return false;
            boolean expected = !params.containsKey("bool") || Boolean.parseBoolean(params.get("bool").toString());
            long time = world.getTime() % 24000L;
            if (time < 0) time += 24000L;
            boolean isDay = (time < 12000L || time > 23800L);
            return isDay == expected;
        });

        register("threat", (context, params) -> {
            Entity target = context.target();
            if (target == null) return false;
            Object threatVal = context.variables().get("threat");
            if (threatVal instanceof Number n) {
                String spec = params.containsKey("amount") ? String.valueOf(params.get("amount")) : params.containsKey("val") ? String.valueOf(params.get("val")) : null;
                if (spec != null) return evaluateComparison(n.doubleValue(), spec);
            }
            return false;
        });
    }

    @FunctionalInterface
    private interface ConditionEvaluator extends Condition {
        @Override
        boolean evaluate(ConditionContext context, Map<String, Object> parameters);
    }

    public record ConditionCall(String id, Map<String, Object> parameters) {
        public ConditionCall {
            Objects.requireNonNull(id, "Condition ID must not be null");
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    private static final class ConditionParameterException extends RuntimeException {
        private ConditionParameterException(String message) {
            super(message);
        }
    }
}
