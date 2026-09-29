# Ponto Morto

**Todo minuto parado tem endereço.**

O Ponto Morto mostra onde e por quanto tempo motoristas e motoboys ficam parados em cada ponto do roteiro do dia, e quanto isso custa. É o MVP do 2º trabalho de Engenharia de Software II (PUC Minas, prof. Sandro Laudares).

**Equipe:** Luana Ferreira Marques e Manuela Edmundo Moss.

- Painel com gráficos de tempo parado por **dia**, **mês** e **período**, ranking de endereços, % da jornada e custo estimado.
- **Entrada de pedidos** com geocodificação (Nominatim/OpenStreetMap). O roteiro é montado a partir dos pedidos do dia.
- **Tela do motorista** para celular, com os botões grandes **Cheguei** e **Saí**.
- Histórico com exportação em **CSV** e **PDF**, parâmetros de custo e jornada pela tela, auditoria e LGPD.

## Tecnologias
Java 17 · Spring Boot 3.5 (Web, Thymeleaf, Security, Data JPA/Hibernate, Validation) · PostgreSQL (Supabase) · Flyway · Chart.js · Leaflet · OpenPDF · JUnit 5 · Docker Compose (alternativa).

## Como rodar

### Requisitos
- **JDK 17 ou mais novo.** Confira com `java -version`. Se o `java` do PATH for antigo, aponte a variável `JAVA_HOME` para um JDK novo.
- Não precisa instalar o Maven: o projeto usa o Maven Wrapper (`mvnw` / `mvnw.cmd`).
- Um banco PostgreSQL: o **Supabase** (recomendado) ou o **Docker Compose**.

### 1. Banco no Supabase (recomendado)
1. Crie um projeto no [Supabase](https://supabase.com).
2. Copie o arquivo de exemplo de configuração:
   ```powershell
   copy .env.example .env
   ```
3. Preencha o `.env` com os dados de **Project Settings → Database** (ou do botão **Connect**):
   ```properties
   DB_URL=jdbc:postgresql://db.SEU_PROJECT_REF.supabase.co:5432/postgres?sslmode=require
   DB_USER=postgres
   DB_PASSWORD=sua_senha_do_banco
   ```
   - A conexão direta (`db.<ref>.supabase.co`) precisa de rede com **IPv6**.
   - Sem IPv6, use o **Session pooler** (porta 5432), com usuário `postgres.<ref>`. Veja os exemplos no `.env.example`.
4. O `.env` **não vai para o Git** (está no `.gitignore`).

Na primeira execução, o **Flyway** cria as tabelas no esquema próprio `pontomorto`. Ele fica fora do esquema `public`, que o Supabase expõe na API REST automática.

### 2. Alternativa: PostgreSQL com Docker
```bash
docker compose up -d          # PostgreSQL 17 na porta 5433
```
Sem `.env`, o sistema já aponta para esse banco (`localhost:5433`, usuário e senha `pontomorto`).

### 3. Subir o sistema
```powershell
.\mvnw.cmd spring-boot:run          # Windows
./mvnw spring-boot:run              # Linux / macOS
```
Abra **http://localhost:8080**. Se a porta estiver ocupada, use `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`.

Quando o banco está vazio, o sistema carrega os **dados de exemplo**:

- os roteiros **A, B e C** do enunciado (ontem);
- roteiros planejados para hoje;
- pedidos pendentes;
- 12 meses de histórico, para os gráficos.

No Supabase isso leva cerca de 1 a 2 minutos na primeira vez.

### Contas de exemplo
| Perfil | Login | Senha |
|---|---|---|
| Administrador | `admin@pontomorto.com.br` | `admin123` |
| Gerente | `gerente@pontomorto.com.br` | `gerente123` |
| Motoristas | `joao@`, `maria@`, `pedro@pontomorto.com.br` | `motorista123` |

No primeiro acesso, o motorista vê o aviso de privacidade (LGPD). Use o celular ou o modo responsivo do navegador para ver a tela dele.

## Testes
```powershell
.\mvnw.cmd test
```
Os testes usam o banco **H2 em memória** (modo PostgreSQL), com as mesmas migrações. Não precisam de internet nem de banco instalado. São 68 testes:

- **Regras de negócio RN01 a RN07** e validações (saída antes da chegada, parada que passa da meia-noite, hodômetro): `src/test/java/.../regras`.
- **Critérios de aceitação** CA1 a CA4: `src/test/java/.../aceitacao/CriteriosAceitacaoTest.java`.
- Coleta, auditoria, segurança por perfil, LGPD, telas, exportação CSV/PDF.
- **Desempenho (RNF03):** 12 meses de dados (cerca de 6.500 roteiros e 39 mil pontos); o painel precisa responder em menos de 3 s.

### Demonstração rápida sem banco
Para abrir o sistema **só para ver as telas**, sem PostgreSQL, há um modo com H2 em memória. Os dados somem ao parar:
```powershell
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.mainClass=br.pucminas.pontomorto.PontoMortoComH2"
```

## Estrutura
```
src/main/java/br/pucminas/pontomorto/
  dominio/      entidades JPA (Roteiro, Ponto, Pedido, Motorista, Veiculo, Gerente, Usuario, Parametro, Auditoria...)
  regras/       cálculos puros das regras de negócio (tempo parado, custo, distância)
  repositorio/  Spring Data, com as consultas agregadas do painel
  servico/      casos de uso (roteiro, coleta, painel, histórico, parâmetros, auditoria, LGPD, geocodificação, OSRM)
  web/          controllers das telas Thymeleaf e API JSON dos gráficos
  seguranca/    login, perfis e acesso por equipe
  config/       dados de exemplo e gerador de histórico
src/main/resources/
  db/migration/ migrações Flyway (V1 esquema, V2 índices)
  templates/    telas Thymeleaf
  static/       CSS (identidade visual, modo escuro), JS (painel, mapa), logo SVG
docs/
  especificacao.md     casos de uso, robustez e classes (Projeto Preliminar)
  rastreabilidade.md   onde cada RN, RF, RNF, entregável e critério foi atendido
  diagramas/           PlantUML (.puml) + imagens (.png)
  campanha/            3 posts (texto + SVG), roteiro de vídeo de 30 s, e-mail de lançamento
```

Para gerar de novo as imagens dos diagramas: `powershell -ExecutionPolicy Bypass -File docs\gerar-diagramas.ps1`.

## Serviços externos
- **Nominatim (OpenStreetMap):** endereço → latitude/longitude. No máximo 1 consulta por segundo, com cache.
- **OSRM:** distância pelas ruas entre os pontos. Se não responder, o sistema usa a linha reta (Haversine) e marca a distância como aproximada.
- Os dois são gratuitos e pensados para uso leve. Para produção, use instâncias próprias.

As coordenadas dos dados de exemplo são aproximadas e servem só para demonstração.
