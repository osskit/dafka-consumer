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

    static Disposable consumer;
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
            consumer = createConsumer(monitoringServer);
            onShutdownHook(consumer, monitoringServer);
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

    private static Disposable createConsumer(MonitoringServer monitoringServer) {
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

        return new Consumer(
            KafkaReceiver.create(receiverOptions),
            KafkaSender.create(senderOptions),
            new HttpTarget(topicsRoutes, new Producer(KafkaClientFactory.createProducer()))
        )
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
    }

    private static void onShutdownHook(Disposable consumer, MonitoringServer monitoringServer) {
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
        if (consumer != null) {
            consumer.dispose();
        }
        if (monitoringServer != null) {
            monitoringServer.close();
        }
    }
}
