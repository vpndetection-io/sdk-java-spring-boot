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
        HttpServer server = serving("{\"is_resproxy\":true,\"resproxy\":{\"hits\":7}}");
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
        HttpServer server = serving("{\"is_resproxy\":true,\"resproxy\":{\"hits\":7}}");
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

    private static Throwable rootCause(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
