package vn.haohan.lunar.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

/**
 * Bootstrap entry point for HaoHanLunar plugin.
 * Handles early lifecycle initialization.
 */
public final class HaoHanLunarBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        context.getLogger().info("HaoHanLunar bootstrap initialized successfully.");
    }
}
