---
title: "MOISE+ no domínio MAPC: onde ajudou, onde brigou e o que achatamos"
date: 2026-06-20
category: docs/solutions/architecture-patterns
module: hive
problem_type: architecture_pattern
component: org
severity: medium
applies_when:
  - "Relatório seção 7 (facilidade/dificuldade do modelo organizacional para este domínio)"
  - "Decisão de arquitetura org (Stance A vs B)"
tags: [moise, organization, field_agent, adoption, KTD1, squad-leader, flat-team, section7]
---

# MOISE+ no domínio MAPC: onde ajudou, onde brigou e o que achatamos

## 1. Visão geral: o que pedimos à org e o que ela entregou

**Posição de design (KTD1):** a organização MOISE+ não dirige o fluxo de controle no HIVE.
Declarado explicitamente em `src/agt/common/organization.asl:9`:

```prolog
// KTD1: a organização NÃO dirige o fluxo de controle. O líder + leilão (TaskBoard)
// seguem no comando do comportamento real.
```

O laço reativo BDI (`+step(S)` em `hive_agent.asl`) retém o controle fino por-step — ele
precisa tomar decisões com latência de agente, dentro do tick do simulador. A org opera numa
camada acima: declara estrutura, obriga missões e regista conclusões, mas não emite ações MASSim.

| Dimensão org | Implementado | Evidência | Avaliação de fit |
|---|---|---|---|
| **Estrutural** | Papel único `field_agent` flat, `max=20`, sem links de autoridade | `hive_org.xml:17-28`, `HiveOrgStructureTest` PASS | ✅ Alinhado com "decentralized is better" (MAPC 2022, §1.3) |
| **Funcional** | `adoption_scheme` + `task_execution_scheme` (missões descritas) | `hive_org.xml:77-95`, `hive.jcm` | ✅ Elo U4 vivo; missões de coleta/montagem disponíveis p/ Stance B |
| **Normativa** | `n_adopt` (obriga `m_adopt`), `n_collect` | `hive_org.xml:107-108`, replay: adoção disparada pela norma | ✅ Gate de nota ativo; `n_collect` preparada para Stance B |

## 2. Onde MOISE+ ajudou: o elo de adoção U4

O único ponto onde a org **genuinamente dirige comportamento mensurável** é a cadeia de adoção
de role MAPC:

1. **Norma dispara compromisso:** `n_adopt` em `hive_org.xml:107` obriga cada `field_agent` a
   comprometer `m_adopt` via `adoption_scheme`. Ao receber `+obligation(…committed(…m_adopt…))`,
   `organization.asl:18-23` chama `commitMission(m_adopt)` e registra `adopt_duty(Scheme, W)`.

2. **BDI adota o role MAPC:** quem emite a ação `adopt(worker)` ao simulador é `role_adoption.asl`
   (plano `+step(S)`), não a org — isso evita a "dupla-ação" que travaria o clock por-step
   (`organization.asl:33-36`).

3. **Org regista a conclusão:** ao virar `worker` (crença `+my_role(_)`) com `can_score_role`
   satisfeito, `organization.asl:37-41` emite `goalAchieved(worker_role_adopted)` descarregando
   o scheme da org. Nenhuma ação MASSim extra é emitida.

4. **Resultado observável:** a adoção é **declarativa e extensível** — mudar o path de adoção
   (por exemplo, introduzir `explorer` antes de `worker`) exige apenas alterar
   `role_adoption_path([…]).` em `role_adoption.asl` e ajustar a norma; o laço `organization.asl`
   não precisa ser reescrito.

Evidência: cenário `01-adopt` PASS; replay oficial com `adopt:1` para cada agente que descobre
a role-zone.

**Por que isso importa para a seção 7 do relatório:** é um caso real onde MOISE+ adicionou
valor — a declaratividade do requisito de adoção — sem criar acoplamento com o controle reativo
BDI. O arcabouço Hübner [3] prevê exatamente esse uso: normas que obrigam missões sem microgerir
como o agente as cumpre.

## 3. Onde MOISE+ brigou: autoridade centralizada e KTD1

### 3a. Hierarquia squad-era vs "decentralized is better"

A estrutura original (`squad_leader → collector / assembler / sentinel`) criou três classes de
problema, todas verificáveis em replays da era-squad:

- **Cardinalidade insuficiente:** a soma `max` dos papéis era 19 — a org não admitia os 20 agentes
  do Sim1. Um agente sempre ficava sem papel e, portanto, sem obrigação de adotar `worker`.
- **`failed_role` em replay:** `squad_leader` tentava emitir `request` diretamente. Mas `request`
  é uma ação do role **MAPC** `worker`, não do role **organizacional** `squad_leader` — os dois
  planos são camadas distintas. Confundir role de cenário com role organizacional é o erro clássico
  documentado em `CONCEPTS.md`.
- **Ponto único de falha:** concentrar decisão num único agente contradiz diretamente a evidência
  do contest: *"it is usually not helpful to come up with a centralized solution"*
  (organizadores MAPC 2022, livro §1.3).

Resolução: achatamento para `field_agent` flat (issue #38). A classe inteira de bugs se dissolve
porque não há mais hierarquia a violar.

### 3b. KTD1 — o controle fino que o BDI não cede

O simulador MASSim opera em ticks discretos (steps). A cada step, cada agente pode emitir **uma**
ação. Decisões como "qual direção mover", "desviar do colega", "executar `attach` agora" exigem
latência de agente — elas devem ser tomadas dentro do mesmo tick, com acesso ao estado de crença
atualizado naquele momento.

MOISE+ opera fora desse clock: obrigações e missões são eventos assíncronos relativamente ao
tick do simulador. Para que a org dirigisse o fluxo de coleta/montagem/submit, seria necessário
um mecanismo de "lock" por-step — que LI(A)RA implementou **manualmente** (delegação de goal
por-step, sem MOISE+, usando Contract-Net por crença). Com MOISE+, a org não tem acesso ao clock
de step do simulador: ela obriga e descarrega, mas não emite ações MASSim.

**Consequência de design:** a org é usada onde o clock de step não importa (adoção — acontece
uma vez, no início da simulação) e **não** onde ele importa (coleta → montagem → submit — cada
step conta). Essa não é uma limitação de MOISE+ em geral; é uma escolha explícita dada a
urgência de cada ciclo de ação no domínio MAPC.

## 4. Decisão de achatamento e alinhamento com a literatura

A escolha de colapsar para `field_agent` flat (A2, issues #38/#53/#54) se apoia em dois pilares:

**Alinhamento com LI(A)RA [ver cap. do livro MAPC 2022]:** o time Jason 4º colocado usou agentes
homogêneos diferenciados por crença (`my_role`, `can_score_role`), não por tipo org fixo. A
diferenciação funcional (explorar/coletar/montar) emergiu de grupos implícitos por avistamento
mútuo e Contract-Net manual — sem MOISE+. O Contest premiou essa arquitetura. O HIVE replica o
princípio: `field_agent` (org) ≠ `worker` (cenário MAPC) — a org descreve a estrutura funcional
do time; o role MAPC gateia ações no simulador. São camadas ortogonais.

**MOISE+ como camada declarativa acima dos roles MAPC:** `hive_org.xml` descreve o que o time
deve fazer (adotar worker, coletar, montar); `hive_org.xml` não descreve como o simulador gere
as ações. Essa separação é teoricamente saudável (Hübner [3], §3: dimensão funcional descreve
responsabilidades coletivas, não protocolos de ação). O problema da era-squad foi misturar os
dois planos, não o modelo em si.

**Missões dinâmicas em vez de roles fixos:** `m_collect`, `m_assemble` e `m_submit` permanecem
especificadas na dimensão funcional (`hive_org.xml:50-71`), mas sem norma ativa — abertas para
Stance B (#22) quando o pipeline pontuar consistentemente.

## 5. Trabalho futuro: Stance B (org como alocador, #22)

A Stance A entregou a dimensão estrutural e o elo de adoção verificado (JUnit + cenário `01-adopt`).
A pergunta que a Stance B deve responder por medição é: a org pode **dirigir a alocação de tasks**
melhor do que o `TaskBoard` Java atual?

O experimento proposto (gated: pipeline pontuando de forma estável):

- Ativar normas `n_assemble`/`n_submit` no scheme de `task_execution` para rôles dinâmicos.
- Medir `submits/run` com org vs baseline sem-org, mantendo tudo o mais constante.
- Hipótese: a redução de agentes ociosos (org monitora quem está em qual missão) pode superar
  o custo de coordenação extra. Mas **não há evidência ainda** — a medição decide.

Se a medição mostrar que a org não melhora o score, a conclusão também é válida para a seção 7:
usamos MOISE+ de forma real e refletida, medimos, e concluímos que neste domínio o valor está
na declaratividade de adoção (camada de obrigação), não na alocação de tasks (camada reativa
BDI é mais ágil para cada step).

## Fontes

| Ref | Uso neste documento |
|---|---|
| [3] Hübner et al. — MOISE+ | Dimensões estrutural/funcional/normativa; modelo de obrigação; §3 (missões não são protocolos de ação) |
| [5] Stabile & Sichman (LTI-USP) | Referência canônica do enunciado para JaCaMo em MAPC |
| MAPC 2022 book (978-3-031-38712-8) | §1.3: "it is usually not helpful to come up with a centralized solution"; análise de times |
| LI(A)RA (cap. do livro MAPC 2022) | Jason sem MOISE+; agentes homogêneos; Contract-Net manual; 4º lugar |
| Código próprio | `hive_org.xml`, `organization.asl`, `HiveOrgStructureTest`, replays `01-adopt`, issues #38/#53/#54 |
