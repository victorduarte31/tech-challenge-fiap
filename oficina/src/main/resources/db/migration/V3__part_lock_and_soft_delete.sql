-- ===================================================
-- V3: Lock no estoque de peças (evita lost update
--     em débitos concorrentes) e soft-delete de peças
--     (consistente com service_items.active).
-- ===================================================

-- coluna de versão gerenciada pelo Hibernate (@Version)
ALTER TABLE parts
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Soft-delete: peça desativada deixa de aparecer no catálogo e nos alertas
ALTER TABLE parts
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Alerta de reposição passa a considerar apenas peças ativas
DROP INDEX IF EXISTS idx_parts_low_stock;
CREATE INDEX IF NOT EXISTS idx_parts_low_stock ON parts(active, stock_quantity, minimum_stock);
