-- ===================================================
-- V4: Token de aprovação de uso único da OS.
--
-- O canal público de aprovação/recusa era autorizado apenas por número da OS
-- (sequencial, portanto enumerável) + CPF/CNPJ (dado amplamente conhecido).
-- Passa a exigir um segredo de 256 bits gerado no envio do orçamento, entregue
-- somente no e-mail do cliente e invalidado no primeiro uso.
-- ===================================================

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS approval_token VARCHAR(64);

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS approval_token_consumed_at TIMESTAMP;

-- A busca pela OS continua sendo por order_number; o índice existe para o caso
-- de auditoria/suporte precisar localizar a OS a partir do link recebido.
CREATE INDEX IF NOT EXISTS idx_work_orders_approval_token
    ON work_orders(approval_token);
