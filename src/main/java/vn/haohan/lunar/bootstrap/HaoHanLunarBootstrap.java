package vn.haohan.lunar.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Bootstrap entry point for HaoHanLunar plugin.
 * Handles early lifecycle initialization and datapack discovery.
 */
public final class HaoHanLunarBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                URL resource = getClass().getResource("/datapack");
                if (resource == null) {
                    context.getLogger().warn("Could not find bundled datapack resource at /datapack!");
                    return;
                }

                URI uri = resource.toURI();
                event.registrar().discoverPack(uri, "provided", configurer -> configurer.autoEnableOnServerStart(false));
                context.getLogger().info("Successfully discovered bundled datapack: {}/provided", context.getConfiguration().getName());
            } catch (URISyntaxException | IOException e) {
                context.getLogger().error("Failed to register bundled datapack from plugin jar", e);
            }
        });
    }
}
