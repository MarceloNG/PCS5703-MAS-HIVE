---
id: "2026-06-18-002"
title: "Navegação: heading-balanceado + handedness"
status: completed
date: 2026-06-18
origin: "docs/brainstorms/2026-06-18-navegacao-heading-handedness-requirements.md"
---

# Navegação: heading-balanceado + handedness

## Summary

Dois ajustes ortogonais à pilha de navegação existente: (1) cada agente deriva uma direção
preferencial estática do próprio nome JaCaMo (`connectionA<N>` → N mod 4 → N/E/S/W) e bialsa
a seleção de frontier em `!do_explore` para esse setor; (2) o empate em `pick_escape` passa a
ser resolvido na ordem horária (N→E→S→W) em vez de `.random`. Nenhum novo artefato CArtAgO.
Três unidades de implementação — U1 Java (operação biased), U2 ASL (!do_explore), U3 ASL
(pick_escape) — separáveis e mensuráveis de forma independente.

## Problem Frame

O boot de validação Fase C (2026-06-18, `conf/OfficialRolesConfig.json`, 70×70, 300 steps)
mediu 4/15 agentes chegando a uma role-zone. Com `absolutePosition:false` e frames
dead-reckoned independentes, cada agente explora por conta própria sem saber o que os colegas
já mapearam, produzindo exploração redundante: os 15 agentes tendem a percorrer as mesmas
regiões próximas à origem em vez de se dispersar pelos quadrantes.

O `pick_escape` atual usa `.random` para desempate. Com agentes aglomerados, deadlocks de
corredor simétrico não são resolvidos de forma confiável pelo jitter aleatório — dois agentes
frente a frente giram para lados opostos ou iguais com probabilidade 50/50 cada step.

(ver origin: `docs/brainstorms/2026-06-18-navegacao-heading-handedness-requirements.md` §Problem Frame)

## Requirements

Carregados do documento de origem sem alteração:

- **R1.** Cada agente deriva uma direção preferencial estática (N, S, E, W) a partir do próprio
  nome, usando índice do agente mod 4. A derivação ocorre uma única vez e é estável para toda a
  simulação.

- **R2.** Ao selecionar uma frontier durante exploração, o agente prioriza frontiers que estejam
  na direção preferencial relativa à sua posição atual.

- **R3.** Quando não existem frontiers na direção preferencial, o agente recorre à frontier
  global mais próxima, sem alterar o comportamento existente de `get_nearest_frontier`.

- **R4.** O heading bias se aplica exclusivamente ao caminho de exploração (`!do_explore`).
  Navegação com destino explícito (role-zone, meeting point, goal zone) usa o A* existente sem
  viés.

- **R5.** Quando `pick_escape` possui múltiplos candidatos empatados na menor distância ao
  destino, o agente seleciona o que aparece primeiro na ordem horária: N → E → S → W.

- **R6.** A exclusão anti-bounce (`is_bounce`) é aplicada antes do tiebreak horário; candidatos
  excluídos por anti-bounce são inelegíveis independentemente da posição na ordem horária.

## Key Technical Decisions

**KTD-1 — Biased frontier em Java (SharedMap), não em ASL.**
A lógica de seleção de frontier envolve iteração sobre `cachedFrontiers` e comparação de
distâncias — código natural em Java e diretamente testável com JUnit. Alternativa (lógica em
ASL + belief `preferred_frontier`) seria inelegante e sem cobertura de teste. A operação CArtAgO
existente `get_nearest_frontier` permanece inalterada (fallback de outros callers).

**KTD-2 — Heading derivado do nome em runtime, sem belief persistido.**
O agente passa o próprio nome (`Me`) como parâmetro a cada chamada de `get_nearest_frontier_biased`;
a extração do índice e o mod 4 ocorrem no Java. Alternativa (belief `my_heading(Dir)` persistido
no startup) exigiria hook de inicialização e ponto de falha adicional. O custo de extrair o
inteiro do nome é desprezível.

**KTD-3 — Bias suave: fallback sempre ativo.**
Quando nenhuma frontier está no setor preferencial, retorna a frontier global mais próxima
(comportamento idêntico ao `get_nearest_frontier` atual). Constraint rígido (só retorna frontier
do setor) travaria agentes em regiões completamente mapeadas. (ver origin §Key Decisions)

**KTD-4 — U3 (handedness) é independente de U1/U2.**
A mudança em `pick_escape` é localizada em navigation.asl:219-233 e não depende da nova operação
Java. Pode ser commitada, medida e revertida separadamente.

**KTD-5 — `shake` mantém `.random`.**
`!shake` (navigation.asl:254-270) é o escape de encurralamento completo, não desempate de
candidatos — o handedness não se aplica aqui. Agente encurralado precisa de jitter genuíno.

**KTD-6 — PENALTY=16 e overlay de ocupação mantidos.**
O custo de colega no A* já funciona; heading + handedness são aditivos, não substituem o overlay.

## Distribuição de headings

| Heading (mod 4) | Agentes | Roles JaCaMo |
|---|---|---|
| N (mod=0) | A4, A8, A12 | collector, collector, assembler |
| E (mod=1) | A1, A5, A9, A13 | squad_leader, collector, collector, sentinel |
| S (mod=2) | A2, A6, A10, A14 | squad_leader, collector, assembler, sentinel |
| W (mod=3) | A3, A7, A11, A15 | squad_leader, collector, assembler, sentinel |

Distribuição 3/4/4/4 (N/E/S/W) — razoável; a assimetria leve de N é aceitável.

## High-Level Technical Design

```
[step(N)] → !do_explore(MX, MY)
                │
                ▼
    get_nearest_frontier_biased(MX, MY, Me, FX, FY)   ← nova op CArtAgO em SharedMap.java
                │
         ┌──────┴──────────────────────────────┐
         │ heading = extractIdx(Me) mod 4       │
         │ filtra cachedFrontiers por setor      │
         │                                       │
         │  ┌─ frontiers no setor? ─ SIM ──────► nearest do setor → (FX, FY)
         │  │
         │  └─ NÃO ───────────────────────────► nearest global   → (FX, FY)
         └──────────────────────────────────────┘
                │
                ▼
        +has_destination(FX, FY) → compute_next_move → action(move)
```

Convenção de coordenadas no frame dead-reckoned:
- N: `fy < agY` (y decresce para cima)
- E: `fx > agX`
- S: `fy > agY`
- W: `fx < agX`

---

## U1 — SharedMap: operação `get_nearest_frontier_biased`

**Arquivo:** `src/env/env/SharedMap.java`
**Arquivo de teste:** `src/test/java/env/SharedMapHeadingTest.java` (novo)

### O que muda

Adicionar nova operação `@OPERATION` imediatamente após `get_nearest_frontier`
(SharedMap.java:279). A operação existente permanece inalterada (backward-compatible).

### Lógica (orientação direcional — não implementação)

```
get_nearest_frontier_biased(agX, agY, agentName, resX, resY):
  1. Rebuild cache se necessário (mesma lógica de get_nearest_frontier)
  2. heading = extractAgentIndex(agentName) mod 4
       extractAgentIndex: extrai dígitos finais do nome (ex: "connectionA7" → 7)
       heading: 0=N, 1=E, 2=S, 3=W
  3. 1ª varredura: iterar cachedFrontiers, selecionar apenas os em inPreferredDirection(f, agX, agY, heading)
       N: f[1] < agY
       E: f[0] > agX
       S: f[1] > agY
       W: f[0] < agX
     → bestDist/best(x,y) pelo menor wrappedManhattan entre eles
  4. Se bestDist == MAX (nenhum encontrado): 2ª varredura com todos os frontiers (= get_nearest_frontier)
  5. resX.set(bx); resY.set(by)
```

Helper privados a adicionar: `extractAgentIndex(String name)` e
`inPreferredDirection(int fx, int fy, int agX, int agY, int heading)`.

### Cenários de teste (SharedMapHeadingTest.java)

| # | Cenário | Verificação |
|---|---|---|
| T1 | `extractAgentIndex("connectionA1")` | retorna 1 |
| T2 | `extractAgentIndex("connectionA15")` | retorna 15 |
| T3 | `extractAgentIndex("agentSemNumero")` | retorna -1 (fallback: heading global) |
| T4 | Agente em (5,5), heading E (mod=1=`connectionA5`), frontier em (7,5) e (5,3) | retorna (7,5) — está a leste |
| T5 | Agente em (5,5), heading N (mod=0=`connectionA4`), frontier apenas em (7,5) (leste) | fallback → retorna (7,5) — sem frontier ao norte |
| T6 | Agente em (5,5), heading S (mod=2=`connectionA2`), frontiers em (5,7) e (8,2) | retorna (5,7) — mais próxima ao sul |
| T7 | Cache vazio (nenhuma frontier) | retorna posição do agente (mesmo comportamento que get_nearest_frontier) |
| T8 | Agente em (5,5), heading W, frontier em (2,5) e (3,5) | retorna (3,5) — mais próxima a oeste |

Padrão de test setup: instanciar `SharedMap` diretamente (sem sim), popular `cachedFrontiers`
via reflection ou método auxiliar de teste, invocar via wrapper de teste (ver
`SharedMapAStarTest.java:src/test/java/env/SharedMapAStarTest.java` para o padrão de invocação).

---

## U2 — navigation.asl: heading em `!do_explore`

**Arquivo:** `src/agt/common/navigation.asl`
**Dependência:** U1 (operação Java disponível)
**Sem arquivo de teste separado** — verificação pelo replay (SC1/SC2).

### O que muda

Uma substituição localizada em `!do_explore` (navigation.asl:130-150):

```prolog
// ANTES (linha 131):
get_nearest_frontier(MX, MY, FX, FY);

// DEPOIS:
.my_name(Me);
get_nearest_frontier_biased(MX, MY, Me, FX, FY);
```

O resto do plan body permanece idêntico. O fallback `get_nearest_frontier` (R3) é
transparente — implementado dentro da operação Java (U1). Nenhuma mudança em handlers de
`last_move_blocked`, `escape_pending`, ou nas demais rotas de navegação (R4).

Callers indiretos: `role_adoption.asl:rz_disperse_until` chama `!do_explore` → recebe
heading bias automaticamente (ver origin §Dependencies/Assumptions).

---

## U3 — navigation.asl: clockwise tiebreak em `pick_escape`

**Arquivo:** `src/agt/common/navigation.asl`
**Dependência:** nenhuma — commitável e mensurável de forma independente.
**Sem arquivo de teste separado** — verificação qualitativa via replay.

### O que muda

Substituir as linhas 225-227 de `pick_escape` (o bloco `.random`) pelo tiebreak horário:

```prolog
// ANTES (navigation.asl:225-227):
.random(R);
RIdx = math.floor(R * NT);
.nth(RIdx, Ties, ChosenDir);

// DEPOIS — ordem horária N→E→S→W:
if (.member(n, Ties)) { ChosenDir = n }
elif (.member(e, Ties)) { ChosenDir = e }
elif (.member(s, Ties)) { ChosenDir = s }
else { ChosenDir = w };
```

O resto do plan body (coleta de candidatos, `.min`, anti-bounce em `score_dir`, execução
da ação) permanece idêntico. A exclusão anti-bounce (R6) ocorre em `score_dir` antes de
`esc_cand` ser populado — os `Ties` chegam até `pick_escape` já filtrados.

Caso degenerado: se `Ties` tiver um único elemento, o `if/elif/else` retorna esse elemento
corretamente. Se `Ties` estiver vazio, `esc_cand(_, _)` não é verdadeiro e o segundo
clause de `pick_escape` (navigation.asl:235-237) captura → `!boxed_step`. Invariante mantido.

---

## Sequência de entrega

```
U3 (handedness) → pode commitar primeiro, sem dependência
U1 (Java biased) → novo @OPERATION + JUnit
U2 (ASL heading) → só após U1 no gradle build
```

Recomendação: U3 primeiro (isolamento limpo, mais fácil de medir regressão de escape), depois
U1+U2 juntos numa branch (a operação Java é inútil sem o caller ASL).

## Verificação

Config: `conf/OfficialRolesConfig.json`, seed fixo, 300 steps.

- **SC1 (cobertura):** células distintas visitadas por agente extraídas do replay. Distribuição
  mais uniforme pelos quadrantes vs baseline pré-mudança. Regressão em cobertura total não é
  aceitável.

- **SC2 (qualitativo):** inspeção visual dos percursos no replay confirma dispersão em direções
  diferentes.

Ferramenta: `run-hive` skill (`.claude/skills/run-hive/run-hive.sh`) com analyzer de replay.
O harness de cenários controlados de capacidade (configurações isoladas + pipeline de análise)
é um item separado e sequencial a este (ver origin §Success Criteria).

## Scope Boundaries

**Deferido (ver origin §Scope Boundaries):**
- GPS artefato CArtAgO — extração do A* para artefato dedicado; pré-requisito para U9.
- Footprint de colegas (B1b) — penalizar footprint inteiro dos colegas no A*.
- Substituição do PENALTY=16 por custo de footprint mais principiado.
- Explorer-first — adotar role `explorer` (speed 3, vision 7) antes de transitar para `worker`.
- U9 — fusão de mapas cross-agente.

## Sources & Research

- Documento de origem: `docs/brainstorms/2026-06-18-navegacao-heading-handedness-requirements.md`
- Boot Fase C (2026-06-18): 4/15 agentes chegando à role-zone em 300 steps; replay em
  `massim_2022/server/replays/`.
- Livro MAPC 2022 (`local/978-3-031-38712-8.pdf`) §4 Lessons Learned: exploração como
  diferencial de times de topo.
- Implementação atual referenciada:
  - `src/env/env/SharedMap.java:257-279` — `get_nearest_frontier`
  - `src/agt/common/navigation.asl:130-150` — `!do_explore`
  - `src/agt/common/navigation.asl:219-233` — `pick_escape` (alvo U3)
  - `src/agt/common/navigation.asl:254-270` — `!shake` (não modificado)
- Testes existentes para padrão: `src/test/java/env/SharedMapAStarTest.java`
- Ideação original: `docs/ideation/2026-06-16-livelock-movimento-agentes-ideation.md` (#8, B6)
