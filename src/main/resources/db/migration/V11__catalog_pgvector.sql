-- UNIVERSAL-OPERATOR-PLAN B.4: a product catalog the agent searches during a call.
--
-- pgvector from here on. A catalog is tens of thousands of rows, and the in-process cosine
-- the document passages use (knowledge_chunk, V1) holds a whole company in memory. The
-- database image has to carry the extension: docker-compose runs pgvector/pgvector:pg18
-- (docs/RUN.md). Document passages stay where they are; only catalog rows live in the
-- vector index.
CREATE EXTENSION IF NOT EXISTS vector;

-- A STRUCTURED_FEED source: where its rows sit in a JSON answer, which column is the key,
-- and how often a URL feed is fetched again (NULL = only by hand).
ALTER TABLE knowledge_source ADD COLUMN feed_items_path VARCHAR(200);
ALTER TABLE knowledge_source ADD COLUMN feed_key_field  VARCHAR(100);
ALTER TABLE knowledge_source ADD COLUMN refresh_minutes INT;
ALTER TABLE knowledge_source ADD CONSTRAINT ck_knowledge_source_refresh
    CHECK (refresh_minutes IS NULL OR refresh_minutes >= 15);

-- One row per catalog item. company_id is repeated from the source so a search narrows on
-- this table before it joins. The embedding is 768 floats — what CatalogItemRepositoryAdapter
-- writes — and NULL when the embedding model was unreachable: such a row is still found by
-- its words. search_text drops apostrophes first, so "ko'k" and "kok" are one word, the way
-- CatalogRanker reads them.
CREATE TABLE catalog_item (
    id           BIGSERIAL    PRIMARY KEY,
    source_id    BIGINT       NOT NULL REFERENCES knowledge_source(id) ON DELETE CASCADE,
    company_id   BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    sku          VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    attributes   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    content_hash VARCHAR(64)  NOT NULL,
    embedding    vector(768),
    search_text  tsvector GENERATED ALWAYS AS
                 (to_tsvector('simple', translate(lower(content), '''`ʻʼ‘’', ''))) STORED,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_catalog_item_source_sku UNIQUE (source_id, sku)
);
CREATE INDEX idx_catalog_item_company ON catalog_item(company_id);
CREATE INDEX idx_catalog_item_embedding ON catalog_item USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_catalog_item_search ON catalog_item USING gin (search_text);
