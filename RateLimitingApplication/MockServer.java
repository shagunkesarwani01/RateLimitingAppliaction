import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class MockServer {

    public static void main(String[] args) throws IOException {
        int port = 8081;

        // Create the server binding to localhost:8081
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);

        // Map all incoming requests (the root path "/") to our custom handler
        server.createContext("/", new MockHandler());

        // Use the default system executor
        server.setExecutor(null);

        System.out.println("Starting mock server on http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop");

        server.start();
    }

    static class MockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // Custom logging mirroring your Python script
            System.out.printf("[%s] %s %s\n",
                    exchange.getRemoteAddress().getAddress().getHostAddress(),
                    method, path);

            // Using modern Java Text Blocks for clean JSON formatting
            String jsonResponse = """
                    {
                        "message": "Request successful",
                        "path": "%s",
                        "status": "ok"
                    }
                    """.formatted(path);

            byte[] responseBytes = jsonResponse.getBytes();

            // Send 200 OK and application/json header
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            // Write the response body and close the stream automatically
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}