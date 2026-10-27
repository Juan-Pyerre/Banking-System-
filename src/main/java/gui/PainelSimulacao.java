package gui;

import app.Aplicacao;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import simulacao.DemonstracaoDeadlock;
import simulacao.RelatorioDesempenho;
import simulacao.SimulacaoConcorrente.Parametros;

/** Simulação concorrente: expõe a race condition, mostra a correção e demonstra o deadlock — tudo com gráfico. */
class PainelSimulacao extends JPanel {
    private final Aplicacao app;
    private final JSpinner contas = new JSpinner(new SpinnerNumberModel(4, 2, 12, 1));
    private final JSpinner threads = new JSpinner(new SpinnerNumberModel(8, 1, 128, 1));
    private final JSpinner operacoes = new JSpinner(new SpinnerNumberModel(20_000, 1, 1_000_000, 1_000));
    private final GraficoSaldos graficoAntes = new GraficoSaldos("SEM sincronização (race condition)");
    private final GraficoSaldos graficoDepois = new GraficoSaldos("COM sincronização (synchronized)");
    private final JTextArea saida = new JTextArea();
    private final JProgressBar progresso = new JProgressBar();
    private final List<JButton> botoes = new ArrayList<>();
    private final JLabel mensagem = Ui.mensagem();

    PainelSimulacao(Aplicacao app) {
        super(new BorderLayout(0, 8));
        this.app = app;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));

        JPanel topo = new JPanel(new BorderLayout(0, 6));
        topo.add(Ui.formularioHorizontal("Contas", contas, "Threads", threads, "Operações por thread", operacoes), BorderLayout.NORTH);
        JPanel botoesPainel = new JPanel(new GridLayout(1, 5, 6, 0));
        botoesPainel.add(registrar(Ui.botao("Sem sincronização", () -> simular(false, false))));
        botoesPainel.add(registrar(Ui.botao("Com sincronização", () -> simular(true, false))));
        botoesPainel.add(registrar(Ui.botao("Com sincronização + banco", () -> simular(true, true))));
        botoesPainel.add(registrar(Ui.botao("Comparar antes × depois", this::comparar)));
        botoesPainel.add(registrar(Ui.botao("Demonstrar deadlock", this::deadlock)));
        topo.add(botoesPainel, BorderLayout.CENTER);
        progresso.setIndeterminate(false);
        progresso.setPreferredSize(new java.awt.Dimension(0, 8));
        progresso.setBorderPainted(false);
        topo.add(progresso, BorderLayout.SOUTH);
        add(topo, BorderLayout.NORTH);

        JPanel graficos = new JPanel(new GridLayout(1, 2, 10, 0));
        graficos.add(graficoAntes);
        graficos.add(graficoDepois);
        saida.setEditable(false);
        saida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        saida.setText("Escolha uma simulação acima.\n\n"
                + "• Sem sincronização: várias threads mexem no mesmo saldo ao mesmo tempo e valores se perdem (race condition).\n"
                + "• Com sincronização: a Conta real (synchronized) garante que o saldo final bata exatamente.\n"
                + "• Com sincronização + banco: cada operação também é gravada via JDBC (use menos operações; é bem mais lento).\n"
                + "• Demonstrar deadlock: duas transferências em sentidos opostos travam uma à outra; depois a versão corrigida.\n");
        JScrollPane rolagem = new JScrollPane(saida);
        rolagem.setPreferredSize(new java.awt.Dimension(100, 170));
        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(graficos, BorderLayout.CENTER);
        centro.add(rolagem, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);
        add(mensagem, BorderLayout.SOUTH);
    }

    private JButton registrar(JButton b) {
        botoes.add(b);
        return b;
    }

    private void ocupado(boolean sim) {
        botoes.forEach(b -> b.setEnabled(!sim));
        progresso.setIndeterminate(sim);
    }

    private Parametros parametros(boolean sincronizada, boolean persistir) {
        return new Parametros((Integer) contas.getValue(), (Integer) threads.getValue(), (Integer) operacoes.getValue(), sincronizada, persistir);
    }

    private void simular(boolean sincronizada, boolean persistir) {
        Parametros p = parametros(sincronizada, persistir);
        if (persistir && (long) p.threads() * p.operacoesPorThread() > 5_000
                && !Ui.confirmar(this, "Gravar " + (long) p.threads() * p.operacoesPorThread()
                + " operações no banco pode demorar bastante. Continuar?\n(Dica: reduza as operações por thread.)")) return;
        ocupado(true);
        Ui.executar(this, () -> app.simulacao.executar(p), r -> {
            ocupado(false);
            (sincronizada ? graficoDepois : graficoAntes).mostrar(r);
            saida.setText(r.formatar());
            saida.setCaretPosition(0);
            Ui.definirMensagem(mensagem, r.consistente() ? "Saldos corretos." : "Os saldos divergiram do esperado (race condition).", r.consistente());
        }, causa -> {
            ocupado(false);
            Ui.erro(this, Ui.mensagem(causa));
        });
    }

    private void comparar() {
        Parametros antes = parametros(false, false);
        Parametros depois = parametros(true, false);
        ocupado(true);
        Ui.executar(this, () -> {
            app.simulacao.executar(antes); // aquecimento da JVM, para o tempo ser justo
            return new RelatorioDesempenho[] {app.simulacao.executar(antes), app.simulacao.executar(depois)};
        }, r -> {
            ocupado(false);
            graficoAntes.mostrar(r[0]);
            graficoDepois.mostrar(r[1]);
            saida.setText("ANTES\n" + r[0].formatar() + "\nDEPOIS\n" + r[1].formatar());
            saida.setCaretPosition(0);
            Ui.definirMensagem(mensagem, "Mesma carga nas duas versões: só a sincronização muda o resultado.", r[1].consistente() && !r[0].consistente());
        }, causa -> {
            ocupado(false);
            Ui.erro(this, Ui.mensagem(causa));
        });
    }

    private void deadlock() {
        ocupado(true);
        saida.setText("Rodando a demonstração (leva alguns segundos)…\n");
        Ui.executar(this, () -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                DemonstracaoDeadlock.demonstrarDeadlock(out);
                out.println();
                DemonstracaoDeadlock.demonstrarCorrecao(out);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }, texto -> {
            ocupado(false);
            saida.setText(texto);
            saida.setCaretPosition(0);
            Ui.definirMensagem(mensagem, "Deadlock detectado na versão errada; versão corrigida concluiu sem travar.", true);
        }, causa -> {
            ocupado(false);
            Ui.erro(this, Ui.mensagem(causa));
        });
    }
}
