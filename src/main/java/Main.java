import configuration.*;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import kafka.*;
import monitoring.*;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import reactor.core.Disposable;
import target.*;

public class Main {

    static Disposable consumer;
    static MonitoringServer monitoringServer;
    static CountDownLatch latch = new CountDownLatch(1);
    static TopicsRoutes topicsRoutes;

    public static void main(String[] args) throws Exception {
        try {
            Config.init();
            LegacyMonitor.init();

            topicsRoutes = new TopicsRoutes(Config.TOPICS_ROUTES);
            waitForTargetHealthcheck();
            monitoringServer = new MonitoringServer().start();
            consumer = createConsumer(monitoringServer);
            onShutdown(consumer, monitoringServer);
            LegacyMonitor.started();
            latch.await();
        } catch (Exception e) {
            LegacyMonitor.initializationError(e);
            throw e;
        }
        LegacyMonitor.serviceTerminated();
    }

    private static void waitForTargetHealthcheck() throws InterruptedException {
        do {
            LegacyMonitor.waitingForTargetHealthcheck();
            Thread.sleep(1000);
        } while (!TargetHealthcheck.check());
        LegacyMonitor.targetHealthcheckPassedSuccessfully();
    }

    private static Disposable createConsumer(MonitoringServer monitoringServer) {
        return new Consumer(
            new ReactiveKafkaClient<>(
                KafkaClientFactory.createConsumer(),
                topicsRoutes.getTopics(),
                new ConsumerRebalanceListener() {
                    @Override
                    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                        if (partitions.size() == 0) {
                            return;
                        }

                        LegacyMonitor.assignedToPartition(partitions);
                        monitoringServer.consumerAssigned();
                    }

                    @Override
                    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                        LegacyMonitor.revokedFromPartition(partitions);
                    }
                }
            ),
            new HttpTarget(topicsRoutes, new Producer(KafkaClientFactory.createProducer()))
        )
            .stream()
            .doOnError(LegacyMonitor::consumerError)
            .subscribe(
                __ -> {},
                exception -> {
                    monitoringServer.consumerDisposed();
                    LegacyMonitor.consumerError(exception);
                },
                () -> {
                    monitoringServer.consumerDisposed();
                    LegacyMonitor.consumerCompleted();
                }
            );
    }

    private static void onShutdown(Disposable consumer, MonitoringServer monitoringServer) {
        Runtime
            .getRuntime()
            .addShutdownHook(
                new Thread(
                    () -> {
                        LegacyMonitor.shuttingDown();
                        consumer.dispose();
                        monitoringServer.close();
                        latch.countDown();
                    }
                )
            );
    }
}
