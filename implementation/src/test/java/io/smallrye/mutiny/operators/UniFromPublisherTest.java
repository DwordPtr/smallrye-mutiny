package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.Test;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.smallrye.mutiny.Uni;

public class UniFromPublisherTest {

    @Test
    public void testWithPublisher() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().publisher(Flowable.just(1)).subscribe().withSubscriber(subscriber);
        subscriber.assertCompletedSuccessfully().assertItem(1);
    }

    @Test
    public void testWithPublisherBuilder() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().publisher(Flowable.just(1)).subscribe().withSubscriber(subscriber);
        subscriber.assertCompletedSuccessfully().assertItem(1);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testWithMultiValuedPublisher() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        AtomicBoolean cancelled = new AtomicBoolean();
        Uni.createFrom().publisher(Flowable.just(1, 2, 3).doOnCancel(() -> cancelled.set(true))).subscribe()
                .withSubscriber(subscriber);
        subscriber.assertCompletedSuccessfully().assertItem(1);
        assertThat(cancelled).isTrue();
    }

    @Test
    public void testWithException() {
        UniAssertSubscriber<Object> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().publisher(Flowable.error(new IOException("boom"))).subscribe().withSubscriber(subscriber);
        subscriber.assertFailure(IOException.class, "boom");
    }

    @Test
    public void testWithEmptyStream() {
        UniAssertSubscriber<Object> subscriber = UniAssertSubscriber.create();
        Uni.createFrom().publisher(Flowable.empty()).subscribe().withSubscriber(subscriber);
        subscriber.assertCompletedSuccessfully().assertItem(null);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testThatValueIsNotEmittedBeforeSubscription() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        Uni<Integer> uni = Uni.createFrom().publisher(Flowable.generate(emitter -> {
            called.set(true);
            emitter.onNext(1);
            emitter.onComplete();
        }));

        assertThat(called).isFalse();
        uni.subscribe().withSubscriber(subscriber);
        subscriber.assertCompletedSuccessfully().assertItem(1);
        assertThat(called).isTrue();
    }

    @Test
    public void testThatSubscriberIsIncompleteIfThePublisherDoesNotEmit() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        AtomicBoolean called = new AtomicBoolean();
        Uni<Integer> uni = Uni.createFrom().<Integer> publisher(Flowable.never()).map(i -> {
            called.set(true);
            return i + 1;
        });

        assertThat(called).isFalse();

        uni.subscribe().withSubscriber(subscriber);
        assertThat(called).isFalse();
        subscriber.assertNotCompleted();
    }

    @Test
    public void testThatSubscriberCanCancelBeforeEmission() {
        UniAssertSubscriber<Integer> subscriber = UniAssertSubscriber.create();
        Uni<Integer> uni = Uni.createFrom().publisher(Flowable.<Integer> create(emitter -> new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            emitter.onNext(1);
        }).start(), BackpressureStrategy.DROP)).map(i -> i + 1);

        uni.subscribe().withSubscriber(subscriber);
        subscriber.cancel();

        subscriber.assertNotCompleted();
    }

}