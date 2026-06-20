package env;

import cartago.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro leve de agentes. O regime squad-era (squads fixos, meeting-points,
 * assignments de collector, pool de soloists) foi REMOVIDO no #53 — junto com seus
 * leitores (find_free_soloist, is_soloist_busy), que já estavam mortos desde o #38.
 * Restam os 3 @OPERATION ainda chamados pelo regime único; preservam o contrato dos
 * .asl (perception, allocator #40, finalize) sem reintroduzir coordenação de squad.
 * Os mapas abaixo são escritos por esses hooks e ficam disponíveis p/ quando um
 * consumidor voltar (refino do allocator #45, coalizões #43/#44).
 *
 * Nome da classe/artefato mantido por ora — rename → AgentRegistry é follow-up do #53
 * (evita churn de makeArtifact/lookupArtifact durante a remoção do regime).
 */
public class SquadCoordinator extends Artifact {

    ConcurrentHashMap<String, Boolean> soloistBusy;   // package-private p/ teste
    ConcurrentHashMap<String, int[]> agentPositions;

    void init() {
        soloistBusy = new ConcurrentHashMap<>();
        agentPositions = new ConcurrentHashMap<>();
    }

    // Bookkeeping de ocupação — escrito pelo allocator #40 (mark_busy ao ganhar task,
    // mark_free no finalize). Sem leitor vivo pós-#53; mantido p/ preservar o contrato.
    @OPERATION
    void mark_busy(Object oagName) {
        soloistBusy.put(oagName.toString(), true);
    }

    @OPERATION
    void mark_free(Object oagName) {
        soloistBusy.put(oagName.toString(), false);
    }

    // Posição por step (perception.asl). Sem leitor vivo pós-#53 (era do find_free_soloist);
    // mantido p/ preservar o contrato e p/ reuso futuro (occupancy/contenção).
    @OPERATION
    void update_agent_pos(Object oagName, Object ox, Object oy) {
        agentPositions.put(oagName.toString(), new int[]{toInt(ox), toInt(oy)});
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }
}
