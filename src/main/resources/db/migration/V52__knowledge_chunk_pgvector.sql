-- Knowledge passages move into pgvector, the way catalog_item already is (KNOWLEDGE-MEMORY-PLAN phase 1).
--
-- Until now the vectors were BYTEA and every search scanned a company's whole knowledge base in the JVM,
-- holding it in a per-company cache. The search now runs in the database: an HNSW index answers the
-- nearest passages and a text index the passages that share the caller's words.
--
-- The old BYTEA vectors are dropped rather than converted: they were stored at the model's full width and
-- the new column holds 768 floats. Every passage starts with no vector, is still found by its words, and
-- KnowledgeEmbeddingBackfillScheduler embeds them again in the background within minutes.
ALTER TABLE knowledge_chunk
    DROP COLUMN embedding,
    ADD COLUMN company_id   BIGINT REFERENCES company(id) ON DELETE CASCADE,
    ADD COLUMN content_hash VARCHAR(64),
    -- The page a sitemap passage came from; null for a file, the source's own url for a URL source.
    ADD COLUMN page_url     VARCHAR(2048),
    ADD COLUMN embedding    vector(768);

UPDATE knowledge_chunk c SET company_id = s.company_id FROM knowledge_source s WHERE s.id = c.source_id;
-- The same digest the indexer writes (Tokens.hash): lower-case hex SHA-256 of the UTF-8 text.
UPDATE knowledge_chunk SET content_hash = encode(sha256(convert_to(content, 'UTF8')), 'hex');

ALTER TABLE knowledge_chunk
    ALTER COLUMN company_id SET NOT NULL,
    ALTER COLUMN content_hash SET NOT NULL;

-- Apostrophes dropped first, so "to'lov" and "tolov" are one word — as in catalog_item.search_text.
ALTER TABLE knowledge_chunk ADD COLUMN search_text tsvector GENERATED ALWAYS AS
    (to_tsvector('simple', translate(lower(content), '''`ʻʼ‘’', ''))) STORED;

CREATE INDEX idx_knowledge_chunk_company ON knowledge_chunk(company_id);
CREATE INDEX idx_knowledge_chunk_embedding ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_knowledge_chunk_search ON knowledge_chunk USING gin (search_text);
CREATE INDEX idx_knowledge_chunk_unembedded ON knowledge_chunk(id) WHERE embedding IS NULL;

-- Curated answers are matched by meaning too, not only by their keywords. Filled when an item is saved;
-- null until then (or while the model is down), and then the backfill fills it.
ALTER TABLE knowledge_base_item ADD COLUMN embedding vector(768);
CREATE INDEX idx_knowledge_base_item_unembedded ON knowledge_base_item(id) WHERE embedding IS NULL;
