-- Ponto Morto - esquema inicial.
-- SQL compatível com PostgreSQL (produção/Supabase) e H2 em modo PostgreSQL (testes automatizados).
-- Identificadores por sequência (incremento 50) para permitir gravação em lote pelo Hibernate.

CREATE SEQUENCE usuario_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE gerente_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE veiculo_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE motorista_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE local_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE roteiro_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE ponto_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE pedido_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE auditoria_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE consentimento_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE solicitacao_teste_seq START WITH 1 INCREMENT BY 50;

-- Login e perfil de acesso (RNF04). Separado dos dados de cadastro: a senha não se mistura com
-- dados pessoais e o administrador não precisa de ficha de gerente.
CREATE TABLE usuario (
    id          BIGINT PRIMARY KEY,
    login       VARCHAR(120) NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    perfil      VARCHAR(20)  NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_usuario_login UNIQUE (login),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMIN', 'GERENTE', 'MOTORISTA'))
);

-- Gerente / coordenador / dono da transportadora (RF02).
CREATE TABLE gerente (
    id          BIGINT PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    telefone    VARCHAR(20),
    email       VARCHAR(120) NOT NULL,
    usuario_id  BIGINT REFERENCES usuario (id),
    CONSTRAINT uk_gerente_usuario UNIQUE (usuario_id)
);

-- Veículo: o rendimento km/l pertence ao veículo, não à pessoa.
CREATE TABLE veiculo (
    id                   BIGINT PRIMARY KEY,
    placa                VARCHAR(10)  NOT NULL,
    tipo                 VARCHAR(20)  NOT NULL,
    modelo               VARCHAR(80),
    rendimento_km_litro  NUMERIC(6, 2) NOT NULL,
    CONSTRAINT uk_veiculo_placa UNIQUE (placa),
    CONSTRAINT ck_veiculo_rendimento CHECK (rendimento_km_litro > 0)
);

-- Motorista / motoboy (RF01). A equipe de um gerente = motoristas com gerente_id dele.
CREATE TABLE motorista (
    id           BIGINT PRIMARY KEY,
    nome         VARCHAR(120) NOT NULL,
    telefone     VARCHAR(20),
    documento    VARCHAR(20),
    veiculo_id   BIGINT REFERENCES veiculo (id),
    gerente_id   BIGINT REFERENCES gerente (id),
    usuario_id   BIGINT REFERENCES usuario (id),
    anonimizado  BOOLEAN NOT NULL DEFAULT FALSE,
    ativo        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_motorista_usuario UNIQUE (usuario_id)
);

-- Pontos cadastrados (RF03): endereços reaproveitáveis, como a base de partida.
CREATE TABLE local_cadastrado (
    id         BIGINT PRIMARY KEY,
    nome       VARCHAR(120) NOT NULL,
    endereco   VARCHAR(255) NOT NULL,
    latitude   NUMERIC(9, 6),
    longitude  NUMERIC(9, 6),
    ativo      BOOLEAN NOT NULL DEFAULT TRUE
);

-- Roteiro diário (RF04). RN05: um roteiro por motorista por data.
CREATE TABLE roteiro (
    id                             BIGINT PRIMARY KEY,
    data_roteiro                   DATE        NOT NULL,
    motorista_id                   BIGINT      NOT NULL REFERENCES motorista (id),
    status                         VARCHAR(20) NOT NULL,
    distancia_estimada_km          NUMERIC(8, 2),
    distancia_estimada_aproximada  BOOLEAN     NOT NULL DEFAULT FALSE,
    km_inicial                     NUMERIC(10, 1),
    km_final                       NUMERIC(10, 1),
    distancia_real_km              NUMERIC(8, 1),
    tempo_total_parado_seg         BIGINT      NOT NULL DEFAULT 0,
    custo_estimado                 NUMERIC(10, 2),
    preco_combustivel_usado        NUMERIC(8, 3),
    km_litro_usado                 NUMERIC(6, 2),
    custo_por_km_usado             NUMERIC(8, 3),
    criado_em                      TIMESTAMP   NOT NULL,
    CONSTRAINT uk_roteiro_motorista_data UNIQUE (motorista_id, data_roteiro),
    CONSTRAINT ck_roteiro_status CHECK (status IN ('PLANEJADO', 'EM_ANDAMENTO', 'CONCLUIDO')),
    CONSTRAINT ck_roteiro_hodometro CHECK (km_inicial IS NULL OR km_final IS NULL OR km_final > km_inicial)
);

-- Ponto do roteiro (parada). RN06: ordem sequencial única dentro do roteiro.
-- O endereço é copiado do pedido/local para que o histórico não mude se o cadastro mudar.
CREATE TABLE ponto (
    id                BIGINT PRIMARY KEY,
    roteiro_id        BIGINT       NOT NULL REFERENCES roteiro (id) ON DELETE CASCADE,
    ordem             INT          NOT NULL,
    endereco          VARCHAR(255) NOT NULL,
    latitude          NUMERIC(9, 6),
    longitude         NUMERIC(9, 6),
    chegada           TIMESTAMP,
    saida             TIMESTAMP,
    tempo_parado_seg  BIGINT,
    tempo_bruto_seg   BIGINT,
    ignorada          BOOLEAN NOT NULL DEFAULT FALSE,
    acima_do_maximo   BOOLEAN NOT NULL DEFAULT FALSE,
    local_id          BIGINT REFERENCES local_cadastrado (id),
    CONSTRAINT uk_ponto_roteiro_ordem UNIQUE (roteiro_id, ordem),
    CONSTRAINT ck_ponto_ordem CHECK (ordem >= 1),
    -- Validação: a saída não pode ser antes da chegada.
    CONSTRAINT ck_ponto_saida_apos_chegada CHECK (saida IS NULL OR chegada IS NULL OR saida >= chegada),
    -- Todo tempo parado maior que zero está ligado a uma chegada e uma saída registradas
    -- (a partida vale sempre zero, mesmo sem horários).
    CONSTRAINT ck_ponto_tempo_com_horarios CHECK (
        tempo_parado_seg IS NULL OR tempo_parado_seg = 0 OR (chegada IS NOT NULL AND saida IS NOT NULL))
);

-- Pedidos (entrada de pedidos). Vários pedidos podem cair no mesmo ponto (mesmo endereço).
CREATE TABLE pedido (
    id                BIGINT PRIMARY KEY,
    numero            VARCHAR(30)  NOT NULL,
    cliente           VARCHAR(120) NOT NULL,
    endereco_entrega  VARCHAR(255) NOT NULL,
    latitude          NUMERIC(9, 6),
    longitude         NUMERIC(9, 6),
    data_prevista     DATE         NOT NULL,
    observacao        VARCHAR(500),
    status            VARCHAR(20)  NOT NULL,
    gerente_id        BIGINT REFERENCES gerente (id),
    ponto_id          BIGINT REFERENCES ponto (id) ON DELETE SET NULL,
    criado_em         TIMESTAMP    NOT NULL,
    CONSTRAINT uk_pedido_numero UNIQUE (numero),
    CONSTRAINT ck_pedido_status CHECK (status IN ('PENDENTE', 'EM_ROTEIRO', 'ENTREGUE'))
);

-- Parâmetros de custo e de cálculo do tempo parado (RF09, RF10). Uma única linha, editável pela tela.
CREATE TABLE parametro (
    id                            BIGINT PRIMARY KEY,
    preco_combustivel             NUMERIC(8, 3) NOT NULL,
    km_litro_padrao               NUMERIC(6, 2) NOT NULL,
    custo_por_km                  NUMERIC(8, 3) NOT NULL,
    jornada_horas                 NUMERIC(4, 2) NOT NULL,
    ignorar_parada_menor_que_min  INT NOT NULL,
    tempo_maximo_parada_min       INT NOT NULL,
    atualizado_em                 TIMESTAMP,
    atualizado_por                VARCHAR(120),
    CONSTRAINT ck_parametro_valores CHECK (
        preco_combustivel >= 0 AND km_litro_padrao > 0 AND custo_por_km >= 0
        AND jornada_horas > 0 AND jornada_horas <= 24
        AND ignorar_parada_menor_que_min >= 0 AND tempo_maximo_parada_min >= 0)
);

INSERT INTO parametro (id, preco_combustivel, km_litro_padrao, custo_por_km, jornada_horas,
                       ignorar_parada_menor_que_min, tempo_maximo_parada_min)
VALUES (1, 6.290, 12.00, 0.450, 8.00, 1, 240);

-- Auditoria (RNF05): quem alterou, quando, valor antigo e valor novo, campo a campo.
-- gerente_id guarda a equipe envolvida para o gerente ver só o que é dele (sem chave estrangeira,
-- para o registro sobreviver à exclusão do cadastro).
CREATE TABLE auditoria (
    id            BIGINT PRIMARY KEY,
    data_hora     TIMESTAMP    NOT NULL,
    usuario       VARCHAR(120) NOT NULL,
    acao          VARCHAR(20)  NOT NULL,
    entidade      VARCHAR(40)  NOT NULL,
    entidade_id   BIGINT,
    descricao     VARCHAR(255),
    campo         VARCHAR(60),
    valor_antigo  VARCHAR(500),
    valor_novo    VARCHAR(500),
    motivo        VARCHAR(255),
    gerente_id    BIGINT
);

-- Consentimento do aviso de privacidade (RNF06 / LGPD).
CREATE TABLE consentimento (
    id            BIGINT PRIMARY KEY,
    usuario_id    BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    versao_aviso  VARCHAR(10) NOT NULL,
    aceito_em     TIMESTAMP   NOT NULL
);

-- Pedidos de teste grátis vindos da landing page.
CREATE TABLE solicitacao_teste (
    id              BIGINT PRIMARY KEY,
    nome            VARCHAR(120) NOT NULL,
    empresa         VARCHAR(120) NOT NULL,
    email           VARCHAR(120) NOT NULL,
    telefone        VARCHAR(20),
    tamanho_frota   INT,
    consentimento   BOOLEAN   NOT NULL,
    criado_em       TIMESTAMP NOT NULL
);
