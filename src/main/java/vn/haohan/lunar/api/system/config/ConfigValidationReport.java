package vn.haohan.lunar.api.system.config;

import java.util.List;

/** Immutable validation result with enough location information for operators to fix a file. */
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
}
