package vn.haohan.lunar.api.system.combat.skill.mechanic;

public record MechanicResult(boolean valid, String error) {
    public static MechanicResult success() { return new MechanicResult(true, null); }
    public static MechanicResult invalid(String error) { return new MechanicResult(false, error); }

    public boolean isSuccess() {
        return valid;
    }
}
