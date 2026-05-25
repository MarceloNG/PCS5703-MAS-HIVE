{ include("common/perception.asl") }
{ include("common/dashboard_hooks.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

my_role_type(squad_leader).

!start.

+!start
    <- .my_name(Me);
       .print("[LEADER] ", Me, " iniciado.");
       !setup_shared_map;
       !setup_task_board;
       !setup_squad_coordinator;
       !setup_hive_dashboard;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[LEADER] Conectado. Modo: exploracao + coordenacao.");
       !register_squad_on_dashboard.

+!setup_shared_map
    <- lookupArtifact("shared_map", MapId); focus(MapId).
-!setup_shared_map
    <- .wait(50); !try_create_map.
+!try_create_map
    <- makeArtifact("shared_map", "env.SharedMap", [], MapId); focus(MapId).
-!try_create_map
    <- .wait(100); !setup_shared_map.

+!setup_task_board
    <- lookupArtifact("task_board", TbId); focus(TbId).
-!setup_task_board
    <- .wait(50); !try_create_task_board.
+!try_create_task_board
    <- makeArtifact("task_board", "env.TaskBoard", [], TbId); focus(TbId).
-!try_create_task_board
    <- .wait(100); !setup_task_board.

+!setup_squad_coordinator
    <- lookupArtifact("squad_coordinator", ScId); focus(ScId).
-!setup_squad_coordinator
    <- .wait(50); !try_create_squad_coordinator.
+!try_create_squad_coordinator
    <- makeArtifact("squad_coordinator", "env.SquadCoordinator", [], ScId); focus(ScId).
-!try_create_squad_coordinator
    <- .wait(100); !setup_squad_coordinator.

+name(N)  <- .print("[LEADER] SIM-START: nome = ", N).
+team(T)  <- .print("[LEADER] SIM-START: time = ", T).
+steps(S) <- .print("[LEADER] SIM-START: steps = ", S).

// --- Reagir a nova task disponivel ---

+new_task_available(TaskName, Deadline, Reward, NBlocks)
    : step(CurrentStep) & my_pos(MX, MY)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       TimeLeft = Deadline - CurrentStep;
       if (MySquad \== "none" & TimeLeft > 40) {
           get_task_first_block(TaskName, BType);
           get_nearest_dispenser(MX, MY, BType, DispX, DispY);
           if (DispX \== -1) {
               manhattan_dist(MX, MY, DispX, DispY, MDist)
           } else {
               MDist = 20
           };
           BaseScore = (Reward / NBlocks) * 100;
           Score = BaseScore - MDist;
           .print("[LEADER] Task ", TaskName, " Score=", Score, " (base=", BaseScore, " dist=", MDist, " type=", BType, ") TimeLeft=", TimeLeft);
           place_bid(TaskName, MySquad, Score);
           .concat("{\"task\":\"", TaskName, "\",\"squad\":\"", MySquad, "\",\"value\":", Score, "}", BidJson);
           !dash_log("bid_placed", BidJson);
           .wait(50);
           resolve_auction(TaskName, Winner);
           if (Winner == MySquad) {
               .print("[LEADER] Ganhamos task ", TaskName, "! Delegando coleta...");
               .concat("{\"task\":\"", TaskName, "\",\"squad\":\"", MySquad, "\"}", WonJson);
               !dash_log("auction_won", WonJson);
               !dash_task_phase(TaskName, "auction", 100);
               set_squad_task(MySquad, TaskName);
               !delegate_collection(TaskName, NBlocks)
           } else {
               .concat("{\"task\":\"", TaskName, "\",\"squad\":\"", MySquad, "\",\"winner\":\"", Winner, "\"}", LostJson);
               !dash_log("auction_lost", LostJson);
               .print("[LEADER] Task ", TaskName, " atribuida a ", Winner)
           }
       }.

// --- Delegar coleta: assembler coleta b0 sempre, collector coleta b1 se 2 blocos ---

+!delegate_collection(TaskName, NBlocks)
    : my_pos(MX, MY)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       !dash_task_phase(TaskName, "collect", 0);
       .print("[LEADER] delegate_collection: ", TaskName, " NBlocks=", NBlocks);
       .print("[LEADER] Task ", TaskName, " buscando soloist livre mais proximo");
       get_task_first_block(TaskName, ReqType);
       get_nearest_dispenser(MX, MY, ReqType, SDispX, SDispY);
       if (SDispX == -1) { SDispX = MX; SDispY = MY };
       find_free_soloist(SDispX, SDispY, SoloWinner);
       if (SoloWinner \== "none") {
           mark_busy(SoloWinner);
           .send(SoloWinner, tell, soloist_task(TaskName, ReqType));
           .print("[LEADER] Soloist ", SoloWinner, " fara coleta+submit de ", TaskName, " tipo=", ReqType)
       } else {
           get_squad_assembler(MySquad, Asm);
           if (Asm \== "none") {
               .send(Asm, tell, solo_task(TaskName, MySquad, ReqType));
               .print("[LEADER] Fallback: ", Asm, " fara solo_task ", TaskName)
           }
       }.

-!delegate_collection(_, _) <- .print("[LEADER] Falha ao delegar coleta (sem posicao?)").

+!register_squad_on_dashboard
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       if (MySquad \== "none") {
           get_squad_collectors(MySquad, Col1, Col2);
           get_squad_assembler(MySquad, Asm);
           .concat("[{\"name\":\"", Me, "\",\"role\":\"leader\"},{\"name\":\"", Col1, "\",\"role\":\"collector\"},{\"name\":\"", Col2, "\",\"role\":\"collector\"},{\"name\":\"", Asm, "\",\"role\":\"assembler\"}]", MembersJson);
           !dash_squad(MySquad, MembersJson)
       }.
-!register_squad_on_dashboard <- true.
+!check_expired_task <- true.
-!check_expired_task <- true.
