package vn.haohan.lunar.api.system.combat.skill.mechanic;

import java.util.Map;

@FunctionalInterface
public interface Mechanic {
    void execute(MechanicContext context, Map<String, Object> parameters);
}
