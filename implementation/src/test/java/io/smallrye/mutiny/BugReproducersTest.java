package io.smallrye.mutiny;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.mutiny.infrastructure.Infrastructure;

class BugReproducersTest {

    @RepeatedTest(100)
    void reproducer_689() {
        // Adapted from https://github.com/smallrye/smallrye-mutiny/issues/689
        AtomicLong src = new AtomicLong();

        AssertSubscriber<Long> sub = Multi.createBy().repeating()
                .supplier(src::incrementAndGet)
                .until(l -> l.equals(10_000L))
                .flatMap(l -> Multi.createFrom().item(l * 2))
                .emitOn(Infrastructure.getDefaultExecutor())
                .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        sub.awaitCompletion();
        assertThat(sub.getItems()).hasSize(9_999);
    }

    @Test
    void reproducer_705() {
        // Adapted from https://github.com/smallrye/smallrye-mutiny/issues/705
        // The issue was an over-interpretation of one of the RS TCK rule regarding releasing subscriber references.
        AssertSubscriber<List<Integer>> sub = AssertSubscriber.create();
        AtomicInteger counter = new AtomicInteger();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();

        ExecutorService threadPool = Executors.newFixedThreadPool(4, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = Executors.defaultThreadFactory().newThread(task);
                thread.setUncaughtExceptionHandler((t, e) -> {
                    e.printStackTrace();
                    threadFailure.set(e);
                });
                return thread;
            }
        });

        Multi.createFrom().range(0, 1000)
                .emitOn(threadPool)
                .group().intoLists().of(100)
                .onItem().invoke(() -> {
                    if (counter.incrementAndGet() == 3) {
                        sub.cancel();
                    }
                })
                .runSubscriptionOn(threadPool)
                .subscribe().withSubscriber(sub);

        sub.request(Long.MAX_VALUE);
        await().atMost(5, TimeUnit.SECONDS).untilAtomic(counter, greaterThanOrEqualTo(3));

        assertThat(threadFailure.get()).isNull();
        sub.assertNotTerminated();
        threadPool.shutdownNow();
    }
}
