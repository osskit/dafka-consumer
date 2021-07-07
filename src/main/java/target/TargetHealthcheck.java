package target;

import configuration.Config;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import monitoring.Monitor;

public class TargetHealthcheck {

    private static final HttpClient client = HttpClient.newHttpClient();

    public String getEndpoint() {
        return Config.TARGET_HEALTHCHECK;
    }

    public boolean check() throws IOException {
        if (Config.TARGET_HEALTHCHECK != null) {
            try {
                final var request = HttpRequest
                    .newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .uri(URI.create(Config.TARGET_BASE_URL + Config.TARGET_HEALTHCHECK))
                    .build();

                var targetHealthcheckResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (targetHealthcheckResponse.statusCode() != 200) {
                    Monitor.targetHealthcheckFailed(targetHealthcheckResponse.statusCode());
                    return false;
                }
                Monitor.targetHealthcheckPassed(targetHealthcheckResponse.statusCode());
                return true;
            } catch (Exception e) {
                Monitor.initializationError(e);
                return false;
            }
        }
        return true;
    }
}
