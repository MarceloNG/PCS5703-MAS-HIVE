{ include("common/perception.asl") }
{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("common/organization.asl") }
{ include("common/dashboard_hooks.asl") }
{ include("common/connect_protocol.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

my_role_type(squad_leader).

!start.

+!start
    <- .my_name(Me);
       .print("[LEADER] ", Me, " iniciado.");
       !setup_shared_map;
       !try_set_grid_dims;
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

// --- Reagir a nova task disponivel (wait 150ms to ensure +task percept is processed) ---

+new_task_available(TaskName, Deadline, Reward, NBlocks)
    <- .wait(50);
       !eval_and_delegate(TaskName, Deadline, Reward, NBlocks).

+!eval_and_delegate(TaskName, Deadline, Reward, NBlocks)
    : step(CurrentStep) & my_pos(MX, MY)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       TimeLeft = Deadline - CurrentStep;
       if (MySquad \== "none" & TimeLeft > 40) {
           if (task_req(TaskName, _, _, FirstBT)) { BType = FirstBT }
           else {
               get_task_first_block(TaskName, FBT);
               BType = FBT
           };
           get_nearest_dispenser(MX, MY, BType, DispX, DispY);
           if (DispX \== -1) { manhattan_dist(MX, MY, DispX, DispY, MDist) }
           else { MDist = 20 };
           if (NBlocks == 1) { BaseScore = Reward * 100 }
           else { BaseScore = (Reward / NBlocks) * 10 };
           Score = BaseScore - MDist;
           .print("[LEADER] Task ", TaskName, " Score=", Score, " type=", BType, " TL=", TimeLeft);
           place_bid(TaskName, MySquad, Score);
           .wait(20);
           resolve_auction(TaskName, Winner);
           if (Winner == MySquad) {
               .print("[LEADER] Ganhamos task ", TaskName, "! Delegando...");
               set_squad_task(MySquad, TaskName);
               !delegate_collection_safe(TaskName, NBlocks, Deadline, BType)
           }
       }.

+!eval_and_delegate(_, _, _, _) <- true.
-!eval_and_delegate(_, _, _, _) <- true.

// --- Delegar coleta (1-block) ---

+!delegate_collection_safe(TaskName, NBlocks, Deadline, BType)
    : my_pos(MX, MY) & NBlocks == 1
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       .print("[LEADER] delegate: ", TaskName, " type=", BType, " deadline=", Deadline);
       get_nearest_dispenser(MX, MY, BType, SDispX, SDispY);
       if (SDispX == -1) { SDispX = MX; SDispY = MY };
       find_free_soloist(SDispX, SDispY, Solo1);
       if (Solo1 \== "none") {
           mark_busy(Solo1);
           .send(Solo1, tell, soloist_task(TaskName, BType, Deadline));
           !dash_task_phase(TaskName, "collect", 0);
           .print("[LEADER] Sol ", Solo1, " -> ", TaskName, " (", BType, ")")
       } else {
           .print("[LEADER] Nenhum soloist livre para ", TaskName)
       }.

// --- Delegar coleta (2-block) ---

+!delegate_collection_safe(TaskName, NBlocks, Deadline, _)
    : my_pos(MX, MY) & NBlocks == 2
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       get_task_blocks(TaskName, Block1Type, Block2Type);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX == -1) {
           .print("[LEADER] 2-block: sem goal zone, skip")
       } else {
           get_squad_collectors(MySquad, Col1, Col2);
           get_squad_assembler(MySquad, Asm);
           if (Col1 \== "none" & Asm \== "none") {
               is_soloist_busy(Col1, Busy1);
               is_soloist_busy(Asm, BusyAsm);
               if (Busy1 | BusyAsm) {
                   .print("[LEADER] 2-block: busy, skip ", TaskName)
               } else {
                   set_meeting_point(MySquad, GX, GY);
                   mark_busy(Col1);
                   mark_busy(Asm);
                   assign_block_to_collector(Col1, Block2Type);
                   .send(Col1, tell, do_collect(Block2Type));
                   .send(Asm, tell, collect_and_connect_task(TaskName, MySquad, Block1Type));
                   !dash_task_phase(TaskName, "collect", 0);
                   .print("[LEADER] 2-block: Col=", Col1, "(", Block2Type, ") Asm=", Asm, "(", Block1Type, ")")
               }
           } else {
               .print("[LEADER] 2-block: squad incompleto, skip")
           }
       }.

+!delegate_collection_safe(TaskName, _, _, _)
    <- .print("[LEADER] delegate fallback: skip ", TaskName).

-!delegate_collection_safe(_, _, _, _) <- true.

// --- SOLOIST TASK: leader tambem pode ser soloist ---

+soloist_task(TaskName, BlockType, Deadline)[source(S)]
    : not my_active_task(_, _) & step(CurStep)
    <- .print("[LEADER] Soloist task ", TaskName, ": coletar ", BlockType, " deadline=", Deadline);
       .my_name(Me);
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(waiting_request(_, _));
       .abolish(waiting_attach_result(_, _));
       .abolish(collected_block(_));
       .abolish(solo_mode(_));
       .abolish(solo_block_type(_));
       .abolish(request_retries(_, _));
       .abolish(task_accepted_step(_, _));
       .abolish(assigned_task_block(_));
       .abolish(my_task_deadline(_, _));
       .abolish(searching_dispenser(_));
       .abolish(needs_clear_blocks(_));
       +my_task_deadline(TaskName, Deadline);
       +my_active_task(TaskName, "solo");
       +solo_mode(TaskName);
       +solo_block_type(BlockType);
       +task_accepted_step(TaskName, CurStep);
       if (attached(_, _)) {
           +needs_clear_blocks(BlockType)
       } else {
           !collect_block(BlockType)
       }.

+soloist_task(TaskName, BlockType, Deadline)[source(S)]
    <- .print("[LEADER] Rejeitando soloist_task ", TaskName, " (task ativa)").

// --- SOLO: bloco coletado → buscar goal zone ---

+collected_block(Type)
    : solo_mode(TaskName) & my_role_type(squad_leader) & my_pos(MX, MY)
    <- .print("[LEADER] Bloco ", Type, " coletado para submit ", TaskName);
       !dash_task_phase(TaskName, "submit_nav", 50);
       +pending_submit(TaskName);
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           .abolish(has_destination(_, _));
           +has_destination(GX, GY);
           .print("[LEADER] Nav goal zone (", GX, ",", GY, ") para submit ", TaskName)
       } else {
           .print("[LEADER] Nenhuma goal zone conhecida. pending_submit ativo.")
       }.

// --- Finalizar soloist task e liberar no pool ---

+!finalize_task(TaskName)
    <- .my_name(Me);
       mark_free(Me);
       complete_task(TaskName);
       release_agent_from_task(TaskName, Me);
       .abolish(my_active_task(_, _));
       .abolish(pending_submit(_));
       .abolish(submitted_task(_));
       .abolish(submit_rotate_count(_, _));
       .abolish(submit_reposition_count(_, _));
       .abolish(task_accepted_step(_, _));
       .abolish(solo_mode(_));
       .abolish(solo_block_type(_));
       .abolish(my_task_deadline(_, _));
       .abolish(collecting(_, _, _));
       .abolish(has_destination(_, _));
       .abolish(assigned_task_block(_));
       .abolish(nav_block_count(_));
       .abolish(searching_dispenser(_));
       .abolish(needs_clear_blocks(_));
       .concat("{\"task\":\"", TaskName, "\"}", FJson);
       !dash_log("task_finalized", FJson);
       !dash_task_phase(TaskName, "done", 100);
       .print("[LEADER] Task ", TaskName, " finalizada.").

-!finalize_task(_) <- true.

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
+!check_expired_task
    : my_active_task(TaskName, _) & step(N) & known_task(TaskName, Deadline, _, _) & N >= Deadline
    <- .print("[LEADER] Task ", TaskName, " expirou! Limpando...");
       !finalize_task(TaskName).

+!check_expired_task
    : my_active_task(TaskName, _) & step(N)
      & task_accepted_step(TaskName, AccStep) & (N - AccStep > 300)
    <- .print("[LEADER] Task ", TaskName, " timeout (", N - AccStep, " steps). Limpando...");
       !finalize_task(TaskName).

+!check_expired_task <- true.
-!check_expired_task <- true.

// --- Re-avaliacao agressiva de tasks a cada 5 steps ---

+step(N)
    : (N mod 10) == 3 & my_pos(MX, MY) & step(N)
    <- !scan_and_delegate_tasks.

+!scan_and_delegate_tasks
    : my_pos(MX, MY) & step(CS)
      & known_task(TN, TD, _, 1) & TD - CS > 40
      & task_req(TN, _, _, BType)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       if (MySquad \== "none") {
           is_task_assigned(TN, Assigned);
           if (not Assigned) {
               !quick_delegate(TN, TD, 1)
           }
       }.

+!quick_delegate(TaskName, Deadline, NBlocks)
    : my_pos(MX, MY) & step(CS)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       if (task_req(TaskName, _, _, FirstBT)) { BType = FirstBT }
       else {
           get_task_first_block(TaskName, FBT);
           BType = FBT
       };
       get_nearest_dispenser(MX, MY, BType, DispX, DispY);
       if (DispX \== -1) { manhattan_dist(MX, MY, DispX, DispY, MDist) }
       else { MDist = 20 };
       Score = 1000 - MDist;
       place_bid(TaskName, MySquad, Score);
       resolve_auction(TaskName, Winner);
       if (Winner == MySquad) {
           set_squad_task(MySquad, TaskName);
           find_free_soloist(MX, MY, Solo1);
           if (Solo1 \== "none") {
               mark_busy(Solo1);
               .send(Solo1, tell, soloist_task(TaskName, BType, Deadline));
               .print("[LEADER] (qd) Sol ", Solo1, " -> ", TaskName, " (", BType, ") dl=", Deadline)
           }
       }.

+!quick_delegate(_, _, _) <- true.
-!quick_delegate(_, _, _) <- true.

+!scan_and_delegate_tasks <- true.
-!scan_and_delegate_tasks <- true.
