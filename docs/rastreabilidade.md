# Rastreabilidade — onde cada requisito foi atendido

Caminhos a partir da raiz do projeto.

- `…/` = `src/main/java/br/pucminas/pontomorto/`
- `T/` = `src/test/java/br/pucminas/pontomorto/`
- `R/` = `src/main/resources/`

## Regras de negócio
| Regra | Implementação | Teste |
|---|---|---|
| RN01 Partida não conta tempo parado | `…/regras/CalculadoraTempoParado.java` (`calcularParada`), `…/dominio/Ponto.java` (`isPartida`) | `T/regras/CalculadoraTempoParadoTest.java` (Rn01), `T/aceitacao/CriteriosAceitacaoTest.java` (CA1) |
| RN02 Tempo = saída − chegada (com meia-noite) | `…/regras/CalculadoraTempoParado.java` (`tempoParado`, `validarHorarios`) | `CalculadoraTempoParadoTest` (Rn02), `T/coleta/ColetaServiceTest.java` (`correcaoMeiaNoite`, `saidaAntesDaChegada`) |
| RN03 Total do roteiro sem a partida | `CalculadoraTempoParado.totalDoRoteiro`, `…/servico/CalculoRoteiroService.java` | `CalculadoraTempoParadoTest` (Rn03: roteiros A = 75, B = 41, C = 45 min) |
| RN04 Jornada padrão de 8 h, base do % | `CalculadoraTempoParado.percentualDaJornada`, `…/dominio/Parametro.java` | `CalculadoraTempoParadoTest` (Rn04), CA4 |
| RN05 Um roteiro por motorista por data | `…/servico/RoteiroService.java` (`verificarDuplicidade`), `R/db/migration/V1__esquema.sql` (`uk_roteiro_motorista_data`) | `T/regras/RoteiroRegrasTest.java` (rn05), `T/persistencia/RoteiroPersistenciaTest.java` |
| RN06 Ordem sequencial 1, 2, 3... | `…/dominio/Roteiro.java` (`adicionarPonto`, `mover`, `renumerar`), `RoteiroService.persistirOrdem`, `V1__esquema.sql` (`uk_ponto_roteiro_ordem`) | `RoteiroRegrasTest` (rn06), `RoteiroPersistenciaTest` |
| RN07 Custo = (dist ÷ km/l × combustível) + (dist × custo/km) | `…/regras/CalculadoraCusto.java`, `CalculoRoteiroService` | `T/regras/CalculadoraCustoTest.java`, `RoteiroRegrasTest` (rn07) |
| Validação: saída ≥ chegada | `CalculadoraTempoParado.validarHorarios`, `V1__esquema.sql` (`ck_ponto_saida_apos_chegada`) | `CalculadoraTempoParadoTest`, `ColetaServiceTest`, `RoteiroPersistenciaTest` |
| Distância estimada (OSRM / Haversine) e real (hodômetro) | `…/servico/DistanciaService.java`, `…/regras/CalculadoraDistancia.java`, `Roteiro.definirHodometro` | `T/regras/CalculadoraDistanciaTest.java`, `ColetaServiceTest` (`hodometro`) |

## Requisitos funcionais
| RF | Onde | Tela |
|---|---|---|
| RF01 Cadastro de motorista (CRUD) | `…/servico/MotoristaService.java`, `…/web/CadastroController.java` | `R/templates/motoristas/lista.html`, `form.html` |
| RF02 Cadastro de gerente e equipe | `…/servico/GerenteService.java`, `CadastroController` | `R/templates/gerentes/lista.html`, `form.html` |
| RF03 Pontos com endereço e coordenadas (Nominatim + ajuste manual) | `…/servico/LocalService.java`, `…/servico/GeocodificacaoService.java`, `…/web/PainelApiController.java` (`/api/geocodificar`) | `R/templates/locais/*.html`, `R/templates/fragmentos/geo.html`, `R/static/js/mapa.js` |
| RF04 Montar roteiro, ligar pontos, reordenar | `…/servico/RoteiroService.java`, `…/web/RoteiroController.java` | `R/templates/roteiros/novo.html`, `detalhe.html` |
| RF05 Registrar chegada/saída (Cheguei/Saí, correção manual) | `…/servico/ColetaService.java`, `…/web/MotoristaAppController.java` | `R/templates/motorista/hoje.html` |
| RF06 Cálculo automático do tempo parado | `…/servico/CalculoRoteiroService.java` (chamado a cada registro) | todas as telas de roteiro |
| RF07 Histórico por período, com endereços, filtrável | `…/servico/HistoricoService.java`, `…/repositorio/PontoRepository.java`, `…/web/HistoricoController.java` | `R/templates/historico.html` |
| RF08 Dashboard (dia, mês, período, ranking, indicadores) | `…/servico/DashboardService.java`, `…/repositorio/RoteiroRepository.java` (consultas agregadas), `…/web/PainelController.java`, `PainelApiController` | `R/templates/painel.html`, `R/static/js/painel.js` |
| RF09 Parâmetros de custo | `…/servico/ParametroService.java`, `…/web/ParametroController.java` | `R/templates/parametros.html` |
| RF10 Regras do tempo parado e jornada | `…/regras/ParametrosTempo.java`, `CalculadoraTempoParado.calcularParada` | `parametros.html`; teste `CalculadoraTempoParadoTest` (Rf10) |
| RF11 Custo estimado do roteiro | `CalculadoraCusto`, `CalculoRoteiroService` | `roteiros/detalhe.html` (fórmula), painel |
| RF12 Exportar CSV e PDF | `…/servico/RelatorioService.java`, `HistoricoController` | botões em `historico.html`; teste `T/web/TelasTest.java` (`exportacao`) |

## Requisitos não funcionais
| RNF | Onde | Teste |
|---|---|---|
| RNF01 Banco relacional com histórico completo | PostgreSQL/Supabase, `R/db/migration/V1__esquema.sql`, `R/application.yml`, `docker-compose.yml`; o endereço é copiado para o ponto (histórico não muda) | `RoteiroPersistenciaTest` |
| RNF02 Interface responsiva (computador e celular) | `R/static/css/ponto-morto.css`, `motorista/hoje.html` | verificação visual em 390 px e 1366 px |
| RNF03 Painel < 3 s para 12 meses | índices em `R/db/migration/V2__indices.sql`, consultas agregadas em `RoteiroRepository`/`PontoRepository`, gerador `…/config/GeradorHistorico.java` | `T/desempenho/DesempenhoPainelTest.java` (período de 12 meses em ~230 ms) |
| RNF04 Login com senha criptografada e perfis | `…/seguranca/SecurityConfig.java` (BCrypt, regras por URL), `…/seguranca/ControleAcesso.java` (equipe), `UsuarioDetailsService` | `T/seguranca/ControleAcessoTest.java`, `ColetaServiceTest` (`naoMexeNoRoteiroDosOutros`) |
| RNF05 Auditoria (quem, quando, antigo, novo) | `…/servico/AuditoriaService.java`, `…/dominio/Auditoria.java`, chamadas em `ColetaService`, `RoteiroService`, `PedidoService`, `LocalService`, `ParametroService`... | `ColetaServiceTest` (`auditoriaDaCorrecao`); tela `R/templates/auditoria.html` |
| RNF06 LGPD (aviso e consentimento, documento mascarado, anonimizar/excluir, dados mínimos) | `…/servico/LgpdService.java`, `…/web/ConsentimentoInterceptor.java`, `…/web/LgpdController.java`, `Motorista.mascarar`, `R/templates/fragmentos/aviso.html` | `T/lgpd/LgpdTest.java` |

## Critérios de aceitação
| Critério | Teste |
|---|---|
| CA1 Não computa tempo parado no ponto de partida | `CriteriosAceitacaoTest.ca1PartidaNaoConta`, `ca1PainelSemPartida` |
| CA2 Dashboard mostra dia, mês e período | `CriteriosAceitacaoTest.ca2TresRecortes` |
| CA3 Todo tempo parado ligado a endereço e data/hora | `CriteriosAceitacaoTest.ca3TempoLigadoAEnderecoEHorario`; restrição `ck_ponto_tempo_com_horarios` em `V1__esquema.sql` |
| CA4 Parâmetros alterados pela tela, sem mudar código | `CriteriosAceitacaoTest.ca4ParametrosPelaTela` |

## Entregáveis (seção 9 do enunciado)
| Entregável | Onde |
|---|---|
| Dashboard por dia, mês e período | `R/templates/painel.html`, `…/servico/DashboardService.java` |
| Indicadores de custo do trajeto (seção 2: custo por km percorrido e consumo km/litro) | cartões "Custo por km rodado" e "Consumo médio" em `R/templates/painel.html` e `R/static/js/painel.js`; somas em `…/repositorio/RoteiroRepository.java` e cálculo em `DashboardService`; teste `CriteriosAceitacaoTest.indicadoresDeCustoDoTrajeto` |
| Histórico de pontos e tempos com endereços | `R/templates/historico.html`, `…/servico/HistoricoService.java` |
| Coleta dos pontos com entrada de pedidos (geocodificação, pedidos → pontos, vários pedidos no mesmo ponto) | `…/servico/PedidoService.java`, `…/servico/RoteiroService.java` (`incluirPedidos`), `R/templates/pedidos/*.html`, `roteiros/novo.html`, `motorista/hoje.html` |
| Parâmetros de custo | `R/templates/parametros.html` (bloco RF09) |
| Parâmetros do tempo parado, jornada de 8 h | `R/templates/parametros.html` (bloco RF10), `V1__esquema.sql` (valor inicial 8 h) |
| Persistência (pontos, pedidos, roteiros, motorista, gerente) | `…/dominio/`, `…/repositorio/`, `R/db/migration/` |
| Diagrama de casos de uso (include/extend) | `docs/diagramas/casos-de-uso.puml` + `.png` |
| Diagramas de robustez (5 casos de uso) | `docs/diagramas/robustez-01…05-*.puml` + `.png` |
| Diagrama de classes conceitual | `docs/diagramas/classes-conceitual.puml` + `.png` |
| Descrição textual dos casos de uso | `docs/especificacao.md` |
| README em português | `README.md` |

## Pontos extras
| Extra | Onde |
|---|---|
| Nome e logotipo em SVG | `R/static/img/logo.svg`, `R/static/favicon.svg` |
| Landing page (slogan, problema, 3 passos, números, teste grátis) | `R/templates/landing.html`, `…/web/PublicoController.java` |
| 3 posts (texto + arte SVG) | `docs/campanha/posts.md`, `post-1-todo-minuto.svg`, `post-2-numeros.svg`, `post-3-cheguei-sai.svg` |
| Roteiro de vídeo de 30 s | `docs/campanha/roteiro-video-30s.md` |
| Vídeo de 30 s (animado, vertical) | `docs/campanha/video-30s.mp4`, feito a partir de `docs/campanha/video-30s.html` |
| E-mail de lançamento | `docs/campanha/email-lancamento.md` |
| Estrada horizontal com ponto proporcional ao tempo parado | `R/templates/fragmentos/estrada.html`, `…/web/EstradaView.java` |
| Modo escuro | `R/static/css/ponto-morto.css`, `R/static/js/tema.js` |
