package com.onehostel.authgtwy.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.onehostel.common.constants.ServiceConstants;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * The permit-all list the gateway checks on every request. Starts with the built-in entries, so
 * the gateway works even before (or without) the database; PublicEndpointSeeder then loads the
 * stored list, and {@link #refresh()} reloads it at runtime (PublicEndpointController).
 *
 * The list is swapped atomically: a request sees either the old or the new list, never a mix.
 */
@Slf4j
@Component
public class PublicEndpointRegistry {

    /** Built in and always public: the gateway cannot work without them. */
    public static final List<PublicEndpoint> BUILT_IN = Stream.concat(
            Stream.of(ServiceConstants.AUTH_PUBLIC_PATHS)
                    .map(path -> new PublicEndpoint(path, "POST", "Authentication", true)),
            Stream.concat(
                    Stream.of(new PublicEndpoint(ServiceConstants.HEALTH_PATH, "GET", "Health check", true)),
                    Stream.of(ServiceConstants.SWAGGER_PATHS)
                            .map(path -> new PublicEndpoint(path, "GET", "API documentation", true))))
            .toList();

    private record Rule(PathPattern pattern, String method) {
    }

    private final PublicEndpointRepository repository;
    private final PathPatternParser parser = new PathPatternParser();
    private volatile List<Rule> rules;

    public PublicEndpointRegistry(PublicEndpointRepository repository) {
        this.repository = repository;
        this.rules = compile(BUILT_IN);
    }

    public boolean isPublic(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        String method = request.getMethod();
        for (Rule rule : rules) {
            if ((rule.method() == null || rule.method().equalsIgnoreCase(method)) && rule.pattern().matches(path)) {
                return true;
            }
        }
        return false;
    }

    /** Reloads the enabled entries from the database; built-in entries always stay. */
    public int refresh() {
        List<PublicEndpoint> stored = repository.findByEnabledTrue();
        List<PublicEndpoint> all = new ArrayList<>(BUILT_IN);
        all.addAll(stored);
        this.rules = compile(all);
        log.info("Public endpoint list loaded: {} entries ({} from the database)", rules.size(), stored.size());
        return rules.size();
    }

    public List<String> describe() {
        return rules.stream().map(rule -> (rule.method() == null ? "*" : rule.method()) + " "
                + rule.pattern().getPatternString()).toList();
    }

    private List<Rule> compile(List<PublicEndpoint> endpoints) {
        List<Rule> compiled = new ArrayList<>();
        for (PublicEndpoint endpoint : endpoints) {
            try {
                compiled.add(new Rule(parser.parse(endpoint.getPattern()), blankToNull(endpoint.getMethod())));
            } catch (RuntimeException ex) {
                log.warn("Ignoring invalid public endpoint pattern '{}': {}", endpoint.getPattern(), ex.getMessage());
            }
        }
        return List.copyOf(compiled);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
