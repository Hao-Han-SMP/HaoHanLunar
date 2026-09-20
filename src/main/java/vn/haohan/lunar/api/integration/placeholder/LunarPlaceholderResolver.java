package vn.haohan.lunar.core.integration.placeholder;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.api.mob.stat.StatType;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.api.mob.scaling.MobLevelApplier;
import vn.haohan.lunar.api.skill.SkillCastContext;
import vn.haohan.lunar.api.system.util.SafeExpressionEvaluator;
import vn.haohan.lunar.api.system.variable.VariableManager;

import java.util.Locale;
import java.util.UUID;

/**
 * High-performance, recursion-guarded placeholder resolver for messages and skills.
 * Integrated with SafeExpressionEvaluator for <skill.calc.equation>.
 */
public final class LunarPlaceholderResolver {

    private static final int MAX_PASSES = 4;

    private LunarPlaceholderResolver() {
    }

    public static String resolve(String template, SkillCastContext context, LivingEntity target, VariableManager variableManager) {
        ActiveLunarMob caster = context != null ? context.caster() : null;
        return resolve(template, caster, target, context, variableManager);
    }

    public static String resolve(String template, ActiveLunarMob caster, LivingEntity target, VariableManager variableManager) {
        return resolve(template, caster, target, null, variableManager);
    }

    public static String resolve(String template, ActiveLunarMob caster, LivingEntity target,
                                 SkillCastContext context, VariableManager variableManager) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (!template.contains("<") || !template.contains(">")) {
            return template;
        }

        String current = template;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            if (!current.contains("<") || !current.contains(">")) {
                break;
            }
            String next = resolveSinglePass(current, caster, target, context, variableManager);
            if (next.equals(current)) {
                break;
            }
            current = next;
        }

        return current;
    }

    private static String resolveSinglePass(String input, ActiveLunarMob caster, LivingEntity target,
                                            SkillCastContext context, VariableManager variableManager) {
        StringBuilder result = new StringBuilder(input.length() + 32);
        int cursor = 0;
        int len = input.length();

        while (cursor < len) {
            int openTag = input.indexOf('<', cursor);
            if (openTag == -1) {
                result.append(input, cursor, len);
                break;
            }

            result.append(input, cursor, openTag);

            int closeTag = input.indexOf('>', openTag + 1);
            if (closeTag == -1) {
                result.append(input.substring(openTag));
                break;
            }

            String tag = input.substring(openTag + 1, closeTag);
            String replacement = evaluateTag(tag, caster, target, context, variableManager);

            if (replacement != null) {
                result.append(replacement);
            } else {
                result.append('<').append(tag).append('>');
            }

            cursor = closeTag + 1;
        }

        return result.toString();
    }

    private static String evaluateTag(String rawTag, ActiveLunarMob caster, LivingEntity target,
                                      SkillCastContext context, VariableManager variableManager) {
        if (rawTag == null || rawTag.isBlank()) {
            return null;
        }

        String lower = rawTag.trim().toLowerCase(Locale.ROOT);

        // Safe Expression Calculation: <skill.calc.10 * 2 + 5> or <calc.10 + 2>
        if (lower.startsWith("skill.calc.") || lower.startsWith("calc.")) {
            String expr = rawTag.trim().substring(rawTag.trim().indexOf('.') + 1);
            if (expr.startsWith("calc.")) {
                expr = expr.substring(5);
            }
            try {
                double val = SafeExpressionEvaluator.evaluate(expr, 0.0);
                if (val == (long) val) {
                    return String.valueOf((long) val);
                }
                return String.format(Locale.ROOT, "%.2f", val);
            } catch (Throwable ignored) {
                return "0";
            }
        }

        // Mob / Caster Placeholders
        if (lower.startsWith("mob.") || lower.startsWith("caster.")) {
            if (caster == null) return "Unknown";
            String sub = lower.substring(lower.indexOf('.') + 1);
            if (sub.startsWith("playercount")) {
                double radius = 32.0;
                if (sub.startsWith("playercount.")) {
                    try { radius = Double.parseDouble(sub.substring(12)); } catch (Exception ignored) {}
                }
                return String.valueOf(countNearbyPlayers(caster, radius));
            }
            return switch (sub) {
                case "name" -> safeGetMobName(caster);
                case "id" -> caster.definitionId().value();
                case "uuid" -> caster.entityId().toString();
                case "health" -> String.valueOf((int) safeGetHealth(caster.entity()));
                case "max_health" -> String.valueOf((int) safeGetMaxHealth(caster.entity()));
                case "damage" -> String.valueOf((int) safeGetDamage(caster.entity()));
                case "stance" -> caster.stance();
                case "level" -> String.valueOf(MobLevelApplier.getLevel(caster.entity()));
                case "threat.top" -> caster.threatTable().topTarget().map(UUID::toString).orElse("none");
                default -> {
                    if (sub.startsWith("stat.")) {
                        String statKey = sub.substring(5).toUpperCase(Locale.ROOT);
                        try {
                            StatType statType = StatType.valueOf(statKey);
                            double val = caster.stats().snapshot(0).get(statType);
                            yield formatStatValue(val);
                        } catch (IllegalArgumentException e) {
                            yield "0";
                        }
                    }
                    if (sub.startsWith("var.") && variableManager != null) {
                        String varName = sub.substring(4);
                        yield variableManager.getCaster(caster.entityId())
                                .get(varName)
                                .map(v -> v.asString())
                                .orElse("");
                    }
                    yield null;
                }
            };
        }

        // Target Placeholders
        if (lower.startsWith("target.")) {
            if (target == null) return "None";
            String sub = lower.substring(7);
            return switch (sub) {
                case "name" -> safeGetEntityName(target);
                case "uuid" -> target.getUniqueId().toString();
                case "health" -> String.valueOf((int) safeGetHealth(target));
                case "max_health" -> String.valueOf((int) safeGetMaxHealth(target));
                case "damage" -> String.valueOf((int) safeGetDamage(target));
                case "distance" -> formatDistance(caster, target);
                case "threat" -> {
                    if (caster != null) {
                        yield String.valueOf((int) caster.threatTable().getThreat(target.getUniqueId()));
                    }
                    yield "0";
                }
                default -> {
                    if (sub.startsWith("var.") && variableManager != null) {
                        String varName = sub.substring(4);
                        yield variableManager.findTarget(target)
                                .flatMap(h -> h.get(varName))
                                .map(v -> v.asString())
                                .orElse("");
                    }
                    yield null;
                }
            };
        }

        // Global Variables
        if (lower.startsWith("global.var.") && variableManager != null) {
            String varName = lower.substring(11);
            return variableManager.getGlobal().get(varName).map(v -> v.asString()).orElse("");
        }

        // Cast Variables
        if (lower.startsWith("cast.var.") && context != null) {
            String varName = lower.substring(9);
            return context.castVariables().get(varName).map(v -> v.asString()).orElse("");
        }

        return null;
    }

    private static String formatStatValue(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String safeGetMobName(ActiveLunarMob mob) {
        if (mob == null) return "Unknown";
        try {
            String custom = mob.entity().getCustomName();
            if (custom != null && !custom.isBlank()) return custom;
        } catch (Throwable ignored) {
        }
        return mob.definition().displayName();
    }

    private static String safeGetEntityName(LivingEntity entity) {
        if (entity == null) return "None";
        try {
            if (entity instanceof Player player) {
                return player.getName();
            }
            String custom = entity.getCustomName();
            return custom != null && !custom.isBlank() ? custom : entity.getName();
        } catch (Throwable ignored) {
            return "Unknown";
        }
    }

    private static double safeGetHealth(LivingEntity entity) {
        if (entity == null) return 0.0;
        try {
            return entity.getHealth();
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double safeGetMaxHealth(LivingEntity entity) {
        if (entity == null) return 0.0;
        try {
            return entity.getMaxHealth();
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double safeGetDamage(LivingEntity entity) {
        if (entity == null) return 5.0;
        try {
            var attr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attr != null) return attr.getValue();
        } catch (Throwable ignored) {
        }
        return 5.0;
    }

    private static int countNearbyPlayers(ActiveLunarMob caster, double radius) {
        if (caster == null || caster.entity() == null) return 0;
        try {
            Location loc = caster.entity().getLocation();
            if (loc == null || loc.getWorld() == null) return 0;
            double rSq = radius * radius;
            int count = 0;
            for (Player p : loc.getWorld().getPlayers()) {
                if (!p.isValid() || p.isDead()) continue;
                if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                if (p.getLocation().getWorld().equals(loc.getWorld()) && p.getLocation().distanceSquared(loc) <= rSq) {
                    count++;
                }
            }
            return count;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String formatDistance(ActiveLunarMob caster, LivingEntity target) {
        if (caster == null || caster.entity() == null || target == null) {
            return "0.0";
        }
        try {
            Location loc1 = caster.entity().getLocation();
            Location loc2 = target.getLocation();
            if (loc1 != null && loc2 != null && loc1.getWorld() != null && loc1.getWorld().equals(loc2.getWorld())) {
                double dist = loc1.distance(loc2);
                return String.format(Locale.ROOT, "%.1f", dist);
            }
        } catch (Throwable ignored) {
        }
        return "0.0";
    }
}
