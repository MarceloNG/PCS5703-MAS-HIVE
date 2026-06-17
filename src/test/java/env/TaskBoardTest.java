package env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mecânica do leilão do TaskBoard (backfill Track 1): maior lance vence.
 * Nota: o viés single-block NÃO está aqui — a fórmula enviesada vive no
 * squad_leader.asl; caracterizá-la exige extrair o scoring p/ Java (prep do Track 2).
 */
class TaskBoardTest {

    private List<TaskBoard.Bid> bids(Object... pares) {
        List<TaskBoard.Bid> list = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            list.add(new TaskBoard.Bid((String) pares[i], ((Number) pares[i + 1]).doubleValue()));
        }
        return list;
    }

    @Test
    void maiorLanceVence() {
        TaskBoard.Bid best = TaskBoard.bestBid(bids("sqA", 10.0, "sqB", 25.0, "sqC", 5.0));
        assertEquals("sqB", best.squadId);
    }

    @Test
    void listaVaziaOuNula_null() {
        assertNull(TaskBoard.bestBid(new ArrayList<>()));
        assertNull(TaskBoard.bestBid(null));
    }

    @Test
    void empate_escolheLanceDeMesmoValor() {
        TaskBoard.Bid best = TaskBoard.bestBid(bids("sqA", 10.0, "sqB", 10.0));
        assertEquals(10.0, best.value, 1e-9);
    }
}
