package vn.haohan.lunar.api.mob.pack;

import org.bukkit.entity.EntityType;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.haohan.lunar.api.loot.DropManager;
import vn.haohan.lunar.api.loot.DropTableDefinition;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.mob.scaling.DynamicScalingDefinition;
import vn.haohan.lunar.core.skill.SkillChainDefinition;
import vn.haohan.lunar.core.skill.SkillChainParser;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages Content Packs, Namespaced Resource Identifiers, Alias Resolution,
 * Pack Isolation, and Hot-Reloading.
 */
public final class PackManager {

    private final Map<String, PackDefinition> loadedPacks = new ConcurrentHashMap<>();

    // Short alias mappings: alias -> full namespaced ID (or null if ambiguous)
    private final Map<String, String> mobAliases = new ConcurrentHashMap<>();
    private final Map<String, String> skillAliases = new ConcurrentHashMap<>();
    private final Map<String, String> dropAliases = new ConcurrentHashMap<>();

    private final SkillChainParser skillParser = new SkillChainParser();

    public record PackLoadReport(
            boolean success,
            int loadedPacksCount,
            List<String> loadedPackNames,
            List<String> errors,
            List<String> warnings
    ) {
        public static PackLoadReport ok(List<String> names, List<String> warnings) {
            return new PackLoadReport(true, names.size(), List.copyOf(names), List.of(), List.copyOf(warnings));
        }

        public static PackLoadReport fail(List<String> errors, List<String> warnings) {
            return new PackLoadReport(false, 0, List.of(), List.copyOf(errors), List.copyOf(warnings));
        }
    }

    public Map<String, PackDefinition> loadedPacks() {
        return Collections.unmodifiableMap(loadedPacks);
    }

    public Optional<PackDefinition> getPack(String packName) {
        if (packName == null) return Optional.empty();
        return Optional.ofNullable(loadedPacks.get(packName.trim().toLowerCase(Locale.ROOT)));
    }

    public synchronized PackLoadReport loadAll(
            Path packsRoot,
            MobDefinitionRegistry mobRegistry,
            SkillRegistry skillRegistry,
            DropManager dropManager
    ) {
        Objects.requireNonNull(packsRoot, "Packs root directory must not be null");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!Files.isDirectory(packsRoot)) {
            return PackLoadReport.ok(List.of(), List.of("Packs directory does not exist: " + packsRoot));
        }

        List<Path> packDirs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(packsRoot)) {
            stream.filter(Files::isDirectory).sorted().forEach(packDirs::add);
        } catch (IOException e) {
            errors.add("Failed to scan packs directory: " + e.getMessage());
            return PackLoadReport.fail(errors, warnings);
        }

        Map<String, PackDefinition> stagedPacks = new LinkedHashMap<>();

        // 1. Parse each pack
        for (Path packDir : packDirs) {
            String folderName = packDir.getFileName().toString().toLowerCase(Locale.ROOT);
            try {
                PackDefinition pack = loadPackFromDisk(packDir, folderName, errors, warnings);
                if (pack != null) {
                    stagedPacks.put(pack.name(), pack);
                }
            } catch (Exception e) {
                errors.add("Error loading pack at " + packDir + ": " + e.getMessage());
            }
        }

        // 2. Validate dependencies
        for (PackDefinition pack : stagedPacks.values()) {
            for (String dep : pack.manifest().dependencies()) {
                if (!stagedPacks.containsKey(dep)) {
                    errors.add("Pack '" + pack.name() + "' is missing required dependency: " + dep);
                }
            }
        }

        if (!errors.isEmpty()) {
            return PackLoadReport.fail(errors, warnings);
        }

        // 3. Clear existing packs and register staged
        this.loadedPacks.clear();
        this.mobAliases.clear();
        this.skillAliases.clear();
        this.dropAliases.clear();

        for (PackDefinition pack : stagedPacks.values()) {
            registerPackDefinitions(pack, mobRegistry, skillRegistry, dropManager);
        }

        rebuildAliases();

        return PackLoadReport.ok(new ArrayList<>(stagedPacks.keySet()), warnings);
    }

    public synchronized PackLoadReport reloadPack(
            Path packsRoot,
            String packName,
            MobDefinitionRegistry mobRegistry,
            SkillRegistry skillRegistry,
            DropManager dropManager
    ) {
        Objects.requireNonNull(packsRoot, "Packs root must not be null");
        Objects.requireNonNull(packName, "Pack name must not be null");
        String normName = packName.trim().toLowerCase(Locale.ROOT);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Path targetPackDir = packsRoot.resolve(normName);
        if (!Files.isDirectory(targetPackDir)) {
            // Check if folder has different casing
            try (Stream<Path> stream = Files.list(packsRoot)) {
                targetPackDir = stream.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(normName))
                        .findFirst()
                        .orElse(null);
            } catch (IOException ignored) {}
        }

        if (targetPackDir == null || !Files.isDirectory(targetPackDir)) {
            errors.add("Pack directory not found for pack: " + normName);
            return PackLoadReport.fail(errors, warnings);
        }

        PackDefinition newPack = loadPackFromDisk(targetPackDir, normName, errors, warnings);
        if (newPack == null || !errors.isEmpty()) {
            return PackLoadReport.fail(errors, warnings);
        }

        // Validate dependencies of reloaded pack
        for (String dep : newPack.manifest().dependencies()) {
            if (!loadedPacks.containsKey(dep) && !dep.equals(newPack.name())) {
                errors.add("Pack '" + newPack.name() + "' is missing required dependency: " + dep);
            }
        }

        if (!errors.isEmpty()) {
            return PackLoadReport.fail(errors, warnings);
        }

        // Unregister old pack resources
        PackDefinition oldPack = loadedPacks.remove(normName);
        if (oldPack != null) {
            unregisterPackDefinitions(oldPack, mobRegistry, skillRegistry, dropManager);
        }

        // Register new pack resources
        registerPackDefinitions(newPack, mobRegistry, skillRegistry, dropManager);
        rebuildAliases();

        return PackLoadReport.ok(List.of(newPack.name()), warnings);
    }

    public synchronized boolean disablePack(
            String packName,
            MobDefinitionRegistry mobRegistry,
            SkillRegistry skillRegistry,
            DropManager dropManager
    ) {
        if (packName == null) return false;
        String normName = packName.trim().toLowerCase(Locale.ROOT);
        PackDefinition oldPack = loadedPacks.remove(normName);
        if (oldPack == null) return false;

        unregisterPackDefinitions(oldPack, mobRegistry, skillRegistry, dropManager);
        rebuildAliases();
        return true;
    }

    private void registerPackDefinitions(
            PackDefinition pack,
            MobDefinitionRegistry mobRegistry,
            SkillRegistry skillRegistry,
            DropManager dropManager
    ) {
        loadedPacks.put(pack.name(), pack);

        if (mobRegistry != null) {
            for (MobDefinition mob : pack.mobs().values()) {
                mobRegistry.register(mob);
            }
        }
        if (skillRegistry != null) {
            for (SkillChainDefinition skill : pack.skills().values()) {
                skillRegistry.register(skill);
            }
        }
        if (dropManager != null) {
            for (DropTableDefinition drop : pack.drops().values()) {
                dropManager.register(drop);
            }
        }
    }

    private void unregisterPackDefinitions(
            PackDefinition pack,
            MobDefinitionRegistry mobRegistry,
            SkillRegistry skillRegistry,
            DropManager dropManager
    ) {
        if (mobRegistry != null) {
            for (String namespacedId : pack.mobs().keySet()) {
                mobRegistry.unregister(new MobDefinitionId(namespacedId));
            }
        }
        if (skillRegistry != null) {
            for (String namespacedId : pack.skills().keySet()) {
                skillRegistry.unregister(namespacedId);
            }
        }
        if (dropManager != null) {
            for (String namespacedId : pack.drops().keySet()) {
                dropManager.unregister(namespacedId);
            }
        }
    }

    public synchronized void rebuildAliases() {
        mobAliases.clear();
        skillAliases.clear();
        dropAliases.clear();

        Map<String, Set<String>> mobShortOccurrences = new HashMap<>();
        Map<String, Set<String>> skillShortOccurrences = new HashMap<>();
        Map<String, Set<String>> dropShortOccurrences = new HashMap<>();

        for (PackDefinition pack : loadedPacks.values()) {
            for (String fullId : pack.mobs().keySet()) {
                String shortId = extractShortId(fullId);
                mobShortOccurrences.computeIfAbsent(shortId, k -> new HashSet<>()).add(fullId);
            }
            for (String fullId : pack.skills().keySet()) {
                String shortId = extractShortId(fullId);
                skillShortOccurrences.computeIfAbsent(shortId, k -> new HashSet<>()).add(fullId);
            }
            for (String fullId : pack.drops().keySet()) {
                String shortId = extractShortId(fullId);
                dropShortOccurrences.computeIfAbsent(shortId, k -> new HashSet<>()).add(fullId);
            }
        }

        mobShortOccurrences.forEach((alias, fullIds) -> {
            if (fullIds.size() == 1) {
                mobAliases.put(alias, fullIds.iterator().next());
            }
        });
        skillShortOccurrences.forEach((alias, fullIds) -> {
            if (fullIds.size() == 1) {
                skillAliases.put(alias, fullIds.iterator().next());
            }
        });
        dropShortOccurrences.forEach((alias, fullIds) -> {
            if (fullIds.size() == 1) {
                dropAliases.put(alias, fullIds.iterator().next());
            }
        });
    }

    public Optional<String> resolveMobId(String idOrAlias) {
        if (idOrAlias == null) return Optional.empty();
        String query = idOrAlias.trim().toLowerCase(Locale.ROOT);
        if (query.contains(":")) {
            for (PackDefinition p : loadedPacks.values()) {
                if (p.mobs().containsKey(query)) return Optional.of(query);
            }
            return Optional.empty();
        }
        return Optional.ofNullable(mobAliases.get(query));
    }

    public Optional<String> resolveSkillId(String idOrAlias) {
        if (idOrAlias == null) return Optional.empty();
        String query = idOrAlias.trim().toLowerCase(Locale.ROOT);
        if (query.contains(":")) {
            for (PackDefinition p : loadedPacks.values()) {
                if (p.skills().containsKey(query)) return Optional.of(query);
            }
            return Optional.empty();
        }
        return Optional.ofNullable(skillAliases.get(query));
    }

    public Optional<String> resolveDropId(String idOrAlias) {
        if (idOrAlias == null) return Optional.empty();
        String query = idOrAlias.trim().toLowerCase(Locale.ROOT);
        if (query.contains(":")) {
            for (PackDefinition p : loadedPacks.values()) {
                if (p.drops().containsKey(query)) return Optional.of(query);
            }
            return Optional.empty();
        }
        return Optional.ofNullable(dropAliases.get(query));
    }

    private static String extractShortId(String namespacedId) {
        int colon = namespacedId.indexOf(':');
        return colon >= 0 ? namespacedId.substring(colon + 1) : namespacedId;
    }

    private PackDefinition loadPackFromDisk(Path packDir, String fallbackName, List<String> errors, List<String> warnings) {
        PackManifest manifest = loadManifest(packDir, fallbackName);
        String packName = manifest.name();

        // 1. Mobs
        Map<String, MobDefinition> mobs = new LinkedHashMap<>();
        Path mobsDir = packDir.resolve("mobs");
        if (Files.isDirectory(mobsDir)) {
            loadPackMobs(mobsDir, packName, mobs, errors);
        }

        // 2. Skills
        Map<String, SkillChainDefinition> skills = new LinkedHashMap<>();
        Path skillsDir = packDir.resolve("skills");
        if (Files.isDirectory(skillsDir)) {
            var skillResult = skillParser.parseDirectory(skillsDir);
            for (Map.Entry<String, SkillChainDefinition> entry : skillResult.definitions().entrySet()) {
                String namespaced = packName + ":" + entry.getKey();
                SkillChainDefinition original = entry.getValue();
                SkillDefinition origDef = original.definition();
                SkillDefinition newDef = new SkillDefinition(namespaced, origDef.triggers(), origDef.cooldownTicks());
                skills.put(namespaced, new SkillChainDefinition(newDef, original.conditions(), original.targeter(), original.mechanics()));
            }
            errors.addAll(skillResult.errors());
        }

        // 3. Drops
        Map<String, DropTableDefinition> drops = new LinkedHashMap<>();
        Path dropsDir = packDir.resolve("drops");
        if (Files.isDirectory(dropsDir)) {
            loadPackDrops(dropsDir, packName, drops, errors);
        }

        // 4. Model Assets (.bbmodel / models)
        List<String> modelAssets = scanModelAssets(packDir);

        return new PackDefinition(manifest, packDir, mobs, skills, drops, modelAssets);
    }

    private PackManifest loadManifest(Path packDir, String fallbackName) {
        Path manifestFile = packDir.resolve("pack.yml");
        if (!Files.isRegularFile(manifestFile)) {
            manifestFile = packDir.resolve("pack.yaml");
        }
        if (!Files.isRegularFile(manifestFile)) {
            return new PackManifest(fallbackName, "1.0.0", "Unknown", "", List.of());
        }

        LoaderOptions options = new LoaderOptions();
        try (InputStream in = Files.newInputStream(manifestFile)) {
            Object raw = new Yaml(new SafeConstructor(options)).load(in);
            if (raw instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new LinkedHashMap<>();
                rawMap.forEach((k, v) -> { if (k instanceof String ks) map.put(ks, v); });
                return PackManifest.fromMap(fallbackName, map);
            }
        } catch (Exception e) {
            // fallback
        }
        return new PackManifest(fallbackName, "1.0.0", "Unknown", "", List.of());
    }

    private void loadPackMobs(Path mobsDir, String packName, Map<String, MobDefinition> mobs, List<String> errors) {
        LoaderOptions options = new LoaderOptions();
        try (Stream<Path> files = Files.walk(mobsDir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yml") || p.getFileName().toString().endsWith(".yaml"))
                    .forEach(file -> {
                        try (InputStream in = Files.newInputStream(file)) {
                            Object raw = new Yaml(new SafeConstructor(options)).load(in);
                            if (raw instanceof Map<?, ?> map) {
                                for (Map.Entry<?, ?> entry : map.entrySet()) {
                                    if (entry.getKey() instanceof String mobId && entry.getValue() instanceof Map<?, ?> mobMap) {
                                        Map<String, Object> castMobMap = new LinkedHashMap<>();
                                        mobMap.forEach((k, v) -> { if (k instanceof String ks) castMobMap.put(ks, v); });
                                        MobDefinition def = parseNamespacedMob(packName, mobId, castMobMap);
                                        mobs.put(def.id().value(), def);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            errors.add("Failed parsing mob file " + file + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add("Failed walking mobs dir in pack " + packName + ": " + e.getMessage());
        }
    }

    private MobDefinition parseNamespacedMob(String packName, String rawMobId, Map<String, Object> map) {
        String namespacedId = packName + ":" + rawMobId.toLowerCase(Locale.ROOT);
        String displayName = (String) map.getOrDefault("display_name",
                map.getOrDefault("display-name", map.getOrDefault("name", rawMobId)));
        String typeStr = (String) map.getOrDefault("type",
                map.getOrDefault("entity_type", map.getOrDefault("entity-type", "IRON_GOLEM")));
        EntityType entityType = EntityType.IRON_GOLEM;
        try {
            entityType = EntityType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {}

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

        List<String> aiGoalSelectors = new ArrayList<>();
        Object goalsObj = map.getOrDefault("AIGoalSelectors", map.get("ai_goal_selectors"));
        if (goalsObj instanceof List<?> gList) {
            for (Object item : gList) {
                if (item != null) aiGoalSelectors.add(item.toString());
            }
        }

        List<String> aiTargetSelectors = new ArrayList<>();
        Object targetsObj = map.getOrDefault("AITargetSelectors", map.get("ai_target_selectors"));
        if (targetsObj instanceof List<?> tList) {
            for (Object item : tList) {
                if (item != null) aiTargetSelectors.add(item.toString());
            }
        }

        return new MobDefinition(
                new MobDefinitionId(namespacedId),
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
                dynamicScaling,
                aiGoalSelectors,
                aiTargetSelectors
        );
    }

    private void loadPackDrops(Path dropsDir, String packName, Map<String, DropTableDefinition> drops, List<String> errors) {
        LoaderOptions options = new LoaderOptions();
        try (Stream<Path> files = Files.walk(dropsDir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yml") || p.getFileName().toString().endsWith(".yaml"))
                    .forEach(file -> {
                        try (InputStream in = Files.newInputStream(file)) {
                            Object raw = new Yaml(new SafeConstructor(options)).load(in);
                            if (raw instanceof Map<?, ?> map) {
                                for (Map.Entry<?, ?> entry : map.entrySet()) {
                                    if (entry.getKey() instanceof String dropId && entry.getValue() instanceof Map<?, ?> dropMap) {
                                        String namespacedId = packName + ":" + dropId.toLowerCase(Locale.ROOT);
                                        int rolls = 1;
                                        if (dropMap.containsKey("rolls") && dropMap.get("rolls") instanceof Number n) {
                                            rolls = n.intValue();
                                        }
                                        drops.put(namespacedId, new DropTableDefinition(namespacedId, DropTableDefinition.RollMode.INDEPENDENT, List.of(), rolls));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            errors.add("Failed parsing drops file " + file + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add("Failed walking drops dir in pack " + packName + ": " + e.getMessage());
        }
    }

    public List<String> scanModelAssets(Path packDir) {
        List<String> models = new ArrayList<>();
        List<Path> assetDirs = List.of(
                packDir.resolve("assets"),
                packDir.resolve("models"),
                packDir.resolve("assets/models")
        );
        for (Path dir : assetDirs) {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".bbmodel"))
                            .forEach(p -> models.add(p.getFileName().toString()));
                } catch (IOException ignored) {}
            }
        }
        return models;
    }
}
