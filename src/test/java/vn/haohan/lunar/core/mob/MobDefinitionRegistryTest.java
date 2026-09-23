package vn.haohan.lunar.core.mob;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.config.LunarYamlLoader;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobDefinitionRegistryTest {

    @Test
    void normalizesIdsAndRejectsDuplicates() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        registry.register(definition("Lunar_Warden"));

        assertTrue(registry.contains("lunar_warden"));
        assertEquals("lunar_warden", registry.get("LUNAR_WARDEN").orElseThrow().id().value());
        assertThrows(IllegalArgumentException.class, () -> registry.register(definition("lunar_warden")));
        assertThrows(IllegalArgumentException.class, () -> registry.contains("not valid"));
    }

    @Test
    void replaceAllIsAtomicWhenInputIsInvalid() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        registry.register(definition("old"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.replaceAll(List.of(definition("new"), definition("new"))));

        assertTrue(registry.contains("old"));
        assertFalse(registry.contains("new"));
    }

    @Test
    void readersOnlyObserveCompleteSnapshotsDuringReplacement() throws Exception {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        registry.replaceAll(List.of(definition("a"), definition("b")));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            Future<?> readers = executor.submit(() -> {
                await(start);
                for (int i = 0; i < 10_000; i++) {
                    Map<String, MobDefinition> snapshot = registry.snapshot();
                    assertTrue(snapshot.size() == 2 || snapshot.size() == 3);
                    assertTrue(snapshot.containsKey("a") || snapshot.containsKey("c"));
                }
            });
            start.countDown();
            for (int i = 0; i < 100; i++) {
                registry.replaceAll(List.of(definition("a"), definition("b"), definition("c")));
                registry.replaceAll(List.of(definition("a"), definition("b")));
            }
            readers.get();
        }
    }

    @Test
    void yamlReloadAppliesDecodedBatch() throws Exception {
        Path root = Files.createDirectories(Files.createTempDirectory("lunar-registry-").resolve("mobs"));
        Files.writeString(root.resolve("warden.yml"), "id: warden\nentity-type: IRON_GOLEM\n");
        MobDefinitionRegistry registry = new MobDefinitionRegistry();

        var report = registry.reload(root.getParent(), new LunarYamlLoader(), batch -> List.of(definition(
                batch.directories().get("mobs").keySet().iterator().next())));

        assertTrue(report.isValid(), report::toString);
        assertTrue(registry.contains("warden"));
    }

    private static MobDefinition definition(String id) {
        return new MobDefinition(new MobDefinitionId(id), EntityType.IRON_GOLEM, id,
                                 null, Map.of(), Map.of(), List.of(), null, Set.of());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
