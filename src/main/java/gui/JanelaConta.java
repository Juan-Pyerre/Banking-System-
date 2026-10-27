package gui;

import app.Aplicacao;
import domain.Conta;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

/** Janela de uma conta. Pode-se abrir várias ao mesmo tempo (até da mesma conta) para ver a concorrência na prática. */
class JanelaConta extends JFrame {
    JanelaConta(Aplicacao app, Conta conta) {
        super("Conta #" + conta.getId() + " - " + Marca.NOME);
        setIconImages(Marca.icones());
        PainelConta painel = new PainelConta(app, conta);
        Ui.estilizar(painel);
        setContentPane(painel);
        setSize(1020, 700);
        setLocationByPlatform(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { painel.parar(); }
        });
    }
}
