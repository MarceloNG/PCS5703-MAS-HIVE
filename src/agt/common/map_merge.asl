// ============================================================
// map_merge.asl — U9/#17: fusao de mapa por avistamento mutuo
//
// Protocolo: i_see_mate -> reciprocidade via thing() ->
//            known_offset -> request_discoveries ->
//            remote_discoveries -> import_* @OPERATIONs
//
// Trigger: +thing(X,Y,entity,Team) em perception.asl faz broadcast
// i_see_mate; este arquivo processa o handshake resultante.
// ============================================================

// Recebe broadcast de avistamento. Verifica reciprocidade via percept
// thing() direto (sem belief intermediario — elimina risco de timing).
// ExpRX/ExpRY = -RelX/-RelY: posicao esperada do emissor no frame do receptor.
// DX converte coordenadas do FRAME DO EMISSOR para o FRAME DESTE AGENTE.
+i_see_mate(PeerName, S, PeerX, PeerY, RelX, RelY)[source(PeerName)]
    : my_pos(MX, MY) & my_team(MyTeam)
      & thing(ExpRX, ExpRY, entity, MyTeam)
      & ExpRX == -RelX & ExpRY == -RelY
      & not known_offset(PeerName, _, _)
    <- DX = MX + ExpRX - PeerX;
       DY = MY + ExpRY - PeerY;
       +known_offset(PeerName, DX, DY);
       .my_name(Me);
       .send(PeerName, tell, request_discoveries(Me));
       .print("[MERGE] Offset com ", PeerName, ": (", DX, ",", DY, ") step=", S).

// Fallback: reciprocidade nao verificada ou offset ja conhecido — descarta.
+i_see_mate(_, _, _, _, _, _)[source(_)] <- true.

// Exporta discoveries ao solicitante via remote_discoveries.
+request_discoveries(RequesterName)[source(RequesterName)]
    <- .abolish(request_discoveries(RequesterName)[source(_)]);
       .findall(disp(X,Y,T), known_dispenser(X,Y,T), Disps);
       .findall(gz(X,Y), known_goal_zone(X,Y), Goals);
       .findall(rz(X,Y), known_role_zone(X,Y), Roles);
       .my_name(Me);
       .send(RequesterName, tell, remote_discoveries(Me, Disps, Goals, Roles)).

// Ingere discoveries do colega, traduzindo ao proprio frame via import_* @OPERATIONs.
+remote_discoveries(SenderName, Disps, Goals, Roles)[source(_)]
    : known_offset(SenderName, DX, DY)
    <- .abolish(remote_discoveries(SenderName, _, _, _)[source(_)]);
       for (.member(disp(X,Y,T), Disps)) {
           TX = X + DX; TY = Y + DY;
           import_dispenser(TX, TY, T)
       };
       for (.member(gz(X,Y), Goals)) {
           TX = X + DX; TY = Y + DY;
           import_goal_zone(TX, TY)
       };
       for (.member(rz(X,Y), Roles)) {
           TX = X + DX; TY = Y + DY;
           import_role_zone(TX, TY)
       };
       .length(Disps, LD); .length(Goals, LG); .length(Roles, LR);
       .print("[MERGE] Importei de ", SenderName, ": ", LD, "d ", LG, "g ", LR, "r").

// Fallback: sem known_offset ainda — descarta (offset nao estabelecido).
+remote_discoveries(_, _, _, _)[source(_)] <- true.
