-- 1. Create relational table for multilingual knowledge item translations
CREATE TABLE knowledge_item_translation (
    item_id   BIGINT       NOT NULL REFERENCES knowledge_base_item(id) ON DELETE CASCADE,
    language  VARCHAR(10)  NOT NULL,
    answer    TEXT         NOT NULL,
    PRIMARY KEY (item_id, language)
);

CREATE INDEX idx_knowledge_item_translation_lang ON knowledge_item_translation(language);

-- 2. Migrate existing answer columns into relational translations
INSERT INTO knowledge_item_translation (item_id, language, answer)
SELECT id, 'uz', answer_uz
FROM knowledge_base_item
WHERE answer_uz IS NOT NULL AND trim(answer_uz) <> ''
ON CONFLICT (item_id, language) DO NOTHING;

INSERT INTO knowledge_item_translation (item_id, language, answer)
SELECT id, 'ru', answer_ru
FROM knowledge_base_item
WHERE answer_ru IS NOT NULL AND trim(answer_ru) <> ''
ON CONFLICT (item_id, language) DO NOTHING;

INSERT INTO knowledge_item_translation (item_id, language, answer)
SELECT id, 'en', answer_en
FROM knowledge_base_item
WHERE answer_en IS NOT NULL AND trim(answer_en) <> ''
ON CONFLICT (item_id, language) DO NOTHING;

-- 3. Drop unnormalized language-specific columns from knowledge_base_item
ALTER TABLE knowledge_base_item
    DROP COLUMN IF EXISTS answer_uz,
    DROP COLUMN IF EXISTS answer_ru,
    DROP COLUMN IF EXISTS answer_en;
