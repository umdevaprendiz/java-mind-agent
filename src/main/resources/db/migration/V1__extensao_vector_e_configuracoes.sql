-- Extensão do pgvector (idempotente com docker/postgres/init/01-extensions.sql da F02).
CREATE EXTENSION IF NOT EXISTS vector;

-- Configurações chave/valor da aplicação (tier padrão, versão padrão do Java, idioma...).
CREATE TABLE app_setting (
    setting_key   varchar(100) PRIMARY KEY,
    setting_value text        NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);
