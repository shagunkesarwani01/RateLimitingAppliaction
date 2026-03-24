package com.example.RateLimitingApplication.filter;

import com.example.RateLimitingApplication.service.RateLimiterService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

// Client Request -> GatewayFilter (intercepts request) -> Check Rate Limit
// if rate limit is allowed, forward the request to the API Server
// if it's not allowed, we block and return error 429 HTTP status code

// Global Filters -> applied to all routes
// Route Filters -> applied to specific routes
// Custom Filters -> your own implementation of filter

@Component
public class TokenBucketRateLimitingFilter extends AbstractGatewayFilterFactory<TokenBucketRateLimitingFilter.Config> {

    private final RateLimiterService rateLimiterService;

    public TokenBucketRateLimitingFilter(RateLimiterService rateLimiterService) {       // 1. Constructor
        super(Config.class);
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public GatewayFilter apply (Config config) {        // 2. gets executed on each HTTP request

        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String clientId = getClientId(request);     // First identify the user

            if(!rateLimiterService.isAllowed(clientId)) {       // if isAllowed() is false
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);       // then, set 429
                addRateLimitHeaders(response, clientId);

                String errorBody = String.format(       // constructs a JSON body for error message
                        "{\"error\":\"Rate limit exceeded\",\"clientId\":\"%s\"}",
                        clientId
                );
                return response.writeWith(      // sends the error message directly to the user (without conveying to the backend)
                        Mono.just(response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8)))
                );
            }

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {        // if request is allowed, then chain.filter() is called
                addRateLimitHeaders(response, clientId);        // when actual server will respond, then add this Rate Limiter Headers to identify the remaining tokens
            }));
        };
    }

    private void addRateLimitHeaders(ServerHttpResponse response, String clientId) {        // 3. This method adds total capacity and remaining tokens in the response
        response.getHeaders().add("X-RateLimit-Limit",
                String.valueOf(rateLimiterService.getCapacity(clientId)));
        response.getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(rateLimiterService.getAvailableTokens(clientId)));
    }

    public static class Config {}

    public String getClientId (ServerHttpRequest request) {     // 4. Smart IP Detection: getClientId, IP Address is used as the client identifier

        String xForwardFor = request.getHeaders().getFirst("X-Forwarded-For");      // first we check the X-ForwardedFor header for proxies and load balancers, then take the first IP
        if (xForwardFor != null && !xForwardFor.isEmpty()) {
            return xForwardFor.split(",")[0].trim();
        }

        // Fallback to direct connection IP Address if no load balancer or proxy is there
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getHostName() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";       // Default Fallback
    }

}

/*
The X-Forwarded-For (XFF) header is a standard HTTP header used to identify the real, originating IP address of a client connecting through a proxy or load balancer.
Without it, servers only see the proxy's IP.
It records client and proxy IPs in a comma-separated list: X-Forwarded-For: client, proxy1, proxy2.

Purpose: Essential for logging the true client IP, geolocation-based routing, rate limiting, and security auditing.

Structure: It follows the format X-Forwarded-For: <Client-IP>, <Proxy1-IP>, <Proxy2-IP>. The left-most IP is the original client, followed by intermediate proxies.

Usage in Proxies: When requests pass through multiple proxies, each appends the IP address of the previous hop.

Spoofing Risk: This header is easily forged by clients.
 */

// ================================================================================

/*
4. Smart IP Detection: getClientId
String xForwardFor = request.getHeaders().getFirst("X-Forwarded-For");

Yeh bohot hi smart logic hai!

Kyun zaroori hai: Jab aap apni app ko cloud par deploy karte hain,
toh traffic aksar kisi Load Balancer ya proxy (jaise Nginx, AWS ELB) se hota hua aata hai.
Aise mein getRemoteAddress() hamesha Load Balancer ka IP dega (jisse sabhi users block ho jayenge).

Fix: Yeh method pehle X-Forwarded-For header check karta hai, jo Load Balancer add karta hai original user ka IP batane ke liye.
Agar woh nahi milta, tabhi yeh direct IP use karta hai.
 */

// =========================================================================

/*
Why didn't we use ResponseEntity<?> to directly return HTTPStatusCode 429 ?
Humne yahan itna lamba aur complex code kyun likha?

1. You are building a "Gateway", not a "Controller"
Aap abhi ek normal backend app nahi bana rahe, aap ek Spring Cloud Gateway configure kar rahe hain.

Normal App (Controller): Jab request Controller tak pahunchti hai, tab Spring aapko ResponseEntity use karne ki azaadi deta hai.

Gateway (Filter): Gateway aapke actual backend servers ke aage ek "Main Gate" ki tarah khada hota hai.
Yahan Controller hota hi nahi hai! Kyunki Gateway network ke bohot lower level par kaam karta hai,
isliye humein raw HTTP response (ServerHttpResponse) aur bytes (bufferFactory) ke saath khelna padta hai.

2. Spring Cloud Gateway is "Reactive" (Non-Blocking)
Yeh sabse bada technical reason hai.
Normal Spring Boot Tomcat server par chalta hai, jahan har user ke liye ek naya "Thread" (worker) banta hai.
Agar aap us request ko "block" karte hain, toh sirf woh ek thread rukta hai.

Lekin Spring Cloud Gateway Netty (WebFlux) par chalta hai. Isme hazaron users ko handle karne ke liye sirf mutthi-bhar (a few) threads hote hain.

Agar aapne Gateway mein koi aisi cheez likh di jo thread ko "block" karti hai, toh aapka poora server turant hang (freeze) ho jayega.

Isliye yahan hum Mono aur Flux (Reactive programming) use karte hain. return response.writeWith(Mono.just(...)) server ko batata hai:
"Jab bhi free ho, yeh error message dheere se user ko stream kar dena, main ruka nahi hoon."

3. Separation of Concerns (Microservices Best Practice)
Sochiye aapke paas 5 alag-alag microservices hain (User Service, Product Service, Order Service, etc.).
Agar aap ResponseEntity wala logic use karte, toh aapko woh rate-limiting ka code un paanchon (5) projects mein copy-paste karna padta.

Is badi Filter class ko Gateway mein likhne ka fayda yeh hai ki:
Ab rate limiting ka logic sirf ek jagah (Gateway) par hai.
Aapki baaki 5 microservices bilkul clean aur safe hain. Unhe pata bhi nahi hai ki bahar koi Rate Limiter chal raha hai.
Unhe sirf wahi requests milengi jo genuinely allowed hain.
 */