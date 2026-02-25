package ingestion;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class BbcBusinessScraper {
    private static final Pattern LSE_STOCK_PATTERN = Pattern.compile(
        "https?://www\\.londonstockexchange\\.com/stock/([^/]+)/[^/]+",
        Pattern.CASE_INSENSITIVE
    );

    public static final String BBC_BUSINESS_URL = "https://www.bbc.co.uk/news/business";

    public List<NewsStory> fetchLatestStories() {
        try {
            Document document = Jsoup.connect(BBC_BUSINESS_URL)
                .userAgent("Mozilla/5.0 HeliosDemo/1.0")
                .timeout(12000)
                .get();

            Map<String, NewsStory> deduped = new LinkedHashMap<>();
            Elements links = document.select("a[href]");
            for (Element link : links) {
                String url = normalizeUrl(link.attr("abs:href"));
                if (!isStoryUrl(url) || deduped.containsKey(url)) {
                    continue;
                }

                String title = cleanTitle(link.text());
                if (title.isBlank()) {
                    title = cleanTitle(link.attr("aria-label"));
                }
                if (!isUsefulTitle(title)) {
                    continue;
                }

                String summary = extractSummary(link);
                String id = storyId(url);
                String detectedSymbol = detectSymbolFromText(title + " " + summary);
                deduped.put(
                    url,
                    new NewsStory(
                        id,
                        title,
                        summary,
                        url,
                        Instant.now().toString(),
                        "BBC News",
                        detectedSymbol
                    )
                );

                if (deduped.size() >= 18) {
                    break;
                }
            }

            if (!deduped.isEmpty()) {
                return new ArrayList<>(deduped.values());
            }
        } catch (Exception ignored) {
            // Fall through to deterministic local fallback.
        }

        return fallbackStories();
    }

        public StoryDetail fetchStoryDetail(NewsStory story) {
        try {
            Document document = Jsoup.connect(story.url())
                .userAgent("Mozilla/5.0 HeliosDemo/1.0")
                .timeout(12000)
                .get();

            String headline = cleanTitle(document.select("h1").text());
            if (headline.isBlank()) {
                headline = story.title();
            }

            String publishedAt = document.select("time").stream()
                .map(element -> element.attr("datetime"))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(story.publishedAt());

            List<String> paragraphs = new ArrayList<>();
            Elements primary = document.select("article p, main article p, [data-component=text-block] p");
            if (primary.isEmpty()) {
                primary = document.select("main p");
            }

            for (Element paragraph : primary) {
                String text = paragraph.text().trim();
                if (text.length() < 30) {
                    continue;
                }
                if (text.toLowerCase(Locale.ROOT).startsWith("more on this story")) {
                    continue;
                }
                paragraphs.add(text);
            }

            String body;
            if (paragraphs.isEmpty()) {
                body = "Story content could not be parsed automatically. Open the source URL for the full article.";
            } else {
                body = String.join("\n\n", paragraphs);
            }

            String detectedSymbol = detectSymbolFromDocument(document);
            if (detectedSymbol.isBlank()) {
                detectedSymbol = detectSymbolFromText(story.title() + " " + headline + " " + body);
            }

            return new StoryDetail(
                story.id(),
                headline,
                body,
                story.url(),
                publishedAt,
                story.source(),
                detectedSymbol
            );
        } catch (Exception ignored) {
            return new StoryDetail(
                story.id(),
                story.title(),
                "Live scrape is unavailable right now. This demo still supports recommendations and paper trading against the headline.",
                story.url(),
                story.publishedAt(),
                story.source(),
                normalizeSymbol(story.suggestedSymbol())
            );
        }
    }

    private static String detectSymbolFromDocument(Document document) {
        Elements links = document.select("a[href]");
        for (Element link : links) {
            String href = normalizeUrl(link.attr("abs:href"));
            String symbol = normalizeSymbol(extractLseSymbol(href));
            if (!symbol.isBlank()) {
                return symbol;
            }
        }
        return "";
    }

    private static String detectSymbolFromText(String text) {
        String fallback = text.toLowerCase(Locale.ROOT);

        if (fallback.contains("john lewis")) {
            return "45GD";
        }
        if (fallback.contains("tesco")) {
            return "TSCO.L";
        }
        if (fallback.contains("sainsbury")) {
            return "SBRY.L";
        }
        if (fallback.contains("barclays")) {
            return "BARC.L";
        }
        if (fallback.contains("british american tobacco") || fallback.contains("bat")) {
            return "BATS.L";
        }

        return "";
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static String extractLseSymbol(String href) {
        Matcher matcher = LSE_STOCK_PATTERN.matcher(href);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String normalizeUrl(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        if (href.startsWith("//")) {
            href = "https:" + href;
        }

        int fragmentIndex = href.indexOf('#');
        if (fragmentIndex >= 0) {
            href = href.substring(0, fragmentIndex);
        }
        return href;
    }

    private static boolean isStoryUrl(String url) {
        if (url.isBlank()) {
            return false;
        }
        if (!url.startsWith("https://www.bbc.co.uk/news/")) {
            return false;
        }
        if (url.contains("/live/")) {
            return false;
        }
        return url.contains("/news/articles/") || url.contains("/news/business-");
    }

    private static boolean isUsefulTitle(String title) {
        if (title.isBlank()) {
            return false;
        }
        if (title.matches("^\\d+$")) {
            return false;
        }
        long letters = title.chars().filter(Character::isLetter).count();
        return letters >= 8;
    }

    private static String cleanTitle(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String extractSummary(Element link) {
        String text = cleanTitle(link.attr("aria-label"));
        if (!text.isBlank() && !text.equalsIgnoreCase(link.text())) {
            return text;
        }

        Element container = link.parent();
        if (container != null) {
            String combined = cleanTitle(container.text());
            if (combined.length() > link.text().length()) {
                return combined;
            }
        }

        return "Open story for details.";
    }

    private static String storyId(String url) {
        return UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static List<NewsStory> fallbackStories() {
        return List.of(
            new NewsStory(
                storyId("fallback:rates"),
                "Bank of England signals rates may stay higher for longer",
                "Fallback sample: rates narrative for demo mode.",
                BBC_BUSINESS_URL,
                Instant.now().toString(),
                "BBC News (fallback)",
                "UK10Y"
            ),
            new NewsStory(
                storyId("fallback:oil"),
                "Oil prices jump amid renewed Middle East tensions",
                "Fallback sample: energy shock story for demo mode.",
                BBC_BUSINESS_URL,
                Instant.now().toString(),
                "BBC News (fallback)",
                "SHEL.L"
            ),
            new NewsStory(
                storyId("fallback:johnlewis"),
                "John Lewis pulls out of housebuilding business",
                "Fallback sample: property-linked weakness for demo mode.",
                BBC_BUSINESS_URL,
                Instant.now().toString(),
                "BBC News (fallback)",
                "45GD"
            )
        );
    }
}
