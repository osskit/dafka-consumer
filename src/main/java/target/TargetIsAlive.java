package target;

import configuration.Config;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import monitoring.Monitor;

public class TargetIsAlive {

    private static final HttpClient client = HttpClient.newHttpClient();

    public String getEndpoint() {
        return Config.TARGET_IS_ALIVE_HTTP_ENDPOINT;
    }

    public boolean check() throws IOException {
        if (Config.TARGET_IS_ALIVE_HTTP_ENDPOINT != null) {
            try {
                final var request = HttpRequest
                    .newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .uri(URI.create(Config.TARGET_IS_ALIVE_HTTP_ENDPOINT))
                    .build();

                var targetIsAliveResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (targetIsAliveResponse.statusCode() != 200) {
                    Monitor.targetNotAlive(targetIsAliveResponse.statusCode());
                    return false;
                }
                Monitor.targetAlive(targetIsAliveResponse.statusCode());
                return true;
            } catch (Exception e) {
                Monitor.initializationError(e);
                return false;
            }
        }
        return true;
    }
}
