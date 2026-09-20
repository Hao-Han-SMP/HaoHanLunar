package vn.haohan.lunar.api.mob.pack;

import vn.haohan.lunar.api.loot.DropTableDefinition;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.core.skill.SkillChainDefinition;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates all content registered by a specific content pack.
 */
public record PackDefinition(
        PackManifest manifest,
        Path packDirectory,
        Map<String, MobDefinition> mobs,
        Map<String, SkillChainDefinition> skills,
        Map<String, DropTableDefinition> drops,
        List<String> modelAssets
) {
    public PackDefinition {
        Objects.requireNonNull(manifest, "Manifest must not be null");
        mobs = mobs != null ? Collections.unmodifiableMap(mobs) : Map.of();
        skills = skills != null ? Collections.unmodifiableMap(skills) : Map.of();
        drops = drops != null ? Collections.unmodifiableMap(drops) : Map.of();
        modelAssets = modelAssets != null ? Collections.unmodifiableList(modelAssets) : List.of();
    }

    public String name() {
        return manifest.name();
    }
}
