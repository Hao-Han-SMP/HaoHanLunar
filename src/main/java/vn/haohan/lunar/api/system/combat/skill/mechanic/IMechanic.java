package vn.haohan.lunar.api.system.combat.skill.mechanic;

import java.util.Map;

@FunctionalInterface
public interface IMechanic {
    void execute(MechanicContext context, Map<String, Object> parameters);
}
