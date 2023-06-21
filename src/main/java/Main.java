import configuration.Config;
import configuration.TopicsRoutes;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import kafka.Consumer;
import kafka.KafkaClientFactory;
import kafka.Producer;
import kafka.ReactiveKafkaClient;
import monitoring.Monitor;
import monitoring.MonitoringServer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import reactor.core.Disposable;
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
            waitForTargetHealthcheck();
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

    private static void waitForTargetHealthcheck() throws InterruptedException {
        do {
            Monitor.waitingForTargetHealthcheck();
            Thread.sleep(1000);
        } while (!TargetHealthcheck.check());
        Monitor.targetHealthcheckPassedSuccessfully();
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

                        Monitor.assignedToPartition(partitions);
                        monitoringServer.consumerAssigned();
                    }

                    @Override
                    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                        Monitor.revokedFromPartition(partitions);
                    }
                }
            ),
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
