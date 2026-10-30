package io.vpndetection.spring;

import com.sun.net.httpserver.HttpServer;
import io.vpndetection.middleware.Core;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The starter driven the way its README documents it: from application.yml.
 *
 * <p>Every other test builds its condition as Java values, which is how a block condition that
 * only ever arrives as strings from the binder passed a green suite while blocking nobody.
 */
class VPNDetectionAutoConfigurationTest {
    private static final String PUBLIC_IP = "45.83.91.1";

    private static HttpServer serving(String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] bytes = ("{\"ip\":\"" + PUBLIC_IP + "\"," + body.substring(1))
                    .getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange -> {
                exchange.getResponseHeaders().add("content-type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static WebApplicationContextRunner runner(HttpServer server, String... condition) {
        String[] properties = new String[condition.length + 3];
        properties[0] = "vpndetection.api-key=k";
        properties[1] = "vpndetection.base-url=http://127.0.0.1:" + server.getAddress().getPort();
        properties[2] = "vpndetection.client-ip-header=X-Real-IP";
        System.arraycopy(condition, 0, properties, 3, condition.length);
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(VPNDetectionAutoConfiguration.class))
                .withPropertyValues(properties);
    }

    private static int statusThrough(VPNDetectionFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", PUBLIC_IP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void aFlagConditionFromYamlBlocks() {
        HttpServer server = serving("{\"is_vpn\":true}");
        try {
            runner(server, "vpndetection.block-condition[0].is_vpn=true").run(context -> {
                assertTrue(context.getBean(Core.class).isBlocking());
                assertEquals(403, statusThrough(context.getBean(VPNDetectionFilter.class)),
                        "a block-condition written in application.yml has to actually block");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aNumericBoundFromYamlBlocks() {
        HttpServer server = serving("{\"is_vpn\":false,\"is_resproxy\":true,\"resproxy\":{\"hits\":7}}");
        try {
            runner(server,
                    "vpndetection.block-condition[0].is_resproxy=true",
                    "vpndetection.block-condition[0].resproxy.hits.gte=7").run(context ->
                    assertEquals(403, statusThrough(context.getBean(VPNDetectionFilter.class)),
                            "gte on the bound itself has to match, not just above it"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aBoundThatMissesLeavesTheVisitorThrough() {
        HttpServer server = serving("{\"is_vpn\":false,\"is_resproxy\":true,\"resproxy\":{\"hits\":7}}");
        try {
            runner(server,
                    "vpndetection.block-condition[0].is_resproxy=true",
                    "vpndetection.block-condition[0].resproxy.hits.gt=7").run(context ->
                    assertEquals(200, statusThrough(context.getBean(VPNDetectionFilter.class)),
                            "gt on the bound must not match, or the bound is being ignored"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aStringMemberFromYamlStillCompares() {
        HttpServer server = serving("{\"is_vpn\":true,\"vpn\":{\"provider\":\"nordvpn\"}}");
        try {
            runner(server,
                    "vpndetection.block-condition[0].is_vpn=true",
                    "vpndetection.block-condition[0].vpn.provider=NordVPN").run(context ->
                    assertEquals(403, statusThrough(context.getBean(VPNDetectionFilter.class)),
                            "a provider slug stays a string, and compares without case"));
        } finally {
            server.stop(0);
        }
    }

    // The README's whole configuration is the key. Every other test here also sets a base URL and a
    // client-address header, so none would see a starter that cannot start without them.
    @Test
    void theReadmesConfigurationAloneStartsTheFilter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(VPNDetectionAutoConfiguration.class))
                .withPropertyValues("vpndetection.api-key=k")
                .run(context -> {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    context.getBean(VPNDetectionFilter.class).doFilter(request, response, new MockFilterChain());
                    assertEquals(200, response.getStatus());
                    // The mock's 127.0.0.1 is a bogon, answered with no request leaving the test.
                    assertTrue(VPNDetectionFilter.lookup(request).orElseThrow().result().orElseThrow().isBogon());
                });
    }

    @Test
    void aKeyPlaceholderNothingResolvedGoesOutAsNoKeyAndSaysSo() {
        List<String> warnings = new CopyOnWriteArrayList<>();
        Handler handler = capturing(warnings);
        List<String> sent = new CopyOnWriteArrayList<>();
        HttpServer server = recordingAuthorization(sent);
        try {
            keyed(server, "${VPNDETECTION_TEST_KEY_NOBODY_SETS}").run(context -> {
                assertEquals(200, statusThrough(context.getBean(VPNDetectionFilter.class)));
                assertEquals(List.of("none"), sent,
                        "an unset variable must not go out as its own placeholder text");
                assertTrue(warnings.stream().anyMatch(w -> w.contains("VPNDETECTION_TEST_KEY_NOBODY_SETS")),
                        "the unset variable has to be named at startup, got " + warnings);
            });
        } finally {
            Logger.getLogger("io.vpndetection").removeHandler(handler);
            server.stop(0);
        }
    }

    @Test
    void aBlankKeyGoesOutAsNoKeyWithoutAWarning() {
        List<String> warnings = new CopyOnWriteArrayList<>();
        Handler handler = capturing(warnings);
        List<String> sent = new CopyOnWriteArrayList<>();
        HttpServer server = recordingAuthorization(sent);
        try {
            keyed(server, "${VPNDETECTION_TEST_KEY_NOBODY_SETS:}").run(context -> {
                assertEquals(200, statusThrough(context.getBean(VPNDetectionFilter.class)));
                assertEquals(List.of("none"), sent, "a blank key is no key, never a bare Bearer");
                assertTrue(warnings.isEmpty(), "a default written on purpose is no mistake: " + warnings);
            });
        } finally {
            Logger.getLogger("io.vpndetection").removeHandler(handler);
            server.stop(0);
        }
    }

    @Test
    void aConditionThatConstrainsNothingRefusesToStart() {
        HttpServer server = serving("{\"is_vpn\":true}");
        try {
            runner(server, "vpndetection.block-condition[0].is_vpn=false").run(context -> {
                assertNotNull(context.getStartupFailure(),
                        "a condition with no terms left blocks everybody, so the context must fail");
                assertTrue(rootCause(context.getStartupFailure()) instanceof IllegalArgumentException);
            });
        } finally {
            server.stop(0);
        }
    }

    // Answers every lookup and records the Authorization header each one carried, "none" for none.
    private static HttpServer recordingAuthorization(List<String> sent) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] bytes = ("{\"ip\":\"" + PUBLIC_IP + "\",\"is_vpn\":false}")
                    .getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange -> {
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                sent.add(authorization == null ? "none" : authorization);
                exchange.getResponseHeaders().add("content-type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static WebApplicationContextRunner keyed(HttpServer server, String apiKey) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(VPNDetectionAutoConfiguration.class))
                .withPropertyValues(
                        "vpndetection.api-key=" + apiKey,
                        "vpndetection.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                        "vpndetection.client-ip-header=X-Real-IP");
    }

    private static Handler capturing(List<String> warnings) {
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger.getLogger("io.vpndetection").addHandler(handler);
        return handler;
    }

    private static Throwable rootCause(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
