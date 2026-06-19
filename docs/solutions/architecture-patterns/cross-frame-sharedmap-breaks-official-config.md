---
title: "SharedMap com frames DR cruzados: navigation cross-agente inválida sem U9"
date: 2026-06-18
category: docs/solutions/architecture-patterns
module: hive
problem_type: architecture_pattern
component: service_object
severity: critical
applies_when:
  - "absolutePosition:false está ativo (OfficialRolesConfig.json, config oficial)"
  - "Qualquer consulta cross-agente ao SharedMap para navegação (dispensers, goal-zones, role-zones)"
tags: [dead-reckoning, sharedmap, U9, navigation, cross-frame, official-config, SELF-ASSIGN]
---

# SharedMap com frames DR cruzados: navigation cross-agente inválida sem U9

## Contexto

Durante a investigação de score zero na config oficial (`conf/OfficialRolesConfig.json`,
`absolutePosition:false`), workers A8/A9/A11 adotavam o role `worker` com sucesso (steps
30/84/86) mas nunca emitiam `request`, `attach` ou `submit` em 150 e 300 steps. O log mostrava:

```
[SELF] Step 4: Auto-assigned task3 type=b0 dl=249
[COL] Nenhum dispenser b0 conhecido, explorando...
```

Nenhum `[COL] Indo coletar` aparecia durante toda a simulação — os agentes exploravam
indefinidamente, mesmo com 15 agentes ativos e outros tendo registrado dispensers no SharedMap.

A causa raiz é uma **restrição arquitetural fundamental**: o SharedMap armazena posições no
frame de dead-reckoning do agente que chamou `update_cell`, e não existe mecanismo de tradução
entre frames enquanto a fusão U9 não for implementada.

## Orientação

### 1. Por que posições DR cruzadas não podem ser compartilhadas sem calibração

No modo `absolutePosition:false`, cada agente começa com `dr_pos(0,0)` na própria posição de
spawn (`perception.asl`, linha 22):

```prolog
// frame local dead-reckoned (origem no inicio); integrado a cada move bem-sucedido.
dr_pos(0, 0).
```

O percept `position(X,Y)` nunca chega — `my_pos(MX,MY)` resolve via `dr_pos`. Dois agentes com
spawns distintos têm origens distintas: o ponto `(5,3)` no frame do agente A é uma célula
absoluta diferente do ponto `(5,3)` no frame do agente B.

### 2. Como `update_cell` armazena no frame local do chamador

Em `perception.asl` (linhas 117–119), todo percept de célula próxima é armazenado somando ao
`my_pos` atual:

```prolog
+thing(X, Y, Type, Details)
    : my_pos(MX, MY) & Type == dispenser
    <- update_cell(MX + X, MY + Y, Type, Details);
       !dash_map_dispenser(MX + X, MY + Y, Details).
```

`MX + X` e `MY + Y` são coordenadas absolutas **no frame DR do agente que está percepcionando**.
Em `SharedMap.java` (`update_cell`, linha 78–104), essas coordenadas são registradas diretamente
como chave `"x,y"` e expostas como obs-property `known_dispenser(x, y, type)`.

O artefato SharedMap é compartilhado por todos os 15 agentes (CArtAgO). Não há campo "frame de
origem" na entrada — a posição armazenada é tratada como se fosse coordenada universal.

### 3. Como `get_nearest_dispenser` calcula distância erroneamente entre frames

Em `collection.asl` (linha 108–120):

```prolog
+!collect_block(Type)
    : my_pos(MX, MY)
    <- get_nearest_dispenser(MX, MY, Type, DX, DY);
       if (DX == -1) {
           +searching_dispenser(Type);
           !do_explore(MX, MY)
       } else {
           +collecting(Type, DX, DY);
           +has_destination(DX, DY)
       }.
```

`get_nearest_dispenser` em `SharedMap.java` (linha 114–151) recebe `(agX, agY)` = `my_pos` do
agente consultante e calcula `wrappedManhattan(dx, dy, agX, agY)` para cada dispenser registrado.
Se o dispenser `(dx,dy)` foi inserido pelo agente A (frame A) e o consultante é o agente B
(frame B), a distância calculada é sem sentido — pode resultar em destino completamente errado no
grid.

**Cenário concreto**:
- Agente A15 (spawn NE, DR position `(244, 31)`) vê dispenser b0 em relativo `(-2,1)` →
  armazena `known_dispenser(242, 32, b0)` no SharedMap
- Worker A11 (spawn SW, DR position `(8, 60)`) consulta `get_nearest_dispenser(8, 60, b0, DX, DY)`
  → recebe `(242, 32)` que está no frame de A15
- A11 navega para `(242, 32)` **no próprio frame** → célula vazia, nunca encontra o dispenser

### 4. O que U9 (fusão de mapas) fornece e por que é pré-requisito

A fusão U9 (inspirada em LI(A)RA, handshake de mútua-sighting DR) resolve o problema ao calibrar
o offset entre frames quando dois agentes se veem mutuamente no mesmo step. Uma vez conhecido o
offset `(dX, dY)` entre frames, `translateCells` em `SharedMap.java` (linha 556–577) aplica
translação toroidal sobre todas as células, convertendo para um frame comum.

O método `translateCells` já existe como infraestrutura (`SharedMap.java`, comentário linha
546–554): *"Inerte no incremento 1 (nenhuma fusão chama); existe para a fusão cross-agente (U9)
entrar como camada de tradução SEM reescrever o mapa."*

Enquanto U9 não for implementado:
- Cada agente usa o SharedMap validamente apenas para as próprias observações
- Queries cross-agente retornam posições em frame errado
- Navegação baseada nessas posições é incorreta

### 5. Implicação para o SELF-ASSIGN: dispara antes de qualquer dispenser conhecido

Em `connect_protocol.asl` (linha 73–108), o plano `+step(N) : (N mod 7) == 4` dispara em step 4
— antes de qualquer exploração. O agente se auto-atribui a tarefa, chama `!collect_block(b0)`,
que chama `get_nearest_dispenser`. Como nenhum agente viu dispenser algum ainda, `DX == -1` e
todos os agentes entram em `searching_dispenser(b0)`.

O retry em `collection.asl` (linha 126–136) consulta `get_nearest_dispenser` a cada 10 steps.
Mesmo que outro agente tenha registrado um dispenser, a posição retornada estará num frame
diferente, tornando a navegação inválida no grid 70×70.

## Por que isso importa

Se assumir que o SharedMap pode ser consultado de forma cross-agente na config oficial
(`absolutePosition:false`):

- Agentes recebem destinos inválidos (frame errado) e navegam para células vazias
- `collecting(b0, DX, DY)` fica ativo com destino incorreto; o agente "chega" à posição mas
  não encontra o dispenser real
- O agente pode oscilar entre `collecting` (destino errado) e `searching` sem nunca executar
  `request` ou `attach`
- Score permanece zero mesmo com workers tendo adotado o role correto

Padrão observado: workers com role `worker` ativo durante ~200 steps fazendo apenas `move` e
`no_action`, nunca `request`. O sinal diagnóstico mais claro é `[COL] Indo coletar X no
dispenser (DX,DY)` seguido de `move` contínuo **sem** `request` — o agente chegou à coordenada
mas o dispenser não estava lá.

## Quando aplicar

- **Sempre que `absolutePosition:false`** — ou seja, toda execução com
  `conf/OfficialRolesConfig.json`. Nesse modo não existe percept `position(X,Y)`.
- Ao usar qualquer query SharedMap de um agente para navegar com base em observações de outro
  agente (dispensers, goal-zones, role-zones).
- **Não se aplica** na config de dev (`absolutePosition:true`, grid 40×40): todos os agentes
  recebem `position(X,Y)` com coordenadas absolutas — o SharedMap é consistente entre agentes e
  queries cross-agente funcionam corretamente. Por isso o sistema pontua na config dev mas marca
  zero na oficial.

## Exemplos

**Config de dev — SharedMap consistente (funciona)**:
```
Agente A (position(10,5)): vê dispenser b0 em (-1,0) → update_cell(9, 5, dispenser, b0)
Agente B (position(30,20)): consulta get_nearest_dispenser(30,20,b0,DX,DY) → recebe (9,5)
  wrappedManhattan(9,5, 30,20) = 36 → calcula rota correta para (9,5) ✓
```

**Config oficial — SharedMap com frames mistos (falha)**:
```
Agente A (dr_pos(10,5)): vê dispenser b0 em (-1,0) → update_cell(9, 5, dispenser, b0)
  // (9,5) é coordenada no frame de A; spawn em algum ponto absoluto desconhecido

Agente B (dr_pos(30,20)): consulta get_nearest_dispenser(30,20,b0,DX,DY) → recebe (9,5)
  // (9,5) está no frame de A; no frame de B esta célula é outra coisa
  wrappedManhattan(9,5, 30,20) = 36 → B navega para (9,5) no SEU frame → célula vazia ✗
```

**O que U9 resolve**:
```
Handshake mutual-sighting: A vê B em relativo (+3,-2); B vê A em relativo (-3,+2) no mesmo step.
  offset A→B calculado a partir dos percepts cruzados.
Após calibração: translateCells(dX, dY) converte entradas do mapa de A para o frame de B.
  Agente B recebe (9,5) já traduzido → coordenada correta no próprio frame → navegação válida ✓
```

**Sinal diagnóstico no log**:
- `[COL] Nenhum dispenser X conhecido, explorando...` persistindo por muitos steps com 15 agentes
  ativos → SharedMap vazio ou retornando posições inacessíveis (frame errado)
- `[COL] Indo coletar X no dispenser (DX,DY)` seguido de `move` sem `request` → agente chegou
  à coordenada mas o dispenser não estava lá (frame errado)

## Relacionado

- [`logic-errors/astar-livelock-teammate-unaware-planner.md`](../logic-errors/astar-livelock-teammate-unaware-planner.md)
  — outro problema em `SharedMap.java`/`perception.asl`, mas de origem **ortogonal**: a
  ocupação de colega usa posição própria do colega (mesmo frame, seguro), não requer calibração
  U9. O problema cross-frame aqui descrito afeta coordenadas de dispensers/goal-zones
  compartilhadas entre agentes. Não confundir os dois ao editar `SharedMap.java`.
- U9 no backlog: `docs/backlog.md` §U9 (fusão de mapas) — implementação do handshake LI(A)RA
  que habilita `translateCells` e torna o SharedMap utilizável cross-agente na config oficial.
