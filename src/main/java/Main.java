import configuration.Config;
import configuration.TopicsRoutes;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import kafka.Consumer;
import kafka.KafkaClientFactory;
import kafka.Producer;
import monitoring.Monitor;
import monitoring.MonitoringServer;
import reactor.core.Disposable;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.sender.KafkaSender;
import target.HttpTarget;
import target.TargetHealthcheck;

public class Main {

    static Disposable consumerSubscription;
    static Consumer consumer;
    static MonitoringServer monitoringServer;
    static CountDownLatch latch = new CountDownLatch(1);
    static TopicsRoutes topicsRoutes;

    public static void main(String[] args) throws Exception {
        try {
            Config.init();
            Monitor.init();

            topicsRoutes = new TopicsRoutes(Config.TOPICS_ROUTES);
            waitForTargetHealthCheck();
            monitoringServer = new MonitoringServer().start();
            var consumerAndSubscription = createConsumer(monitoringServer);
            consumer = consumerAndSubscription.consumer();
            consumerSubscription = consumerAndSubscription.subscription();
            onShutdownHook(monitoringServer);
            Monitor.started();
            latch.await();
        } catch (Exception e) {
            Monitor.initializationError(e);
            shutdown();
        }
        Monitor.serviceTerminated();
    }

    private static void waitForTargetHealthCheck() throws InterruptedException {
        do {
            Monitor.waitingForTargetHealthcheck();
            Thread.sleep(1000);
        } while (!TargetHealthcheck.check());
        Monitor.targetHealthcheckPassedSuccessfully();
    }

    private static ConsumerAndSubscription createConsumer(MonitoringServer monitoringServer) {
        var receiverOptions = KafkaClientFactory
            .createReceiverOptions()
            .commitInterval(Duration.ofMillis(Config.COMMIT_INTERVAL_MS))
            .subscription(Pattern.compile(topicsRoutes.getTopicsPattern()))
            .addAssignListener(partitions -> {
                if (partitions.isEmpty()) {
                    return;
                }
                Monitor.assignedToPartition(partitions);
                monitoringServer.consumerAssigned();
            })
            .addRevokeListener(Monitor::revokedFromPartition);

        var senderOptions = KafkaClientFactory.createSenderOptions();

        var kafkaReceiver = KafkaReceiver.create(receiverOptions);
        var consumerInstance = new Consumer(
            kafkaReceiver,
            KafkaSender.create(senderOptions),
            new HttpTarget(topicsRoutes, new Producer(KafkaClientFactory.createProducer()))
        );

        var subscription = consumerInstance
            .stream()
            .doOnError(Monitor::consumerError)
            .subscribe(
                __ -> {},
                exception -> {
                    monitoringServer.consumerDisposed();
                    Monitor.consumerError(exception);
                },
                () -> {
                    monitoringServer.consumerDisposed();
                    Monitor.consumerCompleted();
                }
            );

        return new ConsumerAndSubscription(consumerInstance, subscription);
    }

    private record ConsumerAndSubscription(Consumer consumer, Disposable subscription) {}

    private static void onShutdownHook(MonitoringServer monitoringServer) {
        Runtime
            .getRuntime()
            .addShutdownHook(
                new Thread(() -> {
                    shutdown();
                    latch.countDown();
                })
            );
    }

    private static void shutdown() {
        Monitor.shuttingDown();
        if (consumerSubscription != null) {
            consumerSubscription.dispose();
        }
        if (monitoringServer != null) {
            monitoringServer.close();
        }
    }
}
