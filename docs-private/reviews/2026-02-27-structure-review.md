# Revisão de Estrutura - 2026-02-27

## Pontos fortes atuais
- Separação de módulos clara (`api`, `consumer`, `worker`, `core`).
- Fronteiras de Clean Architecture preservadas entre domínio, casos de uso e adapters.
- Base de observabilidade local consistente (Prometheus, Grafana, Jaeger).
- Segurança da API evoluída com autenticação por API Key/JWT e escopos por cliente.
- Contrato de erro padronizado em `application/problem+json` com correlação por `traceId`.

## Achados e ações aplicadas

1. **Setup Kafka duplicado**
   - Ação aplicada: `KafkaClientFactory` compartilhado no core.

2. **Risco de commit de offset em ponto inadequado**
   - Ação aplicada: commit por registro processado, com DLQ explícita para erro de parse.

3. **Encerramento incompleto de recursos**
   - Ação aplicada: hooks de shutdown para consumers, producer e HTTP client.

4. **Acoplamento entre persistência e publicação**
   - Ação aplicada: outbox transacional + dispatcher no worker.

5. **Maturidade de retry e erro**
   - Ação aplicada: retry por tipo de notificação e taxonomia de erro padronizada.

6. **Duplicação de wiring entre serviços**
   - Ação aplicada: `BootstrapFactory` centralizando criação de componentes compartilhados.

7. **Tracing distribuído incompleto**
   - Ação aplicada: propagação W3C no publisher Kafka e continuação de spans no consumer/worker.

8. **Observabilidade com baixa granularidade de erro e canal**
   - Ação aplicada: métricas por `type`, `outcome` e `error_category`, com latência (`P95`) por fluxo.

9. **Ausência de guardrails operacionais para regressão em produção**
   - Ação aplicada: regras de alertas no Prometheus + painéis SLO no Grafana + workflow de chaos nightly (não bloqueante).

10. **Fragilidade de autenticação para rotação de credenciais**
    - Ação aplicada: suporte a múltiplas `apiKeys` por cliente (rotação) e autenticação JWT Bearer com `issuer`/`audience`.

11. **Instabilidade no ambiente local de mensageria**
    - Ação aplicada: padronização do stack Docker para `confluentinc/cp-kafka:7.6.1`, ajuste de variáveis KRaft e compatibilização do init de tópicos.

## Como validar a estrutura atual
- Executar `gradle test` para validar contratos e integração.
- Subir stack local (`docker compose up --build -d`) e verificar saúde de API, Kafka e Postgres.
- Enviar notificação pela API e confirmar fluxo em `send` → `retry`/`dlq` conforme cenário.
- Abrir Jaeger e validar um trace contínuo entre API, consumer e worker.
- Conferir no Grafana métricas por canal/categoria de erro, retries, DLQ e latência P95.
- Validar execução de `kafka-init-topics` e status `healthy` do broker no `docker compose ps`.

## Dívida técnica remanescente
- Integrar segredo de API/JWT com secret manager e rotação sem restart (próxima fase do item 16).
- Publicar política formal de versionamento/depreciação e OpenAPI por versão.
- Aumentar cobertura de testes de integração ponta a ponta para fluxos de retry/DLQ e autorização JWT.
- Reduzir acoplamento residual em `Application.kt` movendo mais wiring para bootstrap compartilhado.
- Definir roteamento de alertas (Slack/PagerDuty) e playbook de escalonamento operacional.
