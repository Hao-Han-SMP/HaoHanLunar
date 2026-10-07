package vn.haohan.engine.api.system.combat.skill;

import vn.haohan.engine.api.manager.ISkillManager;
import vn.haohan.engine.api.system.combat.skill.condition.ICondition;
import vn.haohan.engine.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.engine.api.system.combat.skill.mechanic.IMechanic;
import vn.haohan.engine.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.engine.api.system.combat.skill.target.ITargeter;
import vn.haohan.engine.api.system.combat.skill.target.BasicTargeterRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for loaded skill definitions.
 */
public final class SkillRegistry implements ISkillManager {

    private final Map<String, SkillChainDefinition> skills = new ConcurrentHashMap<>();

    public Optional<SkillChainDefinition> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(skills.get(normalize(id)));
    }

    public boolean contains(String id) {
        if (id == null || id.isBlank()) return false;
        return skills.containsKey(normalize(id));
    }

    public void register(SkillChainDefinition skill) {
        Objects.requireNonNull(skill, "Skill definition must not be null");
        String id = normalize(skill.definition().id());
        skills.put(id, skill);
    }

    public void replaceAll(Collection<SkillChainDefinition> replacements) {
        Objects.requireNonNull(replacements, "Skill replacements must not be null");
        Map<String, SkillChainDefinition> newSkills = new LinkedHashMap<>();
        for (SkillChainDefinition skill : replacements) {
            newSkills.put(normalize(skill.definition().id()), skill);
        }
        skills.clear();
        skills.putAll(newSkills);
    }

    public boolean unregister(String id) {
        if (id == null || id.isBlank()) return false;
        return skills.remove(normalize(id)) != null;
    }

    public int size() {
        return skills.size();
    }

    public Map<String, SkillChainDefinition> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(skills));
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    // --- ISkillManager API Implementation ---
    private MechanicRegistry mechanicRegistry;
    private ConditionRegistry conditionRegistry;
    private BasicTargeterRegistry targeterRegistry;

    public void setRegistries(MechanicRegistry mr,
                              ConditionRegistry cr,
                              BasicTargeterRegistry tr) {
        this.mechanicRegistry = mr;
        this.conditionRegistry = cr;
        this.targeterRegistry = tr;
    }

    @Override
    public void registerMechanic(String name, IMechanic mechanic) {
        if (mechanicRegistry != null) mechanicRegistry.register(name, mechanic);
    }

    @Override
    public void registerCondition(String name, ICondition condition) {
        if (conditionRegistry != null) conditionRegistry.register(name, condition);
    }

    @Override
    public void registerTargeter(String name, ITargeter targeter) {
        if (targeterRegistry != null) targeterRegistry.register(name, targeter);
    }

    @Override
    public boolean hasSkill(String skillId) {
        return contains(skillId);
    }
}
