package io.vpndetection.spring;

import io.vpndetection.middleware.Core;
import io.vpndetection.middleware.Lookup;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Classifies the visitor and optionally refuses the request.
 *
 * <p>A {@link OncePerRequestFilter}, so a forward or an error dispatch does not classify the same
 * visitor twice - which would double the cost of every page that renders an error view.
 *
 * <p>Without a block condition this only enriches the request: the answer is on the
 * {@code vpndetection} request attribute, read with {@link #lookup(HttpServletRequest)}.
 */
public class VPNDetectionFilter extends OncePerRequestFilter {
    /** The request attribute the answer is stored on. */
    public static final String ATTRIBUTE = "vpndetection";

    private final Core<HttpServletRequest> core;
    private final Blocked blocked;

    /** How a blocked request is answered. */
    @FunctionalInterface
    public interface Blocked {
        void handle(HttpServletRequest request, HttpServletResponse response, Lookup lookup)
                throws IOException;
    }

    public VPNDetectionFilter(Core<HttpServletRequest> core, Blocked blocked) {
        this.core = core;
        this.blocked = blocked;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Lookup lookup = core.evaluate(request);
        if (lookup == null) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(ATTRIBUTE, lookup);
        if (lookup.isBlocked()) {
            blocked.handle(request, response, lookup);
            return;
        }
        chain.doFilter(request, response);
    }

    /** What the filter found out about this visitor, or empty when it did not run. */
    public static Optional<Lookup> lookup(HttpServletRequest request) {
        Object found = request.getAttribute(ATTRIBUTE);
        return found instanceof Lookup value ? Optional.of(value) : Optional.empty();
    }

    static void refuse(
            HttpServletRequest request, HttpServletResponse response, Lookup lookup)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"access denied\"}");
    }
}
