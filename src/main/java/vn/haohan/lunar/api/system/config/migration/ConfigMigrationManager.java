package vn.haohan.lunar.core.system.config.migration;

import org.bukkit.entity.EntityType;
import vn.haohan.lunar.core.system.config.ConfigLoadException;
import vn.haohan.lunar.core.system.config.LunarYamlLoader;
import vn.haohan.lunar.api.loot.DropManager;
import vn.haohan.lunar.api.loot.DropTableDefinition;
import vn.haohan.lunar.core.survival.boss.warden.WardenSkillRegistry;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.mob.scaling.DynamicScalingDefinition;
import vn.haohan.lunar.api.skill.SkillChainDefinition;
import vn.haohan.lunar.api.skill.SkillChainParser;
import vn.haohan.lunar.api.skill.SkillRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Handles Validate-then-Swap configuration reload and config-version migrations.
 * Prevents active combat disruption and avoids replacing good registries with invalid data.
 */
public final class ConfigMigrationManager {

    public static final int CURRENT_CONFIG_VERSION = 1;

    private final LunarYamlLoader yamlLoader = new LunarYamlLoader();
    private final SkillChainParser skillParser = new SkillChainParser();

    public record ReloadReport(
            boolean isSuccess,
            int loadedMobs,
            int loadedSkills,
            int loadedDrops,
            List<String> issues,
            List<String> warnings
    ) {
        public static ReloadReport failure(List<String> issues, List<String> warnings) {
            return new ReloadReport(false, 0, 0, 0, List.copyOf(issues), List.copyOf(warnings));
        }

        public static ReloadReport success(int mobs, int skills, int drops, List<String> warnings) {
            return new ReloadReport(true, mobs, skills, drops, List.of(), List.copyOf(warnings));
        }
    }

    public ReloadReport reloadAll(
            Path configRoot,
            MobDefinitionRegistry mobRegistry,
            SkillRegistry skillRegistry,
            DropManager dropManager,
            LunarMobManager mobManager
    ) {
        Objects.requireNonNull(configRoot, "Config root must not be null");
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!Files.isDirectory(configRoot)) {
            issues.add("Config root directory does not exist: " + configRoot);
            return ReloadReport.failure(issues, warnings);
        }

        // 1. Raw YAML batch load
        LunarYamlLoader.LoadedConfigBatch batch;
        try {
            batch = yamlLoader.load(configRoot);
        } catch (ConfigLoadException e) {
            e.report().issues().forEach(issue -> issues.add(issue.toString()));
            return ReloadReport.failure(issues, warnings);
        }

        // 2. Parse staging skills
        Map<String, SkillChainDefinition> stagingSkills = new LinkedHashMap<>();
        Path skillsDir = configRoot.resolve("skills");
        if (Files.isDirectory(skillsDir)) {
            var skillResult = skillParser.parseDirectory(skillsDir);
            stagingSkills.putAll(skillResult.definitions());
            issues.addAll(skillResult.errors());
        }

        // 3. Parse staging drops
        Map<String, DropTableDefinition> stagingDrops = new LinkedHashMap<>();
        Map<String, Map<String, Object>> rawDrops = batch.directories().getOrDefault("drops", Map.of());
        for (Map.Entry<String, Map<String, Object>> entry : rawDrops.entrySet()) {
            String dropId = entry.getKey().toLowerCase(Locale.ROOT);
            stagingDrops.put(dropId, parseDropTable(dropId, entry.getValue()));
        }

        // 4. Parse staging mobs & check versions
        Map<String, MobDefinition> stagingMobs = new LinkedHashMap<>();
        Map<String, Map<String, Object>> rawMobs = batch.directories().getOrDefault("mobs", Map.of());
        for (Map.Entry<String, Map<String, Object>> entry : rawMobs.entrySet()) {
            String mobId = entry.getKey().toLowerCase(Locale.ROOT);
            Map<String, Object> mobMap = entry.getValue();

            checkConfigVersion("mobs/" + mobId, mobMap, warnings);
            MobDefinition def = parseMobDefinition(mobId, mobMap, issues);
            if (def != null) {
                stagingMobs.put(mobId, def);
            }
        }

        // 5. Cross-link integrity validation
        for (MobDefinition mobDef : stagingMobs.values()) {
            String mobId = mobDef.id().value();

            // Check skill references
            for (String skillRef : mobDef.skillReferences()) {
                String normSkill = skillRef.trim().toLowerCase(Locale.ROOT);
                boolean inStaging = stagingSkills.containsKey(normSkill);
                boolean inActive = skillRegistry != null && skillRegistry.contains(normSkill);
                boolean inBuiltin = WardenSkillRegistry.skillIds().contains(normSkill);
                if (!inStaging && !inActive && !inBuiltin) {
                    issues.add("Mob '" + mobId + "' references missing skill: " + skillRef);
                }
            }

            // Check drop table reference
            if (mobDef.dropTableReference().isPresent()) {
                String dropRef = mobDef.dropTableReference().get().trim().toLowerCase(Locale.ROOT);
                boolean inStaging = stagingDrops.containsKey(dropRef);
                boolean inActive = dropManager != null && dropManager.hasTable(dropRef);
                if (!inStaging && !inActive) {
                    issues.add("Mob '" + mobId + "' references missing drop table: " + dropRef);
                }
            }

            // Check mount reference
            if (mobDef.mountId().isPresent()) {
                String mountRef = mobDef.mountId().get().trim().toLowerCase(Locale.ROOT);
                boolean inStaging = stagingMobs.containsKey(mountRef);
                boolean inActive = mobRegistry != null && mobRegistry.contains(mountRef);
                if (!inStaging && !inActive) {
                    warnings.add("Mob '" + mobId + "' references unknown mount: " + mountRef);
                }
            }
        }

        // If validation errors occurred, abort without touching active registries
        if (!issues.isEmpty()) {
            return ReloadReport.failure(issues, warnings);
        }

        // 6. Atomic swap
        if (mobRegistry != null) {
            mobRegistry.replaceAll(stagingMobs.values());
        }
        if (skillRegistry != null) {
            skillRegistry.replaceAll(stagingSkills.values());
        }
        if (dropManager != null && !stagingDrops.isEmpty()) {
            dropManager.replaceAll(stagingDrops.values());
        }

        // 7. Hot-swap references on living ActiveLunarMobs without despawning or resetting health
        if (mobManager != null) {
            for (ActiveLunarMob livingMob : mobManager.snapshot()) {
                MobDefinition newDefinition = stagingMobs.get(livingMob.definitionId().value());
                if (newDefinition != null) {
                    livingMob.updateDefinition(newDefinition);
                }
            }
        }

        return ReloadReport.success(stagingMobs.size(), stagingSkills.size(), stagingDrops.size(), warnings);
    }

    public static int checkConfigVersion(String identifier, Map<String, Object> values, List<String> warnings) {
        Object ver = values.get("config-version");
        if (ver == null) {
            ver = values.get("config_version");
        }
        if (ver == null) {
            warnings.add("Config '" + identifier + "' is missing 'config-version'; defaulting to " + CURRENT_CONFIG_VERSION);
            return CURRENT_CONFIG_VERSION;
        }
        if (ver instanceof Number num) {
            int version = num.intValue();
            if (version < CURRENT_CONFIG_VERSION) {
                warnings.add("Config '" + identifier + "' uses older version " + version + " (current is " + CURRENT_CONFIG_VERSION + "); auto-migrated.");
            } else if (version > CURRENT_CONFIG_VERSION) {
                warnings.add("Config '" + identifier + "' uses future version " + version + " (current is " + CURRENT_CONFIG_VERSION + ").");
            }
            return version;
        }
        return CURRENT_CONFIG_VERSION;
    }

    private static MobDefinition parseMobDefinition(String id, Map<String, Object> map, List<String> issues) {
        String displayName = (String) map.getOrDefault("display_name",
                map.getOrDefault("display-name", map.getOrDefault("name", id)));
        String typeStr = (String) map.getOrDefault("type",
                map.getOrDefault("entity_type", map.getOrDefault("entity-type", "IRON_GOLEM")));
        EntityType entityType = EntityType.IRON_GOLEM;
        try {
            entityType = EntityType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            issues.add("Mob '" + id + "': invalid entity type '" + typeStr + "'");
        }

        String modelId = (String) map.getOrDefault("model_id", map.get("model-id"));

        List<String> skills = new ArrayList<>();
        Object skillsObj = map.get("skills");
        if (skillsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    skills.add(s);
                }
            }
        }

        String dropTable = (String) map.getOrDefault("drop_table", map.get("drop-table"));
        String mountId = (String) map.getOrDefault("Mount", map.get("mount"));

        List<String> riders = new ArrayList<>();
        Object ridersObj = map.getOrDefault("Riders", map.get("riders"));
        if (ridersObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    riders.add(s);
                }
            }
        }

        DynamicScalingDefinition dynamicScaling = null;
        Object scalingObj = map.getOrDefault("DynamicScaling", map.get("dynamic_scaling"));
        if (scalingObj instanceof Map<?, ?> sm) {
            Map<String, Object> castMap = new LinkedHashMap<>();
            sm.forEach((k, v) -> { if (k instanceof String ks) castMap.put(ks, v); });
            dynamicScaling = DynamicScalingDefinition.fromMap(castMap);
        }

        return new MobDefinition(
                new MobDefinitionId(id),
                entityType,
                displayName,
                modelId,
                Map.of(),
                Map.of(),
                skills,
                dropTable,
                Set.of(),
                MobEquipmentDefinition.empty(),
                mountId,
                riders,
                dynamicScaling
        );
    }

    private static DropTableDefinition parseDropTable(String id, Map<String, Object> map) {
        int rolls = 1;
        if (map.containsKey("rolls")) {
            Object r = map.get("rolls");
            if (r instanceof Number n) rolls = n.intValue();
        }
        return new DropTableDefinition(id, DropTableDefinition.RollMode.INDEPENDENT, List.of(), rolls);
    }
}
