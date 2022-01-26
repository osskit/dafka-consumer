package kafka;

import configuration.Config;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.RetriableCommitFailedException;
import org.apache.kafka.common.errors.WakeupException;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class ReactiveKafkaClient<K, V> extends Flux<ConsumerRecords<K, V>> implements Disposable {

    final Collection<String> topics;
    final ConsumerRebalanceListener consumerRebalanceListener;

    final AtomicBoolean isActive = new AtomicBoolean();
    final AtomicBoolean isClosed = new AtomicBoolean();

    final Scheduler scheduler;
    final PollEvent pollEvent;
    final CommitEvent commitEvent;

    Consumer<K, V> consumer;
    CoreSubscriber<? super ConsumerRecords<K, V>> actual;

    public ReactiveKafkaClient(
        Consumer<K, V> consumer,
        Collection<String> topics,
        ConsumerRebalanceListener consumerRebalanceListener
    ) {
        this.topics = topics;
        this.consumer = consumer;
        this.consumerRebalanceListener = consumerRebalanceListener;

        pollEvent = new PollEvent();
        commitEvent = new CommitEvent();
        scheduler = KafkaSchedulers.newEvent(Config.GROUP_ID);
    }

    @Override
    public void subscribe(CoreSubscriber<? super ConsumerRecords<K, V>> actual) {
        if (!isActive.compareAndSet(false, true)) {
            Operators.error(
                actual,
                new IllegalStateException("Multiple subscribers are not supported for KafkaReceiver flux")
            );
            return;
        }

        this.actual = actual;

        isClosed.set(false);

        try {
            scheduler.schedule(new SubscribeEvent());

            actual.onSubscribe(
                new Subscription() {
                    @Override
                    public void request(long n) {
                        pollEvent.scheduleIfRequired();
                    }

                    @Override
                    public void cancel() {}
                }
            );

            scheduler.start();
        } catch (Exception e) {
            Operators.error(actual, e);
            return;
        }
    }

    void poll() {
        pollEvent.scheduleIfRequired();
    }

    void commit() {
        commitEvent.scheduleIfRequired();
    }

    @Override
    public void dispose() {
        if (!isActive.compareAndSet(true, false)) {
            return;
        }

        boolean isConsumerClosed = consumer == null;
        if (isConsumerClosed) {
            return;
        }

        try {
            consumer.wakeup();
            CloseEvent closeEvent = new CloseEvent(Duration.ofNanos(Long.MAX_VALUE));

            boolean isEventsThread = KafkaSchedulers.isCurrentThreadFromScheduler();
            if (isEventsThread) {
                closeEvent.run();
                return;
            }

            if (scheduler.isDisposed()) {
                closeEvent.run();
                return;
            }

            scheduler.schedule(closeEvent);
            isConsumerClosed = closeEvent.await();
        } finally {
            scheduler.dispose();

            int maxRetries = 10;
            for (int i = 0; i < maxRetries && !isConsumerClosed; i++) {
                try {
                    if (consumer != null) {
                        consumer.close();
                    }
                    isConsumerClosed = true;
                } catch (Exception e) {}
            }
            isClosed.set(true);
        }
    }

    class SubscribeEvent implements Runnable {

        @Override
        public void run() {
            try {
                consumer.subscribe(Pattern.compile(getTopicsPattern()), consumerRebalanceListener);
            } catch (Exception e) {
                if (isActive.get()) {
                    actual.onError(e);
                }
            }
        }

        private String getTopicsPattern() {
            return topics.stream().map(topic -> String.format("^%s$", topic)).collect(Collectors.joining("|"));
        }
    }

    class PollEvent implements Runnable {

        private final AtomicInteger pendingCount = new AtomicInteger();
        private final Duration pollTimeout = Duration.ofMillis(Config.POLL_TIMEOUT);

        @Override
        public void run() {
            try {
                if (isActive.get()) {
                    pendingCount.decrementAndGet();
                    var records = consumer.poll(pollTimeout);
                    if (records.count() == 0) {
                        scheduleIfRequired();
                        return;
                    }
                    actual.onNext(records);
                }
            } catch (Exception e) {
                if (isActive.get()) {
                    actual.onError(e);
                }
            }
        }

        void scheduleIfRequired() {
            if (pendingCount.get() <= 0) {
                scheduler.schedule(this);
                pendingCount.incrementAndGet();
            }
        }
    }

    class CommitEvent implements Runnable {

        private final AtomicBoolean isPending = new AtomicBoolean();
        private final AtomicInteger inProgress = new AtomicInteger();

        @Override
        public void run() {
            if (!isPending.compareAndSet(true, false)) {
                return;
            }
            try {
                inProgress.incrementAndGet();
                consumer.commitAsync(
                    (__, error) -> {
                        if (
                            error != null &&
                            !(error instanceof RetriableCommitFailedException) &&
                            !(error instanceof CommitFailedException)
                        ) {
                            actual.onError(error);
                            return;
                        }
                    }
                );
                inProgress.decrementAndGet();
            } catch (Exception e) {
                inProgress.decrementAndGet();
                actual.onError(e);
            }
        }

        void runIfRequired(boolean force) {
            if (force) isPending.set(true);
            if (isPending.get()) run();
        }

        void scheduleIfRequired() {
            if (isActive.get() && isPending.compareAndSet(false, true)) {
                scheduler.schedule(this);
            }
        }

        private void waitFor(long endTimeNanos) {
            while (inProgress.get() > 0 && endTimeNanos - System.nanoTime() > 0) {
                consumer.poll(Duration.ofMillis(1));
            }
        }
    }

    class CloseEvent implements Runnable {

        private final long closeEndTimeNanos;
        private final CountDownLatch latch = new CountDownLatch(1);

        CloseEvent(Duration timeout) {
            this.closeEndTimeNanos = System.nanoTime() + timeout.toNanos();
        }

        @Override
        public void run() {
            try {
                if (consumer != null) {
                    int attempts = 3;
                    for (int i = 0; i < attempts; i++) {
                        try {
                            commitEvent.runIfRequired(true);
                            commitEvent.waitFor(closeEndTimeNanos);
                            long timeoutNanos = closeEndTimeNanos - System.nanoTime();
                            if (timeoutNanos < 0) timeoutNanos = 0;
                            consumer.close(Duration.ofNanos(timeoutNanos));
                            break;
                        } catch (WakeupException e) {
                            if (i == attempts - 1) throw e;
                        }
                    }
                }
                latch.countDown();
            } catch (Exception e) {
                actual.onError(e);
            }
        }

        boolean await(long timeoutNanos) throws InterruptedException {
            return latch.await(timeoutNanos, TimeUnit.NANOSECONDS);
        }

        boolean await() {
            boolean closed = false;
            long remainingNanos;
            while (!closed && (remainingNanos = closeEndTimeNanos - System.nanoTime()) > 0) {
                try {
                    closed = await(remainingNanos);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            return closed;
        }
    }
}

class KafkaSchedulers {

    static void defaultUncaughtException(Thread t, Throwable e) {}

    static Scheduler newEvent(String groupId) {
        return Schedulers.newSingle(new EventThreadFactory(groupId));
    }

    static boolean isCurrentThreadFromScheduler() {
        return Thread.currentThread() instanceof EventThreadFactory.EmitterThread;
    }

    static final class EventThreadFactory implements ThreadFactory {

        static final String PREFIX = "reactive-kafka-";
        static final AtomicLong COUNTER_REFERENCE = new AtomicLong();

        private final String groupId;

        EventThreadFactory(String groupId) {
            this.groupId = groupId;
        }

        @Override
        public final Thread newThread(Runnable runnable) {
            String newThreadName = PREFIX + groupId + "-" + COUNTER_REFERENCE.incrementAndGet();
            Thread t = new EmitterThread(runnable, newThreadName);
            t.setUncaughtExceptionHandler(KafkaSchedulers::defaultUncaughtException);
            return t;
        }

        static final class EmitterThread extends Thread {

            EmitterThread(Runnable target, String name) {
                super(target, name);
            }
        }
    }
}
