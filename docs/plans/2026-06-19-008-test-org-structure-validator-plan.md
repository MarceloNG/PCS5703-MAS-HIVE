---
title: "test: validador pseudo-unit do hive_org.xml (issue #37)"
type: test
status: active
date: 2026-06-19
issue: 37
sibling: 38
espinha: 36
---

# test: validador pseudo-unit do `hive_org.xml` (issue #37)

## Summary

Adicionar um único arquivo de teste JUnit 5 — `HiveOrgStructureTest` — que faz parse de
[src/org/hive_org.xml](src/org/hive_org.xml) e asserta três invariantes **estruturais** do MOISE+,
**sem rodar a simulação**. É o degrau **Stance A** da frente Organização (espinha #36) e o **par
test-first** da #38 (achatar a estrutura): a asserção de cardinalidade **nasce VERMELHA hoje**
(soma dos `max` = 19 < 20) e fica verde quando a #38 achatar o time.

Escopo estrito: **só o arquivo de teste novo**. Esta issue **não altera** `hive_org.xml` — isso é a #38.

---

## Problem Frame

A camada MOISE+ tem duas armadilhas estruturais silenciosas, hoje sem guarda:

1. **Cardinalidade cap. o time abaixo do necessário.** A soma dos `max` dos roles do grupo
   `hive_team` é **19** (`squad_leader 4 + collector 8 + assembler 4 + sentinel 3`), mas o Sim1 do
   contest tem **20 agentes** → a org, como está, *não admite o time exigido*. Nada hoje detecta isso.
2. **Role excluído do score.** Se algum role org não comprometer `m_adopt`, agentes daquele role
   **nunca adotam o `worker` MAPC** → não pontuam (era o sintoma do `sentinel`). Nada hoje detecta isso.

Ambas são detectáveis por **parse estático do XML** — um teste pseudo-unit em milissegundos, sem
o ruído/custo de uma simulação. Este plano entrega esse guarda.

---

## Requirements

Rastreáveis à issue #37 (três invariantes):

- **R1 — Cardinalidade cabe o Sim1.** A soma dos atributos `max` dos roles do grupo `hive_team`
  é **≥ 20**. *(VERMELHO hoje: 19 < 20.)*
- **R2 — Todo role pode pontuar.** Todo role declarado em `<role-definitions>` aparece como `role`
  de alguma norma `obligation` com `mission="m_adopt"` na `<normative-specification>`. *(Verde hoje.)*
- **R3 — Integridade referencial + bem-formado.** O XML parseia (bem-formado) e todo role referenciado
  em `<link from/to>` e em `<norm role=...>` existe em `<role-definitions>`. *(Verde hoje.)*

Cada invariante é um método `@Test` independente (relato isolado de PASS/FAIL).

---

## Key Technical Decisions

- **KTD1 — Carregar o XML como recurso de classpath.** `src/org` é um `resources.srcDir` do sourceSet
  `main` (ver [build.gradle](build.gradle)) → `hive_org.xml` está na raiz do classpath de teste. Carregar
  via `getResourceAsStream("/hive_org.xml")` (sem depender do working-dir). *Fallback:* se vier `null`,
  ler o arquivo `src/org/hive_org.xml` relativo ao projeto e, se também faltar, falhar com mensagem clara.
- **KTD2 — DOM não-namespace-aware.** O XML usa `xmlns` default mas **sem prefixos** nos elementos →
  `DocumentBuilderFactory` no modo padrão (não-NS) + `getElementsByTagName("...")` casa pelos nomes de tag
  como aparecem (`role`, `group-specification`, `norm`). Mais simples e suficiente. Navegar o DOM para
  isolar os `role` **dentro de** `<group-specification id="hive_team">/<roles>` (não confundir com os 4
  `<role>` de `role-definitions` nem com o atributo `role` de `<extends>`/`<norm>`).
- **KTD3 — RED é o entregável, e vive no branch (não na main sozinho).** R1 falha hoje **por design** —
  é o documento executável da invariante que a #38 vai satisfazer. Como `gradle test` é o gate verde do
  projeto, **#37 não faz merge na main sozinho**: o teste VERMELHO permanece no branch `feat/sc-37-org-validator`
  e entra na main **junto com a #38** (que o torna VERDE), de modo que a main nunca vê o gate vermelho.
  *(Decisão a confirmar pelo dono no gate de aprovação — alternativa seria shippar `@Disabled` referenciando
  a #38; o pedido literal da #37 é "nascer VERMELHO", então o default é RED-no-branch.)*
- **KTD4 — Não tornar verde editando o XML.** Fazer R1 passar é trabalho da #38 (achatar). Nesta issue,
  a falha de R1 com mensagem clara **é o resultado correto** — não editar `hive_org.xml`.

---

## Implementation Units

### U1. `HiveOrgStructureTest` — validador estático do `hive_org.xml`

**Goal:** um arquivo de teste JUnit 5 com 3 métodos `@Test`, um por invariante (R1/R2/R3), que parseia
o `hive_org.xml` e asserta as invariantes estruturais. R1 VERMELHO hoje; R2/R3 VERDES.

**Requirements:** R1, R2, R3.

**Dependencies:** nenhuma (Java puro; sem sim).

**Files:**
- Criar: `src/test/java/hive/HiveOrgStructureTest.java`

**Approach:**
- Helper privado que carrega e parseia o `hive_org.xml` uma vez (classpath-first — KTD1), via
  `DocumentBuilderFactory` não-NS (KTD2), retornando o `Document`.
- **R1 (`cardinalidadeDoTimeCabeOSim1`)**: localizar `group-specification` de `id="hive_team"`,
  iterar seus `<role>` filhos (dentro de `<roles>`), somar o atributo `max`, asseverar soma **≥ 20**.
  Mensagem de falha deve nomear a soma encontrada (ex.: `"soma dos max = 19, esperado >= 20"`).
- **R2 (`todoRoleComprometeAdoptDoWorker`)**: coletar os `id` dos roles em `<role-definitions>`;
  coletar os atributos `role` das `<norm>` com `mission="m_adopt"`; asseverar que o 1º conjunto
  está contido no 2º. Mensagem nomeia os roles faltantes.
- **R3 (`integridadeReferencialDeRoles`)**: o parse bem-sucedido prova bem-formado; coletar role ids
  declarados; asseverar que todo `from`/`to` de `<link>` e todo `role` de `<norm>` ∈ declarados.
- Sem dependência de ordem entre os métodos; cada um reparseia ou reusa o helper.

**Execution note:** **Test-first / RED intencional.** R1 deve FALHAR hoje (19 < 20) — essa falha é o
entregável de #37, não um defeito. **Não** editar `hive_org.xml` para passar (KTD4 — isso é a #38).

**Patterns to follow:** estilo e nomes em PT-BR como em
[src/test/java/env/SquadCoordinatorTest.java](src/test/java/env/SquadCoordinatorTest.java) e
[src/test/java/hive/](src/test/java/hive/) (JUnit 5 Jupiter, `@Test`, asserts estáticos, nomes de método
descritivos em PT-BR).

**Test scenarios** (o próprio arquivo é o teste; cenários = os 3 métodos):
- `cardinalidadeDoTimeCabeOSim1`: soma dos `max` de `hive_team` ≥ 20. **Esperado HOJE: FAIL (19 < 20).**
- `todoRoleComprometeAdoptDoWorker`: {squad_leader, collector, assembler, sentinel} ⊆ roles com `m_adopt`.
  **Esperado HOJE: PASS** (os 4 têm `n_adopt_*`).
- `integridadeReferencialDeRoles`: roles em links/normas ∈ role-definitions; XML bem-formado.
  **Esperado HOJE: PASS.**

**Verification:**
- `~/tools/gradle-8.10/bin/gradle test` compila e roda; `HiveOrgStructureTest` aparece com **2 passes
  e 1 fail** (R1), e a falha traz a mensagem clara `19 ... >= 20` (assertion, **não** exceção/erro de parse).
- Confirmar que `hive_org.xml` **não** foi modificado (`git status` limpo exceto o novo arquivo de teste).

---

## Scope Boundaries

**Nesta issue (#37):**
- Apenas `src/test/java/hive/HiveOrgStructureTest.java` (novo).

**Fora (Deferred / outras issues):**
- **Editar `hive_org.xml` / achatar a estrutura (max ≥ 20, drop hierarquia)** → **#38** (faz R1 ficar verde).
- **Smoke sim curta** validando role_adoption sem `failed_role` → **#38**.
- **Nota de reflexão seção 7** → #39. **Alocação org-dirigida + baseline** → #22. Visão da frente → espinha #36.

---

## Sequencing & Merge Note

```
#37 (este plano) — escreve o teste; R1 VERMELHO no branch feat/sc-37-org-validator
   └─ #38 achata hive_org.xml → R1 VERDE
```

**Merge:** por KTD3, **não** fazer squash-merge de #37 na main isoladamente (deixaria o gate `gradle test`
vermelho). Ou (a) sequenciar #37+#38 e mergear juntos, ou (b) — se o dono preferir manter a main sempre
verde antes da #38 — shippar o método R1 com `@Disabled` referenciando a #38. Decisão do dono no gate de
aprovação (o projeto faz squash-merge direto na main após aprovação, sem PR).
