package com.example.RateLimitingApplication.config;

// This class defines how spring cloud gateway routes the incoming requests
// It basically maps the client request to the backend service and then it applies filters
// route is a mapping from a request pattern to a destination service

import com.example.RateLimitingApplication.filter.TokenBucketRateLimitingFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final RateLimiterProperties rateLimiterProperties;
    private final TokenBucketRateLimitingFilter tokenBucketRateLimitingFilter;

    public GatewayConfig (RateLimiterProperties rateLimiterProperties, TokenBucketRateLimitingFilter tokenBucketRateLimitingFilter) {   // dependency injection in this class through constructor
        this.rateLimiterProperties = rateLimiterProperties;     // Properties contains target URL
        this.tokenBucketRateLimitingFilter = tokenBucketRateLimitingFilter;     // Filter checks rate limit
    }

    @Bean
    public RouteLocator customRouteLocator (RouteLocatorBuilder builder) {      // RouteLocatorBuilder is like a fluent API for building routes
        // It provides methods like .routes(), .route(), .build()
        return builder.routes()
                .route("api-route", r -> r
                        .path("/api/**")
                        .filters(f -> f                                      // filters are executed inorder, before and after forwarding the request
                                .stripPrefix(1)                        // removes /api prefix from the request
                                .filter(tokenBucketRateLimitingFilter.apply(new TokenBucketRateLimitingFilter.Config()))
                        )
                        .uri(rateLimiterProperties.getApiServerUrl()))      // the gateway forwards the match request to backend API server (request redirection)
                .build();
    }
}

/*
customRouteLocator: Yeh ek "Route" (Rasta) bana raha hai.

1. builder.routes().route("api-route", ...)
Hum ek naya rasta bana rahe hain aur uska naam "api-route" rakh rahe hain.

2. .path("/api/**") (The Condition/Predicate)
Yeh rule kehta hai: "Agar koi bhi user aisi URL par request bhejta hai jo /api/ se shuru hoti hai
(jaise /api/users/123 ya /api/products), toh hi is raste par aane do."

3. .filters(f -> ...) (The Checkposts)
Jab request is raste par aati hai, toh hum uspar 2 operations perform karte hain:

        .stripPrefix(1): Yeh bohot smart tool hai! Agar request /api/users/123 thi, toh yeh usme se pehla hissa (/api) kaat dega.
        Yaani aapke actual backend server ke paas request sirf /users/123 bankar jayegi.
        (Kyunki backend server ko /api se matlab nahi hota, use sirf /users endpoint chahiye).

        .filter(tokenBucketRateLimitingFilter.apply(...)): Yahan humne apne "Security Guard" ko khada kar diya hai!
        Backend tak request pahunchne se thik pehle, hamara Redis wala logic chalega aur check karega ki user ke paas
        tokens bache hain ya nahi. Agar nahi bache, toh request yahin se wapas (429 Error) chali jayegi.

4. .uri(rateLimiterProperties.getApiServerUrl()) (The Destination)
Agar user ke paas tokens bache hain, toh finally request kahan jayegi?
Yeh us target URL par jayegi jo aapne RateLimiterProperties mein define ki thi (jaise http://localhost:8080 ya aapki cloud backend URL).
 */