package vn.haohan.lunar.api.mob.pack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata descriptor parsed from pack.yml in a content pack.
 */
public record PackManifest(
        String name,
        String version,
        String author,
        String description,
        List<String> dependencies
) {
    public PackManifest {
        Objects.requireNonNull(name, "Pack name must not be null");
        name = name.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Pack name must not be empty");
        }
        version = version != null ? version.trim() : "1.0.0";
        author = author != null ? author.trim() : "Unknown";
        description = description != null ? description.trim() : "";
        dependencies = dependencies != null ? dependencies.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList() : List.of();
    }

    public static PackManifest fromMap(String fallbackName, Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new PackManifest(fallbackName, "1.0.0", "Unknown", "", List.of());
        }
        String name = map.get("name") instanceof String s ? s : fallbackName;
        String version = map.get("version") instanceof String v ? v : (map.get("version") != null ? String.valueOf(map.get("version")) : "1.0.0");
        String author = map.get("author") instanceof String a ? a : "Unknown";
        String description = map.get("description") instanceof String d ? d : "";

        List<String> deps = List.of();
        Object depObj = map.get("dependencies");
        if (depObj instanceof List<?> list) {
            deps = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return new PackManifest(name, version, author, description, deps);
    }
}
