// ============================================================
// connect_protocol.asl — Protocolo de connect sincronizado + submit
// Incluir ANTES de collection.asl para prioridade maxima de +step(N)
// ============================================================

// --- DESATIVADO: nao fazer nada ---

+step(N)
    : am_deactivated
    <- action("skip").

// --- ENERGIA BAIXA: priorizar sobrevivencia ---

+step(N)
    : my_energy(E) & E < 5 & not am_deactivated & my_pos(MX, MY)
    <- .print("[ENERGY] Step ", N, ": Energia critica (", E, ")! Skip para conservar.");
       action("skip").

// --- CLEAR BLOCKS: detach stale blocks before new collection (only adjacent) ---

+step(N) : needs_clear_blocks(Type) & attached(0, -1) <- action("detach(n)").
+step(N) : needs_clear_blocks(Type) & attached(0, 1) <- action("detach(s)").
+step(N) : needs_clear_blocks(Type) & attached(1, 0) <- action("detach(e)").
+step(N) : needs_clear_blocks(Type) & attached(-1, 0) <- action("detach(w)").

+step(N)
    : needs_clear_blocks(Type) & attached(_, _) & not trying_rotate(_, _, _)
    <- action("rotate(cw)").

+step(N)
    : needs_clear_blocks(Type)
    <- -needs_clear_blocks(Type);
       !collect_block(Type).

// --- NORM VIOLATION: detach excess blocks to avoid penalty ---

+step(N)
    : carry_limit(Limit) & .count(attached(_, _), NumAtt) & NumAtt > Limit
      & not pending_submit(_) & not submitted_task(_) & not collecting(_, _, _)
      & not collected_block(_) & not trying_rotate(_, _, _)
      & not norm_detach_blocked
      & attached(AX, AY) & (math.abs(AX) + math.abs(AY) == 1)
    <- if (AY == -1) { DDir = n }
       elif (AY == 1) { DDir = s }
       elif (AX == 1) { DDir = e }
       else { DDir = w };
       .print("[NORM] Step ", N, ": Detach excess block dir=", DDir, " (limit=", Limit, " att=", NumAtt, ")");
       .concat("detach(", DDir, ")", Act); action(Act).

// --- PRE-SUBMIT: detach extra blocks if >NBlocks attached (guard por NBlocks, não >1) ---
// Corrigido de NumAtt>1 para NumAtt>NBlocks: task de N blocos legítimos não deve
// descartar blocos em posições distantes (ex.: bloco em (2,0) de task 2-blocos).

+step(N)
    : pending_submit(TaskName) & not submitted_task(_)
      & solo_mode(TaskName) & known_task(TaskName, _, _, NBlocks)
      & .count(attached(_, _), NumAtt) & NumAtt > NBlocks
      & attached(0, -1) & attached(0, 1)
    <- .print("[SUBMIT] Step ", N, ": Detaching extra adj block (n)");
       action("detach(n)").

+step(N)
    : pending_submit(TaskName) & not submitted_task(_)
      & solo_mode(TaskName) & known_task(TaskName, _, _, NBlocks)
      & .count(attached(_, _), NumAtt) & NumAtt > NBlocks
      & attached(1, 0) & attached(-1, 0)
    <- .print("[SUBMIT] Step ", N, ": Detaching extra adj block (w)");
       action("detach(w)").

+step(N)
    : pending_submit(TaskName) & not submitted_task(_)
      & solo_mode(TaskName) & known_task(TaskName, _, _, NBlocks)
      & .count(attached(_, _), NumAtt) & NumAtt > NBlocks
      & attached(AX, AY) & (math.abs(AX) + math.abs(AY) > 1)
    <- action("rotate(cw)").

// --- ROTAÇÃO PRÉ-SUBMIT: continuar girando (Eixo 7a' / issue #18) ---------------
// Loop de rotação CW: decrementa trying_rotate e executa rotate(cw) a cada step.

+step(N)
    : trying_rotate(TaskName, RC, Dir) & RC > 0
      & known_task(TaskName, Deadline, _, _) & Deadline > N
      & not my_active_task(_, _) & not pending_submit(_) & not submitted_task(_)
    <- NewRC = RC - 1;
       .abolish(trying_rotate(TaskName, _, _));
       +trying_rotate(TaskName, NewRC, Dir);
       .print("[ROTATE] Step ", N, ": Rotacionando ", Dir, " p/ alinhar ", TaskName, " (restam ", NewRC, ").");
       .concat("rotate(", Dir, ")", Act); action(Act).

// --- ROTAÇÃO PRÉ-SUBMIT: finalizar — verificar alinhamento e submeter (Eixo 7a') ---
// AllReqsSatisfied no CONTEXTO: se falhar (rotate falhou/lag), plano não é selecionado;
// o RESCUE abaixo faz skip enquanto aguarda o percept correto.

+step(N)
    : trying_rotate(TaskName, 0, _)
      & can_score_role
      & not my_active_task(_, _) & not pending_submit(_) & not submitted_task(_)
      & known_task(TaskName, Deadline, _, NBlocks) & Deadline > N
      & hive.AllReqsSatisfied(TaskName)
      & my_pos(MX, MY)
    <- .abolish(trying_rotate(TaskName, _, _));
       .my_name(Me);
       .print("[ROTATE] Step ", N, ": Alinhado! Multi-req ", NBlocks, " blocos p/ ", TaskName, " → submit.");
       mark_busy(Me);
       +my_active_task(TaskName, "solo");
       +solo_mode(TaskName);
       +task_accepted_step(TaskName, N);
       +my_task_deadline(TaskName, Deadline);
       +pending_submit(TaskName);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY)
       };
       action("skip").

// --- ROTAÇÃO: rescue — RC=0 mas AllReqsSatisfied falhou (rotate falhou ou lag) ----
// Skip e retenta no próximo step; se deadline expirar, CLEANUP ativa.
+step(N)
    : trying_rotate(TaskName, 0, _)
      & known_task(TaskName, Deadline, _, _) & Deadline > N
      & not hive.AllReqsSatisfied(TaskName)
    <- action("skip").

// --- ROTAÇÃO: cleanup — trying_rotate órfão (task expirada) -----------------------
+step(N)
    : trying_rotate(TaskName, _, _) & known_task(TaskName, Deadline, _, _) & N >= Deadline
    <- .abolish(trying_rotate(TaskName, _, _));
       .print("[ROTATE] Step ", N, ": Cleanup trying_rotate (task expirada dl=", Deadline, "): ", TaskName, ".");
       action("skip").

// --- ROTAÇÃO: cleanup — trying_rotate órfão (task desconhecida) -------------------
+step(N)
    : trying_rotate(TaskName, _, _) & not known_task(TaskName, _, _, _)
    <- .abolish(trying_rotate(TaskName, _, _));
       .print("[ROTATE] Step ", N, ": Cleanup trying_rotate (task desconhecida): ", TaskName, ".");
       action("skip").

// --- PRÉ-ALINHAMENTO NO DISPENSER (U3 — resolve OQ-2, #50/#52) --------------------
// Rotaciona o bloco recém-coletado p/ alinhar ao treq AINDA NO DISPENSER (descongestionado,
// `not goalZone`), ANTES de navegar à zona. Distinto do trying_rotate (Eixo 7a', blocks-in-
// hand, gated `not my_active_task`): aqui a task JÁ é ativa (solo_mode + collected_block), então
// a completion RC=0 de l95-114 NÃO serve (review S1+F4). Reusa RotationsNeeded (recomputa por
// step, como a pré-rotação in-zone l283-295); guard próprio prealign_fails (limiar 3 = RotationGuard).

// Alinhado OU já na goal zone → navegar p/ submit (a pré-rotação in-zone cobre o resto). Limpa guard.
+step(N)
    : collected_block(_) & solo_mode(TaskName) & not pending_submit(_) & not submitted_task(_)
      & (hive.AllReqsSatisfied(TaskName) | goalZone(0, 0))
    <- .abolish(prealign_fails(TaskName, _));
       .print("[PREALIGN] Step ", N, ": Pronto p/ submit ", TaskName, " (alinhado/zona) → nav goal zone.");
       !start_submit_nav(TaskName);
       action("skip").

// Guard esgotado (3 falhas de rotate no dispenser) → abortar a task p/ este bloco.
+step(N)
    : collected_block(_) & solo_mode(TaskName) & not pending_submit(_) & not submitted_task(_)
      & not goalZone(0, 0)
      & prealign_fails(TaskName, F) & F >= 3
    <- .print("[PREALIGN] Step ", N, ": Abort pre-alinhamento apos ", F, " falhas em ", TaskName, ".");
       .abolish(prealign_fails(TaskName, _));
       action("skip");
       !finalize_task(TaskName).

// Falha de rotate no pré-alinhamento → incrementar guard (limiar 3, RotationGuard #47).
+step(N)
    : collected_block(_) & solo_mode(TaskName) & not pending_submit(_) & not submitted_task(_)
      & not goalZone(0, 0)
      & lastAction(rotate) & lastActionResult(failed)
    <- if (prealign_fails(TaskName, F)) {
           .abolish(prealign_fails(TaskName, _));
           NF = F + 1;
           +prealign_fails(TaskName, NF)
       } else {
           +prealign_fails(TaskName, 1)
       };
       action("skip").

// Desalinhado, rotacionável e guard ok → rotaciona 1 passo NO DISPENSER.
+step(N)
    : collected_block(_) & solo_mode(TaskName) & not pending_submit(_) & not submitted_task(_)
      & not goalZone(0, 0)
      & hive.RotationsNeeded(TaskName, R, Dir)
      & (not prealign_fails(TaskName, _) | (prealign_fails(TaskName, F) & F < 3))
    <- .print("[PREALIGN] Step ", N, ": Pre-rotacao no dispenser ", Dir, " (", R, " restantes) p/ ", TaskName, ".");
       .concat("rotate(", Dir, ")", Act); action(Act).

// Desalinhado e INCOMPATÍVEL (nenhuma rotação alinha) → abortar a task p/ este bloco.
// known_task gatea o disparo: sem task_req ainda percebido, AllReqsSatisfied e
// RotationsNeeded falham por ausência de dados, não por incompatibilidade real.
+step(N)
    : collected_block(_) & solo_mode(TaskName) & not pending_submit(_) & not submitted_task(_)
      & not goalZone(0, 0)
      & known_task(TaskName, _, _, _)
      & not hive.AllReqsSatisfied(TaskName) & not hive.RotationsNeeded(TaskName, _, _)
    <- .print("[PREALIGN] Step ", N, ": Bloco incompativel c/ ", TaskName, " (nenhuma rotacao alinha) → abortando.");
       action("skip");
       !finalize_task(TaskName).

// --- BLOCOS-NA-MÃO → SUBMIT multi-bloco (gate #18 / Eixo 7a) --------------------
// Se já tenho TODOS os N blocos da task pré-anexados nas posições exigidas, ir direto
// pro submit sem coletar. Regra separada da de 1 bloco (abaixo) para zero risco de
// regressão. solo_block_type omitido intencionalmente: re-coleta multi-bloco é 07b.
// AllReqsSatisfied no CONTEXTO (não no body): quando falha, o plano não é selecionado
// e o Jason passa para ROTATION INITIATE (evita abandono de intenção por falha no body).
+step(N)
    : can_score_role
      & not my_active_task(_, _) & not pending_submit(_) & not submitted_task(_)
      & not needs_clear_blocks(_) & not collecting(_, _, _)
      & known_task(TaskName, Deadline, _, NBlocks) & NBlocks > 1 & Deadline > N
      & .count(attached(_, _), NumAtt) & NumAtt >= NBlocks
      & hive.AllReqsSatisfied(TaskName)
      & my_pos(MX, MY)
    <- .my_name(Me);
       .print("[SUBMIT] Step ", N, ": Multi-req ", NBlocks, " blocos na mão p/ ", TaskName, " → submit direto.");
       mark_busy(Me);
       +my_active_task(TaskName, "solo");
       +solo_mode(TaskName);
       +task_accepted_step(TaskName, N);
       +my_task_deadline(TaskName, Deadline);
       +pending_submit(TaskName);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY)
       };
       action("skip").

// --- ROTAÇÃO PRÉ-SUBMIT: iniciar rotação quando blocos desalinhados (Eixo 7a') ----
// Selecionado quando AllReqsSatisfied falhou no contexto do BLOCOS-NA-MÃO (blocos
// existem mas em posição girada). RotationsNeeded determina quantas CW alinham a
// forma; se retornar false (forma incompatível), o `if` é pulado e apenas skip é
// executado — o agente não submete (comportamento correto para formas inválidas).

+step(N)
    : can_score_role
      & not my_active_task(_, _) & not pending_submit(_) & not submitted_task(_)
      & not needs_clear_blocks(_) & not collecting(_, _, _)
      & not trying_rotate(_, _, _)
      & known_task(TaskName, Deadline, _, NBlocks) & NBlocks > 1 & Deadline > N
      & .count(attached(_, _), NumAtt) & NumAtt >= NBlocks
      & not hive.AllReqsSatisfied(TaskName)
    <- if (hive.RotationsNeeded(TaskName, R, Dir)) {
           +trying_rotate(TaskName, R, Dir);
           .print("[ROTATE] Step ", N, ": Blocos desalinhados p/ ", TaskName, " — ", R, " rotação(ões) ", Dir, " necessária(s).")
       };
       action("skip").

// --- BLOCO-NA-MÃO → SUBMIT (gate de score, issue #14) --------------
// Se já tenho o bloco que a task exige ANEXADO na posição exigida (pré-anexado pela
// fixture, herdado, ou sobra de coleta), NÃO recoletar nem descartar: ir direto pro
// submit. Realiza o steer do dono — "se tem os blocos da task na mão, tem que submeter".
// PRIORIDADE acima do SELF-ASSIGN abaixo (que descartaria o bloco via needs_clear_blocks).
+step(N)
    : can_score_role
      & not my_active_task(_, _) & not pending_submit(_) & not submitted_task(_)
      & not needs_clear_blocks(_) & not collecting(_, _, _)
      & attached(DX, DY)
      & known_task(TaskName, Deadline, _, 1) & Deadline > N
      & task_req(TaskName, DX, DY, BlockType)
      & my_pos(MX, MY)
    <- .my_name(Me);
       .print("[SUBMIT] Step ", N, ": Bloco ", BlockType, " já na mão (", DX, ",", DY, ") p/ task ", TaskName, " → submit direto (sem coletar).");
       mark_busy(Me);
       +my_active_task(TaskName, "solo");
       +solo_mode(TaskName);
       +solo_block_type(BlockType);
       +task_accepted_step(TaskName, N);
       +my_task_deadline(TaskName, Deadline);
       +pending_submit(TaskName);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY)
       };
       action("skip").

// --- SELF-ASSIGN: idle agents pick up tasks autonomously ---

+step(N)
    : can_score_role
      & (N mod 7) == 4
      & N > 30
      & not my_active_task(_, _) & not collecting(_, _, _)
      & not pending_submit(_) & not submitted_task(_)
      & not needs_clear_blocks(_) & not searching_dispenser(_)
      & not navigating_to_meeting_point(_) & not navigating_to_meeting_for_connect(_, _, _)
      & not waiting_connect_collector(_) & not waiting_connect_result(_, _)
      & not pending_connect(_, _, _, _) & not ready_to_connect(_, _, _, _)
      & my_pos(MX, MY) & step(CS)
      & known_task(TN, TD, _, 1) & TD - CS > 40
      & task_req(TN, _, _, BType)
    <- .my_name(Me);
       mark_busy(Me);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(collected_block(_));
       .abolish(solo_mode(_));
       .abolish(solo_block_type(_));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       .abolish(my_task_deadline(_, _));
       .abolish(searching_dispenser(_));
       +my_task_deadline(TN, TD);
       +my_active_task(TN, "solo");
       +solo_mode(TN);
       +solo_block_type(BType);
       +task_accepted_step(TN, CS);
       .print("[SELF] Step ", N, ": Auto-assigned ", TN, " type=", BType, " dl=", TD);
       if (attached(_, _)) {
           +needs_clear_blocks(BType);
           action("skip")
       } else {
           !collect_block(BType)
       }.

// --- SUBMIT: guard de pré-rotação — abortar após 3 falhas acumuladas (#47) --------
// Contador: rotate_pre_submit_fails(TaskName, F) — cumulativo nesta tentativa de submit.
// Sincronizar o literal 3 com RotationGuard.MAX_CONSECUTIVE_FAILS (src/java/hive/RotationGuard.java).
// Prioridade: P0 (abort F>=3) > P1 (incrementa em falha) > P2 (rotaciona se F<3).
// P0: abortar — skip ANTES de finalize para garantir envio ao servidor mesmo se finalize falhar
+step(N)
    : pending_submit(TaskName) & goalZone(0, 0) & not submitted_task(_)
      & rotate_pre_submit_fails(TaskName, F) & F >= 3
    <- .print("[SUBMIT] Step ", N, ": Abort pre-rotacao apos ", F, " falhas acumuladas em ", TaskName, ". Liberando task.");
       .abolish(rotate_pre_submit_fails(TaskName, _));
       action("skip");
       !finalize_task(TaskName).

// P1: detectar falha de rotate e incrementar contador (limite=3, ver RotationGuard.java)
+step(N)
    : pending_submit(TaskName) & goalZone(0, 0) & not submitted_task(_)
      & lastAction(rotate) & lastActionResult(failed)
    <- if (rotate_pre_submit_fails(TaskName, F)) {
           .abolish(rotate_pre_submit_fails(TaskName, _));
           NF = F + 1;
           +rotate_pre_submit_fails(TaskName, NF)
       } else {
           +rotate_pre_submit_fails(TaskName, 1)
       };
       action("skip").

// --- SUBMIT: pré-rotação ótima antes do submit (Eixo 7a' — estendido para single-block) ---
// Se o bloco não está alinhado com os requisitos da task, rotaciona com direção ótima
// ANTES de submeter — sem tentativa-e-erro. RotationsNeeded é chamado por step (lê os
// percepts `attached` atuais) e recalcula quantas rotações ainda faltam após cada giro.
// Cai no plano de submit abaixo quando alinhado (RotationsNeeded retorna false).
// Mesma mecânica do Eixo 7a' (#18) para multi-bloco; aqui estendido para single-block solo.
// P2: guard ativo — dois braços explícitos para evitar variável F não-vinculada após NAF
+step(N)
    : pending_submit(TaskName) & goalZone(0, 0) & not submitted_task(_)
      & hive.RotationsNeeded(TaskName, R, Dir)
      & (not rotate_pre_submit_fails(TaskName, _) | (rotate_pre_submit_fails(TaskName, F) & F < 3))
    <- .print("[SUBMIT] Step ", N, ": Pre-rotacao pre-submit: ", Dir, " (", R, " restantes) p/ ", TaskName, " (Eixo 7a').");
       .concat("rotate(", Dir, ")", Act); action(Act).

// --- SUBMIT: pending_submit e na goal zone ---

+step(N)
    : pending_submit(TaskName) & goalZone(0, 0) & not submitted_task(_)
    <- -pending_submit(TaskName);
       +submitted_task(TaskName);
       .findall(att(AX,AY), attached(AX,AY), AttList);
       .findall(treq(RX,RY,RT), task_req(TaskName, RX, RY, RT), ReqList);
       .print("[SUBMIT] Step ", N, ": submit(", TaskName, ") attached=", AttList, " reqs=", ReqList);
       .concat("submit(", TaskName, ")", Act);
       action(Act);
       .concat("{\"task\":\"", TaskName, "\"}", SJson);
       !dash_log("submit_attempt", SJson);
       !dash_task_phase(TaskName, "submit", 50).

// --- SUBMIT RESULT: sucesso → re-submit ou finalizar ---

+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(success) & attached(_, _)
    <- .print("[SUBMIT] Step ", N, ": Submit de ", TaskName, " SUCESSO! Bloco ainda attached, re-submetendo...");
       .concat("{\"task\":\"", TaskName, "\",\"result\":\"success\"}", SSJson);
       !dash_log("submit_success", SSJson);
       -submitted_task(TaskName);
       +pending_submit(TaskName);
       action("skip").

+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(success)
      & solo_block_type(BType) & my_task_deadline(TaskName, Deadline) & N + 40 < Deadline
    <- .print("[SUBMIT] Step ", N, ": Submit SUCESSO! Re-coletando ", BType, " (deadline=", Deadline, ")");
       .concat("{\"task\":\"", TaskName, "\",\"result\":\"success\"}", SSJson);
       !dash_log("submit_success", SSJson);
       -submitted_task(TaskName);
       .abolish(collected_block(_));
       .abolish(pending_submit(_));
       .abolish(has_destination(_, _));
       .abolish(nav_block_count(_));
       .abolish(collecting(_, _, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       +task_accepted_step(TaskName, N);
       !collect_block(BType);
       action("skip").

+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(success)
    <- if (solo_block_type(SBT)) { SBTInfo = SBT } else { SBTInfo = "NONE" };
       if (known_task(TaskName, KDL, _, _)) { DLInfo = KDL } else { DLInfo = -1 };
       .print("[SUBMIT] Step ", N, ": Submit de ", TaskName, " SUCESSO! Bloco consumido, finalizando. (solo_block_type=", SBTInfo, " deadline=", DLInfo, ")");
       .concat("{\"task\":\"", TaskName, "\",\"result\":\"success\"}", SSJson);
       !dash_log("submit_success", SSJson);
       -submitted_task(TaskName);
       !finalize_task(TaskName);
       action("skip").

// --- SUBMIT RESULT: falha → DECISÃO OBJETIVA (R3/R4, #52) ---
// Substitui o loop cego rotate(cw)×4 + reposição×3. A causa da falha é diagnosticada
// por hive.AllReqsSatisfied / hive.RotationsNeeded — sem tentativa-e-erro.

// R3: falha COM blocos alinhados → a causa NAO e rotacao (zona errada / task expirada
// / ja submetida) → finalizar, nao rotacionar.
+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(failed)
      & hive.AllReqsSatisfied(TaskName)
    <- .print("[SUBMIT] Step ", N, ": Submit FALHOU com blocos alinhados (AllReqsSatisfied) — causa nao e rotacao. Finalizando ", TaskName, ".");
       .concat("{\"task\":\"", TaskName, "\",\"result\":\"failed\"}", SFJson);
       !dash_log("submit_fail", SFJson);
       -submitted_task(TaskName);
       !finalize_task(TaskName);
       action("skip").

// R4: falha DESALINHADO mas rotacionavel → voltar ao fallback bounded de pre-rotacao
// (l283-295, dirigido por RotationsNeeded e limitado por rotate_pre_submit_fails / #47).
+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(failed)
      & not hive.AllReqsSatisfied(TaskName) & hive.RotationsNeeded(TaskName, _, _)
    <- .print("[SUBMIT] Step ", N, ": Submit FALHOU desalinhado — re-tentando pre-rotacao bounded (R4).");
       -submitted_task(TaskName);
       .abolish(rotate_pre_submit_fails(TaskName, _));
       +pending_submit(TaskName);
       action("skip").

// R4: falha DESALINHADO e INCOMPATIVEL (nenhuma rotacao alinha) → finalizar.
+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(failed)
    <- .print("[SUBMIT] Step ", N, ": Submit FALHOU com forma incompativel — finalizando ", TaskName, ".");
       .concat("{\"task\":\"", TaskName, "\",\"result\":\"failed\"}", SFJson);
       !dash_log("submit_fail", SFJson);
       -submitted_task(TaskName);
       !finalize_task(TaskName);
       action("skip").

// --- SUBMIT RESULT: qualquer outro falha (target, status, etc) ---

+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(R) & R \== success
    <- .print("[SUBMIT] Step ", N, ": Submit falhou com ", R, ". Task ", TaskName, " provavelmente expirou.");
       -submitted_task(TaskName);
       !finalize_task(TaskName);
       action("skip").

// --- pending_submit: timeout — desistir apos muitos steps ---

+step(N)
    : pending_submit(TaskName) & task_accepted_step(TaskName, AccStep) & (N - AccStep > 250)
    <- .print("[SUBMIT] Timeout: task ", TaskName, " apos ", N - AccStep, " steps. Desistindo.");
       .abolish(nav_block_count(_));
       !finalize_task(TaskName);
       action("skip").

// --- pending_submit: task expirou (via my_task_deadline ou known_task) ---

+step(N)
    : pending_submit(TaskName) & my_task_deadline(TaskName, Deadline) & N >= Deadline
    <- .print("[SUBMIT] Task ", TaskName, " expirou (deadline=", Deadline, "). Finalizando.");
       .abolish(nav_block_count(_));
       !finalize_task(TaskName);
       action("skip").

+step(N)
    : pending_submit(TaskName) & known_task(TaskName, Deadline, _, _) & N >= Deadline
    <- .print("[SUBMIT] Task ", TaskName, " expirou (deadline=", Deadline, "). Finalizando.");
       .abolish(nav_block_count(_));
       !finalize_task(TaskName);
       action("skip").

// --- pending_submit: VISIBLE goal zone nearby → navigate directly ---

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & not goalZone(0, 0) & not submitted_task(_)
      & goalZone(VGX, VGY) & (VGX \== 0 | VGY \== 0)
      & not last_move_blocked
    <- .abolish(has_destination(_, _));
       +has_destination(MX + VGX, MY + VGY);
       if (math.abs(VGX) >= math.abs(VGY)) {
           if (VGX > 0) { Dir = e } else { Dir = w }
       } else {
           if (VGY > 0) { Dir = s } else { Dir = n }
       };
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act); action(Act).

// --- pending_submit: blocked → rotate, alt direction, or switch goal zone ---

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & not goalZone(0, 0) & not submitted_task(_)
      & (last_move_blocked | escape_pending(_, _)) & attached(_, _)
    <- .abolish(last_move_blocked);
       .abolish(escape_pending(_, _));
       if (nav_block_count(OldC)) { -nav_block_count(OldC); BC = OldC + 1 }
       else { BC = 1 };
       +nav_block_count(BC);
       Mod3 = BC mod 3;
       if (BC >= 6) {
           .abolish(nav_block_count(_));
           +nav_block_count(0);
           .abolish(has_destination(_, _));
           get_alternative_goal_zone(MX, MY, MX, MY, AGX, AGY);
           if (AGX \== -1) {
               +has_destination(AGX, AGY);
               .print("[SUBMIT] Switch goal zone to (", AGX, ",", AGY, ") after ", BC, " blocks")
           };
           action("rotate(cw)")
       } elif (Mod3 == 0 & BC > 0) {
           action("rotate(cw)")
       } else {
           if (has_destination(DGX, DGY)) {
               !escape_move(MX, MY, DGX, DGY)
           } else {
               action("skip")
           }
       }.

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & not goalZone(0, 0) & not submitted_task(_) & last_move_blocked
    <- -last_move_blocked;
       .random(R);
       if (R < 0.25) { Dir = n }
       elif (R < 0.5) { Dir = e }
       elif (R < 0.75) { Dir = s }
       else { Dir = w };
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act); action(Act).

// --- pending_submit: navigate to nearest goal zone (recalc every 15 steps) ---

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & not goalZone(0, 0) & not submitted_task(_)
      & has_destination(DX, DY)
    <- if ((N mod 15) == 0) {
           get_nearest_goal_zone(MX, MY, NGX, NGY);
           if (NGX \== -1) {
               .abolish(has_destination(_, _));
               +has_destination(NGX, NGY);
               compute_next_move(MX, MY, NGX, NGY, Dir);
               .abolish(last_attempted_dir(_));
               +last_attempted_dir(Dir);
               .concat("move(", Dir, ")", Act); action(Act)
           } else {
               compute_next_move(MX, MY, DX, DY, Dir);
               .abolish(last_attempted_dir(_));
               +last_attempted_dir(Dir);
               .concat("move(", Dir, ")", Act); action(Act)
           }
       } else {
           compute_next_move(MX, MY, DX, DY, Dir);
           if (Dir == "skip") {
               get_nearest_goal_zone(MX, MY, GX, GY);
               if (GX \== -1) {
                   .abolish(has_destination(_, _));
                   +has_destination(GX, GY);
                   compute_next_move(MX, MY, GX, GY, Dir2);
                   .abolish(last_attempted_dir(_));
                   +last_attempted_dir(Dir2);
                   .concat("move(", Dir2, ")", Act); action(Act)
               } else { action("skip") }
           } else {
               .abolish(last_attempted_dir(_));
               +last_attempted_dir(Dir);
               .concat("move(", Dir, ")", Act); action(Act)
           }
       }.

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & not goalZone(0, 0) & not submitted_task(_)
    <- get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY);
           compute_next_move(MX, MY, GX, GY, Dir);
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(Dir);
           .concat("move(", Dir, ")", Act); action(Act)
       } else {
           // sem goal zone no mapa: explorar com o bloco até achar uma visualmente
           if ((N mod 20) == 0) { .print("[SUBMIT] Step ", N, ": sem goal zone no mapa, explorando") };
           !do_explore(MX, MY)
       }.

// --- CONNECT RESULT: assembler (sucesso) → ir a goal zone ---

+step(N)
    : waiting_connect_result(Partner, TaskName) & lastActionResult(success) & my_pos(MX, MY)
    <- -waiting_connect_result(Partner, TaskName);
       .print("[CONNECT] Step ", N, ": Connect com ", Partner, " sucesso!");
       .concat("{\"task\":\"", TaskName, "\",\"partner\":\"", Partner, "\",\"result\":\"success\"}", CSJson);
       !dash_log("connect_success", CSJson);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY);
           +pending_submit(TaskName);
           .print("[CONNECT] Indo para goal zone (", GX, ",", GY, ") para submit ", TaskName)
       };
       action("skip").

// --- CONNECT RESULT: assembler (falha) → retentar ---

+step(N)
    : waiting_connect_result(Partner, TaskName) & lastActionResult(R) & my_pos(MX, MY)
    <- -waiting_connect_result(Partner, TaskName);
       .print("[CONNECT] Step ", N, ": Connect falhou: ", R, ". Retentando.");
       .concat("{\"task\":\"", TaskName, "\",\"partner\":\"", Partner, "\",\"result\":\"fail\"}", CFJson);
       !dash_log("connect_fail", CFJson);
       +ready_to_connect(Partner, MX, MY, TaskName).

// --- CONNECT RESULT: collector (sucesso) ---

+step(N)
    : waiting_connect_collector(AsmName) & lastActionResult(success) & my_pos(MX, MY)
    <- -waiting_connect_collector(AsmName);
       .abolish(pending_connect_backup(_, _, _, _));
       .print("[CONNECT] Step ", N, ": Bloco transferido ao assembler com sucesso!");
       !do_explore(MX, MY).

// --- CONNECT RESULT: collector (falha) → retentar ---

+step(N)
    : waiting_connect_collector(AsmName) & lastActionResult(R)
      & pending_connect_backup(AsmName, AX, AY, TS)
    <- -waiting_connect_collector(AsmName);
       -pending_connect_backup(AsmName, AX, AY, TS);
       .print("[CONNECT] Step ", N, ": Connect falhou: ", R, ". Retentando.");
       +pending_connect(AsmName, AX, AY, TS).

// --- ASSEMBLER: chegou ao meeting point, iniciar connect ---

+step(N)
    : navigating_to_meeting_for_connect(SquadId, _, TaskName)
      & my_pos(MX, MY) & not has_destination(_, _)
    <- -navigating_to_meeting_for_connect(SquadId, _, TaskName);
       .print("[ASSEMBLER] Step ", N, ": No meeting point para task ", TaskName);
       get_squad_collectors(SquadId, Col1, Col2);
       !dash_task_phase(TaskName, "connect", 0);
       TargetStep = N + 5;
       if (Col1 \== "none") {
           !request_connect(Col1, TargetStep);
           +ready_to_connect(Col1, MX, MY, TaskName);
           .print("[ASSEMBLER] Solicitando connect com ", Col1)
       };
       action("skip").

// --- TRY CONNECT: assembler — detectar entidade adjacente via thing ---

+step(N)
    : ready_to_connect(Partner, PX, PY, TaskName) & my_pos(MX, MY)
      & attached(AX, AY)
      & thing(TX, TY, entity, _)
      & ((TX == 1 & TY == 0) | (TX == -1 & TY == 0) | (TX == 0 & TY == 1) | (TX == 0 & TY == -1))
    <- .concat("connect(", Partner, ",", AX, ",", AY, ")", Act);
       action(Act);
       -ready_to_connect(Partner, _, _, _);
       +waiting_connect_result(Partner, TaskName);
       .print("[CONNECT] Step ", N, ": Assembler connect(", Partner, ",", AX, ",", AY, ") partner at (", TX, ",", TY, ")").

// --- TRY CONNECT: assembler — sem entidade adjacente, esperar ---

+step(N)
    : ready_to_connect(_, _, _, _) & my_pos(MX, MY)
    <- action("skip").

// --- TRY CONNECT: collector — navegar ou connect ---

+step(N)
    : pending_connect(AsmName, AsmX, AsmY, TS) & my_pos(MX, MY) & attached(AX, AY)
      & thing(TX, TY, entity, _)
      & ((TX == 1 & TY == 0) | (TX == -1 & TY == 0) | (TX == 0 & TY == 1) | (TX == 0 & TY == -1))
    <- .concat("connect(", AsmName, ",", AX, ",", AY, ")", Act);
       action(Act);
       -pending_connect(AsmName, _, _, _);
       +pending_connect_backup(AsmName, AsmX, AsmY, TS);
       +waiting_connect_collector(AsmName);
       .print("[CONNECT] Step ", N, ": Collector connect(", AsmName, ",", AX, ",", AY, ")").

// FIXME Fase D (#2, cross-frame): AsmX,AsmY vem do connect_request no frame do
// ASSEMBLER; MX,MY e o frame do collector (origens distintas pre-fusao). CDX/CDY
// abaixo mistura frames -> navegacao ao ponto de connect fica incorreta no oficial.
// Mesmo problema dos sites ja marcados (communication.asl, squad_leader.asl). A U9
// (frame compartilhado) resolve; ate la, vale connect so por adjacencia percebida.
+step(N)
    : pending_connect(AsmName, AsmX, AsmY, TS) & my_pos(MX, MY)
    <- CDX = AsmX - MX; CDY = AsmY - MY;
       if (CDX > 0 & (CDX >= CDY | CDX >= -CDY)) { MoveDir = e }
       elif (CDX < 0 & (-CDX >= CDY | -CDX >= -CDY)) { MoveDir = w }
       elif (CDY > 0) { MoveDir = s }
       else { MoveDir = n };
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(MoveDir);
       .concat("move(", MoveDir, ")", Act);
       action(Act).
