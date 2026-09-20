package vn.haohan.lunar.api.mob;

import org.bukkit.entity.EntityType;
import vn.haohan.lunar.api.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.mob.scaling.DynamicScalingDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, runtime-independent data describing a configured custom mob.
 * Entity creation and configuration parsing deliberately live outside this type.
 */
public final class MobDefinition {

    private final MobDefinitionId id;
    private final EntityType entityType;
    private final String displayName;
    private final Optional<String> modelId;
    private final Map<String, MobAttributeDefinition> attributes;
    private final Map<String, MobOptionDefinition> options;
    private final List<String> skillReferences;
    private final Optional<String> dropTableReference;
    private final Set<String> tags;
    private final MobEquipmentDefinition equipment;
    private final Optional<String> mountId;
    private final List<String> riderIds;
    private final Optional<DynamicScalingDefinition> dynamicScaling;
    private final List<String> aiGoalSelectors;
    private final List<String> aiTargetSelectors;

    public MobDefinition(
            MobDefinitionId id,
            EntityType entityType,
            String displayName,
            String modelId,
            Map<String, MobAttributeDefinition> attributes,
            Map<String, MobOptionDefinition> options,
            List<String> skillReferences,
            String dropTableReference,
            Set<String> tags,
            MobEquipmentDefinition equipment,
            String mountId,
            List<String> riderIds,
            DynamicScalingDefinition dynamicScaling,
            List<String> aiGoalSelectors,
            List<String> aiTargetSelectors) {
        this.id = Objects.requireNonNull(id, "Mob definition ID must not be null");
        this.entityType = Objects.requireNonNull(entityType, "Entity type must not be null");
        this.displayName = requireText(displayName, "Display name");
        this.modelId = optionalText(modelId, "Model ID");
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "Attributes must not be null"));
        this.options = Map.copyOf(Objects.requireNonNull(options, "Options must not be null"));
        this.skillReferences = immutableTextList(skillReferences, "Skill references");
        this.dropTableReference = optionalText(dropTableReference, "Drop table reference");
        this.tags = immutableTextSet(tags, "Tags");
        this.equipment = equipment != null ? equipment : MobEquipmentDefinition.empty();
        this.mountId = optionalText(mountId, "Mount ID");
        this.riderIds = riderIds != null ? immutableTextList(riderIds, "Rider IDs") : List.of();
        this.dynamicScaling = Optional.ofNullable(dynamicScaling);
        this.aiGoalSelectors = aiGoalSelectors != null ? List.copyOf(aiGoalSelectors) : List.of();
        this.aiTargetSelectors = aiTargetSelectors != null ? List.copyOf(aiTargetSelectors) : List.of();
    }

    public MobDefinition(
            MobDefinitionId id,
            EntityType entityType,
            String displayName,
            String modelId,
            Map<String, MobAttributeDefinition> attributes,
            Map<String, MobOptionDefinition> options,
            List<String> skillReferences,
            String dropTableReference,
            Set<String> tags,
            MobEquipmentDefinition equipment,
            String mountId,
            List<String> riderIds,
            DynamicScalingDefinition dynamicScaling) {
        this(id, entityType, displayName, modelId, attributes, options, skillReferences, dropTableReference, tags, equipment, mountId, riderIds, dynamicScaling, List.of(), List.of());
    }

    public MobDefinition(
            MobDefinitionId id,
            EntityType entityType,
            String displayName,
            String modelId,
            Map<String, MobAttributeDefinition> attributes,
            Map<String, MobOptionDefinition> options,
            List<String> skillReferences,
            String dropTableReference,
            Set<String> tags,
            MobEquipmentDefinition equipment,
            String mountId,
            List<String> riderIds) {
        this(id, entityType, displayName, modelId, attributes, options, skillReferences, dropTableReference, tags, equipment, mountId, riderIds, null, List.of(), List.of());
    }

    public MobDefinition(
            MobDefinitionId id,
            EntityType entityType,
            String displayName,
            String modelId,
            Map<String, MobAttributeDefinition> attributes,
            Map<String, MobOptionDefinition> options,
            List<String> skillReferences,
            String dropTableReference,
            Set<String> tags,
            MobEquipmentDefinition equipment) {
        this(id, entityType, displayName, modelId, attributes, options, skillReferences, dropTableReference, tags, equipment, null, List.of(), null, List.of(), List.of());
    }

    public MobDefinition(
            MobDefinitionId id,
            EntityType entityType,
            String displayName,
            String modelId,
            Map<String, MobAttributeDefinition> attributes,
            Map<String, MobOptionDefinition> options,
            List<String> skillReferences,
            String dropTableReference,
            Set<String> tags) {
        this(id, entityType, displayName, modelId, attributes, options, skillReferences, dropTableReference, tags, MobEquipmentDefinition.empty(), null, List.of(), null, List.of(), List.of());
    }

    public MobDefinitionId id() { return id; }
    public EntityType entityType() { return entityType; }
    public String displayName() { return displayName; }
    public Optional<String> modelId() { return modelId; }
    public Map<String, MobAttributeDefinition> attributes() { return attributes; }
    public Map<String, MobOptionDefinition> options() { return options; }
    public List<String> skillReferences() { return skillReferences; }
    public Optional<String> dropTableReference() { return dropTableReference; }
    public Set<String> tags() { return tags; }
    public MobEquipmentDefinition equipment() { return equipment; }
    public Optional<String> mountId() { return mountId; }
    public List<String> riderIds() { return riderIds; }
    public Optional<DynamicScalingDefinition> dynamicScaling() { return dynamicScaling; }
    public List<String> aiGoalSelectors() { return aiGoalSelectors; }
    public List<String> aiTargetSelectors() { return aiTargetSelectors; }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static Optional<String> optionalText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(requireText(value, field));
    }

    private static List<String> immutableTextList(List<String> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        return List.copyOf(values.stream().map(value -> requireText(value, field + " entry")).toList());
    }

    private static Set<String> immutableTextSet(Set<String> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        return Set.copyOf(values.stream().map(value -> requireText(value, field + " entry")).toList());
    }
}
