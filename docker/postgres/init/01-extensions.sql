-- Executado pelo entrypoint do Postgres apenas quando o volume está vazio.
-- A migration do Flyway (F03) repete o comando com IF NOT EXISTS para ser idempotente.
CREATE EXTENSION IF NOT EXISTS vector;
