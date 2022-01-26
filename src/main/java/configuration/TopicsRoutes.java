package configuration;

import java.util.Map;
import java.util.regex.Pattern;

public class TopicsRoutes {

    public Map<String, String> topicsRoutes;

    public TopicsRoutes(Map<String, String> _topicsRoutes) {
        topicsRoutes = _topicsRoutes;
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
}
