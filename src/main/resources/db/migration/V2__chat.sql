-- Convenções do schema:
--   * java_version é o número "major" do Java: 1.x vale x (Java 1.4 = 4, Java 8 = 8, Java 25 = 25).
--     NULL em conversas/perguntas significa "automático" (detectado pelo agente, F10).
--   * tier guarda o id do tier de modelo (low | medium | strong | extra), definido na F06.
--   * Colunas jsonb guardam estruturas que cada feature detalha (fontes, tool calls, preferências).

CREATE TABLE conversation (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        varchar(200) NOT NULL DEFAULT 'Nova conversa',
    tier         varchar(16),
    java_version integer      CHECK (java_version BETWEEN 1 AND 99),
    language     varchar(8)   NOT NULL DEFAULT 'pt-BR',
    preferences  jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX conversation_updated_at_idx ON conversation (updated_at DESC);

-- id identity (bigint) garante a ordem das mensagens mesmo quando várias são gravadas na mesma transação.
CREATE TABLE message (
    id                bigint            GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id   uuid              NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    role              varchar(16)       NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content           text              NOT NULL,
    tier              varchar(16),
    model             varchar(100),
    java_version      integer           CHECK (java_version BETWEEN 1 AND 99),
    prompt_tokens     integer           CHECK (prompt_tokens >= 0),
    completion_tokens integer           CHECK (completion_tokens >= 0),
    tokens_per_second double precision  CHECK (tokens_per_second >= 0),
    sources           jsonb,
    tool_calls        jsonb,
    created_at        timestamptz       NOT NULL DEFAULT now()
);

CREATE INDEX message_conversation_idx ON message (conversation_id, id);

CREATE TABLE attachment (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid         NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    message_id      bigint       REFERENCES message (id) ON DELETE CASCADE,
    filename        varchar(255) NOT NULL,
    content_type    varchar(100),
    size_bytes      bigint       NOT NULL CHECK (size_bytes >= 0),
    sha256          varchar(64)  NOT NULL,
    storage_path    text         NOT NULL,
    extracted_text  text,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX attachment_conversation_idx ON attachment (conversation_id);
CREATE INDEX attachment_message_idx ON attachment (message_id);
