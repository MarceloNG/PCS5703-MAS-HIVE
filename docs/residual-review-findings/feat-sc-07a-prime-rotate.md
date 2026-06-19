## Residual Review Findings

Findings do ce-code-review (run 20260619-151020-eebd1096) não aplicados em step 4.
Branch: feat/sc-07a-prime-rotate | Commit base dos fixes: 350f2e5

### Residuais (downstream-resolver)

- **P2** `src/java/hive/RotationsNeeded.java:24` — **BB-reading duplicado**
  `RotationsNeeded.execute()` duplica ~32 linhas de leitura de BeliefBase de `AllReqsSatisfied.execute()` (loop getCandidateBeliefs, guard de functor, NumberTerm cast, replaceAll). Qualquer bug de parsing (ex.: task name quotado vs átomo) existe em dois lugares.
  **Fix sugerido:** Extrair `static readTaskReqs(bb, un, taskName)` e `static readAttached(bb, un)` em `AllReqsSatisfied.java`; chamar de ambos `execute()`.
  Deferred: refactor sem mudança comportamental — sem risco de regressão, mas sem urgência para a Fase C.

### Não aplicados por categoria (advisory/human)

- **P2** `src/agt/common/connect_protocol.asl:82` — Contador de `rotate(cw)` decrementado antes de confirmação do MASSim. Indetectável com `randomFail:0` (cenários) mas real com `randomFail>0` (jogo oficial). Advisory — não bloqueante para Fase C.
