package com.collinssoares.helpdesk.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Indexe des procédures (Markdown ou PDF) dans la base vectorielle.
 *
 * <p>L'indexation est idempotente : un document dont le contenu n'a pas changé (même empreinte
 * SHA-256) n'est pas recalculé ; un document modifié remplace entièrement l'ancienne version.
 */
@Service
public class KnowledgeIngestionService {

    static final String TYPE_KB = "kb";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    /** Les sections plus longues sont redécoupées pour rester dans une taille d'embedding raisonnable. */
    private static final int MAX_SECTION_CHARS = 2500;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(500).build();

    public KnowledgeIngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestionResult ingest(String filename, byte[] content) {
        String hash = sha256(content);
        if (isAlreadyIndexed(filename, hash)) {
            return new IngestionResult(filename, 0, IngestionResult.Status.UNCHANGED);
        }

        List<Document> chunks = toChunks(filename, content, hash);
        vectorStore.delete(new FilterExpressionBuilder().eq("source", filename).build());
        vectorStore.add(chunks);

        log.info("Document indexé source={} chunks={}", filename, chunks.size());
        return new IngestionResult(filename, chunks.size(), IngestionResult.Status.INDEXED);
    }

    static boolean isSupported(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".pdf");
    }

    private boolean isAlreadyIndexed(String source, String hash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata ->> 'source' = ? AND metadata ->> 'hash' = ?",
                Integer.class, source, hash);
        return count != null && count > 0;
    }

    private List<Document> toChunks(String filename, byte[] content, String hash) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            return markdownChunks(filename, new String(content, StandardCharsets.UTF_8), hash);
        }
        if (lower.endsWith(".pdf")) {
            return pdfChunks(filename, content, hash);
        }
        throw new IllegalArgumentException("Format non pris en charge : " + filename + " (attendu : .md ou .pdf)");
    }

    private List<Document> markdownChunks(String filename, String markdown, String hash) {
        MarkdownSectionSplitter.ParsedMarkdown parsed = MarkdownSectionSplitter.split(markdown, baseName(filename));
        List<Document> chunks = new ArrayList<>();
        for (MarkdownSectionSplitter.Section section : parsed.sections()) {
            // Le titre et la section sont répétés dans le texte : l'embedding du passage garde ainsi son contexte
            String text = "# " + parsed.title() + "\n## " + section.heading() + "\n\n" + section.body();
            Map<String, Object> metadata = metadata(filename, parsed.title(), section.heading(), hash);
            Document document = new Document(text, metadata);
            chunks.addAll(text.length() > MAX_SECTION_CHARS ? splitter.apply(List.of(document)) : List.of(document));
        }
        return chunks;
    }

    private List<Document> pdfChunks(String filename, byte[] content, String hash) {
        String title = baseName(filename);
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        List<Document> pages = new PagePdfDocumentReader(resource).get().stream()
                .map(page -> new Document(page.getText(),
                        metadata(filename, title, "page " + page.getMetadata().get("page_number"), hash)))
                .toList();
        return splitter.apply(pages);
    }

    private static Map<String, Object> metadata(String source, String title, String section, String hash) {
        return Map.of("type", TYPE_KB, "source", source, "title", title, "section", section, "hash", hash);
    }

    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename).replace('-', ' ').replace('_', ' ');
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
