package vn.haohan.lunar.api.system.config;

/** Checked failure for a configuration batch that must not replace the active snapshot. */
public class ConfigLoadException extends Exception {

    private final ConfigValidationReport report;

    public ConfigLoadException(ConfigValidationReport report) {
        super(format(report));
        this.report = report;
    }

    public ConfigValidationReport report() {
        return report;
    }

    private static String format(ConfigValidationReport report) {
        return "Configuration load failed: " + report.issues().stream()
                .map(ConfigValidationReport.Issue::toString)
                .reduce((left, right) -> left + "; " + right)
                .orElse("unknown validation error");
    }
}
