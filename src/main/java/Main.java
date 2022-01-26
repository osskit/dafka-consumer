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

    public static void main(String[] args) {
        try {
            Config.init();
            Monitor.init();

            monitoringServer = new MonitoringServer(waitForTargetHealthcheck()).start();
            consumer = createConsumer(monitoringServer);
            onShutdown(consumer, monitoringServer);

            Monitor.started();
            latch.await();
        } catch (Exception e) {
            Monitor.initializationError(e);
        }
        Monitor.serviceTerminated();
    }

    private static TargetHealthcheck waitForTargetHealthcheck() throws InterruptedException, IOException {
        var targetHealthcheck = new TargetHealthcheck();
        do {
            System.out.printf("waiting for target healthcheck %s%n", targetHealthcheck.getEndpoint());
            Thread.sleep(1000);
        } while (!targetHealthcheck.check());
        System.out.println("target healthcheck pass successfully");
        return targetHealthcheck;
    }

    private static Disposable createConsumer(MonitoringServer monitoringServer) {
        return new Consumer(
            new ReactiveKafkaClient<String, String>(
                new KafkaClientFactory().createConsumer(),
                Config.TOPICS_ROUTES.topicsRoutes.keySet(),
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
                )
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
