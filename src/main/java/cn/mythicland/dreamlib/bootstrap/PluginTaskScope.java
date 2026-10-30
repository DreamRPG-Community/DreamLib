package cn.mythicland.dreamlib.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks Bukkit scheduler work owned by one dependent plugin bootstrap.
 *
 * <p>Lib remains the shared scheduler implementation, but each dependent plugin gets an
 * independent scope. Closing a plugin bootstrap therefore cancels its delayed and repeating
 * tasks without waiting for the Lib plugin itself to stop.</p>
 */
public final class PluginTaskScope implements AutoCloseable {

    private final JavaPlugin owner;
    private final Set<BukkitTask> tasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean closed;

    /**
     * Creates a task scope for one plugin.
     *
     * @param owner plugin that owns scheduled tasks
     */
    public PluginTaskScope(JavaPlugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Schedules a one-shot primary-thread action and removes it from the scope after execution.
     *
     * @param delayTicks scheduler delay
     * @param action     action to execute
     * @return scheduled task
     */
    public BukkitTask runLater(long delayTicks, Runnable action) {
        if (delayTicks < 0L) throw new IllegalArgumentException("delayTicks cannot be negative");
        Objects.requireNonNull(action, "action");
        AtomicReference<BukkitTask> taskReference = new AtomicReference<>();
        BukkitTask task;
        synchronized (this) {
            ensureOpen();
            task = owner.getServer().getScheduler().runTaskLater(
                    owner,
                    () -> {
                        try {
                            action.run();
                        } finally {
                            BukkitTask completedTask = taskReference.get();
                            if (completedTask != null) tasks.remove(completedTask);
                        }
                    },
                    delayTicks
            );
            taskReference.set(task);
            tasks.add(task);
        }
        return task;
    }

    /**
     * Schedules a repeating primary-thread action.
     *
     * @param delayTicks  initial delay
     * @param periodTicks repeat period
     * @param action      action to execute
     * @return scheduled task
     */
    public BukkitTask runTimer(long delayTicks, long periodTicks, Runnable action) {
        if (delayTicks < 0L) throw new IllegalArgumentException("delayTicks cannot be negative");
        if (periodTicks < 1L) throw new IllegalArgumentException("periodTicks must be positive");
        Objects.requireNonNull(action, "action");
        synchronized (this) {
            ensureOpen();
            BukkitTask task = owner.getServer().getScheduler().runTaskTimer(
                    owner,
                    action,
                    delayTicks,
                    periodTicks
            );
            tasks.add(task);
            return task;
        }
    }

    /**
     * Cancels one task and removes it from this scope.
     *
     * @param task task to cancel; null is ignored
     */
    public void cancel(BukkitTask task) {
        if (task == null) return;
        task.cancel();
        tasks.remove(task);
    }

    /**
     * Indicates whether this scope has been closed.
     *
     * @return true after close
     */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Cancels all tasks in this scope. Closing is idempotent.
     */
    @Override
    public void close() {
        Set<BukkitTask> pending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            pending = Set.copyOf(tasks);
            tasks.clear();
        }
        for (BukkitTask task : pending) task.cancel();
    }

    private synchronized void ensureOpen() {
        if (closed) throw new IllegalStateException("Plugin task scope is closed");
    }
}
