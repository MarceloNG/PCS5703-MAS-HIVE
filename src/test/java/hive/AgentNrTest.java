package hive;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgentNrTest {

    @ParameterizedTest
    @CsvSource({
        "connectionA1,  1",
        "connectionA9,  9",
        "connectionA10, 10",
        "connectionA15, 15"
    })
    void extractNr_parses_suffix(String name, int expected) {
        assertEquals(expected, AgentNr.extractNr(name));
    }

    @Test
    void stagger_distributes_15_agents_across_10_slots() {
        // Nenhum slot deve ter mais de 2 agentes (15 = 5*2 + 5*1).
        // Verifica que o escalonamento (N mod 10) == (AgNr mod 10)
        // não acumula todos os agentes no mesmo slot.
        int[] slotCount = new int[10];
        for (int i = 1; i <= 15; i++) {
            int nr = AgentNr.extractNr("connectionA" + i);
            slotCount[nr % 10]++;
        }
        int doubleFilled = 0;
        for (int slot = 0; slot < 10; slot++) {
            assertTrue(slotCount[slot] <= 2, "slot " + slot + " tem " + slotCount[slot] + " agentes");
            if (slotCount[slot] == 2) doubleFilled++;
        }
        assertEquals(5, doubleFilled, "exatamente 5 slots devem ter 2 agentes (15 = 5*2 + 5*1)");
    }
}
