package monitoring;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import configuration.Config;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import target.TargetHealthcheck;

public class MonitoringServer {

    private boolean consumerAssigned;
    private boolean consumerDisposed;
    private HttpServer server;

    public MonitoringServer() {}

    public MonitoringServer start() throws IOException {
        if (Config.MONITORING_SERVER_PORT == 0) {
            return this;
        }

        server = HttpServer.create(new InetSocketAddress(Config.MONITORING_SERVER_PORT), 0);
        aliveRoute(server);
        readyRoute(server);
        server.setExecutor(Executors.newCachedThreadPool());
        if (Config.USE_PROMETHEUS) {
            DefaultExports.initialize();
            new HTTPServer(server, CollectorRegistry.defaultRegistry, false);
        } else {
            server.start();
        }
        return this;
    }

    public void consumerAssigned() {
        consumerAssigned = true;
    }

    public void consumerDisposed() {
        consumerDisposed = true;
    }

    public void close() {
        server.stop(0);
    }

    private void aliveRoute(final HttpServer server) {
        final var httpContext = server.createContext("/alive");

        httpContext.setHandler(exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            if (!TargetHealthcheck.check()) {
                writeResponse(500, exchange);
                return;
            }

            if (consumerDisposed) {
                writeResponse(500, exchange);
                return;
            }

            writeResponse(200, exchange);
        });
    }

    private void readyRoute(final HttpServer server) {
        final var httpContext = server.createContext("/ready");

        httpContext.setHandler(exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            if (!consumerAssigned) {
                writeResponse(500, exchange);
                return;
            }

            writeResponse(200, exchange);
        });
    }

    private void writeResponse(int statusCode, HttpExchange exchange) throws IOException {
        final var os = exchange.getResponseBody();
        var responseText = (statusCode == 500 ? "false" : "true");
        exchange.sendResponseHeaders(statusCode, responseText.getBytes().length);
        os.write(responseText.getBytes());
        os.close();
    }
}
