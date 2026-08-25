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
The classification mechanism that identifies whether a user query pertains to chess regulations (`chess`), web platform workflows (`system`), or general topics (`all`) to scope vector retrieval.
_Avoid_: IntentDetector, CategorySwitch, DomainFilterer

**CrossEncoderReranker**:
The two-stage retrieval ranker that scores candidate documents against user queries via cross-attention to output high-precision semantic re-ordering.
_Avoid_: Reorderer, Sorter, SecondStageRetriever



