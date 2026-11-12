-- Conjuntos de documentação oficial ingeridos (ex.: "Javadoc Java 21", "JEPs", "JLS 17"). Preenchido pela F11.
CREATE TABLE corpus_source (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             varchar(200) NOT NULL UNIQUE,
    kind             varchar(32)  NOT NULL,
    base_url         text         NOT NULL,
    license_terms    text,
    java_version_min integer      CHECK (java_version_min BETWEEN 1 AND 99),
    java_version_max integer      CHECK (java_version_max BETWEEN 1 AND 99),
    status           varchar(16)  NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'INGESTING', 'READY', 'FAILED')),
    content_hash     varchar(64),
    chunk_count      integer      NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
    last_ingested_at timestamptz,
    error_message    text,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CHECK (java_version_min IS NULL OR java_version_max IS NULL OR java_version_max >= java_version_min)
);

-- Trechos da documentação com seus embeddings.
--
-- O formato (id, content, metadata, embedding) é o esperado pelo PgVectorStore do Spring AI;
-- a F12 configura o store apontando para esta tabela (initialize-schema=false, o Flyway é o dono).
-- Os metadados vão em `metadata` (jsonb): source_id, java_version_min, java_version_max, kind, url...
-- e são filtrados pelas expressões de filtro do Spring AI.
--
-- DIMENSÃO DO VETOR: 1024 = modelo de embedding bge-m3 (multilíngue; as perguntas chegam em
-- português e a documentação está em inglês). Trocar para um modelo com outra dimensão exige uma
-- nova migration alterando vector(N) e a REINDEXAÇÃO COMPLETA do corpus (F14).
CREATE TABLE corpus_chunk (
    id        uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    content   text         NOT NULL,
    metadata  jsonb        NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1024) NOT NULL
);

-- Busca vetorial por similaridade de cosseno (distância padrão do Spring AI).
CREATE INDEX corpus_chunk_embedding_hnsw_idx
    ON corpus_chunk USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- Acelera os filtros por metadados (versão do Java, fonte, tipo).
CREATE INDEX corpus_chunk_metadata_gin_idx ON corpus_chunk USING gin (metadata jsonb_path_ops);
