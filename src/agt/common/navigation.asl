// ============================================================
// navigation.asl — Navegacao step-by-step
// Chamado quando connect_protocol e collection nao interceptam o step
// ============================================================

// (Chegada ao meeting-point squad-era removida no #53 — sem rendezvous; a trilha
//  única é solo. As primitivas connect ficam inertes em connect_protocol.asl.)

// --- Destino generico alcancado ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & DX == MX & DY == MY
    <- -has_destination(DX, DY);
       .print("[NAV] Step ", N, ": Cheguei ao destino (", DX, ",", DY, "). Explorando...");
       .concat("{\"x\":", DX, ",\"y\":", DY, "}", DJ);
       !dash_log("arrived_dest", DJ);
       !do_explore(MX, MY).

// --- Detach forçado quando stuck por 10+ steps ---

+step(N)
    : need_detach(DDir) & solo_mode(TaskName) & pending_submit(TaskName) & my_pos(MX, MY)
    <- -need_detach(DDir);
       .print("[NAV] Step ", N, ": STUCK com bloco para submit! Tentando goal zone alternativa");
       get_alternative_goal_zone(MX, MY, MX, MY, AGX, AGY);
       if (AGX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(AGX, AGY);
           .print("[NAV] Rota alternativa para (", AGX, ",", AGY, ")")
       } else {
           get_nearest_goal_zone(MX, MY, GX, GY);
           if (GX \== -1) {
               .abolish(has_destination(_, _));
               +has_destination(GX, GY)
           }
       };
       .random(R);
       if (R < 0.25) { Dir = n }
       elif (R < 0.5) { Dir = e }
       elif (R < 0.75) { Dir = s }
       else { Dir = w };
       .concat("move(", Dir, ")", Act);
       action(Act).

+step(N)
    : need_detach(DDir) & solo_mode(TaskName)
    <- -need_detach(DDir);
       .print("[NAV] Step ", N, ": STUCK durante coleta solo. Detach(", DDir, ")");
       if (attached(_, _)) { .print("[DETACH] carrier step ", N) };
       .concat("detach(", DDir, ")", Act);
       action(Act).

+step(N)
    : need_detach(DDir)
    <- -need_detach(DDir);
       .print("[NAV] Step ", N, ": STUCK! Detach(", DDir, ") para destravar");
       if (attached(_, _)) { .print("[DETACH] carrier step ", N) };
       .concat("detach(", DDir, ")", Act);
       action(Act).

// --- Desvio de obstaculo: direcao aleatoria (4 direcoes iguais) ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & (last_move_blocked | escape_pending(_, _))
    <- .abolish(last_move_blocked);
       .abolish(escape_pending(_, _));
       !escape_move(MX, MY, DX, DY).

// --- Navegar ao destino (greedy inline) ---

+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY) & pending_submit(TN)
    <- compute_next_move(MX, MY, DX, DY, Dir);
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act);
       action(Act);
       if ((N mod 20) == 0) {
           .print("[NAV] Step ", N, ": nav to goal zone (", DX, ",", DY, ") for submit ", TN, " from (", MX, ",", MY, ")")
       }.

// Issue #27 (Cap A+B unificada): a CADA step re-avalia se o destino virou um
// cul-de-sac visível (paredes agora percebidas). Se sim, abandona e re-explora —
// o filtro de frontier então escolhe uma fronteira ABERTA. Dentro de um beco o
// agente já viu as paredes → o destino interior classifica como beco → ele
// busca a saída (meia-volta p/ evitar entrar; rota p/ fora se já entrou).
+step(N)
    : has_destination(DX, DY) & my_pos(MX, MY)
    <- is_cul_de_sac(MX, MY, DX, DY, IsBeco);
       if (IsBeco == 1) {
           .abolish(has_destination(_, _));
           .print("[NAV] Step ", N, ": destino (", DX, ",", DY, ") é cul-de-sac visível — meia-volta, re-explora");
           !do_explore(MX, MY)
       } else {
           compute_next_move(MX, MY, DX, DY, Dir);
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(Dir);
           .concat("move(", Dir, ")", Act);
           action(Act)
       }.

// --- Exploracao (sem destino) ---

+step(N)
    : my_pos(MX, MY) & not my_active_task(_, _)
    <- if ((N mod 10) == 0) {
           .my_name(Me);
           get_map_stats(V, D, G, R);
           .print("[NAV] Step ", N, " Agent=", Me, " Pos(", MX, ",", MY, ") Map: vis=", V, " disp=", D, " goal=", G, " role=", R)
       };
       !do_explore(MX, MY).

+step(N)
    : my_pos(MX, MY)
    <- !do_explore(MX, MY).

+step(N)
    <- .print("[NAV] Step ", N, ": Sem posicao, skip");
       action("skip").

// --- Exploracao: Bug0 (#49 N2) ou escape quando preso (#27) ---

+!do_explore(MX, MY)
    <- .my_name(Me);
       is_stuck(Stuck);
       if (Stuck == 1) {
           // #27: preso → ray-cast p/ abertura; A* roteia p/ fora do pocket
           get_escape_target(MX, MY, FX, FY);
           .print("[NAV] PRESO em (", MX, ",", MY, ") — escape p/ (", FX, ",", FY, ")");
           if (FX \== MX | FY \== MY) {
               .abolish(has_destination(_, _));
               +has_destination(FX, FY);
               compute_next_move(MX, MY, FX, FY, Dir);
               .abolish(last_attempted_dir(_));
               +last_attempted_dir(Dir);
               .concat("move(", Dir, ")", Act);
               action(Act)
           } else {
               !bug0_move(n)
           }
       } else {
           // N2 Bug0 (#49): direção primária por índice; contorno CW sem A*
           get_explore_dir(Me, PDir);
           !bug0_move(PDir)
       }.

-!do_explore(_, _)
    <- action("move(n)").

// Bug0: move em PDir ou primeiro dir livre em rotação CW (n→e→s→w).
+!bug0_move(PDir)
    <- !bug0_pick_dir(PDir, Dir);
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(Dir);
       .concat("move(", Dir, ")", Act);
       action(Act).
-!bug0_move(PDir)
    <- .abolish(last_attempted_dir(_));
       +last_attempted_dir(PDir);
       .concat("move(", PDir, ")", Act);
       action(Act).

// Primeiro dir não-bloqueado em CW a partir de PDir.
// Bloqueio primário: célula adjacente (obs+entity) OU look-ahead 2-3 (só obstacle).
// Look-ahead simula "vê a parede antes de chegar" → inicia CW contour mais cedo.
+!bug0_pick_dir(PDir, Dir)
    <- if (PDir == n) {
           D1 = n; O1X = 0;  O1Y = -1;
           D2 = e; O2X = 1;  O2Y = 0;
           D3 = s; O3X = 0;  O3Y = 1;
           D4 = w; O4X = -1; O4Y = 0;
           LA1X = 0; LA1Y = -2;
           LA2X = 0; LA2Y = -3
       } elif (PDir == e) {
           D1 = e; O1X = 1;  O1Y = 0;
           D2 = s; O2X = 0;  O2Y = 1;
           D3 = w; O3X = -1; O3Y = 0;
           D4 = n; O4X = 0;  O4Y = -1;
           LA1X = 2; LA1Y = 0;
           LA2X = 3; LA2Y = 0
       } elif (PDir == s) {
           D1 = s; O1X = 0;  O1Y = 1;
           D2 = w; O2X = -1; O2Y = 0;
           D3 = n; O3X = 0;  O3Y = -1;
           D4 = e; O4X = 1;  O4Y = 0;
           LA1X = 0; LA1Y = 2;
           LA2X = 0; LA2Y = 3
       } else {
           D1 = w; O1X = -1; O1Y = 0;
           D2 = n; O2X = 0;  O2Y = -1;
           D3 = e; O3X = 1;  O3Y = 0;
           D4 = s; O4X = 0;  O4Y = 1;
           LA1X = -2; LA1Y = 0;
           LA2X = -3; LA2Y = 0
       };
       // Primária bloqueada: adjacente (obs/entity) OU obstacle visível a 2-3 células
       if (cell_blocked(O1X, O1Y) |
           thing(LA1X, LA1Y, obstacle, _) |
           thing(LA2X, LA2Y, obstacle, _)) {
           if (not cell_blocked(O2X, O2Y)) { Dir = D2 }
           elif (not cell_blocked(O3X, O3Y)) { Dir = D3 }
           elif (not cell_blocked(O4X, O4Y)) { Dir = D4 }
           else { Dir = D1 }
       } else {
           Dir = D1
       }.
-!bug0_pick_dir(_, Dir) <- Dir = n.

// ============================================================
// Escape reativo (#3 + #4 agindo) — camada 100% .asl
// Invocado pelos handlers de bloqueio (move falho / oscilacao).
// Legalidade pela percepcao local; ranking pela distancia toroidal
// do SharedMap (manhattan_dist), consistente com o A*.
// ============================================================

// offsets das 4 direcoes
dir_off(n, 0, -1).
dir_off(e, 1, 0).
dir_off(s, 0, 1).
dir_off(w, -1, 0).

// celula relativa (CX,CY) bloqueada por obstaculo/entidade/bloco-de-outro
cell_blocked(CX, CY) :- thing(CX, CY, obstacle, _).
cell_blocked(CX, CY) :- thing(CX, CY, entity, _).
cell_blocked(CX, CY) :- thing(CX, CY, block, _) & not attached(CX, CY).

// anti-volta: a direcao candidata e o reverso do ultimo movimento (= volta a celula-parceira da oscilacao)
is_bounce(n) :- last_attempted_dir(s).
is_bounce(s) :- last_attempted_dir(n).
is_bounce(e) :- last_attempted_dir(w).
is_bounce(w) :- last_attempted_dir(e).

// legalidade de uma direcao (offset OX,OY): celula do agente + futuros dos
// blocos anexados livres. Resultado deixado na crenca legal_ok.
+!compute_legal(OX, OY)
    <- .abolish(legal_ok);
       .abolish(dir_bad);
       .findall(att(AX, AY), attached(AX, AY), Atts);
       for (.member(att(BX, BY), Atts)) {
           BTX = BX + OX; BTY = BY + OY;
           if ((BTX \== 0 | BTY \== 0) & not attached(BTX, BTY) & cell_blocked(BTX, BTY)) {
               +dir_bad
           }
       };
       if (not cell_blocked(OX, OY) & not dir_bad) { +legal_ok };
       .abolish(dir_bad).
-!compute_legal(_, _) <- .abolish(dir_bad).

// pontua uma direcao: se legal e nao for a celula-parceira da oscilacao
// (osc_p2 = posicao do step anterior), calcula a distancia toroidal ao
// destino e registra esc_cand(Dir, D).
+!score_dir(MX, MY, GX, GY, Dir, OX, OY)
    <- !compute_legal(OX, OY);
       TX = MX + OX; TY = MY + OY;   // manhattan_dist normaliza internamente (toroidal) — independe do tamanho do grid
       if (legal_ok & not is_bounce(Dir)) {   // anti-volta por DIRECAO (reverso do ultimo move), sem hardcode de tamanho
           manhattan_dist(TX, TY, GX, GY, D);
           +esc_cand(Dir, D)
       };
       .abolish(legal_ok).
-!score_dir(_, _, _, _, _, _, _) <- .abolish(legal_ok).

// objetivo principal: escolher e executar a acao de escape
+!escape_move(MX, MY, GX, GY)
    <- .abolish(esc_cand(_, _));
       !score_dir(MX, MY, GX, GY, n, 0, -1);
       !score_dir(MX, MY, GX, GY, e, 1, 0);
       !score_dir(MX, MY, GX, GY, s, 0, 1);
       !score_dir(MX, MY, GX, GY, w, -1, 0);
       !pick_escape(MX, MY).
-!escape_move(_, _, _, _) <- .abolish(esc_cand(_, _)); action("skip").

// ha candidato legal: move ao mais proximo do destino (empate -> ordem horaria)
+!pick_escape(MX, MY)
    : esc_cand(_, _)
    <- .findall(c(D, Dir), esc_cand(Dir, D), L);
       .min(L, c(MinD, _));
       .findall(TDir, esc_cand(TDir, MinD), Ties);
       if (.member(n, Ties)) { ChosenDir = n }
       elif (.member(e, Ties)) { ChosenDir = e }
       elif (.member(s, Ties)) { ChosenDir = s }
       else { ChosenDir = w };
       .abolish(esc_cand(_, _));
       .abolish(boxed_count(_));
       .abolish(last_attempted_dir(_));
       +last_attempted_dir(ChosenDir);
       .concat("move(", ChosenDir, ")", Act);
       action(Act).
// nenhum candidato legal util: encurralado
+!pick_escape(MX, MY)
    <- .abolish(esc_cand(_, _));
       !boxed_step(MX, MY).
-!pick_escape(_, _) <- .abolish(esc_cand(_, _)); action("skip").

// encurralado: ceder; apos K steps consecutivos, sacudir (quebra simetria)
+!boxed_step(MX, MY)
    <- if (boxed_count(C0)) { -boxed_count(C0); BC = C0 + 1 } else { BC = 1 };
       +boxed_count(BC);
       if (escape_shake_k(KK)) { Kv = KK } else { Kv = 3 };
       if (BC >= Kv) {
           .abolish(boxed_count(_)); +boxed_count(0);
           !shake(MX, MY)
       } else {
           action("skip")
       }.
-!boxed_step(_, _) <- action("skip").

// sacode: move aleatorio entre direcoes fisicamente legais (sem ranking/parceiro)
+!shake(MX, MY)
    <- !collect_free(MX, MY);
       .findall(FD, free_dir(FD), Free);
       .abolish(free_dir(_));
       .length(Free, NF);
       if (NF > 0) {
           .random(R2);
           RI = math.floor(R2 * NF);
           .nth(RI, Free, SD);
           .abolish(last_attempted_dir(_));
           +last_attempted_dir(SD);
           .concat("move(", SD, ")", SAct);
           action(SAct)
       } else {
           action("skip")
       }.
-!shake(_, _) <- action("skip").

+!collect_free(MX, MY)
    <- .abolish(free_dir(_));
       !mark_free(n, 0, -1);
       !mark_free(e, 1, 0);
       !mark_free(s, 0, 1);
       !mark_free(w, -1, 0).
-!collect_free(_, _) <- true.

+!mark_free(Dir, OX, OY)
    <- !compute_legal(OX, OY);
       if (legal_ok) { +free_dir(Dir) };
       .abolish(legal_ok).
-!mark_free(_, _, _) <- .abolish(legal_ok).
