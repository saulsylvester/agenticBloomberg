package util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JsonlStore {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public void append(Path outputFile, Object payload) {
        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            String jsonLine = OBJECT_MAPPER.writeValueAsString(payload) + System.lineSeparator();
            Files.writeString(
                outputFile,
                jsonLine,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to append JSONL record to " + outputFile, exception);
        }
    }
}
