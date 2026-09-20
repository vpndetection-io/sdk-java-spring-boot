package io.vpndetection.spring;

import io.vpndetection.middleware.Core;
import io.vpndetection.middleware.Options;
import io.vpndetection.middleware.RequestView;
import io.vpndetection.middleware.Selectors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.function.Function;

/**
 * Wires the filter from {@code application.yml}.
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean}, so defining your own selector, core or
 * filter replaces ours rather than colliding with it - which is how anything the YAML cannot
 * express (a callable selector, a custom refusal) is done.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "vpndetection", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(VPNDetectionProperties.class)
public class VPNDetectionAutoConfiguration {
    private static final Selectors<HttpServletRequest> SELECTORS = new Selectors<>(
            request -> new RequestView(request::getHeader, request::getRemoteAddr));

    /**
     * {@code getRemoteAddr}, the socket peer, unless a header is named.
     *
     * <p>The servlet API has no trusted-proxy setting of its own. Spring Boot's
     * {@code server.forward-headers-strategy=native|framework} is the answer for a chain you
     * trust, and it rewrites {@code getRemoteAddr} before this ever runs - so setting it makes
     * this default correct rather than being something to work around.
     */
    @Bean
    @ConditionalOnMissingBean(name = "vpndetectionIpSelector")
    public Function<HttpServletRequest, String> vpndetectionIpSelector(
            VPNDetectionProperties properties) {
        String header = properties.getClientIpHeader();
        return header == null ? SELECTORS.defaultSelector() : SELECTORS.header(header);
    }

    @Bean
    @ConditionalOnMissingBean
    public Core<HttpServletRequest> vpndetectionCore(
            VPNDetectionProperties properties,
            Function<HttpServletRequest, String> vpndetectionIpSelector) {
        Options<HttpServletRequest> options = new Options<HttpServletRequest>()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .timeout(properties.getTimeout())
                .retries(properties.getRetries())
                .failClosed(properties.isFailClosed())
                .onMissingField(properties.getOnMissingField())
                .ipSelector(vpndetectionIpSelector);
        if (properties.getBlockCondition() != null) {
            options.blockCondition(Conditions.typed(properties.getBlockCondition()));
        }
        List<String> skip = properties.getSkipPaths();
        if (!skip.isEmpty()) {
            AntPathMatcher matcher = new AntPathMatcher();
            options.skip(request -> skip.stream()
                    .anyMatch(pattern -> matcher.match(pattern, request.getRequestURI())));
        }
        return new Core<>(options, SELECTORS.defaultSelector());
    }

    @Bean
    @ConditionalOnMissingBean
    public VPNDetectionFilter vpndetectionFilter(Core<HttpServletRequest> vpndetectionCore) {
        return new VPNDetectionFilter(vpndetectionCore, VPNDetectionFilter::refuse);
    }

    /**
     * Registered high, so a blocked request is refused before the dispatcher does any work, but
     * below Spring Security's own chain (which is {@code HIGHEST_PRECEDENCE + 50} by default) so
     * authentication still answers on its own terms first.
     */
    @Bean
    @ConditionalOnMissingBean(name = "vpndetectionFilterRegistration")
    public FilterRegistrationBean<VPNDetectionFilter> vpndetectionFilterRegistration(
            VPNDetectionFilter vpndetectionFilter) {
        FilterRegistrationBean<VPNDetectionFilter> registration =
                new FilterRegistrationBean<>(vpndetectionFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }
}
