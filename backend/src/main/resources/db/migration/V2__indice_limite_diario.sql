-- V2: suporte ao limite diário de transferências.
-- O serviço soma os débitos de transferência de uma conta desde o início do dia.
-- Este índice permite essa soma sem percorrer todos os movimentos da conta.
-- Nunca editar a V1: alterações ao esquema entram sempre numa nova versão.

CREATE INDEX idx_movements_daily_debits
    ON movements (account_id, movement_type, created_at);
