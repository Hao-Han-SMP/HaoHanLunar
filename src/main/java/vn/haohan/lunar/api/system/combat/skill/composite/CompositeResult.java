package vn.haohan.lunar.api.system.combat.skill.composite;

/** Result of composite skill node execution. */
public record CompositeResult(boolean successful, String message) {

    public boolean isSuccess() {
        return successful;
    }

    public static CompositeResult success() {
        return new CompositeResult(true, null);
    }

    public static CompositeResult failure(String message) {
        return new CompositeResult(false, message != null ? message : "Execution failed");
    }
}
