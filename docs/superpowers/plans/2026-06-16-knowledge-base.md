# Spring Boot Knowledge Base — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a complete Spring Boot project (~25 Java files + config) implementing the RAG knowledge base defined in `SpringAI_RAG_Guide.md`. Ollama/Qdrant are NOT installed yet — only project code.

**Architecture:** Standard Spring Boot 3.5.x layered project: config (Bean wiring) → controller (REST) → service (business logic) → etl (data pipeline). Spring AI 1.0 GA for AI integration, Spring Security for auth.

**Tech Stack:** JDK 21, Spring Boot 3.5.2, Spring AI 1.0.0, Spring Security, Lombok, Micrometer+Prometheus

**Source of truth:** All code is in `/Users/sunan/java_project/demo/Knowledge/SpringAI_RAG_Guide.md`. Copy exact code from the guide into Java files.

---

### Task 1: Build foundation — pom.xml, application.yml, .env, main class

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/application.yml`
- Create: `.env`
- Create: `src/main/java/com/example/knowledge/KnowledgeApplication.java`

- [ ] **Step 1: Create pom.xml**

Copy from guide 第七节. Key deps: spring-boot-starter-web, spring-boot-starter-security, spring-boot-starter-actuator, spring-ai-starter-model-ollama, spring-ai-starter-vector-store-qdrant, spring-ai-pdf-document-reader, spring-ai-tika-document-reader, micrometer-registry-prometheus, lombok. Use BOM `spring-ai-bom:1.0.0`, parent `spring-boot-starter-parent:3.5.2`.

- [ ] **Step 2: Create application.yml**

Copy from guide 第八节. Includes spring.ai.ollama (chat+embedding), spring.ai.vectorstore.qdrant, management (actuator+prometheus), app.security, app.import, app.retrieval sections.

- [ ] **Step 3: Create .env**

```bash
APP_API_KEY=sk-your-secret-api-key-here
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=change-me-on-deploy
QDRANT_API_KEY=your-qdrant-key-here
```

- [ ] **Step 4: Create KnowledgeApplication.java**

```java
package com.example.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
```

- [ ] **Step 5: Create directory structure**

```bash
mkdir -p src/main/java/com/example/knowledge/{config,controller,service,etl,health}
mkdir -p src/main/resources
mkdir -p docs eval
```

---

### Task 2: Config layer — AI, Security, Resilience, Multi-turn

**Files:**
- Create: `src/main/java/com/example/knowledge/config/RagConfig.java`
- Create: `src/main/java/com/example/knowledge/config/SecurityConfig.java`
- Create: `src/main/java/com/example/knowledge/config/ApiKeyFilter.java`
- Create: `src/main/java/com/example/knowledge/config/ResilienceConfig.java`
- Create: `src/main/java/com/example/knowledge/config/MultiTurnConfig.java`

- [ ] **Step 1: Create RagConfig.java**

Copy from guide 第十七节. Configures `QuestionAnswerAdvisor` bean with custom Chinese prompt template, `SearchRequest` (topK, similarityThreshold from `@Value`), and `ChatClient` bean.

- [ ] **Step 2: Create SecurityConfig.java**

Copy from guide 第十六节. `@EnableWebSecurity` + `@EnableMethodSecurity`. Stateless session, CSRF disabled. API Key filter before Basic Auth. In-memory users: admin (ROLE_ADMIN) + viewer (ROLE_USER). BCryptPasswordEncoder.

- [ ] **Step 3: Create ApiKeyFilter.java**

Copy from guide 第十六节. `OncePerRequestFilter` that reads `X-API-Key` header, compares with `app.security.api-key`, grants ROLE_ADMIN+ROLE_USER on match.

- [ ] **Step 4: Create ResilienceConfig.java**

Copy from guide 第二十一节. Custom `RestClient.Builder` bean with 3-retry exponential backoff interceptor for Ollama calls.

- [ ] **Step 5: Create MultiTurnConfig.java**

Copy from guide 第二十节. `InMemoryChatMemory` bean + `MessageChatMemoryAdvisor` bean (10 messages, "conversation" ID). `ChatClient` bean with both QA advisor + memory advisor.

---

### Task 3: ETL layer — DocumentLoader, Cleaner, Splitter, Metadata, ContextExpander, InputSanitizer

**Files:**
- Create: `src/main/java/com/example/knowledge/etl/DocumentLoader.java`
- Create: `src/main/java/com/example/knowledge/etl/DocumentCleaner.java`
- Create: `src/main/java/com/example/knowledge/etl/MarkdownCleaner.java`
- Create: `src/main/java/com/example/knowledge/etl/ChunkSplitter.java`
- Create: `src/main/java/com/example/knowledge/etl/MetadataEnricher.java`
- Create: `src/main/java/com/example/knowledge/etl/ContextExpander.java`
- Create: `src/main/java/com/example/knowledge/etl/InputSanitizer.java`

- [ ] **Step 1: Create DocumentLoader.java**

Copy from guide 第十一节. Methods: `loadFromDirectory(String)` for flat list, `loadGroupedByFile(String)` returning `Map<String, List<Document>>` (key=file path), `loadFile(String)` dispatching by extension to PDF/Tika/Text readers. Supported extensions: pdf/docx/doc/md/txt/ppt/pptx/xls/xlsx/html. Exclude dirs: archive/.git/node_modules.

- [ ] **Step 2: Create DocumentCleaner.java**

Copy from guide 第十二节. `clean(List<Document>)` filtering blank/short docs (<30 chars). Removes page numbers, copyright lines, standalone digits.

- [ ] **Step 3: Create MarkdownCleaner.java**

Copy from guide 第十一节. Removes YAML frontmatter, HTML tags, image refs (keeps alt text), links (keeps text), collapses blank lines.

- [ ] **Step 4: Create ChunkSplitter.java**

Copy from guide 第十三节. `TokenTextSplitter` with chunkSize=800, minChunkSizeChars=350, minChunkLengthToEmbed=50, keepSeparator=true, splitContinuationIgnore=true.

- [ ] **Step 5: Create MetadataEnricher.java**

Copy from guide 第十四节. Injects: source, file_name, file_type, chunk_index, chunk_total, doc_id (UUID), imported_at (Instant), category (guessed from path: api/database/product/wiki/general).

- [ ] **Step 6: Create ContextExpander.java**

Copy from guide 第十三节. `expandWithNeighbors(List<Document>, VectorStore, int neighbors)` — for each chunk, query adjacent chunks by source+chunk_index filter.

- [ ] **Step 7: Create InputSanitizer.java**

Copy from guide 第十七节. Validates non-empty, max 2000 chars. Strips injection patterns: "ignore/forget/disregard all/your/above instructions/rules/prompt", "system:", `<|im_start|>`/`<|im_end|>`.

---

### Task 4: Service layer — Import, Sync, QA, Eval, Metrics

**Files:**
- Create: `src/main/java/com/example/knowledge/service/KnowledgeImportService.java`
- Create: `src/main/java/com/example/knowledge/service/ImportResult.java`
- Create: `src/main/java/com/example/knowledge/service/IncrementalSyncService.java`
- Create: `src/main/java/com/example/knowledge/service/SyncResult.java`
- Create: `src/main/java/com/example/knowledge/service/SyncScheduler.java`
- Create: `src/main/java/com/example/knowledge/service/QAService.java`
- Create: `src/main/java/com/example/knowledge/service/MultiTurnQAService.java`
- Create: `src/main/java/com/example/knowledge/service/RAGEvaluator.java`
- Create: `src/main/java/com/example/knowledge/service/KnowledgeMetrics.java`

- [ ] **Step 1: Create ImportResult.java (DTO)**

```java
package com.example.knowledge.service;
import lombok.Data;
@Data
public class ImportResult {
    private boolean success;
    private int fileCount;
    private int afterClean;
    private int chunkCount;
    private long elapsedMs;
    private String error;
}
```

- [ ] **Step 2: Create KnowledgeImportService.java**

Copy from guide 第十五节. `fullImport(String docsDir)`: uses `loadGroupedByFile()`, iterates per-file through clean→split→enrich, then partitions chunks into batches (`@Value batch-size`), parallel writes via `ExecutorService` (`@Value parallel-threads`). `importSingleFile(String filePath)` for incremental sync. Private `partition()` helper. Includes batch embedding performance note.

- [ ] **Step 3: Create SyncResult.java (DTO)**

```java
package com.example.knowledge.service;
import lombok.Data;
@Data
public class SyncResult {
    private int added, updated, deleted, skipped;
    public void incrementAdded() { added++; }
    public void incrementUpdated() { updated++; }
    public void incrementDeleted() { deleted++; }
    public void incrementSkipped() { skipped++; }
}
```

- [ ] **Step 4: Create IncrementalSyncService.java**

Copy from guide 第十九节. `sync(String docsDir)`: MD5 hash per file, detect new/modified/deleted, call `importSingleFile`/`deleteBySource` accordingly. `saveIndex`/`loadIndex` to Properties file. Embedding cache note.

- [ ] **Step 5: Create SyncScheduler.java**

Copy from guide 第十九节. `@Scheduled(cron = "0 0 3 * * *")` nightly sync.

- [ ] **Step 6: Create QAService.java**

Copy from guide 第十八节. `ask(String question)` using `ChatClient.call().content()`. `askStream(String question)` using `ChatClient.stream().content()` returning `Flux<String>`. Both sanitize input first.

- [ ] **Step 7: Create MultiTurnQAService.java**

Copy from guide 第二十节. `ask(conversationId, question)` with `chat_memory_conversation_id` advisor param. `askStream(conversationId, question)` streaming variant.

- [ ] **Step 8: Create RAGEvaluator.java**

Copy from guide 第二十四节. `evaluate(List<EvalCase>)`: per test case, search vector store, check source hit + keyword coverage, compute Top-5 hit rate and avg keyword coverage.

- [ ] **Step 9: Create KnowledgeMetrics.java**

Copy from guide 第二十二节. `MeterRegistry` gauge for `knowledge.chunks.total`, counters for `knowledge.import.count` and `knowledge.search.count` (by category).

---

### Task 5: Controller layer — Import, Admin, QA, Exception Handler

**Files:**
- Create: `src/main/java/com/example/knowledge/controller/KnowledgeController.java`
- Create: `src/main/java/com/example/knowledge/controller/KnowledgeAdminController.java`
- Create: `src/main/java/com/example/knowledge/controller/QAController.java`
- Create: `src/main/java/com/example/knowledge/controller/KnowledgeExceptionHandler.java`

- [ ] **Step 1: Create KnowledgeController.java**

Copy from guide 第十五节. `POST /api/knowledge/import?dir=` and `POST /api/knowledge/import-file?path=`, both `@PreAuthorize("hasRole('ADMIN')")`.

- [ ] **Step 2: Create KnowledgeAdminController.java**

Copy from guide 第十五节 15.4. `GET /api/knowledge/admin/check?source=` (file exists check), `DELETE /api/knowledge/admin/delete?source=` (delete by source), `GET /api/knowledge/admin/stats` (collection info). All `@PreAuthorize("hasRole('ADMIN')")`.

- [ ] **Step 3: Create QAController.java**

Copy from guide 第十八节. `POST /api/knowledge/ask` (blocking, returns JSON), `POST /api/knowledge/ask-stream` (SSE, returns `Flux<ServerSentEvent<String>>`). Both authenticated. Uses `QuestionRequest` and `AnswerResponse` inner DTOs.

- [ ] **Step 4: Create KnowledgeExceptionHandler.java**

Copy from guide 第二十一节. `@RestControllerAdvice`. Catches `Exception`, returns 500 with `{"error": "ExceptionName", "message": "..."}`.

---

### Task 6: Health checker + Infrastructure files

**Files:**
- Create: `src/main/java/com/example/knowledge/health/QdrantHealthChecker.java`
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `docker-compose.prod.yml`
- Create: `docs/test.md`
- Create: `eval/test_questions.json`

- [ ] **Step 1: Create QdrantHealthChecker.java**

Copy from guide 第二十一节. `ApplicationListener<ApplicationReadyEvent>`, logs Qdrant connection status on startup.

- [ ] **Step 2: Create Dockerfile**

Copy from guide 第五节. Multi-stage: `eclipse-temurin:21-jdk-alpine` builder → `eclipse-temurin:21-jre-alpine` runtime, non-root user, healthcheck.

- [ ] **Step 3: Create docker-compose.yml**

Copy from guide 第五节. Qdrant service (ports 6333:6333, 6334:6334, volume, healthcheck). Ollama commented out. Volumes: qdrant_storage.

- [ ] **Step 4: Create docker-compose.prod.yml**

Copy from guide 第五节. Full stack: app (build ., env vars for Ollama/Qdrant hosts, depends_on qdrant healthy), qdrant (127.0.0.1 binding, memory limits), ollama (GPU reservation, 127.0.0.1). Volumes: qdrant_storage, ollama_data.

- [ ] **Step 5: Create test document**

```markdown
# 测试文档
docs/test.md — 订单创建接口说明
```

- [ ] **Step 6: Create eval test questions**

```json
[
  {
    "question": "订单创建接口有哪些必填参数？",
    "expected_sources": ["test.md"],
    "expected_keywords": ["用户ID", "商品列表", "收货地址"]
  }
]
```

---

### Task 7: Verify — compile check

- [ ] **Step 1: Run Maven compile**

```bash
cd /Users/sunan/java_project/demo/Knowledge && mvn compile
```
Expected: BUILD SUCCESS (requires JDK 21, Maven, and network for dependencies).

- [ ] **Step 2: Fix any compilation errors**

If compilation fails, check error messages and fix imports/missing dependencies. Common issues: missing Lombok annotation processor in IDE, Spring AI BOM not resolving from Maven Central.

---

### Task 8: Git init and initial commit

- [ ] **Step 1: Initialize git and commit**

```bash
cd /Users/sunan/java_project/demo/Knowledge
git init
echo ".env\ntarget/\n*.log\n.idea/\n*.iml" > .gitignore
git add .
git commit -m "feat: initialize Spring Boot knowledge base project

Complete project structure based on SpringAI_RAG_Guide.md:
- Config: AI, Security (role+API Key dual auth), Resilience, Multi-turn
- Controller: Import, Admin, QA (blocking+SSE), Exception handler
- Service: ETL pipeline, Incremental sync, QA, Eval, Metrics
- ETL: Multi-format loader, Cleaner, TokenTextSplitter, Metadata, Context expander, Input sanitizer
- Infra: Dockerfile, docker-compose (dev+prod), health checker"
```
