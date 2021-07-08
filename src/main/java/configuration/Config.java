package configuration;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Config {

    //Constants
    public static String ORIGINAL_TOPIC = "original-topic";

    //Required
    public static String KAFKA_BROKER;
    public static String GROUP_ID;
    public static String TARGET_BASE_URL;
    public static Map<String, String> TOPICS_ROUTES;

    //Optional
    public static int POLL_TIMEOUT;
    public static int MAX_POLL_RECORDS;
    public static int PROCESSING_DELAY;
    public static int SESSION_TIMEOUT;
    public static String RETRY_TOPIC;
    public static String DEAD_LETTER_TOPIC;
    public static String RETRY_PROCESS_WHEN_STATUS_CODE_MATCH;
    public static String PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH;
    public static String PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH;
    public static List<Integer> RETRY_POLICY_EXPONENTIAL_BACKOFF;

    //Authentication
    public static boolean USE_SASL_AUTH;
    public static String SASL_USERNAME;
    public static String SASL_PASSWORD;
    public static String TRUSTSTORE_FILE_PATH;
    public static String TRUSTSTORE_PASSWORD;

    //Monitoring
    public static int MONITORING_SERVER_PORT;
    public static boolean USE_PROMETHEUS;
    public static String PROMETHEUS_BUCKETS;
    public static boolean LOG_RECORD;
    public static String TARGET_HEALTHCHECK;

    public static void init() throws Exception {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        KAFKA_BROKER = getString(dotenv, "KAFKA_BROKER");
        GROUP_ID = getString(dotenv, "GROUP_ID");
        TARGET_BASE_URL = getString(dotenv, "TARGET_BASE_URL");
        TOPICS_ROUTES = getStringMap(dotenv, "TOPICS_ROUTES");

        POLL_TIMEOUT = getOptionalInt(dotenv, "POLL_TIMEOUT", 1000);
        MAX_POLL_RECORDS = getOptionalInt(dotenv, "MAX_POLL_RECORDS", 50);
        SESSION_TIMEOUT = getOptionalInt(dotenv, "SESSION_TIMEOUT", 10000);

        RETRY_PROCESS_WHEN_STATUS_CODE_MATCH =
            getOptionalString(dotenv, "RETRY_PROCESS_WHEN_STATUS_CODE_MATCH", "5[0-9][0-9]");

        PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH =
            getOptionalString(dotenv, "PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH", "408");
        PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH =
            getOptionalString(dotenv, "PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH", "4[0-9][0-79]");
        RETRY_POLICY_EXPONENTIAL_BACKOFF =
            getOptionalIntList(dotenv, "RETRY_POLICY_EXPONENTIAL_BACKOFF", 3, List.of(50, 5000, 10));
        RETRY_TOPIC = getOptionalString(dotenv, "RETRY_TOPIC", null);

        DEAD_LETTER_TOPIC = getOptionalString(dotenv, "DEAD_LETTER_TOPIC", null);
        PROCESSING_DELAY = getOptionalInt(dotenv, "PROCESSING_DELAY", 0);
        MONITORING_SERVER_PORT = getOptionalInt(dotenv, "MONITORING_SERVER_PORT", 0);
        TARGET_HEALTHCHECK = getOptionalString(dotenv, "TARGET_HEALTHCHECK", null);

        USE_SASL_AUTH = getOptionalBool(dotenv, "USE_SASL_AUTH", false);
        if (USE_SASL_AUTH) {
            SASL_USERNAME = getString(dotenv, "SASL_USERNAME");
            SASL_PASSWORD = getStringValueOrFromFile(dotenv, "SASL_PASSWORD");
            TRUSTSTORE_FILE_PATH = getOptionalString(dotenv, "TRUSTSTORE_FILE_PATH", null);
            if (TRUSTSTORE_FILE_PATH != null) {
                TRUSTSTORE_PASSWORD = getStringValueOrFromFile(dotenv, "TRUSTSTORE_PASSWORD");
            }
        }

        USE_PROMETHEUS = getOptionalBool(dotenv, "USE_PROMETHEUS", false);
        PROMETHEUS_BUCKETS = getOptionalString(dotenv, "PROMETHEUS_BUCKETS", "0.003,0.03,0.1,0.3,1.5,10");
        LOG_RECORD = getOptionalBool(dotenv, "LOG_RECORD", false);
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
        var list = Arrays.asList(value.split(",")).stream().map(x -> Integer.parseInt(x)).collect(Collectors.toList());

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
        var list = getStringList(dotenv, name);
        var map = new HashMap<String, String>();
        for (var i = 0; i < list.size(); i++) {
            var left = list.get(i).split(":")[0];
            var right = list.get(i).split(":")[1];
            map.put(left, right);
        }
        return map;
    }
}
