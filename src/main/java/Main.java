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
            onShutdown(consumer, monitoringServer);
            Monitor.started();
            latch.await();
        } catch (Exception e) {
            Monitor.initializationError(e);
            throw e;
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
            .commitInterval(Duration.ZERO)
            .commitBatchSize(0)
            .subscription(Pattern.compile(topicsRoutes.getTopicsPattern()))
            .addAssignListener(partitions -> {
                Monitor.assignedToPartition(partitions);
                if (partitions.isEmpty()) {
                    return;
                }
                monitoringServer.consumerAssigned();
            })
            .addRevokeListener(Monitor::revokedFromPartition);

        return new Consumer(
            KafkaReceiver.create(receiverOptions),
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

    private static void onShutdown(Disposable consumer, MonitoringServer monitoringServer) {
        Runtime
            .getRuntime()
            .addShutdownHook(
                new Thread(() -> {
                    Monitor.shuttingDown();
                    consumer.dispose();
                    monitoringServer.close();
                    latch.countDown();
                })
            );
    }
}
