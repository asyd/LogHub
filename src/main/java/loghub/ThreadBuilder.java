package loghub;

import java.util.concurrent.FutureTask;

public class ThreadBuilder {

    public static ThreadBuilder get() {
        return new ThreadBuilder();
    }

    private Runnable task;
    private String name = null;
    private Boolean daemon = null;
    private boolean shutdownHook = false;

    private ThreadBuilder() {
    }

    public ThreadBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public ThreadBuilder setDaemon(boolean on) {
        return this;
    }

    public ThreadBuilder setCallable(FutureTask<?> task) {
        this.task = task;
        return this;
    }

    public ThreadBuilder setRunnable(Runnable r) {
        task = r;
        return this;
    }

    public ThreadBuilder setShutdownHook(boolean shutdownHook) {
        this.shutdownHook = shutdownHook;
        return this;
    }

    public Thread build() {
        return build(false);
    }

    public Thread build(boolean start) {
        if (shutdownHook && start) {
            throw new IllegalArgumentException("A thread can't be both to be started and shutdown hook");
        }
        Thread t = new Thread(task);
        if (daemon != null) t.setDaemon(daemon);
        if (name != null) t.setName(name);
        if (shutdownHook) Runtime.getRuntime().addShutdownHook(t);
        if (start) t.start();
        return t;
    }
}
