package vn.haohan.lunar.core.system.config;

import java.util.List;

/**
 * Compatibility alias for ConfigValidationReport.
 */
public record ConfigValidationReport(List<Issue> issues) {

    public ConfigValidationReport {
        issues = List.copyOf(issues);
    }

    public boolean isValid() {
        return issues.isEmpty();
    }

    public record Issue(String file, String path, String message, int line, int column) {
        public Issue {
            file = file == null ? "<unknown>" : file;
            path = path == null || path.isBlank() ? "$" : path;
            message = message == null || message.isBlank() ? "Invalid configuration" : message;
            line = Math.max(0, line);
            column = Math.max(0, column);
        }

        @Override
        public String toString() {
            String location = line > 0 ? file + ":" + line + ":" + Math.max(1, column) : file;
            return location + " at " + path + ": " + message;
        }
    }

    public vn.haohan.lunar.api.system.config.ConfigValidationReport toApi() {
        return new vn.haohan.lunar.api.system.config.ConfigValidationReport(
                issues.stream().map(i -> new vn.haohan.lunar.api.system.config.ConfigValidationReport.Issue(
                        i.file(), i.path(), i.message(), i.line(), i.column()
                )).toList()
        );
    }
}
