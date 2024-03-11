package configuration;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.consumer.RoundRobinAssignor;
import org.apache.kafka.clients.consumer.StickyAssignor;

public class Config {

    private enum AssignmentStrategyConfig {
        Range,
        RoundRobin,
        Sticky,
        CooperativeSticky,
    }

    //Constants
    public static String ORIGINAL_TOPIC = "original-topic";

    //Required
    public static String KAFKA_BROKER;
    public static String GROUP_ID;
    public static String TARGET_BASE_URL;

    public static String TARGET_PROCESS_TYPE;

    public static Map<String, String> TOPICS_ROUTES;

    //Optional
    public static int POLL_TIMEOUT;
    public static int KAFKA_POLL_INTERVAL_MS;
    public static int MAX_POLL_RECORDS;
    public static int COMMIT_INTERVAL_MS;
    public static String DEAD_LETTER_TOPIC;
    public static String CONNECTION_RETRY_PROCESS_WHEN_STATUS_CODE_MATCH;
    public static String RETRY_PROCESS_WHEN_STATUS_CODE_MATCH;
    public static String PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH;
    public static List<Integer> RETRY_POLICY_EXPONENTIAL_BACKOFF;
    public static List<Integer> CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF;
    public static int RETRY_POLICY_MAX_RETRIES;
    public static int RETRY_POLICY_MAX_DURATION_MS;
    public static int CONNECTION_FAILURE_RETRY_POLICY_MAX_RETRIES;
    public static int CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS;
    public static long TARGET_TIMEOUT_MS;
    public static List<String> BODY_HEADERS_PATHS;

    //Authentication
    public static boolean USE_SASL_AUTH;
    public static String SASL_USERNAME;
    public static String SASL_PASSWORD;
    public static String SASL_MECHANISM;
    public static String TRUSTSTORE_FILE_PATH;
    public static String TRUSTSTORE_PASSWORD;

    //Monitoring
    public static int MONITORING_SERVER_PORT;
    public static boolean USE_PROMETHEUS;

    public static boolean EXPOSE_JAVA_METRICS;
    public static String PROMETHEUS_BUCKETS;
    public static String TARGET_HEALTHCHECK;
    public static List<String> ASSIGNMENT_STRATEGY;

    public static int CONNECTION_POOL_MAX_IDLE_CONNECTIONS;
    public static int CONNECTION_POOL_KEEP_ALIVE_DURATION_MS;

    public static void init() throws Exception {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        KAFKA_BROKER = getString(dotenv, "KAFKA_BROKER");
        GROUP_ID = getString(dotenv, "GROUP_ID");
        COMMIT_INTERVAL_MS = getOptionalInt(dotenv, "COMMIT_INTERVAL_MS", 5000);

        var k8sServiceHost = getOptionalString(dotenv, "K8S_SERVICE_HOST_ENV_VAR", "");

        if (k8sServiceHost != "") {
            var targetPort = getOptionalInt(dotenv, "TARGET_PORT", 80);
            var targetIp = getString(dotenv, k8sServiceHost);

                        TARGET_BASE_URL = String.format("http://%s:%d", targetIp, targetPort);
        } else {
            TARGET_BASE_URL = getString(dotenv, "TARGET_BASE_URL");
        }

        TOPICS_ROUTES = getStringMap(dotenv, "TOPICS_ROUTES");

        TARGET_PROCESS_TYPE = getOptionalString(dotenv, "TARGET_PROCESS_TYPE", "stream");

        CONNECTION_POOL_MAX_IDLE_CONNECTIONS = getOptionalInt(dotenv, "CONNECTION_POOL_MAX_IDLE_CONNECTIONS", 0);
        CONNECTION_POOL_KEEP_ALIVE_DURATION_MS = getOptionalInt(dotenv, "CONNECTION_POOL_KEEP_ALIVE_DURATION_MS", 1000);

        BODY_HEADERS_PATHS = getOptionalStringList(dotenv, "BODY_HEADERS_PATHS");
        KAFKA_POLL_INTERVAL_MS = getOptionalInt(dotenv, "KAFKA_POLL_INTERVAL_MS", 5 * 60 * 1000);

        MAX_POLL_RECORDS = getOptionalInt(dotenv, "MAX_POLL_RECORDS", 500);
        RETRY_POLICY_MAX_DURATION_MS =
            getOptionalInt(dotenv, "RETRY_POLICY_MAX_DURATION_MS", KAFKA_POLL_INTERVAL_MS - 1000);
        CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS =
            getOptionalInt(dotenv, "CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS", KAFKA_POLL_INTERVAL_MS - 1000);
        TARGET_TIMEOUT_MS = getOptionalLong(dotenv, "TARGET_TIMEOUT_MS", RETRY_POLICY_MAX_DURATION_MS - 1000);

        if (KAFKA_POLL_INTERVAL_MS < RETRY_POLICY_MAX_DURATION_MS) {
            throw new IllegalArgumentException(
                String.format(
                    "KAFKA_POLL_INTERVAL_MS (%s) must be greater than RETRY_POLICY_MAX_DURATION_MS (%s)",
                    KAFKA_POLL_INTERVAL_MS,
                    RETRY_POLICY_MAX_DURATION_MS
                )
            );
        }

        if (RETRY_POLICY_MAX_DURATION_MS < TARGET_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                String.format(
                    "RETRY_POLICY_MAX_DURATION_MS (%s) must be greater than TARGET_TIMEOUT_MS (%s)",
                    RETRY_POLICY_MAX_DURATION_MS,
                    TARGET_TIMEOUT_MS
                )
            );
        }

        CONNECTION_RETRY_PROCESS_WHEN_STATUS_CODE_MATCH =
            getOptionalString(dotenv, "CONNECTION_RETRY_PROCESS_WHEN_STATUS_CODE_MATCH", "502|503|504");

        RETRY_PROCESS_WHEN_STATUS_CODE_MATCH = getOptionalString(dotenv, "RETRY_PROCESS_WHEN_STATUS_CODE_MATCH", "500");

        PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH =
            getOptionalString(dotenv, "PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH", "^(?!2\\d\\d$)\\d{3}$");

        RETRY_POLICY_EXPONENTIAL_BACKOFF =
            getOptionalIntList(dotenv, "RETRY_POLICY_EXPONENTIAL_BACKOFF", 3, List.of(50, 5000, 10));

        CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF =
            getOptionalIntList(
                dotenv,
                "CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF",
                3,
                List.of(5000, 300_000, 2)
            );
        RETRY_POLICY_MAX_RETRIES = getOptionalInt(dotenv, "RETRY_POLICY_MAX_RETRIES", 10);

        CONNECTION_FAILURE_RETRY_POLICY_MAX_RETRIES =
            getOptionalInt(dotenv, "CONNECTION_FAILURE_RETRY_POLICY_MAX_RETRIES", 10);

        DEAD_LETTER_TOPIC = getOptionalString(dotenv, "DEAD_LETTER_TOPIC", null);

        MONITORING_SERVER_PORT = getOptionalInt(dotenv, "MONITORING_SERVER_PORT", 0);

        TARGET_HEALTHCHECK = getOptionalString(dotenv, "TARGET_HEALTHCHECK", null);
        List<String> assignmentStrategies = getOptionalStringList(dotenv, "ASSIGNMENT_STRATEGY");
        if (assignmentStrategies != null && assignmentStrategies.size() > 0) {
            ASSIGNMENT_STRATEGY =
                assignmentStrategies
                    .stream()
                    .map(AssignmentStrategyConfig::valueOf)
                    .map(strategy -> {
                        switch (strategy) {
                            case Range -> {
                                return RangeAssignor.class.getName();
                            }
                            case Sticky -> {
                                return StickyAssignor.class.getName();
                            }
                            case CooperativeSticky -> {
                                return CooperativeStickyAssignor.class.getName();
                            }
                            case RoundRobin -> {
                                return RoundRobinAssignor.class.getName();
                            }
                            default -> throw new RuntimeException(
                                "ASSIGNMENT_STRATEGY value not supported {" + strategy + "}"
                            );
                        }
                    })
                    .collect(Collectors.toList());
        }

        USE_SASL_AUTH = getOptionalBool(dotenv, "USE_SASL_AUTH", false);
        if (USE_SASL_AUTH) {
            SASL_USERNAME = getString(dotenv, "SASL_USERNAME");
            SASL_PASSWORD = getStringValueOrFromFile(dotenv, "SASL_PASSWORD");
            SASL_MECHANISM = getOptionalString(dotenv, "SASL_MECHANISM", "PLAIN");
            TRUSTSTORE_FILE_PATH = getOptionalString(dotenv, "TRUSTSTORE_FILE_PATH", null);
            if (TRUSTSTORE_FILE_PATH != null) {
                TRUSTSTORE_PASSWORD = getStringValueOrFromFile(dotenv, "TRUSTSTORE_PASSWORD");
            }
        }

        USE_PROMETHEUS = getOptionalBool(dotenv, "USE_PROMETHEUS", false);

        EXPOSE_JAVA_METRICS = getOptionalBool(dotenv, "EXPOSE_JAVA_METRICS", false);

        PROMETHEUS_BUCKETS = getOptionalString(dotenv, "PROMETHEUS_BUCKETS", "0.003,0.03,0.1,0.3,1.5,10");
    }

    private static String getString(Dotenv dotenv, String name) throws Exception {
        String value = dotenv.get(name);

        if (value == null) {
            throw new Exception("missing env var: " + name);
        }

        return value;
    }

    private static List<String> getStringList(Dotenv dotenv, String name) throws Exception {
        String value = dotenv.get(name);

        if (value == null) {
            throw new Exception("missing env var: " + name);
        }

        return Arrays.asList(value.replaceAll(" ", "").split(","));
    }

    private static List<Integer> getOptionalIntList(
        Dotenv dotenv,
        String name,
        int expectedSize,
        List<Integer> fallback
    ) throws Exception {
        String value = dotenv.get(name);

        if (value == null) {
            return fallback;
        }

        var list = Arrays.stream(value.split(",")).map(Integer::parseInt).collect(Collectors.toList());

        if (expectedSize != -1 && expectedSize != list.size()) {
            throw new Exception(
                String.format(
                    "env var parse error: expected list with size of %1$s, got list with size of %2$s",
                    expectedSize,
                    list.size()
                )
            );
        }
        return list;
    }

    private static List<String> getOptionalStringList(Dotenv dotenv, String name) throws Exception {
        String value = dotenv.get(name);

        if (value == null) {
            return null;
        }

        return Arrays.stream(value.split(",")).collect(Collectors.toList());
    }

    private static String getOptionalString(Dotenv dotenv, String name, String fallback) {
        try {
            return getString(dotenv, name);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean getOptionalBool(Dotenv dotenv, String name, boolean fallback) {
        try {
            return Boolean.parseBoolean(getString(dotenv, name));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int getOptionalInt(Dotenv dotenv, String name, int fallback) {
        try {
            return Integer.parseInt(dotenv.get(name));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long getOptionalLong(Dotenv dotenv, String name, long fallback) {
        try {
            return Long.parseLong(getString(dotenv, name));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String getStringValueOrFromFile(Dotenv dotenv, String name) throws Exception {
        String value = dotenv.get(name);

        if (value != null) {
            return value;
        }

        String filePath = dotenv.get(name + "_FILE_PATH");

        if (filePath == null) {
            throw new Exception("missing env var: " + name + " or " + name + "_FILE_PATH");
        }

        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private static Map<String, String> getStringMap(Dotenv dotenv, String name) throws Exception {
        return getStringList(dotenv, name)
            .stream()
            .map(x -> x.split(":"))
            .collect(Collectors.toMap(x -> x[0], x -> x[1]));
    }
}
