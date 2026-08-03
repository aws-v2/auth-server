package org.serwin.auth_server.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.serwin.auth_server.dto.docs_dto.DocCategory;
import org.serwin.auth_server.dto.docs_dto.DocManifest;
import org.serwin.auth_server.dto.docs_dto.DocResponse;
import org.serwin.auth_server.dto.docs_dto.Metadata;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class DocsService {

    // NOTE: the original had PUBLIC_PATH pointing at "docs/internal/" —
    // that looked like a leftover from testing, fixed here.
    private static final String PUBLIC_PATH = "docs/public/";
    private static final String INTERNAL_PATH = "docs/internal/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================
    // MANIFEST LOADER
    // =========================
    // Mirrors Go's GetManifest(internal bool): reads manifest.json which has
    // {service, version, categories} on disk, and reshapes it into
    // {service, apiVersion, scope, internal | public}.
    public DocManifest getManifest(boolean internal) {
        String scope = scopeFor(internal);
        String path = resolvePath(internal) + "manifest.json";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {

            if (is == null) {
                throw new RuntimeException("manifest not found at " + path + " — check docs folder structure");
            }

            RawManifest raw = objectMapper.readValue(is, RawManifest.class);

            DocManifest manifest = new DocManifest();
            manifest.setService(raw.service);
            manifest.setApiVersion(raw.version);
            manifest.setScope(scope);

            if (internal) {
                manifest.setInternal(raw.categories);
            } else {
                manifest.setPublicCategories(raw.categories);
            }

            return manifest;

        } catch (Exception e) {
            throw new RuntimeException("failed to load manifest for scope: " + scope+" error; "+e.getMessage(), e);
        }
    }

    // =========================
    // SINGLE DOC LOADER
    // =========================
    // Mirrors Go's GetDoc(slug, internal bool): validates the slug, reads the
    // markdown file, and parses frontmatter into Metadata + content.
    public DocResponse getDoc(String slug, boolean internal) {
        if (!isValidSlug(slug)) {
            throw new IllegalArgumentException("invalid slug");
        }

        String path = resolvePath(internal) + slug + ".md";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {

            if (is == null) {
                throw new RuntimeException("not found");
            }

            String raw = new String(is.readAllBytes());

            return parseMarkdown(raw);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("not found", e);
        }
    }

    // =========================
    // HELPERS
    // =========================
    private String scopeFor(boolean internal) {
        return internal ? "internal" : "public";
    }

    private String resolvePath(boolean internal) {
        return internal ? INTERNAL_PATH : PUBLIC_PATH;
    }

    // Prevent path traversal attacks — mirrors Go's isValidSlug
    private boolean isValidSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            return false;
        }
        return !slug.contains("..") && !slug.contains("/") && !slug.contains("\\");
    }

    // =========================
    // MARKDOWN PARSER
    // =========================
    private DocResponse parseMarkdown(String raw) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        if (!raw.startsWith("---")) {
            Metadata meta = new Metadata();
            meta.setLastUpdated(today);
            return new DocResponse(meta, raw.trim());
        }

        String[] parts = raw.split("---", 3);

        String metaRaw = parts.length > 1 ? parts[1] : "";
        String content = parts.length > 2 ? parts[2] : "";

        Metadata meta = parseYaml(metaRaw);
        meta.setLastUpdated(today);

        return new DocResponse(meta, content.trim());
    }

    @SuppressWarnings("unchecked")
    private Metadata parseYaml(String yaml) {
        Yaml parser = new Yaml();
        Map<String, Object> raw = parser.load(yaml);

        Metadata meta = new Metadata();
        if (raw == null) {
            return meta;
        }

        if (raw.get("title") != null) {
            meta.setTitle(String.valueOf(raw.get("title")));
        }
        if (raw.get("description") != null) {
            meta.setDescription(String.valueOf(raw.get("description")));
        }
        if (raw.get("icon") != null) {
            meta.setIcon(String.valueOf(raw.get("icon")));
        }
        if (raw.get("tags") instanceof List) {
            meta.setTags((List<String>) raw.get("tags"));
        }

        return meta;
    }

    // Matches the on-disk manifest.json shape: {service, version, categories}
    private static class RawManifest {
        public String service;
        public String version;
        public String type;
        public String description;
        public String visibility;
        public String audience;
        public List<DocCategory> categories;
    }
}