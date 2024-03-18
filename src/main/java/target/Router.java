package target;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;
import reactor.kafka.receiver.ReceiverRecord;

import java.util.ArrayList;
import java.util.List;

public class Router {
    private static List<TopicRoute> topicRoutes;

    public Router(String jsonRouter) {
        topicRoutes = new Gson().fromJson(jsonRouter,new TypeToken<ArrayList<TopicRoute>>(){}.getType());
    }

    public Route getRoute(ReceiverRecord<String, String> record) {
        var recordObj = new JSONObject(record.value());
        var y = topicRoutes.stream()
                .filter(x -> x.topic.equals(record.topic()) && recordObj.get(x.filter.field) == x.filter.value)
                .map(x -> {
                    var foo = new Route();
                    foo.endpoint = x.endpoint;
                    foo.body = new JSONObject(record.value()).getJSONObject(x.projection).toString();
                    return foo;
                }).toList();

        return y.get(0);

    }
}

class Route {
    public String endpoint;
    public String body;
}

class Filter {
    public String field;
    public String value;
}

class TopicRoute {
    public String topic;
    public Filter filter;
    public String projection;
    public String endpoint;
}
