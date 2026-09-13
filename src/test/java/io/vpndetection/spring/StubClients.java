package io.vpndetection.spring;

import com.sun.net.httpserver.HttpServer;
import io.vpndetection.VPNDetection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Clients pointed at a real HTTP server on an ephemeral port.
 *
 * <p>A real server rather than a mocked HttpClient: the SDK builds its own client internally, and
 * a stub would have to reimplement enough of java.net.http to be its own source of bugs. The
 * server costs a millisecond and exercises the actual transport.
 */
final class StubClients {
    private StubClients() {}

    /** Answers every lookup with {@code body}, merged onto the address asked about. */
    static VPNDetection serving(String body, String ip) {
        String answer = body.equals("{}")
                ? "{\"ip\":\"" + ip + "\"}"
                : "{\"ip\":\"" + ip + "\"," + body.substring(1);
        return clientOn(200, answer);
    }

    /** Answers every lookup with a 500, for the fail-open path. */
    static VPNDetection failing() {
        return clientOn(500, "{\"error\":\"boom\"}");
    }

    private static VPNDetection clientOn(int status, String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange -> {
                exchange.getResponseHeaders().add("content-type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return VPNDetection.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .cacheEnabled(false)
                    .retries(0)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
