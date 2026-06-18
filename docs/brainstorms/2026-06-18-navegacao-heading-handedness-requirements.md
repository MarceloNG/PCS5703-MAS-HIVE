---
date: 2026-06-18
topic: navegacao-heading-handedness
---

# Navegação: heading-balanceado + handedness

## Summary

Dois ajustes comportamentais na pilha de navegação existente para reduzir exploração redundante e deadlocks de corredor. Cada agente deriva uma direção preferencial estática do próprio nome e a usa para biasar a seleção de frontier durante exploração, distribuindo os 15 agentes pelos quadrantes do mapa. Empates no escape reativo passam a ser quebrados no sentido horário em vez de aleatoriamente. Nenhum novo artefato CArtAgO; ambas as mudanças ficam no código existente.

---

## Problem Frame

O boot de validação da Fase C (2026-06-18, config `OfficialRolesConfig.json`, 70×70, 300 steps) mediu apenas 4/15 agentes chegando a uma role-zone. Com `absolutePosition:false` e frames dead-reckoned independentes, cada agente explora por conta própria sem saber o que os colegas já mapearam. O resultado observado é **exploração redundante**: os 15 agentes tendem a percorrer as mesmas regiões próximas à origem, sem se dispersar pelos quadrantes do grid.

O livro MAPC 2022 confirma que a qualidade da estratégia de exploração distingue os times de topo: a Paula (warm-up, quase vice) foi a única a usar explorer-first sistematicamente (~100 steps de speed 3/vision 7) antes de transitar para worker. Mesmo sem compartilhamento de mapa (U9), a dispersão coordenada reduz a redundância e acelera a descoberta de role-zones, dispensers e goal-zones.

O mecanismo de fuga reativo atual (`pick_escape`) usa `.random` para quebrar empates de distância. Com 15 agentes que tendem a se aglomerar na mesma região, deadlocks de corredor simétrico — dois agentes se encarando e alternando a mesma célula — não são resolvidos de forma confiável pelo jitter aleatório.

---

## Key Decisions

- **Heading como viés suave, não partição rígida.** Quando o setor preferencial do agente não tem mais frontiers, ele volta à frontier global mais próxima. Um constraint rígido travaria agentes em áreas já completamente mapeadas e produziria ociosidade.

- **GPS artefato deferido para a U9.** A extração do A* do `SharedMap` para um artefato CArtAgO separado é o pré-requisito para rotear sobre o mapa fundido (U9), não para heading + handedness. Introduzir a extração agora aumenta o blast radius sem benefício mensurável nesta fase.

- **PENALTY=16 e overlay de ocupação mantidos.** O custo de colega no A* já funciona; heading + handedness são aditivos, não substituem o overlay.

- **Footprint de colegas deferido.** O gargalo atual é agente `default` sem blocos buscando role-zone; validação de footprint de bloco importa quando o agente carrega. A validação do próprio footprint no escape já existe parcialmente em `compute_legal` e cobre o caso imediato.

---

## Requirements

### Heading-balanceado (dispersão por setor)

- R1. Cada agente deriva uma direção preferencial estática (N, S, L, O) a partir do próprio nome, usando índice do agente mod 4. A derivação ocorre uma única vez e é estável para toda a simulação.

- R2. Ao selecionar uma frontier durante exploração, o agente prioriza frontiers que estejam na direção preferencial relativa à sua posição atual.

- R3. Quando não existem frontiers na direção preferencial, o agente recorre à frontier global mais próxima, sem alterar o comportamento existente de `get_nearest_frontier`.

- R4. O heading bias se aplica exclusivamente ao caminho de exploração (`!do_explore`). Navegação com destino explícito (busca de role-zone conhecida, meeting point, goal zone) usa o A* existente sem viés.

### Handedness (quebra de simetria no escape)

- R5. Quando `pick_escape` possui múltiplos candidatos empatados na menor distância ao destino, o agente seleciona o que aparece primeiro na ordem horária: N → L → S → O.

- R6. A exclusão anti-bounce (`is_bounce`) é aplicada antes do tiebreak horário; candidatos excluídos por anti-bounce são inelegíveis independentemente da posição na ordem horária.

---

## Key Flows

- F1. **Exploração com heading bias**
  - **Trigger:** agente sem destino ativo entra em `!do_explore`.
  - **Passos:** (1) determina direção preferencial do nome; (2) busca frontiers na direção preferencial; (3) se encontrou: move em direção à frontier mais próxima nessa direção; (4) senão: fallback para frontier global mais próxima (comportamento atual).
  - **Cobre:** R1, R2, R3, R4.

- F2. **Escape com tiebreak horário**
  - **Trigger:** move bloqueado ou oscilação detectada; `pick_escape` tem candidatos empatados.
  - **Passos:** (1) coleta candidatos legais na menor distância; (2) remove candidatos anti-bounce; (3) dos restantes, seleciona o primeiro na ordem N → L → S → O; (4) se nenhum sobrou após anti-bounce: comportamento encurralado existente (`!boxed_step`).
  - **Cobre:** R5, R6.

---

## Success Criteria

Ambas as mudanças são validadas em **cenário controlado com seed fixo** (70×70, 300 steps), lendo o replay como fonte de verdade:

- **SC1 (primário — cobertura).** Células distintas visitadas por agente após 300 steps devem mostrar distribuição mais uniforme pelos quadrantes do grid comparado ao baseline (pré-mudança). O analyzer de replay extrai o conjunto de posições por agente e calcula cobertura por quadrante.

- **SC2 (qualitativo).** Inspeção visual dos percursos no replay confirma que os agentes se dispersam em direções diferentes em vez de se aglomerar na mesma região. Regressão de cobertura total (soma de células distintas de todos os agentes) não é aceitável.

Nota: o harness de cenários controlados de capacidade (definição de configurações isoladas + pipeline de análise) é um item separado e sequencial a este.

---

## Scope Boundaries

**Deferido para depois:**
- GPS artefato CArtAgO — extração do A* para artefato dedicado; pré-requisito para U9 (roteamento sobre mapa fundido).
- Footprint de colegas (B1b) — penalizar o footprint inteiro dos colegas no A*; exige infra nova (publicação de footprint por agente).
- Substituição do PENALTY=16 por custo de footprint mais principiado.
- Explorer-first — adotar role `explorer` (speed 3, vision 7) para mapear rapidamente antes de transitar para `worker`; próxima fase de capacidade.
- U9 — fusão de mapas cross-agente; gated atrás de role adoption + navegação estável.

---

## Dependencies / Assumptions

- O frame dead-reckoned é estável dentro de uma execução (sem restart de agente). A direção N/S/L/O tem sentido consistente no frame local — heading-balanceado é robusto a drift de posição (o drift acumula na posição, não na direção).
- O mecanismo `rz_disperse_until` (janela de dispersão de 15 steps após bloqueio na role-zone) chama `!do_explore`, logo receberá o heading bias automaticamente — o agente dispersa em direção ao seu setor, que é o comportamento desejado.

---

## Sources / Research

- Boot de validação Fase C (2026-06-18): medição de 4/15 chegando à role-zone em 300 steps; `failed_path ~50%`. Replay em `massim_2022/server/replays/`.
- Livro MAPC 2022 (`local/978-3-031-38712-8.pdf`), §4 Lessons Learned: Paula (warm-up, quase vice) única a usar explorer sistematicamente; strategy de exploração como diferencial de times de topo.
- Ideação de livelock (`docs/ideation/2026-06-16-livelock-movimento-agentes-ideation.md`): conceitos de heading-balanceado e handedness (#8, B6); convergência de múltiplos subagentes cegos nas ideias de anti-simetria.
- Backlog (`docs/backlog.md`), parking lot "Navegação — dispersão + handedness consistente (ELEVADO)": contexto da elevação + evidência do boot que motivou.
- Implementação atual: `src/agt/common/navigation.asl` (escape reativo, `pick_escape`, `!do_explore`); `src/env/env/SharedMap.java` (`get_nearest_frontier`, overlay #2 PENALTY=16, `astar`).
