package gui;

import static org.junit.jupiter.api.Assertions.*;

import app.Aplicacao;
import domain.Conta;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.UUID;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.Test;
import persistence.ConexaoJdbc;

/** Garante que as telas são montadas sem erro (roda sem monitor, em modo headless). */
class TelasTest {

    private static JTabbedPane primeiraAba(Container c) {
        for (Component f : c.getComponents()) {
            if (f instanceof JTabbedPane t) return t;
            if (f instanceof Container k) {
                JTabbedPane r = primeiraAba(k);
                if (r != null) return r;
            }
        }
        return null;
    }

    @Test
    void janelaPrincipalETelaDaContaSaoMontadasComTodasAsAbas() {
        System.setProperty("java.awt.headless", "true");
        Aplicacao app = new Aplicacao(new ConexaoJdbc("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"), false, null);
        Conta conta = app.banco.abrirConta(app.banco.criarCliente("Teste").getId(), "CORRENTE", new BigDecimal("100.00"), "1234", "987654");

        JPanel principal = JanelaPrincipal.criarConteudo(app);
        assertEquals(4, primeiraAba(principal).getTabCount());

        PainelConta painel = new PainelConta(app, conta);
        assertEquals(5, primeiraAba(painel).getTabCount());
        painel.parar();
        app.agendador.encerrar();
    }
}
