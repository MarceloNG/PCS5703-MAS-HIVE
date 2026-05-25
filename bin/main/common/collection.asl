// ============================================================
// collection.asl — Ciclo de coleta de blocos
// Toda logica via +step(N) com verificacao de lastActionResult no contexto
// Incluir ANTES de navigation.asl para prioridade de +step(N)
// ============================================================

// --- Step: resultado do attach veio (prioridade maxima) ---

+step(N)
    : waiting_attach_result(Dir, Type) & lastActionResult(success) & my_pos(MX, MY)
    <- -waiting_attach_result(Dir, Type);
       -collecting(Type, _, _);
       -has_destination(_, _);
       +collected_block(Type);
       .print("[COL] Step ", N, ": Bloco ", Type, " attached com sucesso! Pos(", MX, ",", MY, ")");
       action("skip").

+step(N)
    : waiting_attach_result(Dir, Type) & lastActionResult(R) & my_pos(MX, MY)
    <- -waiting_attach_result(Dir, Type);
       .print("[COL] Step ", N, ": Attach falhou: ", R, ". Retentando...");
       .concat("attach(", Dir, ")", Act);
       action(Act);
       +waiting_attach_result(Dir, Type).

// --- Step: request deu certo, agora fazer attach ---

+step(N)
    : waiting_request(Dir, Type) & lastActionResult(success) & my_pos(MX, MY)
    <- -waiting_request(Dir, Type);
       .print("[COL] Step ", N, ": Request OK! Fazendo attach(", Dir, ")");
       .concat("attach(", Dir, ")", Act);
       action(Act);
       +waiting_attach_result(Dir, Type).

+step(N)
    : waiting_request(Dir, Type) & lastActionResult(R) & my_pos(MX, MY) & request_retries(Type, Retries) & Retries >= 5
    <- -waiting_request(Dir, Type);
       -request_retries(Type, _);
       -collecting(Type, _, _);
       .print("[COL] Step ", N, ": Request falhou ", Retries, "x. Tentando outro dispenser.");
       action("move(n)");
       !collect_block(Type).

+step(N)
    : waiting_request(Dir, Type) & lastActionResult(failed_blocked) & my_pos(MX, MY)
    <- -waiting_request(Dir, Type);
       if (request_retries(Type, OldR)) {
           -request_retries(Type, OldR); NewR = OldR + 1
       } else {
           NewR = 1
       };
       +request_retries(Type, NewR);
       .print("[COL] Step ", N, ": Request blocked (", NewR, "/5). Movendo e retentando...");
       .random(R);
       if (R < 0.25) { action("move(n)") }
       elif (R < 0.5) { action("move(e)") }
       elif (R < 0.75) { action("move(s)") }
       else { action("move(w)") }.

+step(N)
    : waiting_request(Dir, Type) & lastActionResult(R) & my_pos(MX, MY)
    <- -waiting_request(Dir, Type);
       .print("[COL] Step ", N, ": Request falhou: ", R, ". Retentando...");
       .concat("request(", Dir, ")", Act);
       action(Act);
       +waiting_request(Dir, Type).

// --- Step: coletando, desvio de obstaculo ---

+step(N)
    : collecting(Type, DX, DY) & my_pos(MX, MY) & last_move_blocked & not waiting_request(_, _)
    <- -last_move_blocked;
       .random(R);
       if (R < 0.25) { Dir = n }
       elif (R < 0.5) { Dir = e }
       elif (R < 0.75) { Dir = s }
       else { Dir = w };
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act);
       action(Act).

// --- Step: coletando, verificar adjacencia ao dispenser ---

+step(N)
    : collecting(Type, DX, DY) & my_pos(MX, MY)
      & not waiting_request(_, _) & not waiting_attach_result(_, _)
    <- hive.AdjacentDirection(MX, MY, DX, DY, Dir);
       if (Dir \== none) {
           .print("[COL] Step ", N, ": Adjacente ao dispenser ", Type, "! request(", Dir, ")");
           -has_destination(_, _);
           .concat("request(", Dir, ")", Act);
           action(Act);
           +waiting_request(Dir, Type)
       } else {
           CDX = DX - MX; CDY = DY - MY;
           if (CDX > 0 & (CDX >= CDY | CDX >= -CDY)) { MoveDir = e }
           elif (CDX < 0 & (-CDX >= CDY | -CDX >= -CDY)) { MoveDir = w }
           elif (CDY > 0) { MoveDir = s }
           else { MoveDir = n };
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(MoveDir);
           .concat("move(", MoveDir, ")", Act);
           action(Act)
       }.

// --- Goal: iniciar coleta ---

+!collect_block(Type)
    : my_pos(MX, MY)
    <- get_nearest_dispenser(MX, MY, Type, DX, DY);
       if (DX == -1) {
           .print("[COL] Nenhum dispenser ", Type, " conhecido")
       } else {
           .print("[COL] Indo coletar ", Type, " no dispenser (", DX, ",", DY, ")");
           +collecting(Type, DX, DY);
           +has_destination(DX, DY)
       }.

+!collect_block(_) <- true.

// --- Detach e Rotate ---

+!detach_block(Dir)
    <- .concat("detach(", Dir, ")", Act); action(Act).

+!rotate(Dir)
    <- .concat("rotate(", Dir, ")", Act); action(Act).
