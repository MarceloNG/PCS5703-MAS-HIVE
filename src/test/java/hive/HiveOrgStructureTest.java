package hive;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Validador pseudo-unit do {@code src/org/hive_org.xml} (issue #37, frente:org Stance A, espinha #36).
 *
 * <p>Faz parse ESTÁTICO do XML do MOISE+ (sem rodar a simulação) e asserta três invariantes
 * estruturais. É o par <b>test-first</b> da issue #38 (achatar a estrutura):
 *
 * <ul>
 *   <li><b>R1 — cardinalidade ≥ 20:</b> nasce <b>VERMELHO hoje</b> (soma dos {@code max} = 19 &lt; 20).
 *       A #38 achata o time e torna verde. NÃO editar o {@code hive_org.xml} para passar aqui.</li>
 *   <li><b>R2 — todo role compromete {@code m_adopt}:</b> verde hoje (guarda "role excluído do score").</li>
 *   <li><b>R3 — integridade referencial:</b> verde hoje (todo role citado em link/norma existe).</li>
 * </ul>
 *
 * <p>O XML é recurso do classpath ({@code src/org} é {@code resources.srcDir} de {@code main}),
 * carregado por {@code getResourceAsStream("/hive_org.xml")} — sem depender do working-dir.
 */
class HiveOrgStructureTest {

    /** Carrega e parseia o hive_org.xml (classpath-first; fallback p/ arquivo do projeto). */
    private static Document parseOrg() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // Não-namespace-aware (default): as tags do XML não usam prefixo, então
        // getElementsByTagName("role"/"norm"/...) casa pelo nome como aparece.
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputStream is = HiveOrgStructureTest.class.getResourceAsStream("/hive_org.xml");
        if (is == null) {
            File f = new File("src/org/hive_org.xml");
            assertTrue(f.exists(),
                "hive_org.xml não encontrado no classpath nem em src/org/hive_org.xml");
            return db.parse(f);
        }
        try (is) {
            return db.parse(is);
        }
    }

    /** group-specification com o id pedido (falha o teste se ausente). */
    private static Element findGroup(Document doc, String id) {
        NodeList groups = doc.getElementsByTagName("group-specification");
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if (id.equals(g.getAttribute("id"))) {
                return g;
            }
        }
        fail("group-specification id=" + id + " não encontrado no hive_org.xml");
        return null; // inalcançável
    }

    /** Ids dos roles declarados em &lt;role-definitions&gt;. */
    private static Set<String> declaredRoles(Document doc) {
        Element roleDefs = (Element) doc.getElementsByTagName("role-definitions").item(0);
        Set<String> roles = new TreeSet<>();
        NodeList rs = roleDefs.getElementsByTagName("role");
        for (int i = 0; i < rs.getLength(); i++) {
            roles.add(((Element) rs.item(i)).getAttribute("id"));
        }
        return roles;
    }

    /**
     * R1 — a soma dos {@code max} dos roles do grupo {@code hive_team} cabe os 20 agentes do Sim1.
     * VERMELHO HOJE por design (squad_leader 4 + collector 8 + assembler 4 + sentinel 3 = 19 &lt; 20).
     */
    @Test
    void cardinalidadeDoTimeCabeOSim1() throws Exception {
        Document doc = parseOrg();
        Element team = findGroup(doc, "hive_team");
        Element rolesEl = (Element) team.getElementsByTagName("roles").item(0);
        NodeList roles = rolesEl.getElementsByTagName("role");

        int sumMax = 0;
        for (int i = 0; i < roles.getLength(); i++) {
            sumMax += Integer.parseInt(((Element) roles.item(i)).getAttribute("max"));
        }

        assertTrue(sumMax >= 20,
            "soma dos max dos roles de hive_team = " + sumMax
                + ", esperado >= 20 (o Sim1 do contest tem 20 agentes)");
    }

    /**
     * R2 — todo role declarado compromete a mission {@code m_adopt} via alguma norma de obrigação,
     * i.e., nenhum role fica impedido de adotar o {@code worker} MAPC (gate de score).
     */
    @Test
    void todoRoleComprometeAdoptDoWorker() throws Exception {
        Document doc = parseOrg();
        Set<String> declarados = declaredRoles(doc);

        Set<String> comAdopt = new TreeSet<>();
        NodeList norms = doc.getElementsByTagName("norm");
        for (int i = 0; i < norms.getLength(); i++) {
            Element norm = (Element) norms.item(i);
            if ("m_adopt".equals(norm.getAttribute("mission"))) {
                comAdopt.add(norm.getAttribute("role"));
            }
        }

        Set<String> faltantes = new TreeSet<>(declarados);
        faltantes.removeAll(comAdopt);
        assertTrue(faltantes.isEmpty(),
            "roles que NÃO comprometem m_adopt (nunca adotam worker → não pontuam): " + faltantes);
    }

    /**
     * R3 — integridade referencial: o XML é bem-formado (o parse não lança) e todo role citado em
     * &lt;link from/to&gt; e em &lt;norm role=...&gt; existe em &lt;role-definitions&gt;.
     */
    @Test
    void integridadeReferencialDeRoles() throws Exception {
        Document doc = parseOrg();
        Set<String> declarados = declaredRoles(doc);

        Set<String> referenciados = new TreeSet<>();
        NodeList links = doc.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            referenciados.add(link.getAttribute("from"));
            referenciados.add(link.getAttribute("to"));
        }
        NodeList norms = doc.getElementsByTagName("norm");
        for (int i = 0; i < norms.getLength(); i++) {
            referenciados.add(((Element) norms.item(i)).getAttribute("role"));
        }

        referenciados.removeAll(declarados);
        assertTrue(referenciados.isEmpty(),
            "roles referenciados em link/norma mas não declarados em role-definitions: " + referenciados);
    }
}
