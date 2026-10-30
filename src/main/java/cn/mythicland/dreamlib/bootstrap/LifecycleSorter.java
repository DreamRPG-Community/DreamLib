package cn.mythicland.dreamlib.bootstrap;

import cn.mythicland.dreamlib.bootstrap.annotation.LifecycleComponent;

import java.util.*;

/**
 * Performs deterministic topological ordering for one bootstrap phase.
 */
final class LifecycleSorter {

    private LifecycleSorter() {
    }

    static List<Class<?>> sort(Collection<Class<?>> candidates) {
        List<Class<?>> orderedCandidates = new ArrayList<>(candidates);
        orderedCandidates.sort(
                Comparator.comparingInt(LifecycleSorter::order)
                        .thenComparing(Class::getName)
        );
        Set<Class<?>> candidateSet = Set.copyOf(orderedCandidates);
        List<Class<?>> result = new ArrayList<>(orderedCandidates.size());
        Set<Class<?>> visiting = new HashSet<>();
        Set<Class<?>> visited = new HashSet<>();
        List<Class<?>> path = new ArrayList<>();
        for (Class<?> candidate : orderedCandidates) {
            visit(candidate, candidateSet, visiting, visited, path, result);
        }
        return List.copyOf(result);
    }

    private static void visit(
            Class<?> candidate,
            Set<Class<?>> candidates,
            Set<Class<?>> visiting,
            Set<Class<?>> visited,
            List<Class<?>> path,
            List<Class<?>> result
    ) {
        if (visited.contains(candidate)) return;
        if (!visiting.add(candidate)) {
            throw new IllegalStateException("Circular bootstrap dependency detected: " + formatPath(path, candidate));
        }

        path.add(candidate);
        List<Class<?>> dependencies = dependenciesOf(candidate);
        for (Class<?> dependency : dependencies) {
            if (!candidates.contains(dependency)) {
                throw new IllegalStateException(
                        "Bootstrap dependency is not part of the resolved phase: "
                                + candidate.getName() + " -> " + dependency.getName()
                );
            }
            visit(dependency, candidates, visiting, visited, path, result);
        }
        path.removeLast();
        visiting.remove(candidate);
        visited.add(candidate);
        result.add(candidate);
    }

    private static List<Class<?>> dependenciesOf(Class<?> candidate) {
        List<Class<?>> dependencies = new ArrayList<>();
        LifecycleComponent lifecycle = candidate.getAnnotation(LifecycleComponent.class);
        if (lifecycle != null) dependencies.addAll(List.of(lifecycle.dependsOn()));
        return dependencies.stream()
                .distinct()
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    private static int order(Class<?> candidate) {
        LifecycleComponent annotation = candidate.getAnnotation(LifecycleComponent.class);
        return annotation == null ? 0 : annotation.order();
    }

    private static String formatPath(List<Class<?>> path, Class<?> repeated) {
        List<String> names = new ArrayList<>(path.size() + 1);
        for (Class<?> type : path) names.add(type.getName());
        names.add(repeated.getName());
        return String.join(" -> ", names);
    }
}
