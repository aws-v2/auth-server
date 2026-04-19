package org.serwin.auth_server.controller;

import java.util.Map;

import org.serwin.auth_server.enums.DocType;
import org.serwin.auth_server.service.DocsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/docs")
public class PublicDocsController {

    private final DocsService docsService;

    public PublicDocsController(DocsService docsService) {
        this.docsService = docsService;
    }



    @GetMapping
    public ResponseEntity<?> getManifest() {
        return ResponseEntity.ok(
                Map.of("data", docsService.getManifest(DocType.PUBLIC)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getDoc(@PathVariable String slug) {
        return ResponseEntity.ok(
                Map.of("data", docsService.getDoc(DocType.PUBLIC, slug)));
    }
}