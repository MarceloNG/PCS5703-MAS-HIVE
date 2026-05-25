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

// --- SUBMIT: pending_submit e na goal zone ---

+step(N)
    : pending_submit(TaskName) & goalZone(0, 0) & not submitted_task(_)
    <- -pending_submit(TaskName);
       +submitted_task(TaskName);
       if (not submit_rotate_count(TaskName, _)) {
           +submit_rotate_count(TaskName, 0)
       };
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
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(success)
    <- .print("[SUBMIT] Step ", N, ": Submit de ", TaskName, " SUCESSO! Re-submetendo...");
       .concat("{\"task\":\"", TaskName, "\",\"result\":\"success\"}", SSJson);
       !dash_log("submit_success", SSJson);
       -submitted_task(TaskName);
       .abolish(submit_rotate_count(TaskName, _));
       +pending_submit(TaskName);
       action("skip").

// --- SUBMIT RESULT: falha → rotacionar e re-tentar (até 3x) ---

+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(failed)
      & submit_rotate_count(TaskName, RC) & RC < 4
    <- NewRC = RC + 1;
       .print("[SUBMIT] Step ", N, ": Submit FALHOU (rotacao ", NewRC, "/4). Rotacionando cw.");
       -submitted_task(TaskName);
       .abolish(submit_rotate_count(TaskName, _));
       +submit_rotate_count(TaskName, NewRC);
       +pending_submit(TaskName);
       action("rotate(cw)").

+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(failed)
    <- .print("[SUBMIT] Step ", N, ": Submit FALHOU apos 4 rotacoes. Desistindo.");
       .concat("{\"task\":\"", TaskName, "\",\"result\":\"failed\"}", SFJson);
       !dash_log("submit_fail", SFJson);
       -submitted_task(TaskName);
       .abolish(submit_rotate_count(TaskName, _));
       !finalize_task(TaskName);
       action("skip").

// --- SUBMIT RESULT: qualquer outro falha (target, status, etc) ---

+step(N)
    : submitted_task(TaskName) & lastAction(submit) & lastActionResult(R) & R \== success
    <- .print("[SUBMIT] Step ", N, ": Submit falhou com ", R, ". Task ", TaskName, " provavelmente expirou.");
       -submitted_task(TaskName);
       .abolish(submit_rotate_count(TaskName, _));
       !finalize_task(TaskName);
       action("skip").

// --- pending_submit: blocked → random direction ---

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & not goalZone(0, 0) & not submitted_task(_) & last_move_blocked
      & has_destination(GX, GY)
    <- -last_move_blocked;
       if (nav_block_count(OldC)) { -nav_block_count(OldC); BC = OldC + 1 }
       else { BC = 1 };
       +nav_block_count(BC);
       if (BC >= 8) {
           -nav_block_count(BC);
           get_alternative_goal_zone(MX, MY, GX, GY, NGX, NGY);
           .abolish(has_destination(_, _));
           +has_destination(NGX, NGY);
           .print("[SUBMIT] Trocando goal zone para (", NGX, ",", NGY, ") apos ", BC, " bloqueios")
       };
       RDX = GX - MX; RDY = GY - MY;
       .random(R);
       if (RDX > 0 & (RDX >= RDY | RDX >= -RDY)) {
           if (R < 0.5) { Dir = n } else { Dir = s }
       }
       elif (RDX < 0 & (-RDX >= RDY | -RDX >= -RDY)) {
           if (R < 0.5) { Dir = n } else { Dir = s }
       }
       elif (RDY > 0) {
           if (R < 0.5) { Dir = e } else { Dir = w }
       }
       else {
           if (R < 0.5) { Dir = e } else { Dir = w }
       };
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act); action(Act).

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

// --- pending_submit: navigate to nearest goal zone EVERY step ---

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & not goalZone(0, 0) & not submitted_task(_)
    <- .abolish(nav_block_count(_));
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY);
           RDX = GX - MX; RDY = GY - MY;
           if (RDX > 0 & (RDX >= RDY | RDX >= -RDY)) { Dir = e }
           elif (RDX < 0 & (-RDX >= RDY | -RDX >= -RDY)) { Dir = w }
           elif (RDY > 0) { Dir = s }
           else { Dir = n };
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(Dir);
           .concat("move(", Dir, ")", Act); action(Act)
       } else { action("skip") };
       if ((N mod 20) == 0) {
           .print("[SUBMIT] Step ", N, ": nav goal zone para submit ", TaskName)
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
       .concat("{\"task\":\"", TaskName, "\",\"collector\":\"", Col1, "\"}", CIJson);
       !dash_log("connect_initiated", CIJson);
       !dash_task_phase(TaskName, "connect", 0);
       if (Col1 \== "none") {
           TargetStep = N + 3;
           !request_connect(Col1, TargetStep);
           +ready_to_connect(Col1, MX, MY, TaskName);
           .print("[ASSEMBLER] Solicitando connect com ", Col1)
       };
       action("skip").

// --- TRY CONNECT: assembler — detectar entidade adjacente via thing ---

+step(N)
    : ready_to_connect(Partner, PX, PY, TaskName) & my_pos(MX, MY)
      & thing(TX, TY, entity, _)
      & ((TX == 1 & TY == 0) | (TX == -1 & TY == 0) | (TX == 0 & TY == 1) | (TX == 0 & TY == -1))
    <- .concat("connect(", Partner, ",", TX, ",", TY, ")", Act);
       action(Act);
       -ready_to_connect(Partner, _, _, _);
       +waiting_connect_result(Partner, TaskName);
       .print("[CONNECT] Step ", N, ": Assembler connect(", Partner, ",", TX, ",", TY, ")").

// --- TRY CONNECT: assembler — sem entidade adjacente, esperar ---

+step(N)
    : ready_to_connect(_, _, _, _) & my_pos(MX, MY)
    <- action("skip").

// --- TRY CONNECT: collector — navegar ou connect ---

+step(N)
    : pending_connect(AsmName, AsmX, AsmY, TS) & my_pos(MX, MY)
    <- hive.AdjacentDirection(MX, MY, AsmX, AsmY, Dir);
       if (Dir \== none) {
           hive.ConnectCalculator(MX, MY, AsmX, AsmY, RelX, RelY);
           .concat("connect(", AsmName, ",", RelX, ",", RelY, ")", Act);
           action(Act);
           -pending_connect(AsmName, _, _, _);
           +pending_connect_backup(AsmName, AsmX, AsmY, TS);
           +waiting_connect_collector(AsmName);
           .print("[CONNECT] Step ", N, ": Collector connect(", AsmName, ",", RelX, ",", RelY, ")")
       } else {
           CDX = AsmX - MX; CDY = AsmY - MY;
           if (CDX > 0 & (CDX >= CDY | CDX >= -CDY)) { MoveDir = e }
           elif (CDX < 0 & (-CDX >= CDY | -CDX >= -CDY)) { MoveDir = w }
           elif (CDY > 0) { MoveDir = s }
           else { MoveDir = n };
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(MoveDir);
           .concat("move(", MoveDir, ")", Act);
           action(Act)
       }.
