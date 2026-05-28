# 2. Revisão de Conceitos

Esta seção apresenta os fundamentos teóricos e tecnológicos sobre os quais o sistema HIVE é construído. O objetivo é fornecer ao leitor uma base conceitual suficiente para compreender as decisões de projeto, os resultados experimentais e a análise crítica apresentados nas seções seguintes.

## 2.1 Agentes Inteligentes e Sistemas Multi-Agentes

Um **agente inteligente** é um sistema computacional situado em um ambiente, capaz de ação autônoma e flexível para atingir seus objetivos [2]. A flexibilidade é decomposta em três propriedades:

- **Reatividade**: o agente percebe mudanças no ambiente e responde em tempo hábil;
- **Proatividade**: o agente exibe comportamento dirigido por objetivos, tomando iniciativa quando necessário;
- **Sociabilidade**: o agente interage com outros agentes (e possivelmente humanos) por meio de linguagens de comunicação.

Um **Sistema Multi-Agente** (SMA) é um sistema composto por múltiplos agentes que interagem entre si dentro de um ambiente compartilhado [1]. A motivação para SMA reside em problemas cuja solução é inerentemente distribuída — seja por limitações de percepção (nenhum agente possui visão global), por requisitos de paralelismo, ou pela natureza descentralizada do domínio. No contexto do MAPC, cada agente possui visão local limitada (raio 5), o que torna a cooperação entre múltiplos agentes não apenas desejável, mas necessária para explorar o mapa, localizar recursos e montar estruturas.

As interações entre agentes em um SMA podem ser **cooperativas** (agentes com objetivos alinhados) ou **competitivas** (agentes com objetivos conflitantes). O cenário Agents Assemble combina ambas: cooperação dentro do time (15 agentes colaboram para submeter tarefas) e competição entre times (dois times disputam os mesmos recursos e goal zones).

## 2.2 A Arquitetura BDI (Belief-Desire-Intention)

### 2.2.1 Fundamento Filosófico

O modelo BDI tem origem na teoria do raciocínio prático de Michael Bratman [3], que argumenta que a ação racional humana não pode ser explicada apenas por crenças e desejos. Bratman introduz as **intenções** como uma terceira atitude mental, com propriedades funcionais distintas:

1. **Intenções conduzem ao raciocínio prático**: uma vez formada, a intenção de fazer X leva o agente a planejar *como* fazer X, sem reconsiderar *se* deve fazer X a cada instante;
2. **Intenções persistem**: diferentemente de desejos (que podem ser transitórios), intenções são compromissos que o agente mantém até concluí-las, abandoná-las por impossibilidade, ou reconsiderá-las deliberadamente;
3. **Intenções restringem a deliberação futura**: uma intenção de fazer X torna inadmissíveis intenções inconsistentes com X, funcionando como *filtro de admissibilidade* que reduz o espaço de deliberação.

No HIVE, essas propriedades se manifestam concretamente: quando um agente se compromete a coletar um bloco em um dispenser distante, ele mantém essa intenção por dezenas de steps sem reconsiderá-la — comportamento essencial dado que o timeout de 8 segundos por step não permite deliberação exaustiva.

### 2.2.2 Formalização Computacional

Rao e Georgeff [4, 5] formalizaram a arquitetura BDI como um framework computacional com semântica em lógica modal:

- **Crenças** (B): conjunto de sentenças que representam o conhecimento do agente sobre o estado do mundo. No HIVE, crenças incluem `my_pos(X,Y)`, `known_task(Name, Deadline, Reward, Size)`, `thing(X, Y, Type, Details)`;
- **Desejos** (D): conjunto de estados do mundo que o agente gostaria de alcançar. No HIVE, desejos são representados como eventos gatilho em AgentSpeak: `+!explore`, `+!collect_block`, `+!submit_task`;
- **Intenções** (I): planos de ação que o agente selecionou e está ativamente executando. No Jason, cada intenção é uma pilha de planos em execução.

O **ciclo de raciocínio BDI** opera da seguinte forma:

```
repetir:
  1. Perceber o ambiente → atualizar Crenças
  2. Gerar opções (Desejos candidatos) com base nas Crenças
  3. Filtrar opções com base nas Intenções atuais
  4. Selecionar Intenção (comprometer-se com um plano)
  5. Executar a próxima ação da Intenção selecionada
```

A distinção crucial entre BDI e planejamento clássico é o **compromisso parcial**: o agente não precisa de um plano completo do estado atual ao objetivo — ele se compromete com o próximo sub-objetivo e replaneja conforme necessário. Essa propriedade é particularmente adequada a ambientes dinâmicos como o MAPC, onde o estado do mundo muda a cada step.

## 2.3 AgentSpeak(L) e Jason

**AgentSpeak(L)** é uma linguagem de programação para agentes BDI baseada em programação lógica, proposta por Rao [4] e implementada na plataforma **Jason** [8]. A linguagem traduz os conceitos BDI em construtos programáticos:

| Conceito BDI | Construto AgentSpeak | Exemplo no HIVE |
|---|---|---|
| Crença | Literal na base de crenças | `my_pos(10, 20)` |
| Desejo / Evento | Evento gatilho (`+!`, `-!`, `+`, `-`) | `+!go_to(X,Y)` |
| Intenção | Instância de plano em execução | Plano `+!collect(B) : ...` ativo |
| Plano | Regra `evento : contexto <- corpo` | `+!go_to(X,Y) : my_pos(X,Y) <- true.` |

O **ciclo de raciocínio do Jason** implementa o loop BDI em 10 passos [8]:

1. Percepção do ambiente (receber percepts)
2. Atualização da base de crenças (BRF — *Belief Revision Function*)
3. Geração de eventos (crenças adicionadas/removidas geram eventos)
4. Seleção de evento (SE — *Selection Event*)
5. Recuperação de planos relevantes
6. Verificação de planos aplicáveis (unificação de contexto)
7. Seleção de plano aplicável (SO — *Selection Option*)
8. Atualização da pilha de intenções
9. Seleção de intenção (SI — *Selection Intention*)
10. Execução da próxima ação

Esse ciclo é interpretado — cada step envolve unificação lógica contra a base de crenças —, o que confere expressividade mas introduz overhead computacional em relação a implementações compiladas. No HIVE, com 15 agentes executando este ciclo simultaneamente e cada um possuindo centenas de crenças (obstáculos, dispensers, tarefas), o tempo de deliberação torna-se um fator crítico de desempenho.

## 2.4 JaCaMo: Programação Orientada a Multi-Agentes

**JaCaMo** [6, 7] é uma plataforma que integra três tecnologias complementares sob o paradigma de **Programação Orientada a Multi-Agentes** (MAOP):

```
JaCaMo = Jason (agentes) + CArtAgO (ambiente) + MOISE+ (organização)
```

A premissa central da MAOP é que um SMA completo requer três dimensões ortogonais de programação [7]:

| Dimensão | Tecnologia | O que programa | Abstração central |
|---|---|---|---|
| **Agente** | Jason | Raciocínio individual | Crenças, Planos, Intenções |
| **Ambiente** | CArtAgO | Recursos compartilhados | Artefatos, Operações, Propriedades observáveis |
| **Organização** | MOISE+ | Restrições sociais | Papéis, Grupos, Normas |

Cada dimensão possui sua própria linguagem e semântica, e o JaCaMo fornece a *cola* que permite agentes Jason interagirem com artefatos CArtAgO e respeitarem especificações MOISE+.

No HIVE, essa separação é explorada integralmente: a lógica de decisão reside nos agentes (arquivos `.asl`), os serviços compartilhados residem nos artefatos (classes Java), e a estrutura organizacional é declarada em XML MOISE+.

## 2.5 MOISE+: Modelo Organizacional

**MOISE+** [9, 10] é um modelo para especificação de organizações multi-agente, estruturado em três dimensões complementares:

### 2.5.1 Especificação Estrutural (SS)

Define a estrutura social estática do SMA:

- **Papéis** (*roles*): abstrações de funções que agentes podem desempenhar. No HIVE: `squad_leader`, `collector`, `assembler`, `sentinel`;
- **Grupos** (*groups*): conjuntos de papéis com cardinalidades. No HIVE: `squad_group` (1 líder + 2 coletores + 1 assembler), `sentinel_group` (3 sentinelas);
- **Relações**: ligações de autoridade (*authority*), comunicação (*communication*) e familiaridade (*acquaintance*) entre papéis.

### 2.5.2 Especificação Funcional (FS)

Define os objetivos coletivos e sua decomposição:

- **Esquemas sociais** (*social schemes*): grafos de metas com relações de decomposição sequencial ou paralela;
- **Missões** (*missions*): conjuntos de metas que um agente se compromete a executar.

No HIVE, o esquema `task_execution_scheme` decompõe a meta global "submeter task" em sub-metas: localizar dispenser → navegar → coletar → transportar → submeter.

### 2.5.3 Especificação Deôntica (DS)

Conecta a estrutura (SS) à funcionalidade (FS) por meio de **normas**:

- **Obrigações**: o papel X é obrigado a executar a missão M;
- **Permissões**: o papel X pode executar a missão M.

Por exemplo: o papel `squad_leader` é *obrigado* a executar `m_scout` (escanear e delegar tarefas), enquanto o papel `sentinel` é *permitido* executar `m_solo_collect` (coleta autônoma).

A vantagem do MOISE+ é que a organização é **declarativa e verificável**: é possível analisar se todos os papéis necessários estão preenchidos e se todas as missões obrigatórias estão atribuídas, independentemente do comportamento interno dos agentes.

## 2.6 CArtAgO: Artefatos e o Meta-Modelo A&A

**CArtAgO** (*Common ARtifact infrastructure for AGents Open environments*) [11] implementa o meta-modelo **Agentes & Artefatos** (A&A) [13], que estende o conceito de SMA com uma noção explícita de **ambiente computacional**.

### 2.6.1 Conceito de Artefato

Um **artefato** é uma entidade computacional de primeira classe, projetada para ser *usada* por agentes — analogamente a ferramentas no mundo físico [13]. Diferentemente dos agentes, artefatos são passivos (não possuem autonomia) e são projetados para:

- **Encapsular funcionalidade**: o artefato `SharedMap` encapsula A*, gerenciamento de obstáculos e fronteiras em uma interface coesa;
- **Mediar coordenação**: o artefato `TaskBoard` permite que líderes coordenem alocação de tarefas sem comunicação direta;
- **Prover propriedades observáveis**: artefatos expõem estado que os agentes podem perceber, criando um canal de comunicação indireto (*stigmergy* computacional).

### 2.6.2 Modelo de Execução

A interação agente-artefato segue o modelo:

- **Operações** (*operations*): ações que o agente invoca sobre o artefato (e.g., `mark_obstacle(X, Y)`);
- **Propriedades observáveis** (*observable properties*): atributos do artefato que geram eventos de crença nos agentes que o focam;
- **Sinais** (*signals*): eventos assíncronos emitidos pelo artefato.

Uma propriedade fundamental do CArtAgO é a **serialização por artefato**: todas as operações invocadas sobre um mesmo artefato são executadas sequencialmente, garantindo consistência sem necessidade de locks explícitos [12]. Essa propriedade simplifica a programação mas introduz um gargalo quando muitos agentes operam o mesmo artefato com alta frequência — exatamente o cenário encontrado no HIVE com o `SharedMap`.

## 2.7 Contract Net Protocol

O **Contract Net Protocol** (CNP) [22] é um mecanismo clássico de alocação distribuída de tarefas em SMA, inspirado em processos de licitação:

1. **Anúncio** (*call for proposals*): um agente *manager* anuncia uma tarefa disponível;
2. **Proposta** (*bid*): agentes *contractors* interessados enviam propostas com estimativas de custo/qualidade;
3. **Avaliação**: o manager seleciona a melhor proposta segundo critério definido;
4. **Adjudicação** (*award*): o manager notifica o contractor vencedor e rejeita os demais;
5. **Execução e reporte**: o contractor executa a tarefa e reporta o resultado.

No HIVE, o CNP é adaptado de forma significativa: os **managers são distribuídos** (3 líderes competem para resolver o leilão, não há manager único), e os **contractors são universais** (qualquer agente livre pode ser selecionado como soloist, independentemente de seu papel organizacional). Essa adaptação aumenta a flexibilidade e a utilização dos agentes em relação ao CNP clássico, onde managers e contractors são papéis fixos.

## 2.8 Navegação em Grids Toroidais

### 2.8.1 Topologia Toroidal

O mapa do cenário Agents Assemble é um **grid toroidal** de dimensões W × H: as bordas do mapa "conectam-se" — um agente que se move para a direita além da coluna W-1 reaparece na coluna 0, e analogamente para as linhas. Formalmente, a topologia é um toro plano T² = (Z/WZ) × (Z/HZ).

Essa topologia afeta diretamente o cálculo de distâncias e a navegação. A **distância Manhattan toroidal** entre dois pontos (x₁, y₁) e (x₂, y₂) é:

```
d((x₁,y₁), (x₂,y₂)) = min(|Δx|, W-|Δx|) + min(|Δy|, H-|Δy|)
```

onde Δx = x₂ - x₁ e Δy = y₂ - y₁. Essa fórmula garante que o caminho mais curto pode cruzar a borda do mapa.

### 2.8.2 Algoritmo A*

O **A*** é um algoritmo de busca informada que encontra o caminho de menor custo em um grafo ponderado [1]. A ideia central é manter uma fila de prioridade de nós a expandir, ordenada pela função:

```
f(n) = g(n) + h(n)
```

onde g(n) é o custo real do caminho da origem até n, e h(n) é uma heurística estimando o custo de n ao destino. Se h(n) é **admissível** (nunca superestima o custo real), o A* garante encontrar o caminho ótimo.

No HIVE, o A* é implementado no artefato `SharedMap` com:

- **Heurística**: distância Manhattan toroidal (admissível no grid);
- **Custo de aresta**: 1 para células livres, infinito para obstáculos;
- **Limite de iterações**: 2.000 expansões, com fallback para navegação greedy;
- **Obstáculos com expiração**: obstáculos registrados expiram após 30 steps para lidar com mudanças ambientais (clear events).

### 2.8.3 Exploração por Fronteira

A **exploração por fronteira** (*frontier-based exploration*) é uma estratégia onde o agente navega até a **fronteira** mais próxima — definida como uma célula livre adjacente a pelo menos uma célula não visitada. Ao alcançar a fronteira, o agente observa novas células, que podem gerar novas fronteiras. Esse processo continua até que todo o espaço acessível tenha sido explorado.

No HIVE, o `SharedMap` mantém um cache de fronteiras atualizado a cada `mark_visited`, e os agentes consultam `get_nearest_frontier` para selecionar seu próximo destino de exploração.

## 2.9 Multi-Agent Programming Contest (MAPC)

O **MAPC** (*Multi-Agent Programming Contest*) é uma competição acadêmica anual organizada desde 2005, com o objetivo de promover a pesquisa em programação de SMA [16]. A competição utiliza a plataforma **MASSim** (*Multi-Agent Systems Simulation*), que implementa cenários padronizados com regras, percepções e ações bem definidas.

### 2.9.1 Cenário Agents Assemble (2022)

Na edição 2022, o cenário **Agents Assemble III** [14, 23] desafia times de agentes a:

1. **Explorar** um grid toroidal parcialmente observável com visão limitada (raio 5);
2. **Coletar blocos** em dispensers espalhados pelo mapa (ação `request` + `attach`);
3. **Montar estruturas** conectando blocos entre agentes adjacentes (ação `connect`);
4. **Submeter tarefas** em goal zones de acordo com padrões especificados pelo servidor (ação `submit`);
5. **Respeitar normas** dinâmicas que podem limitar o número de blocos transportados simultaneamente.

A complexidade do cenário reside na combinação de:

- **Observabilidade parcial**: cada agente vê apenas um raio de 5 células ao redor de si;
- **Dinamicidade**: tarefas aparecem e expiram, goal zones podem se mover, normas mudam;
- **Restrições temporais**: cada agente tem um timeout (tipicamente 4–10 segundos) para enviar sua ação a cada step;
- **Coordenação multi-agente**: tasks multi-bloco exigem que dois ou mais agentes se encontrem em posições adjacentes e executem ações sincronizadas.

### 2.9.2 Ações Disponíveis

O servidor MASSim disponibiliza as seguintes ações para os agentes [23]:

| Ação | Descrição |
|------|-----------|
| `move(direction)` | Move o agente em uma das 4 direções cardinais |
| `request(direction)` | Solicita um bloco de um dispenser adjacente |
| `attach(direction)` | Acopla um bloco adjacente ao agente |
| `detach(direction)` | Desacopla um bloco |
| `rotate(direction)` | Rotaciona os blocos acoplados (cw/ccw) |
| `connect(agent, X, Y)` | Conecta blocos entre dois agentes adjacentes |
| `submit(taskName)` | Submete a tarefa se o agente está em goal zone com a estrutura correta |
| `skip` | Não faz nada (ação nula) |

### 2.9.3 Histórico e Relevância

O MAPC já avaliou dezenas de plataformas ao longo de suas edições, incluindo Jason, JaCaMo, JADE, GOAL e implementações em Java/Python puro [16]. A competição serve como benchmark empírico para comparar abordagens teóricas em condições padronizadas, e os proceedings publicados anualmente constituem um acervo valioso de arquiteturas e estratégias para SMA [14].

Times que utilizam frameworks declarativos como JaCaMo tendem a apresentar scores inferiores aos de implementações procedurais (Java puro), mas oferecem vantagens em termos de modularidade, rastreabilidade e alinhamento com modelos teóricos [20]. Esse trade-off entre **desempenho** e **fidelidade conceitual** é uma das questões centrais discutidas na comunidade MAPC.

## 2.10 Conceitos Complementares

### 2.10.1 Estigmergia Computacional

**Estigmergia** é um mecanismo de coordenação indireta onde agentes comunicam-se por meio de modificações no ambiente compartilhado, em vez de mensagens diretas [13]. No HIVE, o artefato `SharedMap` funciona como meio estigmérgico: quando um agente marca um obstáculo ou célula visitada, todos os demais agentes que focam o artefato percebem a mudança via propriedades observáveis — sem que o agente emissor precise conhecer ou endereçar os demais.

### 2.10.2 Arquitetura de Subsunção

A **arquitetura de subsunção** (*subsumption architecture*), proposta por Brooks, organiza o comportamento de um agente em camadas de prioridade, onde camadas superiores podem inibir ou substituir as inferiores [1]. Embora o HIVE utilize uma arquitetura BDI (e não reativa pura), a organização dos planos em camadas de prioridade (sobrevivência > normas > submissão > coleta > exploração) é inspirada nesse princípio — implementada pela ordem de seleção de planos no Jason.

### 2.10.3 Racionalidade Limitada

O conceito de **racionalidade limitada** (*bounded rationality*), originário de Herbert Simon, reconhece que agentes reais operam sob restrições de tempo, informação e capacidade computacional. No contexto do MAPC, essa limitação é concretizada pelo timeout por step: o agente não pode computar a ação globalmente ótima — precisa de heurísticas, compromissos parciais (intenções) e políticas de satisficing (*satisfação suficiente*) [3]. O HIVE incorpora esse princípio ao utilizar A* com limite de iterações, intenções persistentes e self-assignment baseado na primeira task disponível em vez de busca exaustiva.
