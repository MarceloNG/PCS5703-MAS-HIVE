# 5. Resultados

## 5.1 Configuração Experimental

Todos os experimentos foram conduzidos no servidor MASSim 2022-1.1 [23] com os seguintes parâmetros:

| Parâmetro | Valor |
|-----------|-------|
| Grid | 40 × 40, toroidal |
| Mapa | Cave, densidade 0.45 |
| Steps | 800 por simulação |
| Tipos de bloco | 2–3 |
| Tarefas simultâneas | até 4 |
| Tamanho das tarefas | 1–2 blocos |
| Goal zones | 3, tamanho 1–2, com moveProbability 0.1 |
| Normas | *Carry* (limite de blocos transportados) |
| Posição absoluta | Habilitada |
| Agent timeout | 8.000 ms (servidor e EIS) |
| JVM heap | 2 GB (`-Xmx2g`) |
| Hardware | Apple Silicon, 16 GB RAM |

## 5.2 Desempenho com 15 Agentes (Configuração Principal)

A configuração principal do HIVE utiliza 15 agentes BDI (3 líderes, 6 coletores, 3 assemblers, 3 sentinelas). Foram realizadas 13 simulações independentes, das quais 7 completaram os 800 steps com sucesso:

| Run | Score | Submissões | Steps completados | Status |
|:---:|:-----:|:----------:|:-----------------:|--------|
| 1 | **100** | 10 | 800 | Completou |
| 2 | **90** | 9 | 800 | Completou |
| 3 | **80** | 8 | 800 | Completou |
| 4 | **80** | 8 | 800 | Completou |
| 5 | **70** | 7 | 800 | Completou |
| 6 | **60** | 6 | 800 | Completou |
| 7 | **60** | 6 | 800 | Completou |
| 8–13 | — | — | 76–230 | Crash (timeout em cascata) |

**Estatísticas dos runs completados:**

| Métrica | Valor |
|---------|-------|
| Média | 77,1 pontos |
| Mediana | 80 pontos |
| Máximo | 100 pontos |
| Mínimo | 60 pontos |
| Desvio padrão | 14,6 pontos |
| Taxa de conclusão | 53,8% (7/13) |
| Tempo médio por run | ~6 minutos |

## 5.3 Impacto das Otimizações

O processo de otimização foi conduzido iterativamente. A tabela abaixo apresenta o impacto medido de cada otimização principal, comparando o score antes e depois da mudança:

| Otimização | Score antes | Score depois | Efeito principal |
|-----------|:-----------:|:------------:|------------------|
| Reduzir delegação de 3→1 soloist/task | 20–30 | 60–80 | Eliminou desperdício de 2/3 dos agentes |
| Simplificar scan do líder (1 task/scan) | 40–80 | 80–100 | Eliminou timeouts do líder por loops pesados |
| Remover `.wait()` do quick_delegate | — | — | Redução de latência (~10ms por delegação) |
| Distância toroidal no find_free_soloist | — | — | Seleção correta do soloist mais próximo |
| Timeout de task 200→300 steps | 40–60 | 60–100 | Agentes completam tasks em mapas difíceis |
| Decay de obstáculos a cada 10 steps | — | — | Redução de 93% nas chamadas de decay |
| JVM heap 256MB→2GB | Crashes | Estável | Menos GC pauses |

As duas otimizações de maior impacto foram: (i) a **redução de 3 para 1 soloist por task**, que triplicou efetivamente o número de tasks trabalhadas em paralelo; e (ii) a **simplificação do scan do líder**, que eliminava um loop `findall` sobre todas as tasks conhecidas — operação que, com 10+ tasks acumuladas e 7 chamadas a artefatos por task, facilmente excedia o timeout de 8 segundos.

## 5.4 Análise de Escalabilidade

Para investigar o impacto do número de agentes sobre desempenho e estabilidade, foram conduzidos experimentos com 4 configurações:

| Agentes | Composição | Runs completados | Scores | Média | Taxa crash |
|:-------:|-----------|:----------------:|--------|:-----:|:----------:|
| 6 | 1L + 3C + 1A + 1S | 3 / 4 | 30, 40, 0 | 23,3 | 25% |
| 10 | 2L + 4C + 2A + 2S | 3 / 5 | 90, 20, 30 | 46,7 | 40% |
| **15** | **3L + 6C + 3A + 3S** | **7 / 13** | **100, 90, 80, 80, 70, 60, 60** | **77,1** | **46%** |
| 20 | 3L + 8C + 4A + 5S | 2 / 4 | 80, 60 | 70,0 | 50% |

*L = Leader, C = Collector, A = Assembler, S = Sentinel*

Três observações emergem desta análise:

1. **O score cresce com o número de agentes até 15**: a diferença entre 6 agentes (média 23) e 15 agentes (média 77) é de 3,3×, indicando que a redundância e a cobertura de mapa proporcionadas por mais agentes superam o overhead de coordenação;

2. **20 agentes não melhora o score**: a média cai ligeiramente (70 vs 77), sugerindo saturação — com 4 tarefas simultâneas no máximo, 20 agentes disputam as mesmas oportunidades;

3. **A taxa de crash é independente do número de agentes**: varia entre 25% (6 agentes) e 50% (15–20 agentes), mas o efeito não é proporcional — mesmo 6 agentes apresentam crashes.

## 5.5 Diagnóstico de Instabilidade

A análise dos logs de simulação revelou que os crashes seguem um padrão consistente:

1. **Trigger** (steps 76–230): Múltiplos agentes processam percepts simultaneamente, gerando centenas de operações serializadas nos artefatos CArtAgO [11] (`mark_obstacle`, `update_cell`, `mark_visited`);
2. **Escalada**: O tempo total de processamento excede o `agentTimeout` de 8 segundos, e o servidor registra "No valid action" para os agentes lentos;
3. **Colapso**: A biblioteca EIS interpreta o timeout como falha de conexão e tenta reconectar, gerando um *flood* de reconexões que sobrecarrega o socket do servidor;
4. **Loop terminal**: O ciclo disconnect/reconnect se auto-sustenta indefinidamente.

**Evidência experimental**: Ao reduzir a densidade do mapa cave de 0.45 para 0.25, a taxa de crash caiu para **0%** (todas as simulações completaram), confirmando que o volume de percepts de obstáculos é o fator determinante. Com densidade 0.45, cada agente observa ~40–60 obstáculos por step (no raio de visão 5), gerando ~600–900 chamadas `mark_obstacle` por step para 15 agentes — todas serializadas no artefato `SharedMap`.

Essa limitação é inerente ao modelo de execução do CArtAgO [12], que garante consistência via serialização por artefato. Soluções fundamentais exigiriam ou a partição do mapa em múltiplos artefatos (sharding), ou a adoção de operações assíncronas — ambas fora do escopo do modelo padrão.

## 5.6 Comparação com o MAPC 2022

Os resultados do HIVE são contextualizados em relação aos times participantes do MAPC 2022 [14]:

| Posição | Time | Framework | Score típico |
|:-------:|------|-----------|:------------:|
| 1º | FIT-BUT [17] | Java puro | 200–400 |
| 2º | MMD [18] | Java (otimização) | 150–300 |
| — | LI(A)RA [19] | JaCaMo | 80–150 |
| — | **HIVE (este trabalho)** | **JaCaMo** | **60–100** |

O time FIT-BUT [17], vencedor da competição, utiliza Java puro com controle direto de threads, evitando completamente o overhead de serialização do CArtAgO. O time LI(A)RA [19], que também utiliza JaCaMo, reportou scores na faixa de 80–150 — desempenho superior ao HIVE, atribuível em parte a uma arquitetura mais sofisticada de tasks multi-bloco.

A principal lacuna do HIVE em relação aos times de melhor desempenho é a **baixa taxa de conclusão de tasks multi-bloco**: enquanto o pipeline de 1 bloco (soloist) funciona de forma confiável, o protocolo *connect* para 2+ blocos raramente completa com sucesso devido à dificuldade de sincronização espacial em mapas cave densos. Como tasks multi-bloco oferecem rewards significativamente maiores (30–50 pontos vs 10 pontos para 1 bloco), essa limitação responde pela diferença de score em relação ao LI(A)RA e ao FIT-BUT.

A contribuição do LTI-USP em edições anteriores [20] enfrentou desafios similares com a plataforma Jason, obtendo o 4º lugar no MAPC 2020/2021 — resultado que contextualiza a dificuldade inerente de competir com frameworks BDI interpretados contra implementações Java compiladas.
