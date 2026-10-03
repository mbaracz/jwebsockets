package pl.mbaracz.jwebsockets;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Runs tasks on another executor one at a time, in the order they were submitted.
 */
final class SerialExecutor implements Executor {

    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final Executor executor;
    private Runnable active;

    SerialExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public synchronized void execute(Runnable task) {
        tasks.add(() -> {
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        });

        if (active == null) {
            scheduleNext();
        }
    }

    private synchronized void scheduleNext() {
        active = tasks.poll();

        if (active != null) {
            try {
                executor.execute(active);
            } catch (RuntimeException exception) {
                // A rejected task never runs, so the next submitted task must not wait for it
                active = null;
                throw exception;
            }
        }
    }
}
