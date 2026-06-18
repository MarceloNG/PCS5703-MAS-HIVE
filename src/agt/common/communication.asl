// ============================================================
// communication.asl — Mensagens de sincronizacao para connect
// ============================================================

// FIXME Fase D (#2, cross-frame): MX,MY estao no frame dead-reckoned DESTE agente.
// Pre-fusao (sem U9) o collector le essas coords no SEU proprio frame (origem distinta),
// entao a navegacao ate o ponto de connect fica incorreta no oficial — so o fallback por
// adjacencia percebida (connect_protocol) funciona. A U9 (frame compartilhado) torna a
// troca de coordenadas valida de novo.
+!request_connect(CollectorName, TargetStep)
    : my_pos(MX, MY)
    <- .my_name(Me);
       .send(CollectorName, tell, connect_request(Me, MX, MY, TargetStep));
       .print("[COMM] Pedido de connect enviado para ", CollectorName, " no step ", TargetStep).

+connect_request(AssemblerName, AsmX, AsmY, TargetStep)[source(S)]
    <- .print("[COMM] Recebi pedido de connect de ", AssemblerName, " para step ", TargetStep);
       .abolish(navigating_to_meeting_point(_));
       .abolish(has_destination(_, _));
       +pending_connect(AssemblerName, AsmX, AsmY, TargetStep).

+!confirm_connect(AssemblerName)
    : my_pos(MX, MY)
    <- .my_name(Me);
       .send(AssemblerName, tell, connect_confirmed(Me, MX, MY));
       .print("[COMM] Confirmacao de connect enviada para ", AssemblerName).

+connect_confirmed(CollectorName, ColX, ColY)[source(S)]
    <- .print("[COMM] ", CollectorName, " confirmou connect em (", ColX, ",", ColY, ")");
       +partner_confirmed(CollectorName, ColX, ColY).

+!request_connect(_, _) <- true.
+!confirm_connect(_) <- true.
