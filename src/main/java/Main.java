import configuration.*;
import java.io.IOException;
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

    public static void main(String[] args) {
        try {
            Config.init();
            Monitor.init();

            topicsRoutes = new TopicsRoutes(Config.TOPICS_ROUTES);
            var targetHealthcheck = new TargetHealthcheck();
            monitoringServer = new MonitoringServer(targetHealthcheck).start();
            waitForTargetHealthcheck(targetHealthcheck);
            consumer = createConsumer(monitoringServer);
            onShutdown(consumer, monitoringServer);
            Monitor.started();
            latch.await();
        } catch (Exception e) {
            Monitor.initializationError(e);
        }
        Monitor.serviceTerminated();
    }

    private static void waitForTargetHealthcheck(TargetHealthcheck targetHealthcheck)
        throws InterruptedException, IOException {
        do {
            System.out.printf("waiting for target healthcheck %s%n", targetHealthcheck.getEndpoint());
            Thread.sleep(1000);
        } while (!targetHealthcheck.check());
        System.out.println("target healthcheck pass successfully");
    }

    private static Disposable createConsumer(MonitoringServer monitoringServer) {
        return new Consumer(
            new ReactiveKafkaClient<String, String>(
                new KafkaClientFactory().createConsumer(),
                topicsRoutes.getTopics(),
                new ConsumerRebalanceListener() {
                    @Override
                    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                        Monitor.assignedToPartition(partitions);
                        monitoringServer.consumerAssigned();
                    }

                    @Override
                    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                        Monitor.revokedFromPartition(partitions);
                    }
                }
            ),
            new HttpTarget(
                new TargetRetryPolicy(
                    new Producer(new KafkaClientFactory().createProducer()),
                    Config.RETRY_TOPIC,
                    Config.DEAD_LETTER_TOPIC
                ),
                topicsRoutes
            )
        )
            .stream()
            .doOnError(
                e -> {
                    Monitor.consumerError(e);
                }
            )
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
                new Thread(
                    () -> {
                        Monitor.shuttingDown();
                        consumer.dispose();
                        monitoringServer.close();
                        latch.countDown();
                    }
                )
            );
    }
}
