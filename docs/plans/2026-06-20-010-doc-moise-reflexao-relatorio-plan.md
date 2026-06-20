# doc: Nota de reflexão MOISE+ para a seção 7 do relatório (#39)

Created: 2026-06-20

## O que é este plano

Roteiro para escrever `docs/solutions/moise-fit-neste-dominio.md` — a nota de
reflexão sobre o uso de MOISE+ no HIVE, que alimenta diretamente a **seção 7 do
relatório PCS5703** (*"facilidade/dificuldade do modelo organizacional para este domínio"*).
É avaliada explicitamente. Não é código — é documentação acadêmica.

## Contexto e requisito

O enunciado do exercício (§ "deve utilizar o arcabouço JaCaMo, e **mais particularmente
o modelo organizacional MOISE+**") e a seção 7 do relatório pedem reflexão honesta
sobre o fit do modelo no domínio MAPC. Dois fatos de partida:

- **Nenhum time do MAPC 2022 usou MOISE+.** O único time Jason foi LI(A)RA — usou
  Jason puro, sem a dimensão org; diferenciação por crença/memória, controle por planos
  `+!step(S)`, grupos implícitos por avistamento mútuo (Contract-Net manual). 4º lugar.
- **A referência canônica para MOISE+ em MAPC é LTI-USP** (Stabile & Sichman, ref. [5]
  do enunciado) + Hübner [3] — não um competidor.

A nota não precisa fingir que MOISE+ foi a chave do score. Precisa mostrar que foi
**usado de forma real e refletida**, e que entendemos onde ajudou e onde brigou.

## Estrutura do documento de destino

`docs/solutions/moise-fit-neste-dominio.md` deve cobrir 5 seções:

---

### Seção 1 — Visão geral: o que pedimos à org e o que ela entregou

- Uma sentença de posição: a org **não comanda o fluxo de controle** no HIVE (KTD1
  declarado em `organization.asl:9`); ela declara estrutura, obriga missões e descarrega
  goals — o laço reativo BDI (`+step(S)` em `.asl`) segura o controle fino por-step.
- Tabela rápida: dimensão org | o que existe | evidence | avaliação.

| Dimensão | Implementado | Evidência | Fit |
|---|---|---|---|
| Estrutural | `field_agent` flat, `max=20`, sem links de autoridade | `hive_org.xml`, `HiveOrgStructureTest` PASS | ✅ alinhado com "decentralized is better" |
| Funcional | `adoption_scheme` + `task_execution_scheme` (missões descritas) | `hive.jcm`, cenário `01-adopt` PASS | ✅ elo U4 real; restante é potencial |
| Normativa | `n_adopt` (obriga `m_adopt`), `n_collect` | replay: adoção disparada pela org | ✅ gate de nota ativo |

---

### Seção 2 — Onde MOISE+ ajudou: o elo de adoção U4

O único ponto onde a org **genuinamente dirige comportamento mensurável**:

1. `n_adopt` obriga `field_agent` a comprometer `m_adopt` via `adoption_scheme`.
2. `organization.asl` reage ao `+obligation(…committed(…m_adopt…))` → `commitMission`.
3. Ao virar `worker` (`+my_role(_)` com `can_score_role`), `goalAchieved(worker_role_adopted)`
   é chamado — descarregando o goal do scheme.
4. Resultado observável: a adoção é **declarativa e extensível** — mudar o path de adoção
   (`role_adoption_path([worker]).`) e a norma é suficiente, sem reescrever o laço.

Evidência a citar: cenário `01-adopt` PASS; replay oficial com `adopt:1` para cada agente.

**Por que isso importa para a seção 7:** é um caso real onde MOISE+ adicionou valor
(declaratividade do requisito de adoção) sem criar acoplamento com o controle reativo.

---

### Seção 3 — Onde MOISE+ brigou: autoridade centralizada e KTD1

**3a. Hierarquia squad-era vs "decentralized is better"**

A estrutura original (`squad_leader` → `collector`/`assembler`/`sentinel`) criou:
- Cardinalidade `max=19 < 20` — a org não admitia os 20 agentes do Sim1.
- `failed_role` nos replays: `squad_leader` tentava `request` sem ter a ação (`worker`
  é quem tem `request` no cenário; o role org e o role MAPC são camadas distintas).
- Concentração de decisão num único agente = ponto único de falha; contradiz a evidência
  do contest: *"it is usually not helpful to come up with a centralized solution"*
  (organizers, MAPC 2022 book, §1.3).

Resolução: A2 (`field_agent` flat, #38) — achatamento eliminou a classe de bug.

**3b. KTD1 — o controle fino que o BDI não cede**

O laço reativo Jason (`+step(S)`, `+!move(D)`, guarded plans) precisa tomar decisões
por-step com latência de agente. Delegar esse controle à org exigiria um mecanismo de
"lock" — que LI(A)RA **implementou manualmente** (per-step goal delegation, sem MOISE+).
Com MOISE+, a org não tem o clock de step do simulador: ela obriga e descarrega, mas não
emite ações MASSim.

Consequência: a org é usada onde esse clock não importa (adoção — acontece uma vez,
no início) e não onde importa (coleta → montagem → submit — cada step conta).

---

### Seção 4 — Decisão de achatamento e alinhamento com a literatura

A escolha de colapsar para `field_agent` flat (A2, #38/#53/#54):
- **Alinhamento com LI(A)RA:** agentes homogêneos diferenciados por crença (`my_role`,
  `can_score_role`), não por tipo org fixo. O Contest premeia essa arquitetura.
- **MOISE+ como camada declarativa acima dos roles MAPC:** `field_agent` (org) ≠ `worker`
  (cenário) — a org descreve a estrutura funcional do time; o role MAPC gateia ações no
  simulador. Confundir os dois é o erro clássico (documentado em `CONCEPTS.md`).
- **Missões dinâmicas em vez de roles fixos:** `m_collect`/`m_assemble`/`m_submit`
  permanecem como missões disponíveis, não obrigadas por norma — abertas para Stance B.

---

### Seção 5 — Trabalho futuro: Stance B (org como alocador, #22)

Se o pipeline pontuar consistentemente (gate: submits/run estável), o próximo passo é
medir se a org pode **dirigir a alocação de tasks** (substituindo parte do `TaskBoard`
Java):

- Um scheme de task-execution com obrigações por role dinâmico vs baseline sem-org.
- Métrica: `submits/run` com org vs sem org, mantendo tudo o mais constante.
- Hipótese: a redução de agentes ociosos (org monitora quem está em qual missão) pode
  superar o custo de coordenação, mas não há evidência ainda.

Se a medição mostrar que a org não melhora o score, a conclusão também é válida —
e ainda mais honesta para a seção 7.

---

## Fontes a citar no documento

| Ref | Conteúdo a citar |
|---|---|
| [3] Hübner et al. — MOISE+ | Definição das dimensões estrutural/funcional/normativa; modelo de obrigação |
| [5] Stabile & Sichman (LTI-USP) | Uso de JaCaMo em MAPC — referência canônica do enunciado |
| MAPC 2022 book (978-3-031-38712-8) | "it is usually not helpful to come up with a centralized solution" (§1.3); análise dos times |
| LI(A)RA (cap. do livro) | Jason sem MOISE+; grupos implícitos; Contract-Net manual; por que funcionou |
| Código próprio | `hive_org.xml`, `organization.asl`, `HiveOrgStructureTest`, replays `01-adopt` |

As referências [3] e [5] estão citadas no enunciado (`local/5703_ex02_26.pdf`) e devem
ser usadas exatamente com o número do enunciado para manter consistência no relatório.

## Arquivo de destino e formato

`docs/solutions/moise-fit-neste-dominio.md`

Frontmatter seguindo o padrão de `docs/solutions/`:

```yaml
---
title: "MOISE+ no domínio MAPC: onde ajudou, onde brigou e o que achatamos"
date: 2026-06-20
category: docs/solutions/architecture-patterns
module: hive
problem_type: architecture_pattern
component: org
severity: medium
applies_when:
  - "Relatório seção 7 (facilidade/dificuldade do modelo organizacional)"
  - "Decisão de arquitetura org (Stance A vs B)"
tags: [moise, organization, field_agent, adoption, KTD1, squad-leader, flat-team, section7]
---
```

## O que NÃO cobrir (fora de escopo)

- Detalhes de implementação de código (já cobertos em #36 e nos commits)
- Comparação quantitativa de score com/sem MOISE+ (precisa #22, ainda aberta)
- Reflexão sobre outras frentes (navegação, coleta) — a seção 7 é sobre o modelo org
- Opinião sobre o contest ou a disciplina — apenas o fit técnico do modelo

## Verificação

O documento está completo quando:
- Cobre as 5 seções acima, cada uma com evidência concreta (código ou replay)
- Cita [3], [5] e o livro MAPC 2022 com pelo menos uma afirmação específica
- KTD1 está explicado em termos de BDI (não só "a org não dirige")
- A distinção role-org ≠ role-MAPC está explícita
- Não afirma que MOISE+ melhorou o score sem evidência mensurável
