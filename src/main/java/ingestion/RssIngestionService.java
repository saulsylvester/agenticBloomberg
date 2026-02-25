package ingestion;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import util.JsonlStore;

public final class RssIngestionService {
    private final JsonlStore jsonlStore;

    public RssIngestionService(JsonlStore jsonlStore) {
        this.jsonlStore = jsonlStore;
    }

    public List<RawEventRecord> poll(List<URI> rssSources) {
        List<RawEventRecord> records = new ArrayList<>();
        for (URI source : rssSources) {
            records.addAll(fetchSource(source));
        }
        return records;
    }

    private List<RawEventRecord> fetchSource(URI source) {
        List<RawEventRecord> records = new ArrayList<>();
        try (InputStream inputStream = source.toURL().openStream()) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            NodeList items = document.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String title = text(item, "title");
                String description = text(item, "description");
                records.add(new RawEventRecord(source.toString(), title, title + "\n" + description, Instant.now()));
            }
            return records;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch RSS source: " + source, exception);
        }
    }

    public void persist(java.nio.file.Path outputPath, List<RawEventRecord> records) {
        for (RawEventRecord record : records) {
            jsonlStore.append(outputPath, record);
        }
    }

    private static String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }
}
