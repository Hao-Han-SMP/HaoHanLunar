package vn.haohan.lunar.core.skill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSchedulerTest {

    @Test
    void delayAndFiniteRepeatRunOnTheCentralDispatcher() {
        List<Long> ticks = new ArrayList<>();
        SkillScheduler scheduler = new SkillScheduler(null, ignored -> { }, 0);
        scheduler.schedule(UUID.randomUUID(), 2, 3, 3, () -> ticks.add(scheduler.currentTick()));

        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        assertEquals(List.of(2L, 5L, 8L), ticks);
        assertEquals(0, scheduler.scheduledCount());
    }

    @Test
    void cancellationByEntityStopsAllItsTasks() {
        UUID entity = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        SkillScheduler scheduler = new SkillScheduler(null, ignored -> { }, 0);
        scheduler.schedule(entity, 0, 1, 10, calls::incrementAndGet);
        scheduler.schedule(entity, 0, 1, 10, calls::incrementAndGet);
        scheduler.schedule(UUID.randomUUID(), 0, 1, 10, calls::incrementAndGet);

        assertEquals(2, scheduler.cancelByEntity(entity));
        scheduler.tick();

        assertEquals(1, calls.get());
        assertEquals(1, scheduler.scheduledCount());
    }

    @Test
    void callbackFailureDoesNotStopOtherTasksAndLogsBudgetWarning() {
        List<String> warnings = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        SkillScheduler scheduler = new SkillScheduler(null, warnings::add, 1);
        scheduler.schedule(UUID.randomUUID(), 0, 1, 1, () -> { throw new IllegalStateException("boom"); });
        scheduler.schedule(UUID.randomUUID(), 0, 1, 1, calls::incrementAndGet);

        scheduler.tick();

        assertEquals(1, calls.get());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("failed")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("budget")));
    }
}
