//package com.ximofam.graduation_project.rag;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
//import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
//import org.springframework.ai.transformer.splitter.TokenTextSplitter;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.core.io.Resource;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class DocumentIngestionRunner implements ApplicationRunner {
//
//    private final VectorStore vectorStore; // đóng vai trò DocumentWriter
//    private final JdbcTemplate jdbcTemplate;
//
//    @Value("classpath:docs/chess-rules.md")
//    private Resource chessRulesResource;
//
//    @Override
//    public void run(ApplicationArguments args) {
//        Integer count = jdbcTemplate.queryForObject(
//                "SELECT COUNT(*) FROM vector_store", Integer.class);
//
//        if (count != null && count > 0) {
//            log.info("Vector store đã có {} documents, bỏ qua init", count);
//            return;
//        }
//
//        ingestMarkdown();
//    }
//
//    private void ingestMarkdown() {
//        // 1. DocumentReader — đọc markdown thành Document
//        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
//                .withHorizontalRuleCreateDocument(true)   // mỗi --- tách thành 1 Document riêng
//                .withIncludeCodeBlock(false)              // code block tách Document riêng
//                .withIncludeBlockquote(false)
//                .withAdditionalMetadata("source", "chess-rules")
//                .build();
//
//        MarkdownDocumentReader reader = new MarkdownDocumentReader(chessRulesResource, config);
//        List<Document> documents = reader.read();
//
//        // 2. DocumentTransformer — chunk theo token, dùng builder pattern
//        TokenTextSplitter splitter = TokenTextSplitter.builder()
//                .withChunkSize(800)
//                .withMinChunkSizeChars(350)
//                .withMinChunkLengthToEmbed(5)
//                .withMaxNumChunks(10000)
//                .withKeepSeparator(true)
//                // Nếu content có tiếng Việt, dấu câu vẫn dùng chung bộ Latin nên
//                // không cần custom punctuationMarks như tiếng Trung/Nhật
//                .build();
//
//        List<Document> chunks = splitter.split(documents);
//
//        // 3. DocumentWriter — ghi vào vector store, batch để tránh timeout embedding API
//        writeBatched(chunks, 50);
//
//        log.info("Đã init {} chunks vào vector store", chunks.size());
//    }
//
//    private void writeBatched(List<Document> chunks, int batchSize) {
//        for (int i = 0; i < chunks.size(); i += batchSize) {
//            List<Document> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
//            vectorStore.write(batch); // Consumer<List<Document>>.write() — tương đương accept()
//        }
//    }
//}