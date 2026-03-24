import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RateLimiterTest {

    // ANSI Colors for terminal output
    private static final String GREEN = "\033[0;32m";
    private static final String RED = "\033[0;31m";
    private static final String YELLOW = "\033[1;33m";
    private static final String NC = "\033[0m"; // No Color

    public static void main(String[] args) {
        System.out.println("=== Rate Limiter Quick Test ===\n");
        System.out.println("4. Making 12 requests (capacity is 10)...");

        int successCount = 0;
        int blockedCount = 0;

        // Using try-with-resources for the new HttpClient
        try (HttpClient client = HttpClient.newHttpClient()) {

            // Build the request (Make sure the URL matches your Gateway route!)
            HttpRequest testRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/get")) // Updated to match your earlier setup
                    .GET()
                    .build();

            // 1. Make 12 requests
            for (int i = 1; i <= 12; i++) {
                HttpResponse<String> response = client.send(testRequest, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();

                if (statusCode == 200 || statusCode == 502) {
                    // Note: Treating 502 as success if the mock server isn't running
                    System.out.printf("  Request %d: %s✓ Allowed (%d)%s%n", i, GREEN, statusCode, NC);
                    successCount++;
                } else if (statusCode == 429) {
                    System.out.printf("  Request %d: %s✗ Blocked (429)%s%n", i, RED, NC);
                    blockedCount++;
                } else {
                    System.out.printf("  Request %d: %s? Status (%d)%s%n", i, YELLOW, statusCode, NC);
                }
            }
            System.out.println();

            // 2. Final Status Check
            System.out.println("5. Final Rate Limit Status:");
            HttpRequest statusReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/gateway/rate-limit/status"))
                    .GET()
                    .build();

            String finalTokens = "unknown";
            try {
                HttpResponse<String> statusResp = client.send(statusReq, HttpResponse.BodyHandlers.ofString());
                String finalStatus = statusResp.body();
                System.out.println(finalStatus.isEmpty() ? "No status endpoint configured" : finalStatus);

                // Simulate 'jq' by extracting availableTokens using a simple Regex
                Matcher matcher = Pattern.compile("\"availableTokens\"\\s*:\\s*(\\d+)").matcher(finalStatus);
                if (matcher.find()) {
                    finalTokens = matcher.group(1);
                }
            } catch (Exception e) {
                System.out.println("Status endpoint not available or failed: " + e.getMessage());
            }
            System.out.println();

            // 3. Summary
            System.out.println("=== Test Summary ===");
            System.out.println("Successful requests: " + successCount);
            System.out.println("Blocked requests: " + blockedCount);
            // Note: initial_tokens was in your bash script but not initialized, keeping it unknown
            System.out.println("Initial tokens: unknown");
            System.out.println("Final tokens: " + finalTokens);
            System.out.println();

            // 4. Verification
            if (successCount == 10 && blockedCount == 2) {
                System.out.printf("%s✓ Test PASSED! Rate limiting is working correctly.%s%n", GREEN, NC);
            } else {
                System.out.printf("%s⚠ Test results unexpected. Check configuration.%s%n", YELLOW, NC);
            }

        } catch (Exception e) {
            System.err.println("Test failed to execute: " + e.getMessage());
        }
    }
}