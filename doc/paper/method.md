# 4. Metodologia

## 4.1 Abordagem Geral

O desenvolvimento do HIVE seguiu o paradigma de **Programação Orientada a Multi-Agentes** (MAOP — *Multi-Agent Oriented Programming*), conforme formalizado por Boissier et al. [6]. A MAOP estrutura a construção de SMA em três dimensões ortogonais e complementares: (i) a dimensão de **agentes**, responsável pela lógica deliberativa individual; (ii) a dimensão de **ambiente**, que encapsula recursos e ferramentas compartilhadas; e (iii) a dimensão de **organização**, que especifica restrições sociais sobre o comportamento coletivo [7].

A plataforma concreta utilizada foi o **JaCaMo** [6], que integra três tecnologias distintas sob o modelo MAOP:

- **Jason** [8] para a programação dos agentes em AgentSpeak(L), uma linguagem baseada na arquitetura BDI;
- **CArtAgO** [11] para a programação do ambiente computacional por meio de artefatos;
- **MOISE+** [9] para a especificação organizacional normativa.

Essa escolha se justifica pela aderência ao enunciado da disciplina, que exige explicitamente o uso de JaCaMo, e pela maturidade da plataforma em competições do MAPC [20, 21].

## 4.2 Arquitetura dos Agentes — Modelo BDI

Os 15 agentes do HIVE seguem a arquitetura **BDI** (*Belief-Desire-Intention*), fundamentada teoricamente por Bratman [3] e formalizada computacionalmente por Rao e Georgeff [4]. Nesse modelo, o comportamento racional emerge da interação entre três atitudes mentais:

- **Crenças** (*Beliefs*): representação do estado do mundo percebido pelo agente — posição, mapa parcial, obstáculos, dispensers, goal zones, tarefas ativas e estados dos demais agentes;
- **Desejos** (*Desires*): objetivos de alto nível — explorar o mapa, coletar blocos, navegar até goal zones, submeter tarefas;
- **Intenções** (*Intentions*): compromissos com planos de ação selecionados que persistem até serem completados, tornados impossíveis ou explicitamente abandonados.

Seguindo Bratman [3], as intenções desempenham papel crucial no HIVE como **filtros de admissibilidade**: uma vez que um agente se compromete com a coleta de um bloco específico, ele não reconsidera essa decisão a cada ciclo de raciocínio, reduzindo drasticamente o custo computacional — aspecto essencial dado o limite de tempo de 8 segundos por step imposto pelo servidor MASSim.

A implementação em **AgentSpeak(L)** via Jason [8] traduz esse modelo em construtos concretos: crenças são literais na base de crenças do agente, desejos correspondem a eventos gatilho (`+!goal`), e intenções são instâncias de planos selecionados e em execução. O ciclo de raciocínio do Jason — percepção → atualização de crenças → seleção de eventos → unificação de planos → execução — implementa o *loop* deliberativo BDI descrito por Rao e Georgeff [4].

### 4.2.1 Arquitetura Híbrida em Camadas

Embora puramente BDI em sua implementação, o comportamento dos agentes do HIVE exibe uma **organização em camadas de prioridade** inspirada na arquitetura de subsunção [1]:

| Prioridade | Camada | Comportamento | Arquivo |
|:---:|---|---|---|
| 0 | Sobrevivência | Desativação, energia crítica | `connect_protocol.asl` |
| 1 | Normas | Detach de blocos excedentes (norma *Carry*) | `connect_protocol.asl` |
| 2 | Submissão | Submit de tarefas em goal zone | `connect_protocol.asl` |
| 3 | Conexão | Protocolo *connect* para tasks multi-bloco | `connect_protocol.asl` |
| 4 | Coleta | Navegação até dispenser, *request*, *attach* | `collection.asl` |
| 5 | Navegação | Movimento A* ou greedy até destino | `navigation.asl` |
| 6 | Exploração | Busca por fronteira não visitada | `navigation.asl` |

Essa hierarquia é implementada pela **ordem de inclusão** dos arquivos `.asl` e pela **ordem dos planos** dentro de cada arquivo: o Jason seleciona o primeiro plano aplicável cuja guarda de contexto é satisfeita, garantindo que comportamentos de alta prioridade (e.g., submissão em goal zone) prevaleçam sobre comportamentos de baixa prioridade (e.g., exploração) [8].

### 4.2.2 Especialização por Papel

Os 15 agentes são instanciados a partir de 4 programas distintos, cada um estendendo a base comum com comportamentos especializados:

| Papel | Qtd | Responsabilidade Principal |
|-------|:---:|----------------------------|
| **Squad Leader** | 3 | Leilão de tarefas, delegação a soloists, scan periódico |
| **Collector** | 6 | Coleta de blocos, navegação até meeting points |
| **Assembler** | 3 | Coordenação *connect* para multi-bloco, submissão |
| **Sentinel** | 3 | Modo solo híbrido — opera como soloist autônomo |

Todos os agentes incluem os mesmos módulos compartilhados (`connect_protocol.asl`, `collection.asl`, `navigation.asl`, `perception.asl`), diferindo apenas nos planos específicos de seu papel. Essa arquitetura de **herança composicional** maximiza o reúso de código e garante que qualquer agente livre pode atuar como soloist, independentemente de seu papel original.

## 4.3 Organização — MOISE+

A dimensão organizacional do HIVE é especificada em MOISE+ [9, 10], que fornece um modelo tridimensional para organizações de agentes:

- **Especificação Estrutural**: define papéis (*squad_leader*, *collector*, *assembler*, *sentinel*), grupos (*squad_group* × 3, *sentinel_group*) e relações de autoridade e comunicação;
- **Especificação Funcional**: define esquemas de atividade (*exploration_scheme*, *task_execution_scheme*, *defense_scheme*) com metas coletivas e decomposição em sub-metas;
- **Especificação Deôntica**: define obrigações normativas que vinculam papéis a missões (e.g., o papel *squad_leader* é obrigado a executar a missão *m_scout*).

A organização divide os 15 agentes em **3 esquadrões de 4 membros** (1 líder + 2 coletores + 1 assembler) mais um **pool de 3 sentinelas**. Essa estrutura é inspirada em organizações militares descentralizadas, onde cada esquadrão possui autonomia tática local enquanto a coordenação estratégica emerge da competição entre esquadrões via leilão [9].

## 4.4 Ambiente Computacional — CArtAgO

O ambiente compartilhado é implementado por meio de **artefatos CArtAgO** [11, 13], entidades computacionais de primeira classe que encapsulam funcionalidades acessíveis aos agentes:

| Artefato | Responsabilidade | Operações Principais |
|----------|------------------|----------------------|
| **SharedMap** | Mapa toroidal 40×40, A*, fronteiras | `mark_visited`, `compute_next_move`, `get_nearest_frontier`, `get_nearest_dispenser`, `get_nearest_goal_zone` |
| **TaskBoard** | Registro de tarefas e leilão | `register_task`, `place_bid`, `resolve_auction`, `is_task_assigned` |
| **SquadCoordinator** | Composição de squads e pool de soloists | `find_free_soloist`, `mark_busy`, `mark_free`, `signal_ready` |
| **HiveDashboard** | Broadcast WebSocket para dashboard | `set_step`, `set_score`, `broadcast` |

Seguindo o meta-modelo **Agentes & Artefatos** (A&A) [13], os artefatos funcionam como ferramentas compartilhadas que os agentes *observam* e *operam*. Essa separação entre lógica deliberativa (nos agentes) e funcionalidades de infraestrutura (nos artefatos) promove modularidade e permite que múltiplos agentes coordenem suas atividades sem comunicação direta ponto-a-ponto [12].

Um aspecto crítico de projeto é a **serialização de operações** por artefato no CArtAgO: todas as invocações a um mesmo artefato são processadas sequencialmente. Essa propriedade garante consistência, mas impõe um gargalo de desempenho proporcional ao número de agentes e à frequência de operações — fator que se revelou determinante nos experimentos de escalabilidade (cf. Seção 5).

## 4.5 Mecanismo de Coordenação — Leilão Distribuído

A alocação de tarefas no HIVE é realizada por um **leilão distribuído** inspirado no *Contract Net Protocol* [22]. Quando o servidor MASSim anuncia uma nova tarefa, o fluxo de coordenação é:

1. **Anúncio**: O artefato `TaskBoard` registra a tarefa e emite um sinal `new_task_available`;
2. **Avaliação**: Cada líder calcula um score baseado no reward da tarefa e na distância Manhattan (com wrapping toroidal) até o dispenser mais próximo;
3. **Oferta**: Cada líder submete sua oferta (*bid*) ao `TaskBoard`;
4. **Resolução**: O primeiro líder a invocar `resolve_auction` obtém o resultado — o squad com maior score vence;
5. **Delegação**: O líder vencedor consulta o `SquadCoordinator` para encontrar o soloist livre mais próximo do dispenser e delega a tarefa via mensagem ACL.

Esse protocolo difere do Contract Net clássico [22] em um aspecto importante: o papel de *manager* é distribuído entre os 3 líderes (cada um pode resolver o leilão), e o *contractor* não é um agente fixo, mas qualquer agente livre do pool universal de soloists. Essa flexibilidade permite que até mesmo assemblers e coletores sem tarefa ativa atuem como soloists, maximizando a utilização dos agentes.

## 4.6 Algoritmos de Navegação

A navegação no grid toroidal 40×40 é implementada por dois algoritmos complementares:

- **A\* com wrapping toroidal**: Usado quando a distância Manhattan ao destino é ≤ 60. A heurística é a distância Manhattan com wrapping (`min(|dx|, W-|dx|) + min(|dy|, H-|dy|)`). O limite de iterações é 2.000, com fallback para greedy se excedido. Os obstáculos são obtidos do `SharedMap` e expiram após 30 steps para lidar com mudanças no ambiente;
- **Greedy direction**: Usado para distâncias > 60 ou como fallback do A*. Calcula a direção cardinal que minimiza a distância Manhattan ao destino, considerando wrapping.

A **exploração** utiliza o algoritmo de *frontier-based exploration*: o `SharedMap` mantém um cache de células fronteira (adjacentes a células visitadas mas ainda não exploradas), e os agentes navegam em direção à fronteira mais próxima [1].

## 4.7 Metodologia de Avaliação

A avaliação do HIVE seguiu uma abordagem **experimental iterativa**, alinhada com a prática estabelecida nas edições anteriores do MAPC [16]:

1. **Ambiente de teste**: Servidor MASSim 2022-1.1 [23] configurado com grid 40×40, cave density 0.45, 800 steps, 2–3 tipos de blocos, até 4 tarefas simultâneas, e posições absolutas habilitadas;
2. **Protocolo de teste**: Cada configuração foi submetida a no mínimo 3 simulações independentes para capturar a variância inerente à geração aleatória de mapas;
3. **Métricas**: Score final (pontos obtidos por submissões bem-sucedidas), taxa de conclusão (percentual de runs que completaram sem crash), e número de submissões por simulação;
4. **Análise de escalabilidade**: Variação sistemática do número de agentes (6, 10, 15, 20) com registro de score, estabilidade e tempo de execução;
5. **Comparação**: Resultados contextualizados em relação aos scores reportados pelos times participantes do MAPC 2022 [14], com atenção particular ao time FIT-BUT [17] (1º lugar) e ao time LI(A)RA [19] (implementação declarativa em JaCaMo).
