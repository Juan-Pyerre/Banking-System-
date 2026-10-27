package gui;

import app.Aplicacao;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Janela principal do sistema. */
public class JanelaPrincipal extends JFrame {
    public JanelaPrincipal(Aplicacao app) {
        super(Marca.NOME + " - " + Marca.SUBTITULO);
        setIconImages(Marca.icones());
        setContentPane(criarConteudo(app));
        setSize(1120, 760);
        setLocationByPlatform(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { app.agendador.encerrar(); }
        });
    }

    /** Separado do JFrame para poder ser montado e desenhado também sem tela (testes visuais). */
    public static JPanel criarConteudo(Aplicacao app) {
        JPanel raiz = new JPanel(new BorderLayout());

        PainelGradiente faixa = new PainelGradiente(new BorderLayout(16, 0), Ui.NOITE, Ui.ROXO_PROFUNDO);
        faixa.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
        JLabel logo = new JLabel(Marca.iconeEmTamanho(60));
        JLabel nome = new JLabel(Marca.NOME);
        nome.setForeground(Color.WHITE);
        nome.setFont(Ui.fonte(java.awt.Font.BOLD, 26));
        JLabel sub = new JLabel(Marca.SUBTITULO + "  ·  contas, saques, depósitos e transferências em tempo real");
        sub.setForeground(Ui.CIANO);
        sub.setFont(Ui.fonte(java.awt.Font.PLAIN, 13));
        JPanel textos = new JPanel(new BorderLayout(0, 2));
        textos.setOpaque(false);
        textos.add(nome, BorderLayout.CENTER);
        textos.add(sub, BorderLayout.SOUTH);
        faixa.add(logo, BorderLayout.WEST);
        faixa.add(textos, BorderLayout.CENTER);
        raiz.add(faixa, BorderLayout.NORTH);

        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Clientes e contas", new PainelClientes(app, conta -> new JanelaConta(app, conta).setVisible(true)));
        abas.addTab("Simulação concorrente", new PainelSimulacao(app));
        abas.addTab("Importar operações (CSV)", new PainelImportacao(app));
        abas.addTab("Poupança", new PainelPoupanca(app));
        raiz.add(abas, BorderLayout.CENTER);
        Ui.estilizar(raiz);
        return raiz;
    }
}
