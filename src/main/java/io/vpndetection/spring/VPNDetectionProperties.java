package io.vpndetection.spring;

import io.vpndetection.middleware.Options;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Everything the filter reads from {@code application.yml}, under {@code vpndetection}. */
@ConfigurationProperties(prefix = "vpndetection")
public class VPNDetectionProperties {
    /**
     * Your API key. The free allowance is counted per source address and a server is one source
     * address, so this is what makes the filter usable in production.
     */
    private String apiKey;

    private String baseUrl;

    /**
     * How long a lookup may hold the request. Defaults to 2500ms, a much tighter bound than the
     * client's own: on a request path, failing open quickly beats holding a visitor.
     */
    private Duration timeout = Options.DEFAULT_TIMEOUT;

    /** Retry attempts for a transient failure. Defaults to 0, unlike the client's 2. */
    private int retries = Options.DEFAULT_RETRIES;

    /**
     * What to block on, in the shape of a served answer. Leave it unset to only enrich the request
     * and decide in your own controllers. A list means OR.
     */
    private List<Map<String, Object>> blockCondition;

    /** Block when the lookup itself fails. Our outage should not become yours. */
    private boolean failClosed;

    /** One of warn, throw or ignore. */
    private Options.OnMissingField onMissingField = Options.OnMissingField.WARN;

    /**
     * A header your edge writes the client address into - CF-Connecting-IP behind Cloudflare.
     *
     * <p>Unset uses {@code HttpServletRequest#getRemoteAddr}, the socket peer. A callable cannot
     * live in YAML, so anything more exotic means defining your own
     * {@code Function<HttpServletRequest, String>} bean, which the auto-configuration backs off to.
     */
    private String clientIpHeader;

    /** Paths the filter never runs for, as Ant patterns. */
    private List<String> skipPaths = List.of();

    /** Turn the filter off without removing the dependency. */
    private boolean enabled = true;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public List<Map<String, Object>> getBlockCondition() {
        return blockCondition;
    }

    public void setBlockCondition(List<Map<String, Object>> blockCondition) {
        this.blockCondition = blockCondition;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public Options.OnMissingField getOnMissingField() {
        return onMissingField;
    }

    public void setOnMissingField(Options.OnMissingField onMissingField) {
        this.onMissingField = onMissingField;
    }

    public String getClientIpHeader() {
        return clientIpHeader;
    }

    public void setClientIpHeader(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    public List<String> getSkipPaths() {
        return skipPaths;
    }

    public void setSkipPaths(List<String> skipPaths) {
        this.skipPaths = skipPaths;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
