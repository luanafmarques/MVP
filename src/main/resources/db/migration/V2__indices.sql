-- Índices para o painel e o histórico responderem rápido em consultas de até 12 meses (RNF03).
-- (motorista_id, data_roteiro) e (roteiro_id, ordem) já têm índice pelas restrições UNIQUE.
CREATE INDEX ix_roteiro_data ON roteiro (data_roteiro, status);
CREATE INDEX ix_ponto_endereco ON ponto (endereco);
CREATE INDEX ix_ponto_local ON ponto (local_id);
CREATE INDEX ix_pedido_data_status ON pedido (data_prevista, status);
CREATE INDEX ix_pedido_ponto ON pedido (ponto_id);
CREATE INDEX ix_pedido_gerente ON pedido (gerente_id);
CREATE INDEX ix_motorista_gerente ON motorista (gerente_id);
CREATE INDEX ix_motorista_veiculo ON motorista (veiculo_id);
CREATE INDEX ix_auditoria_data ON auditoria (data_hora);
CREATE INDEX ix_auditoria_gerente ON auditoria (gerente_id, data_hora);
CREATE INDEX ix_auditoria_entidade ON auditoria (entidade, entidade_id);
CREATE INDEX ix_consentimento_usuario ON consentimento (usuario_id);
