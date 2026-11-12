CREATE TABLE pdf_document (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    filename      varchar(255) NOT NULL,
    size_bytes    bigint       NOT NULL CHECK (size_bytes >= 0),
    -- Hash do conteúdo: o mesmo PDF enviado duas vezes reaproveita o registro.
    sha256        varchar(64)  NOT NULL UNIQUE,
    page_count    integer      CHECK (page_count > 0),
    storage_path  text         NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'UPLOADED'
                  CHECK (status IN ('UPLOADED', 'EXTRACTING', 'EXTRACTED', 'SEGMENTING', 'SEGMENTED', 'FAILED')),
    error_message text,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

-- Uma execução de "resolver todas as questões" de um PDF. Persistida para sobreviver a reinício (F28).
CREATE TABLE pdf_job (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    pdf_document_id     uuid        NOT NULL REFERENCES pdf_document (id) ON DELETE CASCADE,
    status              varchar(16) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    tier                varchar(16) NOT NULL,
    java_version        integer     CHECK (java_version BETWEEN 1 AND 99),
    total_questions     integer     NOT NULL DEFAULT 0 CHECK (total_questions >= 0),
    completed_questions integer     NOT NULL DEFAULT 0 CHECK (completed_questions >= 0),
    error_message       text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    started_at          timestamptz,
    finished_at         timestamptz
);

CREATE INDEX pdf_job_pdf_idx ON pdf_job (pdf_document_id);
CREATE INDEX pdf_job_status_idx ON pdf_job (status);

CREATE TABLE question (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    pdf_document_id uuid         NOT NULL REFERENCES pdf_document (id) ON DELETE CASCADE,
    -- ordinal: ordem de leitura (1..n); label: numeração como aparece no PDF ("7", "7.a", "Questão 7").
    ordinal         integer      NOT NULL CHECK (ordinal > 0),
    label           varchar(40),
    question_type   varchar(24)  NOT NULL,
    statement       text         NOT NULL,
    code            text,
    -- Lista de {label, text}; NULL em questões dissertativas.
    alternatives    jsonb,
    java_version    integer      CHECK (java_version BETWEEN 1 AND 99),
    page_number     integer      CHECK (page_number > 0),
    -- Marcada pelo OCR/segmentação como duvidosa; a UI destaca para revisão (F26/F38).
    needs_review    boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (pdf_document_id, ordinal)
);

-- Pode haver várias respostas por questão (reexecuções, tiers diferentes); vale a mais recente.
CREATE TABLE answer (
    id                    uuid             PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id           uuid             NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    job_id                uuid             REFERENCES pdf_job (id) ON DELETE SET NULL,
    status                varchar(16)      NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    chosen_alternative    varchar(10),
    explanation           text,
    steps                 jsonb,
    java_version          integer          CHECK (java_version BETWEEN 1 AND 99),
    confidence            double precision CHECK (confidence BETWEEN 0 AND 1),
    sources               jsonb,
    verified_by_execution boolean          NOT NULL DEFAULT false,
    execution_output      text,
    tier                  varchar(16),
    model                 varchar(100),
    error_message         text,
    created_at            timestamptz      NOT NULL DEFAULT now()
);

CREATE INDEX answer_question_idx ON answer (question_id, created_at DESC);
CREATE INDEX answer_job_idx ON answer (job_id);
