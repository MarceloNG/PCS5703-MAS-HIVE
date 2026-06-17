/* organization.asl (U2 — Track 3, Fase A)
   Participação MOISE+ mínima-mas-real.

   - Os agentes adotam o role organizacional (squad_leader/collector/assembler/sentinel)
     automaticamente, via a lista 'players' do bloco 'organisation' no hive.jcm (U1).
   - Aqui eles reagem às OBRIGAÇÕES emitidas pelo scheme ativo (task_execution_scheme):
     comprometem-se com a mission obrigada pela norma (n_collect, n_assemble, n_submit).

   KTD1: a organização NÃO dirige o fluxo de controle. O líder + leilão (TaskBoard)
   seguem no comando do comportamento real. Sinalizar a CONCLUSÃO dos goals da mission
   (blocks_collected, blocks_assembled, pattern_submitted) a partir do comportamento que
   já existe é um refinamento documentado, a aterrissar junto com o harness de medição
   (Track 1) para validar que não regride o que funciona.

   Idioma ancorado no 'org-obedient.asl' padrão do JaCaMo. */

// --- Obrigação de se COMPROMETER com uma mission -> compromete-se (participação real) ---
+obligation(Ag, Norm, committed(Ag, Mission, Scheme), Deadline)[artifact_id(ArtId), workspace(_, W)]
    : .my_name(Ag)
   <- .print("[ORG] Obrigado a comprometer ", Mission, " em ", Scheme, " — comprometendo.");
      commitMission(Mission)[artifact_name(Scheme), wid(W)].

// --- Obrigação de ATINGIR um goal: reconhecida, mas não dirigida pela org (KTD1) ---
// O comportamento real (coleta/montagem/submit) é quem progride; emitir goalAchieved
// a partir dele é refinamento futuro.
+obligation(Ag, Norm, What, Deadline)[artifact_id(ArtId), norm(_, Un)]
    : .my_name(Ag) & (satisfied(_, _) = What | done(_, _, _) = What)
   <- true.

// --- Qualquer outra obrigação não modelada (apenas registra) ---
+obligation(Ag, Norm, What, Deadline)
    : .my_name(Ag)
   <- .print("[ORG] Obrigação não tratada: ", What).
