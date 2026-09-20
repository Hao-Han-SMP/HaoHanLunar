package vn.haohan.lunar.api.system.combat.skill.condition;

/** Result that distinguishes a false condition from invalid configuration. */
public record ConditionResult(boolean valid, boolean matched, String error) {

    public ConditionResult {
        if (valid && error != null) {
            throw new IllegalArgumentException("A valid condition cannot have an error");
        }
        if (!valid && (error == null || error.isBlank())) {
            throw new IllegalArgumentException("An invalid condition must have an error");
        }
    }

    public static ConditionResult matched(boolean matched) {
        return new ConditionResult(true, matched, null);
    }

    public static ConditionResult invalid(String error) {
        return new ConditionResult(false, false, error);
    }
}
