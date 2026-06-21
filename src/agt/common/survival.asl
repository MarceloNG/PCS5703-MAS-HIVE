// ============================================================
// survival.asl — Fuga reativa de clear-events (U10/#20)
// Inclusa ANTES de connect_protocol.asl para prioridade maxima
// de +step(N): este handler dispara antes dos de navegacao.
// ============================================================

// --- P1: ci (clear iminente, <= 2 steps) ---
// Padrao de direcao: identico ao connect_protocol.asl (math.abs inline no if).
+step(N)
    : my_pos(MX, MY) & thing(RX, RY, marker, ci) & not am_deactivated
    <- .print("[SURVIVAL] Step ", N, ": CI em (", RX, ",", RY, ")! Fugindo.");
       if (math.abs(RX) >= math.abs(RY)) {
           if (RX > 0) { FleeDir = w } else { FleeDir = e }
       } else {
           if (RY > 0) { FleeDir = n } else { FleeDir = s }
       };
       !flee_dir(FleeDir, MX, MY, RX, RY).

// --- P2: clear (aviso com warning steps de antecedencia) ---
+step(N)
    : my_pos(MX, MY) & thing(RX, RY, marker, clear) & not am_deactivated
    <- .print("[SURVIVAL] Step ", N, ": CLEAR em (", RX, ",", RY, "). Saindo.");
       if (math.abs(RX) >= math.abs(RY)) {
           if (RX > 0) { FleeDir = w } else { FleeDir = e }
       } else {
           if (RY > 0) { FleeDir = n } else { FleeDir = s }
       };
       !flee_dir(FleeDir, MX, MY, RX, RY).

// Executa a direcao de fuga calculada. Fallback: !escape_move se bloqueada.
+!flee_dir(FleeDir, MX, MY, RX, RY)
    <- !get_dir_offset(FleeDir, OX, OY);
       !compute_legal(OX, OY);
       if (legal_ok) {
           .abolish(legal_ok);
           .concat("move(", FleeDir, ")", Act);
           .print("[SURVIVAL] Fugindo dir=", FleeDir, " marker rel=(", RX, ",", RY, ")");
           action(Act)
       } else {
           .abolish(legal_ok);
           GX is MX - RX; GY is MY - RY;
           .print("[SURVIVAL] Dir ", FleeDir, " bloqueada — escape_move alvo=(", GX, ",", GY, ")");
           !escape_move(MX, MY, GX, GY)
       }.
-!flee_dir(_, _, _, _, _) <- action("skip").
