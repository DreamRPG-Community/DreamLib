package cn.mythicland.dreamlib.bootstrap;

import cn.mythicland.dreamlib.bootstrap.annotation.LifecycleComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleSorterTest {

    @Test
    void dependenciesAreEnabledBeforeDependents() {
        List<Class<?>> result = LifecycleSorter.sort(List.of(ThirdStage.class, FirstStage.class, SecondStage.class));

        assertEquals(List.of(FirstStage.class, SecondStage.class, ThirdStage.class), result);
    }

    @Test
    void missingDependencyFailsExplicitly() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> LifecycleSorter.sort(List.of(MissingDependencyStage.class))
        );

        assertEquals(
                "Bootstrap dependency is not part of the resolved phase: "
                        + MissingDependencyStage.class.getName() + " -> " + FirstStage.class.getName(),
                failure.getMessage()
        );
    }

    @Test
    void dependencyCycleFailsExplicitly() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> LifecycleSorter.sort(List.of(CycleA.class, CycleB.class))
        );

        assertEquals(
                "Circular bootstrap dependency detected: "
                        + CycleA.class.getName() + " -> " + CycleB.class.getName() + " -> " + CycleA.class.getName(),
                failure.getMessage()
        );
    }

    private static final class FirstStage {
    }

    @LifecycleComponent(dependsOn = FirstStage.class)
    private static final class SecondStage {
    }

    @LifecycleComponent(dependsOn = SecondStage.class)
    private static final class ThirdStage {
    }

    @LifecycleComponent(dependsOn = FirstStage.class)
    private static final class MissingDependencyStage {
    }

    @LifecycleComponent(dependsOn = CycleB.class)
    private static final class CycleA {
    }

    @LifecycleComponent(dependsOn = CycleA.class)
    private static final class CycleB {
    }
}
