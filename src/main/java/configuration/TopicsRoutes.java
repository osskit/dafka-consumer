package configuration;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class TopicsRoutes {

    private Map<String, String> topicsRoutes;

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
}
