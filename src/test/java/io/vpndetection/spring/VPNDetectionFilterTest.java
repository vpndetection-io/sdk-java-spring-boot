package io.vpndetection.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vpndetection.middleware.Bound;
import io.vpndetection.middleware.Condition;
import io.vpndetection.middleware.Core;
import io.vpndetection.middleware.Lookup;
import io.vpndetection.middleware.Options;
import io.vpndetection.middleware.RequestView;
import io.vpndetection.middleware.Selectors;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The filter, driven through a real servlet filter chain, plus the shared corpus.
 *
 * <p>A MockHttpServletRequest reports 127.0.0.1, which is a bogon and is answered locally without
 * a request. Anything that needs a served answer therefore has to arrive wearing a public address,
 * through a selector.
 */
class VPNDetectionFilterTest {
    private static final String PUBLIC_IP = "45.83.91.1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> BOUND_KEYS = Set.of("gte", "gt", "lte", "lt");
    private static JsonNode middleware;

    @BeforeAll
    static void loadCorpus() throws IOException {
        middleware = MAPPER.readTree(
                Files.readString(Path.of("testdata", "testdata.json"))).get("middleware");
    }

    private static final Selectors<HttpServletRequest> SELECTORS = new Selectors<>(
            request -> new RequestView(request::getHeader, request::getRemoteAddr));

    private static Core<HttpServletRequest> coreServing(String body, String ip) {
        Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                .client(StubClients.serving(body, ip))
                .ipSelector(request -> ip);
        return new Core<>(options, SELECTORS.defaultSelector());
    }

    private static MockHttpServletResponse run(VPNDetectionFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void enrichesTheRequestAndLeavesTheDecisionToTheApp() throws Exception {
        Core<HttpServletRequest> core = coreServing("{\"is_vpn\":true}", PUBLIC_IP);
        VPNDetectionFilter filter = new VPNDetectionFilter(core, VPNDetectionFilter::refuse);
        MockHttpServletRequest request = request();

        assertEquals(200, run(filter, request).getStatus());
        Lookup lookup = VPNDetectionFilter.lookup(request).orElseThrow();
        assertTrue(lookup.result().orElseThrow().isVpn());
        assertEquals(PUBLIC_IP, lookup.ip().orElseThrow());
    }

    @Test
    void blocksWhenTheConditionMatchesAndTheChainNeverRuns() throws Exception {
        Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                .client(StubClients.serving("{\"is_vpn\":true}", PUBLIC_IP))
                .ipSelector(request -> PUBLIC_IP)
                .blockCondition(Map.of("is_vpn", true));
        VPNDetectionFilter filter = new VPNDetectionFilter(
                new Core<>(options, SELECTORS.defaultSelector()), VPNDetectionFilter::refuse);

        AtomicBoolean reached = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                reached.set(true);
            }
        };
        filter.doFilter(request(), response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("{\"error\":\"access denied\"}", response.getContentAsString());
        assertFalse(reached.get(), "the chain answered a blocked request");
    }

    @Test
    void aPrivateClientAddressIsAnsweredLocallyAndNeverBlocks() throws Exception {
        Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                .client(StubClients.serving("{\"is_vpn\":true}", PUBLIC_IP))
                .blockCondition(Map.of("is_vpn", true))
                .onWarn(message -> { });
        VPNDetectionFilter filter = new VPNDetectionFilter(
                new Core<>(options, SELECTORS.defaultSelector()), VPNDetectionFilter::refuse);
        MockHttpServletRequest request = request();

        assertEquals(200, run(filter, request).getStatus(),
                "local development must not lock you out of your own app");
        assertTrue(VPNDetectionFilter.lookup(request).orElseThrow().result().orElseThrow().isBogon());
    }

    // The servlet API has no trusted-proxy setting, so the default really is the socket peer and
    // a forwarded header is a forgery until something upstream rewrote getRemoteAddr.
    @Test
    void aForgedXForwardedForIsIgnoredByDefault() throws Exception {
        Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                .client(StubClients.serving("{\"is_vpn\":true}", PUBLIC_IP))
                .onWarn(message -> { });
        VPNDetectionFilter filter = new VPNDetectionFilter(
                new Core<>(options, SELECTORS.defaultSelector()), VPNDetectionFilter::refuse);

        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", PUBLIC_IP);
        run(filter, request);
        assertEquals("127.0.0.1", VPNDetectionFilter.lookup(request).orElseThrow().ip().orElseThrow());

        Options<HttpServletRequest> viaHeader = new Options<HttpServletRequest>()
                .client(StubClients.serving("{\"is_vpn\":true}", PUBLIC_IP))
                .ipSelector(SELECTORS.forwardedFor(0));
        VPNDetectionFilter explicit = new VPNDetectionFilter(
                new Core<>(viaHeader, SELECTORS.defaultSelector()), VPNDetectionFilter::refuse);
        MockHttpServletRequest forged = request();
        forged.addHeader("X-Forwarded-For", PUBLIC_IP);
        run(explicit, forged);
        assertEquals(PUBLIC_IP, VPNDetectionFilter.lookup(forged).orElseThrow().ip().orElseThrow());
    }

    @Test
    void skipLeavesTheRequestUntouched() throws Exception {
        Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                .client(StubClients.serving("{\"is_vpn\":true}", PUBLIC_IP))
                .ipSelector(request -> PUBLIC_IP)
                .blockCondition(Map.of("is_vpn", true))
                .skip(request -> true);
        VPNDetectionFilter filter = new VPNDetectionFilter(
                new Core<>(options, SELECTORS.defaultSelector()), VPNDetectionFilter::refuse);
        MockHttpServletRequest request = request();

        assertEquals(200, run(filter, request).getStatus());
        assertTrue(VPNDetectionFilter.lookup(request).isEmpty());
    }

    @Test
    void aFailingLookupLetsTheVisitorThrough() throws Exception {
        Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                .client(StubClients.failing())
                .ipSelector(request -> PUBLIC_IP)
                .blockCondition(Map.of("is_vpn", true));
        VPNDetectionFilter filter = new VPNDetectionFilter(
                new Core<>(options, SELECTORS.defaultSelector()), VPNDetectionFilter::refuse);
        MockHttpServletRequest request = request();

        assertEquals(200, run(filter, request).getStatus());
        assertTrue(VPNDetectionFilter.lookup(request).orElseThrow().error().isPresent());
    }

    @Test
    void corpusConditionsThroughTheFilter() throws Exception {
        for (JsonNode c : middleware.get("conditions")) {
            String why = c.get("name").asText() + ": " + c.get("why").asText();
            String ip = c.has("bogon") ? c.get("bogon").asText() : c.get("body").get("ip").asText();
            String body = c.has("bogon") ? "{}" : MAPPER.writeValueAsString(c.get("body"));

            List<String> warnings = new ArrayList<>();
            Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                    .client(StubClients.serving(body, ip))
                    .ipSelector(request -> ip)
                    .blockCondition(toConditions(c.get("condition")))
                    .onWarn(warnings::add);
            VPNDetectionFilter filter = new VPNDetectionFilter(
                    new Core<>(options, SELECTORS.defaultSelector()), VPNDetectionFilter::refuse);

            int status = run(filter, request()).getStatus();
            assertEquals(c.get("expect").get("blocked").asBoolean() ? 403 : 200, status, why);

            long reported = warnings.stream().filter(w -> w.contains("does not include")).count();
            assertEquals(c.get("expect").get("missing").isEmpty() ? 0 : 1, reported, why);
        }
    }

    @Test
    void corpusRefusesAConditionThatConstrainsNothing() {
        for (JsonNode c : middleware.get("invalidConditions")) {
            String why = c.get("name").asText() + ": " + c.get("why").asText();
            assertThrows(IllegalArgumentException.class,
                    () -> Condition.validate(toConditions(c.get("condition"))), why);
        }
    }

    private static List<Map<String, Object>> toConditions(JsonNode raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw.isArray()) {
            raw.forEach(entry -> out.add(toCondition(entry)));
        } else {
            out.add(toCondition(raw));
        }
        return out;
    }

    private static Map<String, Object> toCondition(JsonNode raw) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        raw.fields().forEachRemaining(e -> out.put(e.getKey(), toValue(e.getValue())));
        return out;
    }

    private static Object toValue(JsonNode raw) {
        if (raw.isArray()) {
            List<Object> any = new ArrayList<>();
            raw.forEach(entry -> any.add(toValue(entry)));
            return any;
        }
        if (raw.isObject()) {
            if (isBound(raw)) {
                Bound bound = null;
                var names = raw.fieldNames();
                while (names.hasNext()) {
                    String key = names.next();
                    double value = raw.get(key).asDouble();
                    bound = switch (key) {
                        case "gte" -> bound == null ? Bound.gte(value) : bound.andGte(value);
                        case "gt" -> bound == null ? Bound.gt(value) : bound.andGt(value);
                        case "lte" -> bound == null ? Bound.lte(value) : bound.andLte(value);
                        default -> bound == null ? Bound.lt(value) : bound.andLt(value);
                    };
                }
                return bound;
            }
            return toCondition(raw);
        }
        if (raw.isBoolean()) {
            return raw.asBoolean();
        }
        if (raw.isNumber()) {
            return raw.numberValue();
        }
        if (raw.isNull()) {
            return null;
        }
        return raw.asText();
    }

    private static boolean isBound(JsonNode raw) {
        if (raw.isEmpty()) {
            return false;
        }
        var names = raw.fieldNames();
        while (names.hasNext()) {
            if (!BOUND_KEYS.contains(names.next())) {
                return false;
            }
        }
        return true;
    }
}
