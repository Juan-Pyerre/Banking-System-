package gui;

import app.Aplicacao;
import config.Configuracao;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

/** Rendimento da poupança: aplicar na hora ou deixar um agendador (outra thread) aplicar periodicamente. */
class PainelPoupanca extends JPanel {
    private final Aplicacao app;
    private final JSpinner periodo = new JSpinner(new SpinnerNumberModel(30, 1, 3600, 1));
    private final JLabel situacao = new JLabel();
    private final JLabel mensagem = Ui.mensagem();

    PainelPoupanca(Aplicacao app) {
        super(new BorderLayout(0, 12));
        this.app = app;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));

        String taxa = Configuracao.RENDIMENTO_POUPANCA_PERIODO.multiply(java.math.BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
        JLabel explicacao = new JLabel("<html>Cada aplicação rende <b>" + taxa + "%</b> sobre o saldo atual de todas as contas poupança ativas. "
                + "O agendador roda em outra thread e concorre com depósitos, saques e transferências: o saldo continua sempre exato.</html>");
        explicacao.setForeground(Ui.CINZA);

        JPanel agora = Ui.cartao("Aplicar agora", new JLabel("Aplica o rendimento uma vez, imediatamente."),
                Ui.botao("Aplicar rendimento agora", this::aplicarAgora));
        JPanel automatico = Ui.cartao("Rendimento automático", Ui.formulario("A cada (segundos)", periodo, "Situação", situacao), null);
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botoes.add(Ui.botao("Ligar", this::ligar));
        botoes.add(Ui.botaoSec("Desligar", () -> {
            app.agendador.pararRendimento();
            atualizarSituacao();
        }));
        automatico.add(botoes, BorderLayout.SOUTH);

        JPanel centro = new JPanel(new java.awt.GridLayout(2, 1, 0, 12));
        centro.add(agora);
        centro.add(automatico);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.add(explicacao, BorderLayout.NORTH);
        p.add(centro, BorderLayout.CENTER);
        add(p, BorderLayout.NORTH);
        add(mensagem, BorderLayout.SOUTH);

        atualizarSituacao();
        new Timer(1000, e -> atualizarSituacao()).start();
    }

    private void aplicarAgora() {
        Ui.executar(this, () -> app.banco.aplicarRendimentoEmTodas(Configuracao.RENDIMENTO_POUPANCA_PERIODO),
                n -> Ui.definirMensagem(mensagem, n + " conta(s) poupança receberam rendimento.", true));
    }

    private void ligar() {
        app.agendador.iniciarRendimento((Integer) periodo.getValue(), Configuracao.RENDIMENTO_POUPANCA_PERIODO);
        Ui.definirMensagem(mensagem, "Rendimento automático ligado (outra thread, a cada " + periodo.getValue() + " s).", true);
        atualizarSituacao();
    }

    private void atualizarSituacao() {
        situacao.setText(app.agendador.rendimentoAtivo()
                ? "LIGADO — " + app.agendador.ciclosDeRendimento() + " aplicação(ões) até agora" : "desligado");
    }
}
