package sh.harold.blackbox.hytale;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class NamedThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger counter = new AtomicInteger();

    NamedThreadFactory(String namePrefix) {
        this.namePrefix = Objects.requireNonNull(namePrefix, "namePrefix");
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName(namePrefix + "-" + counter.incrementAndGet());
        return thread;
    }
}

