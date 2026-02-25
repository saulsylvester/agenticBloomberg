package presentation;

import analysis.CausalPropagationEngine;
import analysis.ExplanationSynthesizer;
import analysis.HeliosAnalyzer;
import analysis.PortfolioView;
import analysis.RecommendationEngine;
import analysis.RuleBasedEventClassifier;
import analysis.TradeExecutionResult;
import analysis.TradeRecommendation;
import analysis.TradeTicket;
import analysis.TradingLedger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ingestion.BbcBusinessScraper;
import ingestion.NewsStory;
import ingestion.StoryDetail;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import knowledge.CausalGraph;
import knowledge.CausalGraphLoader;

public final class HeliosWebServer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BbcBusinessScraper scraper;
    private final RecommendationEngine recommendationEngine;
    private final TradingLedger tradingLedger;
    private final Map<String, NewsStory> storyCache = new ConcurrentHashMap<>();
    private final Map<String, StoryInsights> storyInsightCache =
        new ConcurrentHashMap<>();
    private final int port;

    private HttpServer server;

    public HeliosWebServer(int port) {
        this.port = port;

        CausalGraph graph = CausalGraphLoader.loadFromResource(
            "/causal_graph.json"
        );
        RuleBasedEventClassifier classifier =
            RuleBasedEventClassifier.fromResources(
                graph,
                "/entity_aliases.json"
            );
        CausalPropagationEngine propagationEngine = new CausalPropagationEngine(
            graph
        );
        ExplanationSynthesizer synthesizer = new ExplanationSynthesizer();
        HeliosAnalyzer analyzer = new HeliosAnalyzer(
            classifier,
            propagationEngine,
            synthesizer
        );

        this.scraper = new BbcBusinessScraper();
        this.recommendationEngine = new RecommendationEngine(analyzer);
        this.tradingLedger = new TradingLedger(100_000.0);
    }

    public static void start(int port) {
        try {
            HeliosWebServer app = new HeliosWebServer(port);
            app.start();
        } catch (BindException exception) {
            throw new IllegalStateException(
                "Port " +
                    port +
                    " is already in use. Try: ./helios serve " +
                    (port + 1),
                exception
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to start web server",
                exception
            );
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/", exchange ->
            serveResource(
                exchange,
                "/web/index.html",
                "text/html; charset=utf-8"
            )
        );
        server.createContext("/styles.css", exchange ->
            serveResource(
                exchange,
                "/web/styles.css",
                "text/css; charset=utf-8"
            )
        );
        server.createContext("/app.js", exchange ->
            serveResource(
                exchange,
                "/web/app.js",
                "application/javascript; charset=utf-8"
            )
        );
        server.createContext("/favicon.ico", exchange ->
            sendStatus(exchange, 204)
        );

        server.createContext("/api/stories", this::handleStories);
        server.createContext("/api/story", this::handleStory);
        server.createContext("/api/portfolio", this::handlePortfolio);
        server.createContext("/api/trades", this::handleTrades);

        server.start();
        System.out.println(
            "Helios Terminal running at http://localhost:" + port
        );
    }

    private void handleStories(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        List<NewsStory> stories = scraper.fetchLatestStories();
        storyCache.clear();
        storyInsightCache.clear();
        for (NewsStory story : stories) {
            storyCache.put(story.id(), story);
        }

        sendJson(exchange, 200, stories);
    }

    private void handleStory(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Map<String, String> params = parseQueryParams(exchange.getRequestURI());
        String id = params.get("id");
        if (id == null || id.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "Story id is required"));
            return;
        }

        StoryInsights cached = storyInsightCache.get(id);
        if (cached != null) {
            sendJson(exchange, 200, cached);
            return;
        }

        NewsStory story = storyCache.get(id);
        if (story == null) {
            List<NewsStory> stories = scraper.fetchLatestStories();
            storyCache.clear();
            for (NewsStory item : stories) {
                storyCache.put(item.id(), item);
            }
            story = storyCache.get(id);
        }

        if (story == null) {
            sendJson(exchange, 404, Map.of("error", "Story not found"));
            return;
        }

        StoryDetail detail = scraper.fetchStoryDetail(story);
        List<TradeRecommendation> recommendations =
            recommendationEngine.recommend(
                detail.title() + "\n" + detail.body(),
                detail.suggestedSymbol()
            );
        StoryInsights insights = new StoryInsights(detail, recommendations);
        storyInsightCache.put(id, insights);
        sendJson(exchange, 200, insights);
    }

    private void handlePortfolio(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        PortfolioView portfolio = tradingLedger.snapshot();
        sendJson(exchange, 200, portfolio);
    }

    private void handleTrades(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try {
            TradeTicket ticket = parseJsonBody(exchange, TradeTicket.class);
            TradeExecutionResult result = tradingLedger.execute(ticket);
            sendJson(exchange, 200, result);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        }
    }

    private <T> T parseJsonBody(HttpExchange exchange, Class<T> type)
        throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            return objectMapper.readValue(body, type);
        }
    }

    private void serveResource(
        HttpExchange exchange,
        String resourcePath,
        String contentType
    ) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        try (
            InputStream inputStream = HeliosWebServer.class.getResourceAsStream(
                resourcePath
            )
        ) {
            if (inputStream == null) {
                sendStatus(exchange, 404);
                return;
            }

            byte[] payload = inputStream.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange
                .getResponseHeaders()
                .set("Cache-Control", "no-store, no-cache, must-revalidate");
            exchange
                .getResponseHeaders()
                .set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
        } finally {
            exchange.close();
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object payload)
        throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        exchange
            .getResponseHeaders()
            .set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void sendStatus(HttpExchange exchange, int statusCode)
        throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
        exchange.close();
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        String query = uri.getRawQuery();
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] segments = pair.split("=", 2);
            String key = urlDecode(segments[0]);
            String value = segments.length == 2 ? urlDecode(segments[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(
            Objects.requireNonNullElse(value, ""),
            StandardCharsets.UTF_8
        );
    }

    private record StoryInsights(
        StoryDetail story,
        List<TradeRecommendation> recommendations
    ) {}
}
