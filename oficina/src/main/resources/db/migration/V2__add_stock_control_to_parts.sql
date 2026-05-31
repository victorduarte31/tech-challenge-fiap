-- ===================================================
-- V2: Controle de estoque por peça (estoque mínimo)
--     e distinção entre peça e insumo
-- ===================================================

ALTER TABLE parts
    ADD COLUMN IF NOT EXISTS minimum_stock INT          NOT NULL DEFAULT 0;

ALTER TABLE parts
    ADD COLUMN IF NOT EXISTS part_type     VARCHAR(10)  NOT NULL DEFAULT 'PECA';

-- Índice para consultas de reposição (peças com estoque <= mínimo)
CREATE INDEX IF NOT EXISTS idx_parts_low_stock ON parts(stock_quantity, minimum_stock);
