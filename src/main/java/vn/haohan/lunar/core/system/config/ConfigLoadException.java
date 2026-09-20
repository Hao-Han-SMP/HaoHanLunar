package vn.haohan.lunar.core.system.config;

import vn.haohan.lunar.api.system.config.ConfigValidationReport;

/**
 * Compatibility alias for ConfigLoadException.
 */
public class ConfigLoadException extends vn.haohan.lunar.api.system.config.ConfigLoadException {

    public ConfigLoadException(ConfigValidationReport report) {
        super(report);
    }
}
