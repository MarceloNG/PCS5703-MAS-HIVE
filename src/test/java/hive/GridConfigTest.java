package hive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fonte única das dimensões do grid (U4 — Track 3, Fase B). */
class GridConfigTest {

    @Test
    void dimensoesSaoPositivas() {
        assertTrue(GridConfig.width() > 0);
        assertTrue(GridConfig.height() > 0);
    }

    @Test
    void setAtualizaDimensoes() {
        int w0 = GridConfig.width();
        int h0 = GridConfig.height();
        try {
            GridConfig.set(70, 70);
            assertEquals(70, GridConfig.width());
            assertEquals(70, GridConfig.height());
        } finally {
            GridConfig.set(w0, h0); // restaura p/ não vazar entre testes
        }
    }

    @Test
    void setIgnoraValorNaoPositivo() {
        int w0 = GridConfig.width();
        GridConfig.set(0, -5);
        assertEquals(w0, GridConfig.width());
    }
}
