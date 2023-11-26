package configuration;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TopicsRoutes {

    private final Map<String, String> topicsRoutes;

    public TopicsRoutes(Map<String, String> topicsRoutes) {
        this.topicsRoutes = topicsRoutes;
    }

    public String getRoute(String topic) {
        var route = topicsRoutes
            .keySet()
            .stream()
            .filter(pattern -> Pattern.matches(pattern, topic))
            .findAny()
            .orElse(null);

        if (route == null) {
            return null;
        }

        return topicsRoutes.get(route);
    }

    public Set<String> getTopics() {
        return this.topicsRoutes.keySet();
    }

    public String getTopicsPattern() {
        return this.topicsRoutes.keySet()
            .stream()
            .map(topic -> String.format("^%s$", topic))
            .collect(Collectors.joining("|"));
    }
}
