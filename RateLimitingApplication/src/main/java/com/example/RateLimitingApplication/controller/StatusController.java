package com.example.RateLimitingApplication.controller;

import com.example.RateLimitingApplication.service.RateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

// Controller for health check and rate limit status endpoints.
// These endpoints are not routed through the gateway filter.
// Note: Spring Cloud Gateway is reactive, so this uses reactive types.

@RestController
@RequestMapping("/gateway")
public class StatusController {

    private final RateLimiterService rateLimiterService;

    public StatusController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/health")      // Health check endpoint
    public Mono<ResponseEntity<Map<String, Object>>> health() {

        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rate-limiting-gateway")));
    }

    @GetMapping("/rate-limit/status")       // Endpoint to check rate limit status for a client
    public Mono<ResponseEntity<Map<String, Object>>> getRateLimitStatus (ServerWebExchange exchange) {
        String clientId = getClientId(exchange);
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rate-limiting-gateway",
                "clientId", clientId,
                "capacity", rateLimiterService.getCapacity(clientId),
                "availableTokens", rateLimiterService.getAvailableTokens(clientId))));
    }

    private String getClientId(ServerWebExchange exchange) {        // Extracts client (IP address) identifier from the request
        ServerHttpRequest request = exchange.getRequest();
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

}

/*
1. The /health Endpoint
Yeh kya hai: Yeh ek standard practice hai jise Health Check kehte hain.
AWS, Kubernetes, ya koi bhi Load Balancer baar-baar is URL (/health) par request bhej kar check karta hai ki
server zinda ("UP") hai ya crash ho gaya hai.

Response: Yeh simple JSON return karega: {"status": "UP", "service": "rate-limiting-gateway"}.

Mono.just(...) kya hai? Kyunki Gateway "Reactive" (WebFlux) par chalta hai, hum direct data return nahi kar sakte.
Mono ek box hai. Mono.just() ka matlab hai: "Mera data ready hai, isko box mein daalo aur bina server ko block kiye user ko bhej do."

2. The /rate-limit/status Endpoint
Yeh exchange object se user ka IP nikalta hai (getClientId).
Phir Redis service se poochta hai: "Is IP ki total capacity kya hai? Aur abhi kitne tokens bache hain?"
 */
