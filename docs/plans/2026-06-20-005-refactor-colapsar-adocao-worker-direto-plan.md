---
date: 2026-06-20
type: refactor
title: "refactor(role): colapsar adoção default→worker direto (extensível)"
status: completed
origin: docs/brainstorms/2026-06-18-fase-c-adocao-role-requirements.md
closes: "#54"
---

# refactor(role): colapsar adoção default→worker direto (extensível)

## Summary

O fluxo atual `default→explorer→worker` tem dois degraus porque o `explorer` conferia `speed=3` na busca pela role-zone — ganho **não medido**. A issue #54 colapsa para `default→worker` direto, mantendo um ponto único e parametrizável (`role_adoption_path`) para que reintroduzir `explorer`/`constructor` seja trocar a lista, não reescrever os planos.

---

## Problem Frame

Em `role_adoption.asl` o plano `!ensure_worker_role` tem três ramos relevantes:

1. `role(explorer) & roleZone(0,0)` → `adopt(worker)` (segundo degrau)
2. `role(explorer) & my_pos(MX, MY)` → `!seek_role_zone` com speed=3 herdado
3. `roleZone(0,0)` → `adopt(explorer)` (primeiro degrau — estado intermediário)

O ramo 3 introduz um step extra de adoção sem benefício comprovado. Removê-lo encurta o caminho para `worker` e elimina o risco de agentes ficarem presos no role `explorer` esperando confirmação.

A DoD da issue exige:
- Zero `adopt(explorer)` no replay.
- Adoção em IsolationRolesConfig não regride (≥ baseline de workers adotados).
- "Único ponto" documentado para reintroduzir explorer/constructor.

---

## Key Technical Decisions

| ID | Decisão | Rationale |
|----|---------|-----------|
| KTD1 | Adicionar `role_adoption_path([worker]).` como fato único | O path fica em **uma linha**; trocar para `[explorer, worker]` amanhã reintroduz o degrau sem reescrever planos — alinha com DoD #54 |
| KTD2 | Arm `roleZone(0,0) & role_adoption_path([Next\|_])` — quando sobre a role-zone, lê o próximo role do path e adota-o | Evita hardcode de `worker`; hoje `Next=worker`, amanhã `Next=explorer` |
| KTD3 | Remover ramos `role(explorer) & …` sem guardar lógica morta | Não há explorer no time pós-mudança; deixar arms mortos aumentaria ruído |
| KTD4 | Atualizar comentário de `can_score_role` | O comentário cita `explorer` como estado intermediário — ficará desatualizado após a mudança |

---

## Scope Boundaries

**Em escopo**
- Colapso dos ramos `explorer` em `!ensure_worker_role`.
- Adição do fato `role_adoption_path([worker])` como ponto único.
- Atualização de comentários afetados.

**Deferred to Follow-Up Work**
- Medir time-to-first-adoption com vs sem `explorer` (speed=3): issue separada se o ganho aparecer (parking lot do backlog).
- Elo MOISE+ (adoção dirigida por obrigação org): TODO U4 em `role_adoption.asl:68` — issue #56 (ou nova).
- Reintrodução de `explorer`/`constructor` quando evidência sustentar.

---

## Implementation Units

### U1. Colapsar !ensure_worker_role — default→worker direto + ponto parametrizável

**Goal:** Remover os dois ramos `explorer` de `!ensure_worker_role`, mudar o arm `roleZone(0,0)` para adotar `worker` diretamente via `role_adoption_path`, e atualizar comentários afetados.

**Requirements:** DoD #54 — zero `adopt(explorer)`, ponto único, baseline não regride.

**Dependencies:** Nenhuma.

**Files:**
- `src/agt/common/role_adoption.asl` (modify)

**Approach:**

1. Adicionar o fato de configuração estático **no topo de `role_adoption.asl`**, antes de qualquer plano (mesmo padrão de `my_role_type(hive_agent).` em `hive_agent.asl` — fato de crença inicial, não asserted em runtime):

   ```
   // ÚNICO PONTO: trocar para [explorer, worker] para reintroduzir o degrau de speed=3.
   role_adoption_path([worker]).
   ```

2. Remover os planos (linhas 140-148):
   ```
   // explorer sobre a role-zone → segundo passo do path default→explorer→worker
   +!ensure_worker_role : role(explorer) & roleZone(0, 0) ...
   // explorer fora da role-zone → buscar com speed=3 (herdado de default via merge aditivo)
   +!ensure_worker_role : role(explorer) & my_pos(MX, MY) ...
   ```

3. Substituir o arm `roleZone(0,0)` (linha 151-154) para ler do fato, e adicionar arm defensivo imediatamente abaixo:
   ```
   // default sobre a role-zone → adotar o próximo role no path (worker hoje)
   +!ensure_worker_role : roleZone(0, 0) & role_adoption_path([Next|_])
       <- .print("[ROLE] Default sobre role-zone — adopt(", Next, ") direto.");
          .abolish(has_destination(_, _));
          .concat("adopt(", Next, ")", Act);
          action(Act).

   // fallback defensivo: role_adoption_path ausente mas agente sobre a role-zone
   // (ex: fato removido por acidente) — adota worker direto e loga o erro.
   +!ensure_worker_role : roleZone(0, 0)
       <- .print("[ROLE] WARN: role_adoption_path ausente — fallback adopt(worker).");
          .abolish(has_destination(_, _));
          action("adopt(worker)").
   ```
   O arm defensivo evita o loop navegação→retorno que ocorreria se o arm `my_pos(MX, MY)` capturasse o caso de "agente sobre role-zone sem fato definido".

4. Atualizar o comentário da cláusula `can_score_role` (linhas 42-43): remover a frase
   "`explorer` é estado intermediário no path default→explorer→worker: NÃO pontua (sem submit)
   mas está em trânsito → !ensure_worker_role continua gerenciando a transição step-a-step." — que
   deixa de ser verdade após a mudança.

**Patterns to follow:** Restante de `!ensure_worker_role` e `!seek_role_zone` — idioma Jason com `.concat` para construir a string da action.

**Test scenarios:**

- **Happy path — adoção direta**: Num replay de `IsolationRolesConfig`, o histograma mostra `adopt:N` em agentes que chegaram à role-zone, `N≥1`, sem nenhum `adopt(explorer)` no log; `can_score_role` se torna verdadeiro logo após o adopt (mesma step ou a seguinte via percept).
- **Zero explorer no replay**: Grep no replay JSON (`massim_2022/server/replays/`) por `"action":"adopt"` com parâmetro `explorer` retorna zero linhas.
- **Baseline workers não regride**: ≥ mesma contagem de agentes chegando a `worker` em IsolationRolesConfig comparado ao último replay com o config (12/15 no smoke de 59 steps em 2026-06-20; ou qualquer baseline recente disponível).
- **can_score_role continua funcionando**: após `adopt(worker)`, o agente entra em `can_score_role=true` na próxima step com percept `role(worker)` — verificável no histograma (plano (c) de allocator dispara em `can_score_role`).
- **Fallback intacto**: agentes sem role-zone conhecida continuam executando `!seek_role_zone` normalmente (histograma mostra `move` + ocasional `skip`, sem travar).

**Verification:** Sim com `IsolationRolesConfig` mostra:
- Replay grep por `adopt(explorer)` → 0 resultados.
- `≥12/15` agentes finalizam como `worker` (ou baseline equivalente ao run anterior).
- Score > 0 no `OfficialRolesConfig` (se rodado — valida que o pipeline de coleta não quebrou).

---

## Risks & Dependencies

| Risco | Mitigação |
|-------|-----------|
| `.concat` com variável atom `Next` pode gerar `"adopt(worker)"` com aspas extras se Jason tratar como string | Verificar no replay/log; se necessário usar `.term2string` ou hardcode condicional |
| Role `explorer` ainda percebido em config que o define explicitamente | `can_score_role` não muda — ele lida com o config DEV pela 3ª cláusula |
| `role_adoption_path` pode entrar em conflito com belief do percept | Verificar que o servidor MASSim não envia `role_adoption_path` no percept inicial |

---

## Sources & Research

- Issue #54 (MarceloNG/PCS5703-MAS-HIVE) — decisão e DoD.
- Brainstorm `docs/brainstorms/2026-06-18-fase-c-adocao-role-requirements.md` — KD3 (roles aditivos, adopt-once).
- `src/agt/common/role_adoption.asl` linhas 140-154 — código alvo.
