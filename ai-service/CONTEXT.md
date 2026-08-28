# AI Service Context

Domain language and glossary for the Chess AI Service, powering intelligent chat assistance, FIDE chess rules retrieval, and platform Q&A.

## Language

### Conversation & Chat

**ChatSession**:
An isolated stateful conversation thread between an authenticated user and the AI assistant.
_Avoid_: Room, Channel, ChatThread

**AiChatMessage**:
A persisted message entity representing a single turn (user question or assistant response) enriched with classification metadata.
_Avoid_: MessageRecord, ChatEntry, Log

**ConversationEngine**:
The deep module coordinating conversation turns, dual-state persistence, state graph execution, and asynchronous auto-titling.
_Avoid_: ChatService, ConversationManager, BotHandler

### Knowledge & Ingestion

**KnowledgeIndex**:
The indexed knowledge base and vector collection storing embeddings and structural chunks for retrieval.
_Avoid_: VectorDb, DocumentStore, RAGStorage

**FideSplitter**:
The structure-aware chunker that models the legal hierarchy of the FIDE Laws of Chess into semantic sections.
_Avoid_: TextSplitter, TokenSplitter, NaiveChunker

**CheckpointerAdapter**:
The persistence adapter for saving and resuming multi-turn LangGraph states across turns.
_Avoid_: StateStorage, GraphSaver, MemoryBackend

**ChessDiagram**:
A structured representation of an illustrative chessboard diagram extracted from FIDE regulations, containing a verified FEN string and semantic explanation.
_Avoid_: BoardImage, ChessPicture, DiagramSnippet

**DomainRouter**:
The classification mechanism that identifies whether a user query pertains to official chess regulations (`chess_law`), chess openings & variations (`chess_opening`), web platform workflows (`system`), or general topics (`all`) to scope vector retrieval.
_Avoid_: IntentDetector, CategorySwitch, DomainFilterer

**OpeningCrawler**:
The MediaWiki export extractor that crawls Wikibooks Chess Opening Theory, deterministically converts board diagrams into FEN notations, folds stub variations into parent documents, and joins standardized ECO codes.
_Avoid_: PageScraper, OpeningScraper

**CrossEncoderReranker**:
The two-stage retrieval ranker that scores candidate documents against user queries via cross-attention to output high-precision semantic re-ordering.
_Avoid_: Reorderer, Sorter, SecondStageRetriever

**LLMFactory**:
The centralized factory providing decoupled, multi-provider (`groq`, `openai`, `ollama`) instantiation with lazy loading and role-specific granularity for chat, routing, and vision.
_Avoid_: ModelCreator, LLMHelper, PromptClient

**DomainTools**:
The set of specialized LangChain tools (`search_fide_rules`, `search_chess_openings`, `search_platform_support`) providing authoritative knowledge retrieval with embedded cross-encoder reranking and parallel execution support.
_Avoid_: SearchFunctions, RetrieverCallers, QueryPlugins




