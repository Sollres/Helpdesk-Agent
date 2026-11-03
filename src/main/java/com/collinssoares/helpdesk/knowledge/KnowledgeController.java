package com.collinssoares.helpdesk.knowledge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
public class KnowledgeController {

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeController(KnowledgeIngestionService ingestionService, KnowledgeBaseService knowledgeBaseService) {
        this.ingestionService = ingestionService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** Ajoute ou met à jour des procédures (.md ou .pdf). */
    @PostMapping("/api/admin/documents")
    public List<IngestionResult> upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        for (MultipartFile file : files) {
            if (file.getOriginalFilename() == null || !KnowledgeIngestionService.isSupported(file.getOriginalFilename())) {
                throw new IllegalArgumentException(
                        "Format non pris en charge : " + file.getOriginalFilename() + " (attendu : .md ou .pdf)");
            }
        }
        List<IngestionResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(ingestionService.ingest(file.getOriginalFilename(), file.getBytes()));
        }
        return results;
    }

    /** Recherche directe dans la base, sans LLM : utile pour déboguer et évaluer le RAG. */
    @GetMapping("/api/knowledge/search")
    public List<KnowledgeSnippet> search(@RequestParam("q") @NotBlank String query) {
        return knowledgeBaseService.search(query);
    }
}
