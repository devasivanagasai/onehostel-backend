package com.onehostel.common.filter;

import com.onehostel.common.helper.CorrelationContext;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.f4b6a3.ulid.UlidCreator;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Takes X-Correlation-Id from the request (the gateway always sets one) or creates one, puts it in the
 * MDC for the whole request and returns it as a response header. Order HIGHEST_PRECEDENCE + 2.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Accept only ids that are safe to log and echo back. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String incoming = request.getHeader(CorrelationContext.HEADER);
        String correlationId = incoming != null && VALID_ID.matcher(incoming).matches()
                ? incoming
                : UlidCreator.getMonotonicUlid().toString();

        response.setHeader(CorrelationContext.HEADER, correlationId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationContext.MDC_KEY, correlationId)) {
            chain.doFilter(request, response);
        }
    }
}
