package org.serwin.auth_server.service;

import java.io.InputStream;
import java.util.Map;

import org.serwin.auth_server.dto.DocResponse;
import org.serwin.auth_server.entities.DocType;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DocsService {

    // private static final String PUBLIC_PATH = "docs/internal/";
    private static final String PUBLIC_PATH = "docs/public/";
    private static final String INTERNAL_PATH = "docs/internal/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================
    // MANIFEST LOADER
    // =========================
    public Object getManifest(DocType type) {
        String path = resolvePath(type) + "manifest.json";

        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(path)) {

            if (is == null) {
                throw new RuntimeException("Manifest not found: " + type);
            }

            return objectMapper.readValue(is, Object.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load manifest for: " + type, e);
        }
    }

    // =========================
    // SINGLE DOC LOADER
    // =========================
    public DocResponse getDoc(DocType type, String slug) {
        String path = resolvePath(type) + slug + ".md";

        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(path)) {

            if (is == null) {
                throw new RuntimeException("Doc not found: " + slug + " (" + type + ")");
            }

            String raw = new String(is.readAllBytes());

            return parseMarkdown(raw);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load doc: " + slug, e);
        }
    }

    // =========================
    // PATH RESOLVER
    // =========================
    private String resolvePath(DocType type) {
        return switch (type) {
            case PUBLIC -> PUBLIC_PATH;
            case INTERNAL -> INTERNAL_PATH;
        };
    }

    // =========================
    // MARKDOWN PARSER
    // =========================
    private DocResponse parseMarkdown(String raw) {
        if (!raw.startsWith("---")) {
            return new DocResponse(Map.of(), raw);
        }

        String[] parts = raw.split("---", 3);

        String metaRaw = parts.length > 1 ? parts[1] : "";
        String content = parts.length > 2 ? parts[2] : "";

        Map<String, Object> metadata = parseYaml(metaRaw);

        return new DocResponse(metadata, content.trim());
    }

    // =========================
    // YAML PARSER
    // =========================
    private Map<String, Object> parseYaml(String yaml) {
        Yaml parser = new Yaml();
        return parser.load(yaml);
    }
}