package org.serwin.auth_server.controller;


import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.serwin.auth_server.dto.docs_dto.DocCategory;
import org.serwin.auth_server.dto.docs_dto.DocManifest;
import org.serwin.auth_server.dto.docs_dto.DocResponse;
import org.serwin.auth_server.service.DocsService;
import org.serwin.auth_server.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/docs")
public class PublicDocsController {
    @Autowired
    private JwtUtil jwtUtil;

    private final DocsService docsService;

    public PublicDocsController(DocsService docsService) {
        this.docsService = docsService;
    }


    @GetMapping
    public ResponseEntity<?> getManifest(
            @RequestHeader(value = "Authorization", required = false) Optional<String> token) {
        String role = "USER";
        // System.out.println("token not present ===>: " + token.get().strip().equals(token.get()));

        // role = jwtUtil.extractRole(token.get().split(" ")[1].strip());
        if (token.isPresent() && !token.get().isBlank()) {
            String raw = token.get().trim();
            if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
                raw = raw.substring(7).trim();
            }
            role = jwtUtil.extractRole(raw);
        }

        try {
            if ("USER".equals(role)) {
                DocManifest publicManifest = docsService.getManifest(false);

                return ResponseEntity.ok(Map.of("data", Map.of(
                        "service", nullToEmpty(publicManifest.getService()),
                        "apiVersion", nullToEmpty(publicManifest.getApiVersion()),
                        "scope", "public",
                        "internal", List.<DocCategory>of(),
                        "public", publicManifest.getPublicCategories())));
            }

            // Administrative/internal roles get both manifests
            DocManifest publicManifest = docsService.getManifest(false);
            DocManifest internalManifest = docsService.getManifest(true);

            return ResponseEntity.ok(Map.of("data", Map.of(
                    "service", chooseString(publicManifest, internalManifest),
                    "apiVersion", chooseVersion(publicManifest, internalManifest),
                    "scope", "internal",
                    "internal", safeCategories(internalManifest),
                    "public", safeCategories(publicManifest))));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "failed to get docs manifest : " + e.getMessage()));
        }
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getDoc(@PathVariable String slug, @RequestHeader("X-User-Role") String role) {

        try {
            if ("USER".equals(role)) {
                DocResponse doc = docsService.getDoc(slug, false);
                return ResponseEntity.ok(Map.of("data", doc));
            }

            // Try internal doc first, fall back to public doc if not found
            DocResponse doc;
            try {
                doc = docsService.getDoc(slug, true);
            } catch (Exception e) {
                doc = docsService.getDoc(slug, false);
            }

            return ResponseEntity.ok(Map.of("data", doc));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "document not found"));
        }
    }

    // =========================
    // HELPERS — mirror the Go handler's chooseString/chooseVersion/safeCategories
    // =========================

    private List<DocCategory> safeCategories(DocManifest m) {
        if (m == null) {
            return List.of();
        }
        if (m.getPublicCategories() != null && !m.getPublicCategories().isEmpty()) {
            return m.getPublicCategories();
        }
        if (m.getInternal() != null && !m.getInternal().isEmpty()) {
            return m.getInternal();
        }
        return List.of();
    }

    private String chooseString(DocManifest a, DocManifest b) {
        if (a != null && a.getService() != null && !a.getService().isEmpty()) {
            return a.getService();
        }
        return b != null ? nullToEmpty(b.getService()) : "";
    }

    private String chooseVersion(DocManifest a, DocManifest b) {
        if (a != null && a.getApiVersion() != null && !a.getApiVersion().isEmpty()) {
            return a.getApiVersion();
        }
        return b != null ? nullToEmpty(b.getApiVersion()) : "";
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}