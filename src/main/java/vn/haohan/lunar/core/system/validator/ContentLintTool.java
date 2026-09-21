package vn.haohan.lunar.core.system.validator;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Standalone Content Lint and Schema Validation CLI tool.
 * Verifies YAML configuration schemas, data integrity, and cross-references (orphan & cycle detection).
 */
public final class ContentLintTool {

    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_.-]+$");

    // Pre-registered system built-in skills
    private static final Set<String> BUILTIN_SKILLS = Set.of(
            "aerial_slash_combo", "ground_slam", "celestial_summon",
            "shield_block", "shield_block_push", "shield_charge",
            "shield_sword_slam", "thrust_fling", "targeted_light_strike", "pursuit"
    );

    public record LintIssue(String file, String field, String message, boolean isError) {
        @Override
        public String toString() {
            String prefix = isError ? "[ERROR]" : "[WARN]";
            return String.format("%s %s -> %s: %s", prefix, file, field, message);
        }
    }

    public record LintReport(List<LintIssue> issues) {
        public boolean hasErrors() {
            return issues.stream().anyMatch(LintIssue::isError);
        }

        public int errorCount() {
            return (int) issues.stream().filter(LintIssue::isError).count();
        }

        public int warningCount() {
            return (int) issues.stream().filter(i -> !i.isError()).count();
        }
    }

    /**
     * Asynchronously lints a configuration directory off the Bukkit primary thread.
     */
    public static CompletableFuture<LintReport> lintDirectoryAsync(Path rootDir) {
        return CompletableFuture.supplyAsync(() -> lintDirectory(rootDir), ForkJoinPool.commonPool());
    }

    /**
     * Scans and validates a directory containing configuration files.
     */
    public static LintReport lintDirectory(Path rootDir) {
        List<LintIssue> issues = new ArrayList<>();
        Yaml yaml = new Yaml();

        Set<String> knownMobIds = new HashSet<>();
        Set<String> knownSkillIds = new HashSet<>(BUILTIN_SKILLS);
        Set<String> knownDropTableIds = new HashSet<>();

        Map<String, List<String>> mobSkillReferences = new HashMap<>();
        Map<String, String> mobDropTableReferences = new HashMap<>();
        Map<String, String> spawnerMobReferences = new HashMap<>();
        Map<String, List<String>> skillSubskillReferences = new HashMap<>();

        try {
            if (!Files.exists(rootDir)) {
                issues.add(new LintIssue(rootDir.toString(), "path", "Configuration root directory does not exist", true));
                return new LintReport(issues);
            }

            // 1. Collect all YAML files
            List<Path> yamlFiles;
            try (Stream<Path> stream = Files.walk(rootDir)) {
                yamlFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                        .toList();
            }

            for (Path file : yamlFiles) {
                String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
                if (relativePath.equalsIgnoreCase("paper-plugin.yml") || relativePath.equalsIgnoreCase("plugin.yml")
                        || relativePath.equalsIgnoreCase("config.yml") || relativePath.contains("example")) {
                    continue; // Skip plugin metadata / non-content configs
                }

                Map<String, Object> data;
                try (InputStream in = new FileInputStream(file.toFile())) {
                    Object parsed = yaml.load(in);
                    if (!(parsed instanceof Map<?, ?> rawMap)) {
                        issues.add(new LintIssue(relativePath, "root", "YAML root must be a map/dictionary", true));
                        continue;
                    }
                    data = new HashMap<>();
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        if (entry.getKey() != null) {
                            data.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                } catch (Exception ex) {
                    issues.add(new LintIssue(relativePath, "syntax", "Failed to parse YAML: " + ex.getMessage(), true));
                    continue;
                }

                if (relativePath.startsWith("mobs/")) {
                    validateMobConfig(relativePath, data, issues, knownMobIds, mobSkillReferences, mobDropTableReferences);
                } else if (relativePath.startsWith("skills/")) {
                    validateSkillConfig(relativePath, data, issues, knownSkillIds, skillSubskillReferences);
                } else if (relativePath.startsWith("spawners/")) {
                    validateSpawnerConfig(relativePath, data, issues, spawnerMobReferences);
                } else if (relativePath.startsWith("drops/")) {
                    validateDropTableConfig(relativePath, data, issues, knownDropTableIds);
                }
            }

            // 2. Cross-reference / Orphan validation
            for (Map.Entry<String, List<String>> entry : mobSkillReferences.entrySet()) {
                String mobFile = entry.getKey();
                for (String skillRef : entry.getValue()) {
                    if (!knownSkillIds.contains(skillRef.toLowerCase(Locale.ROOT))) {
                        issues.add(new LintIssue(mobFile, "skills", "Referenced skill ID '" + skillRef + "' does not exist (orphan reference)", true));
                    }
                }
            }

            for (Map.Entry<String, String> entry : mobDropTableReferences.entrySet()) {
                String mobFile = entry.getKey();
                String dropRef = entry.getValue();
                if (!knownDropTableIds.isEmpty() && !knownDropTableIds.contains(dropRef.toLowerCase(Locale.ROOT))) {
                    issues.add(new LintIssue(mobFile, "drop_table", "Referenced drop table '" + dropRef + "' does not exist", false));
                }
            }

            for (Map.Entry<String, String> entry : spawnerMobReferences.entrySet()) {
                String spawnerFile = entry.getKey();
                String mobRef = entry.getValue();
                if (!knownMobIds.contains(mobRef.toLowerCase(Locale.ROOT))) {
                    issues.add(new LintIssue(spawnerFile, "mob", "Referenced mob ID '" + mobRef + "' does not exist", true));
                }
            }

            // 3. Subskill reference & cycle validation
            for (Map.Entry<String, List<String>> entry : skillSubskillReferences.entrySet()) {
                String parentSkill = entry.getKey();
                for (String childSkill : entry.getValue()) {
                    if (!knownSkillIds.contains(childSkill)) {
                        issues.add(new LintIssue("skills/" + parentSkill, "mechanics", "Referenced subskill ID '" + childSkill + "' does not exist", true));
                    }
                }
            }

            detectCircularSkills(skillSubskillReferences, issues);

        } catch (Exception ex) {
            issues.add(new LintIssue("Global", "runtime", "Linting execution failed: " + ex.getMessage(), true));
        }

        return new LintReport(issues);
    }

    private static void detectCircularSkills(Map<String, List<String>> graph, List<LintIssue> issues) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfsCycle(node, graph, visited, visiting, new ArrayList<>(), issues);
            }
        }
    }

    private static void dfsCycle(String current, Map<String, List<String>> graph, Set<String> visited, Set<String> visiting, List<String> path, List<LintIssue> issues) {
        visiting.add(current);
        path.add(current);

        List<String> neighbors = graph.getOrDefault(current, Collections.emptyList());
        for (String next : neighbors) {
            if (visiting.contains(next)) {
                int cycleStart = path.indexOf(next);
                List<String> cycle = path.subList(cycleStart, path.size());
                issues.add(new LintIssue("skills/" + current, "subskill", "Circular skill recursion detected: " + String.join(" -> ", cycle) + " -> " + next, true));
            } else if (!visited.contains(next)) {
                dfsCycle(next, graph, visited, visiting, path, issues);
            }
        }

        path.remove(path.size() - 1);
        visiting.remove(current);
        visited.add(current);
    }

    private static void validateMobConfig(String file, Map<String, Object> data, List<LintIssue> issues,
                                          Set<String> knownMobIds,
                                          Map<String, List<String>> mobSkillRefs,
                                          Map<String, String> mobDropTableRefs) {
        String id = validateId(file, data, "id", issues);
        if (id != null) {
            knownMobIds.add(id);
        }

        if (!data.containsKey("type") && !data.containsKey("entity-type")) {
            issues.add(new LintIssue(file, "type", "Missing required field 'type' or 'entity-type' (EntityType)", true));
        }

        if (data.containsKey("skills")) {
            Object skillsObj = data.get("skills");
            if (skillsObj instanceof List<?> list) {
                List<String> refs = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) refs.add(item.toString());
                }
                mobSkillRefs.put(file, refs);
            } else {
                issues.add(new LintIssue(file, "skills", "'skills' must be a list of skill IDs", true));
            }
        }

        Object dropRef = data.getOrDefault("drop_table", data.get("drop-table"));
        if (dropRef != null) {
            mobDropTableRefs.put(file, dropRef.toString());
        }
    }

    private static void validateSkillConfig(String file, Map<String, Object> data, List<LintIssue> issues, Set<String> knownSkillIds, Map<String, List<String>> skillSubskillRefs) {
        String id = validateId(file, data, "id", issues);
        if (id != null) {
            knownSkillIds.add(id);
        }

        if (data.containsKey("mechanics")) {
            Object mechs = data.get("mechanics");
            if (mechs instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (!(item instanceof Map<?, ?> mechMap)) {
                        issues.add(new LintIssue(file, "mechanics[" + i + "]", "Mechanic entry must be a map", true));
                    } else if (!mechMap.containsKey("type") && !mechMap.containsKey("mechanic")) {
                        issues.add(new LintIssue(file, "mechanics[" + i + "]", "Mechanic entry missing required 'type'", true));
                    } else {
                        Object type = mechMap.containsKey("type") ? mechMap.get("type") : mechMap.get("mechanic");
                        if (type != null && "skill".equalsIgnoreCase(type.toString())) {
                            Object targetSkill = mechMap.get("skill");
                            if (targetSkill != null && id != null) {
                                skillSubskillRefs.computeIfAbsent(id, k -> new ArrayList<>()).add(targetSkill.toString().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            } else {
                issues.add(new LintIssue(file, "mechanics", "'mechanics' must be a list", true));
            }
        }
    }

    private static void validateSpawnerConfig(String file, Map<String, Object> data, List<LintIssue> issues,
                                              Map<String, String> spawnerMobRefs) {
        validateId(file, data, "id", issues);

        if (!data.containsKey("mob") && !data.containsKey("mobId")) {
            issues.add(new LintIssue(file, "mob", "Missing required field 'mob'", true));
        } else {
            Object mobRef = data.containsKey("mob") ? data.get("mob") : data.get("mobId");
            if (mobRef != null) {
                spawnerMobRefs.put(file, mobRef.toString());
            }
        }
    }

    private static void validateDropTableConfig(String file, Map<String, Object> data, List<LintIssue> issues,
                                                Set<String> knownDropTableIds) {
        String id = validateId(file, data, "id", issues);
        if (id != null) {
            knownDropTableIds.add(id);
        }
    }

    private static String validateId(String file, Map<String, Object> data, String field, List<LintIssue> issues) {
        Object val = data.get(field);
        if (val == null || val.toString().isBlank()) {
            issues.add(new LintIssue(file, field, "Missing required identifier '" + field + "'", true));
            return null;
        }
        String id = val.toString().trim().toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(id).matches()) {
            issues.add(new LintIssue(file, field, "Invalid identifier format '" + id + "'. Must match ^[a-z0-9_.-]+$", true));
            return null;
        }
        return id;
    }

    public static void main(String[] args) {
        Path targetPath = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        System.out.println("=== HaoHanLunar Content Lint & Schema Validator ===");
        System.out.println("Scanning directory: " + targetPath.toAbsolutePath());

        LintReport report = lintDirectory(targetPath);

        System.out.println("\n--- Linting Results ---");
        for (LintIssue issue : report.issues()) {
            System.out.println(issue);
        }

        System.out.println("\nSummary: " + report.errorCount() + " errors, " + report.warningCount() + " warnings.");

        if (report.hasErrors()) {
            System.err.println("FAILED: Configuration contains validation errors!");
            System.exit(1);
        } else {
            System.out.println("PASSED: All content definitions validated successfully!");
        }
    }
}
