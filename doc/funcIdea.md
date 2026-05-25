# Documento Funcional — Projeto MAPC 2022 / PCS 5703

## Codinome do Time: **HIVE** (Hierarchical Intelligent Virtual Ensemble)

---

## 1. A Ideia Central

### O Quê

Um SMA com **arquitetura de enxame hierárquico** onde os agentes operam em **esquadrões dinâmicos** de 3-4 membros, cada esquadrão autônomo o suficiente para completar tarefas sozinho (explorar, coletar, montar, submeter), mas coordenado globalmente por um **protocolo de leilão distribuído** para maximizar throughput de tarefas.

### Por Quê

A maioria dos times no MAPC comete um de dois erros:

1. **Centralização excessiva** — um agente "coordenador" vira gargalo e ponto único de falha.
2. **Descentralização total** — agentes independentes competem por recursos entre si, gerando desperdício.

Nossa abordagem é o **meio-termo ótimo**: esquadrões pequenos com autonomia local, coordenados por broadcast de intenções. Cada esquadrão é uma "mini-fábrica" capaz de completar o ciclo inteiro de uma tarefa. Isso dá:

- **Resiliência**: se um esquadrão perde um membro (desativação por clear event), os demais se reorganizam.
- **Paralelismo**: múltiplas tarefas são executadas simultaneamente por esquadrões diferentes.
- **Eficiência**: sem overhead de um coordenador central processando tudo.

### Para Quem

Time de alunos PCS 5703, competindo contra os demais times da turma no simulador MASSIM 2022.

### Quando

- **Semana 1 (19-25/mai)**: Ambiente + agente mínimo conectado ao MASSIM
- **Semana 2 (26/mai-01/jun)**: Estratégia completa + organização MOISE+
- **02/jun**: Entrega final (relatório + código)

---

## 2. Análise do Cenário — Mecânicas que Definem a Estratégia

Cada decisão arquitetural abaixo é justificada por uma mecânica real do Agents Assemble 2022.

### 2.1 Sistema de Roles (Papéis do Servidor)

O servidor define roles com atributos diferentes:

| Atributo | Impacto Estratégico |
|----------|-------------------|
| **vision** | Roles com visão alta são essenciais na fase de exploração |
| **speed** (array) | `speed[0]` = vel. sem carga, `speed[1]` = vel. com 1 bloco, etc. Roles rápidos sem carga exploram; roles que mantêm velocidade com carga transportam |
| **actions** | Nem todo role pode fazer tudo (ex: submit, clear, request). O role precisa ter a ação permitida |
| **clear.chance** | Probabilidade de sucesso do clear — roles com alta chance são usados para ataque/defesa |
| **clear.maxDistance** | Se > 1, pode causar dano a distância em entidades inimigas |

**Decisão**: Os agentes DEVEM mudar de role dinamicamente conforme a fase (exploração → coleta → montagem → submissão). Role zones são fixas durante a simulação — mapear role zones cedo é crítico.

### 2.2 Sistema de Tarefas

- Tarefas aparecem aleatoriamente com deadline, reward e um padrão de blocos (posições relativas ao agente que submete).
- Podem ser submetidas **múltiplas vezes** até um limite desconhecido.
- Só podem ser submetidas em **goal zones**.
- Goal zones **se movem** após submissões — precisa rastrear continuamente.

**Decisão**: Priorizar tarefas por `reward / complexidade` (número de blocos × distância dos dispensers). Tarefas simples (1-2 blocos) dão pontos rápidos no início; tarefas complexas (3-4 blocos) exigem connect entre agentes e dão mais reward.

### 2.3 Dispensers e Blocos

- Dispensers são fixos e produzem um tipo específico de bloco.
- Ação `request` gera o bloco na posição do dispenser (precisa estar adjacente).
- Blocos podem ser attached, rotated (cw/ccw), e connected entre agentes.
- **Speed degrada com attachments**: carregar blocos te deixa lento.

**Decisão**: Minimizar tempo de carregamento. O agente coleta o bloco e leva direto para o ponto de montagem. Não acumular blocos desnecessariamente.

### 2.4 Connect — A Mecânica Mais Importante

Para tarefas complexas (3+ blocos), um único agente pode não conseguir carregar tudo (speed cai a 0 com muitos attachments). A ação `connect` permite que dois agentes juntem seus blocos:

- Ambos precisam executar `connect` no **mesmo step**.
- Precisam referenciar o parceiro e as coordenadas locais do bloco.
- Após connect, os blocos ficam ligados e attached a ambos os agentes.

**Decisão**: Esta é a mecânica que separa times bons de times medíocres. Nossa arquitetura de esquadrão é desenhada especificamente para facilitar connects: agentes do mesmo esquadrão se encontram em pontos combinados para executar connects sincronizados.

### 2.5 Normas Dinâmicas

O servidor cria normas que podem:

- **Carry**: limitar quantos blocos um agente pode carregar (punição = perda de energia).
- **Adopt**: limitar quantos agentes do time podem ter o mesmo role.

**Decisão**: Monitorar normas a cada step. Se uma norma Carry limita a 1 bloco, ajustar a estratégia para tarefas simples. Se uma norma Adopt limita um role, redistribuir roles no esquadrão.

### 2.6 Clear Events e Ação Clear

- **Clear events**: hazards ambientais que desativam agentes e destroem blocos em uma área. São sinalizados com markers 5 steps antes.
- **Ação clear**: agentes podem limpar obstáculos/blocos e causar dano a inimigos.

**Decisão**: Detectar markers do tipo `clear` e `ci` (clear_immediate) e evacuar a área. Usar ação clear ofensivamente contra agentes inimigos que estão montando padrões em goal zones.

---

## 3. Arquitetura do SMA — O Design HIVE

### 3.1 Organização MOISE+

```
HIVE Organization
│
├── SchemeSpec: exploration_scheme
│   ├── Goal: map_explored
│   └── Missions: m_scout (obrigatória para Scouts)
│
├── SchemeSpec: task_execution_scheme
│   ├── Goal: task_submitted
│   ├── SubGoal: blocks_collected
│   ├── SubGoal: blocks_assembled
│   ├── SubGoal: pattern_submitted
│   └── Missions: m_collect, m_assemble, m_submit
│
├── SchemeSpec: defense_scheme
│   ├── Goal: team_protected
│   └── Missions: m_guard, m_clear_threat
│
├── GroupSpec: squad_group (min=2, max=4)
│   ├── Role: squad_leader (1..1)
│   ├── Role: collector (1..2)
│   ├── Role: assembler (1..1)
│   └── Norms:
│       ├── squad_leader MUST fulfill m_scout
│       ├── collector MUST fulfill m_collect
│       └── assembler MUST fulfill m_assemble, m_submit
│
└── GroupSpec: sentinel_group (min=1, max=2)
    ├── Role: sentinel (1..2)
    └── Norms:
        └── sentinel MUST fulfill m_guard
```

### 3.2 Papéis dos Agentes (Nível MOISE+)

| Papel MOISE+ | Role do Servidor Preferido | Responsabilidades |
|---------------|---------------------------|-------------------|
| **squad_leader** | Role com alta visão | Explorar, mapear dispensers/goal zones/role zones, coordenar esquadrão via broadcast de crenças |
| **collector** | Role com boa speed carregando | Ir até dispensers, fazer request, attach blocos, transportar até ponto de montagem |
| **assembler** | Role com permissão de submit | Receber blocos via connect, rotacionar para padrão correto, levar até goal zone, submit |
| **sentinel** | Role com bom clear | Patrulhar goal zones, usar clear em inimigos que estão montando, proteger assemblers aliados |

### 3.3 Ciclo de Vida de uma Tarefa (Pipeline)

```
[1. DETECTAR]  Qualquer agente percebe task nos percepts
       │
       ▼
[2. AVALIAR]   Squad leader calcula score = reward / (n_blocos × dist_média_dispensers)
       │
       ▼
[3. LEILOAR]   Squad leader anuncia intenção via mensagem broadcast
               Outros squads podem competir (maior score ganha)
       │
       ▼
[4. COLETAR]   Collectors do squad vão aos dispensers relevantes
               Cada collector busca os blocos que faltam
       │
       ▼
[5. MONTAR]    Collectors se encontram com assembler em ponto combinado
               Executam connect sincronizado para juntar blocos
       │
       ▼
[6. SUBMETER]  Assembler navega até goal zone com padrão completo
               Executa submit
       │
       ▼
[7. REPETIR]   Se task ainda ativa, repete submissão (re-submit dá pontos extras!)
```

### 3.4 Arquitetura BDI dos Agentes (Jason/AgentSpeak)

Cada agente opera com o ciclo BDI:

**Beliefs (Crenças)**:
- Mapa parcial compartilhado (posições de dispensers, goal zones, role zones, obstáculos)
- Estado das tarefas ativas (quais existem, deadline, reward)
- Estado do esquadrão (quem está fazendo o quê, posições relativas)
- Normas vigentes e suas restrições
- Próprio estado (energia, role atual, attachments)

**Desires (Desejos)** — priorizados:
1. Sobreviver (evacuar clear events, manter energia > 0)
2. Cumprir normas (evitar punições)
3. Completar tarefas do esquadrão
4. Explorar mapa (quando ocioso)
5. Defender goal zones (se sentinel)

**Intentions (Intenções)**:
- Planos concretos gerados pelo motor Jason
- Commitment strategy: manter intenção até conclusão ou impossibilidade comprovada
- Re-planejamento quando precepts invalidam o plano atual

---

## 4. Estratégias Diferenciadas — O Que Nos Faz Ganhar

### 4.1 Exploração por Fronteira com Mapa Compartilhado

Ao invés de exploração aleatória:

1. Cada agente mantém um mapa local das células visitadas.
2. Squad leaders compartilham suas observações via `tell` (mensagem Jason).
3. O mapa global é construído incrementalmente como artefato CArtAgO.
4. Agentes navegam para a **fronteira** mais próxima (célula não visitada adjacente a célula visitada).
5. Survey action é usada para obter distância até dispensers/goal zones quando não encontrados.

**Vantagem**: Cobertura completa do mapa em menos steps, sem sobreposição de exploração.

### 4.2 Alocação de Tarefas por Leilão Distribuído (Contract Net simplificado)

Quando uma task aparece nos percepts:

1. Todos os squad_leaders recebem a task.
2. Cada leader calcula um **bid** = `reward / (tempo_estimado_conclusão)`.
3. O tempo estimado considera: distância até dispensers dos tipos necessários + distância até goal zone mais próxima.
4. O leader com melhor bid "vence" e compromete seu esquadrão.
5. Se nenhum esquadrão está livre, a task com menor reward é abandonada.

**Vantagem**: Alocação descentralizada, sem gargalo. O melhor posicionado para a task a executa.

### 4.3 Montagem Colaborativa com Connect Sincronizado

Para tarefas com 2+ blocos:

1. Assembler define um **ponto de encontro** (meeting point) — preferencialmente próximo a uma goal zone.
2. Collectors navegam até seus dispensers designados, fazem request + attach.
3. Collectors vão ao meeting point.
4. No meeting point, collector e assembler se posicionam adjacentes e executam connect no mesmo step.
5. Se a tarefa tem 3+ blocos, múltiplos connects são feitos em sequência.
6. Assembler rotaciona blocos (rotate cw/ccw) para alinhar com o padrão da task.
7. Assembler vai à goal zone e faz submit.

**Vantagem**: Divisão eficiente do trabalho. Collectors podem ir em paralelo a dispensers diferentes.

### 4.4 Adaptação Dinâmica a Normas

A cada step, todos os agentes checam as normas vigentes:

- **Norma Carry (limite de blocos)**: Se o limite é 1, priorizar tarefas de 1 bloco. Se não há tarefas simples, um agente carrega o bloco e imediatamente faz connect com o assembler (ficando abaixo do limite o mais rápido possível).
- **Norma Adopt (limite de roles)**: Redistribuir roles no time. Se máximo de "worker" é 3, os demais agentes trocam para outro role em role zones.

**Vantagem**: Times que ignoram normas perdem energia massivamente. Nós nos adaptamos e mantemos eficiência.

### 4.5 Evasão de Clear Events

Quando markers do tipo `clear` ou `ci` são percebidos:

1. Calcular o centro e raio estimado do evento.
2. Se o agente está na zona de perigo, evacuar imediatamente (prioridade máxima).
3. Se carregando blocos, tentar sair mantendo os blocos; se impossível, detach e salvar o agente.
4. Após o evento, recolher blocos sobreviventes se possível.

**Vantagem**: Evitar desativação = manter produtividade enquanto adversário perde agentes.

### 4.6 Tática Ofensiva (Sentinel)

1-2 agentes com role de alto clear dedicados a:

1. Patrulhar goal zones que o time adversário está usando.
2. Quando um agente inimigo está posicionado em goal zone com blocos attached (provável submit), usar **clear** na posição dele.
3. Isso causa dano de energia, potencialmente desativa o agente inimigo e destrói os blocos que ele carregava — negando a submissão.

**Vantagem**: Mesmo que nosso sentinel não marque pontos diretamente, ele **nega pontos do adversário**. Em um jogo de 700-800 steps, negar 2-3 submissões do adversário pode decidir a partida.

### 4.7 Re-submissão de Tarefas

Insight importante do cenário: tarefas podem ser submetidas múltiplas vezes até serem substituídas. Após um submit bem-sucedido:

1. Se o assembler ainda está na goal zone com o padrão intacto: submit novamente.
2. Repetir até a task expirar ou a goal zone se mover.
3. Se a goal zone se move, navegar até a nova posição e submeter de novo.

**Vantagem**: Multiplicar pontos por tarefa sem custo adicional de coleta.

---

## 5. Stack Tecnológico

| Componente | Tecnologia | Justificativa |
|-----------|------------|---------------|
| Agentes | **Jason** (AgentSpeak(L)) | Linguagem BDI nativa, ciclo percepção-raciocínio-ação natural para o domínio |
| Organização | **MOISE+** | Especificação formal de papéis, grupos, missões e normas — exigido pelo enunciado e ideal para esquadrões |
| Ambiente | **CArtAgO** | Artefatos para mapa compartilhado, task board, e interface com MASSIM |
| Integração | **JaCaMo** | Une Jason + MOISE+ + CArtAgO em um único framework |
| Simulador | **MASSIM 2022** | Servidor do Agents Assemble, comunicação via EIS (Environment Interface Standard) |

---

## 6. Estrutura de Arquivos do Projeto

```
project/
├── src/
│   ├── agt/                        # Agentes Jason (.asl)
│   │   ├── squad_leader.asl        # Lógica BDI do líder de esquadrão
│   │   ├── collector.asl           # Lógica BDI do coletor
│   │   ├── assembler.asl           # Lógica BDI do montador
│   │   ├── sentinel.asl            # Lógica BDI do sentinela
│   │   └── common/
│   │       ├── navigation.asl      # Planos de navegação (A* simplificado)
│   │       ├── perception.asl      # Processamento de percepts
│   │       └── norms.asl           # Monitoramento e adaptação a normas
│   │
│   ├── org/                        # Organização MOISE+ (.xml)
│   │   └── hive_org.xml            # Especificação de grupos, papéis, missões
│   │
│   ├── env/                        # Artefatos CArtAgO (.java)
│   │   ├── SharedMap.java          # Mapa compartilhado incremental
│   │   ├── TaskBoard.java          # Board de tarefas e leilão distribuído
│   │   └── MassimConnector.java    # Interface EIS com o simulador
│   │
│   └── jcm/
│       └── hive.jcm                # Arquivo de configuração JaCaMo
│
├── doc/
│   └── relatorio.tex               # Relatório em formato artigo científico
│
└── conf/                           # Configurações do MASSIM
```

---

## 7. Cronograma Detalhado

### Semana 1 (19-25 de maio)

| Dia | Entregável |
|-----|-----------|
| Seg-Ter | Ambiente MASSIM rodando, agente mínimo conectado e recebendo percepts |
| Qua-Qui | Navegação básica (move), processamento de percepts, mapa local |
| Sex-Dom | Exploração por fronteira, request de blocos em dispensers, attach/detach |

### Semana 2 (26 de maio - 01 de junho)

| Dia | Entregável |
|-----|-----------|
| Seg | Organização MOISE+ implementada (papéis, grupos, esquemas) |
| Ter | Leilão de tarefas funcionando, collectors indo a dispensers corretos |
| Qua | Connect sincronizado entre agentes, montagem de padrões de 2-3 blocos |
| Qui | Submit em goal zones, re-submissão, adaptação a normas |
| Sex | Sentinel tático, evasão de clear events, robustez |
| Sáb-Dom | Testes intensivos, otimização, relatório final |

### 02 de junho — Entrega

---

## 8. Métricas de Sucesso

| Métrica | Alvo Mínimo | Alvo Ideal |
|---------|------------|------------|
| Tarefas submetidas por simulação (800 steps) | 8-10 | 15+ |
| Tempo médio de conclusão por tarefa | < 80 steps | < 50 steps |
| Taxa de sobrevivência a clear events | > 80% | > 95% |
| Cobertura do mapa em 200 steps | > 40% | > 60% |
| Normas violadas por step | < 0.1 | 0 |

---

## 9. Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| Dificuldade de integração JaCaMo-MASSIM | Alta | Alto | Começar pela conexão no dia 1; usar exemplos do LTI-USP como base |
| Connect sincronizado falha frequentemente | Média | Alto | Protocolo de retry: se connect falha, agentes se reposicionam e tentam novamente no próximo step |
| Curva de aprendizado de AgentSpeak | Alta | Médio | Focar em planos simples e incrementar; testar cada plano individualmente |
| Time adversário usa tática ofensiva (clear) | Média | Médio | Assemblers evitam ficar parados em goal zones; submit o mais rápido possível; sentinels protegem |
| Normas muito restritivas (Carry=1) | Baixa | Alto | Fallback para tarefas de 1 bloco; manter flexibilidade na alocação |
| Prazo curto (2 semanas efetivas) | Alta | Alto | Priorizar funcionalidades core (explorar → coletar → montar → submeter) antes de otimizações |

---

## 10. Diferencial Competitivo — Por Que HIVE Ganha

1. **Esquadrões autônomos** — enquanto times centralizados travam quando o coordenador é desativado, nossos esquadrões continuam operando independentemente.

2. **Connect como first-class citizen** — a montagem colaborativa é o core da nossa arquitetura, não um afterthought. Isso nos permite completar tarefas complexas (alto reward) que times simplistas não conseguem.

3. **Re-submissão agressiva** — exploramos uma mecânica que a maioria dos times ignora: submeter a mesma tarefa múltiplas vezes para multiplicar pontos.

4. **Tática ofensiva** — o sentinel não apenas defende, mas ativamente sabota o adversário em goal zones, criando uma vantagem assimétrica.

5. **Adaptação a normas** — compliance automático garante que nunca perdemos energia por violações, enquanto times que ignoram normas ficam progressivamente mais fracos.

6. **Fundamentação teórica sólida** — cada decisão é justificável com referências da disciplina (Bratman/BDI, Wooldridge/autonomia, MOISE+/organização, Contract Net/coordenação), gerando um relatório academicamente forte.
