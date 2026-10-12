package vn.haohan.lunar.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HaoHanLunarBootstrapTest {

    @Test
    @DisplayName("HaoHanLunarBootstrap implements io.papermc.paper.plugin.bootstrap.PluginBootstrap")
    void testBootstrapClassImplementsInterface() throws ClassNotFoundException {
        Class<?> clazz = Class.forName("vn.haohan.lunar.bootstrap.HaoHanLunarBootstrap");
        assertTrue(io.papermc.paper.plugin.bootstrap.PluginBootstrap.class.isAssignableFrom(clazz),
                "HaoHanLunarBootstrap must implement PluginBootstrap");
    }

    @Test
    @DisplayName("paper-plugin.yml configures bootstrapper entry")
    void testPaperPluginYmlBootstrapperEntry() {
        InputStream is = getClass().getResourceAsStream("/paper-plugin.yml");
        assertNotNull(is, "paper-plugin.yml should exist in classpath");

        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(is);
        assertNotNull(data, "paper-plugin.yml content must parse");
        assertEquals("vn.haohan.lunar.bootstrap.HaoHanLunarBootstrap", data.get("bootstrapper"),
                "bootstrapper field in paper-plugin.yml must match HaoHanLunarBootstrap class name");
    }
}
