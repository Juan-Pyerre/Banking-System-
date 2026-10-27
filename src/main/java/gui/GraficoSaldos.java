package gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import simulacao.RelatorioDesempenho;
import simulacao.RelatorioDesempenho.LinhaConta;
import util.Dinheiro;

/** Gráfico de barras: para cada conta, o saldo ESPERADO (azul) ao lado do saldo OBTIDO (verde se bateu, vermelho se divergiu). */
class GraficoSaldos extends JPanel {
    private final String tituloPadrao;
    private RelatorioDesempenho relatorio;

    GraficoSaldos(String tituloPadrao) {
        this.tituloPadrao = tituloPadrao;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Ui.BORDA));
    }

    void mostrar(RelatorioDesempenho r) {
        this.relatorio = r;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        g.setFont(Ui.fonte(Font.BOLD, 14));
        g.setColor(Ui.AZUL);
        g.drawString(relatorio == null ? tituloPadrao : relatorio.titulo(), 12, 22);

        if (relatorio == null) {
            g.setFont(Ui.fonte(Font.PLAIN, 13));
            g.setColor(Ui.CINZA);
            g.drawString("Rode a simulação para ver o resultado aqui.", 12, h / 2);
            g.dispose();
            return;
        }

        int topo = 90, base = h - 42, esquerda = 14, direita = w - 14;
        BigDecimal maior = BigDecimal.ONE;
        for (LinhaConta l : relatorio.linhas()) maior = maior.max(l.esperado().abs()).max(l.obtido().abs());
        int n = relatorio.linhas().size();
        int slot = (direita - esquerda) / Math.max(1, n);
        int largBarra = Math.max(6, Math.min(34, slot / 3));

        g.setFont(Ui.fonte(Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < n; i++) {
            LinhaConta l = relatorio.linhas().get(i);
            int x0 = esquerda + i * slot + (slot - 2 * largBarra - 4) / 2;
            int alturaEsperada = escala(l.esperado(), maior, base - topo);
            int alturaObtida = escala(l.obtido(), maior, base - topo);
            g.setColor(Ui.CIANO);
            g.fillRect(x0, base - alturaEsperada, largBarra, alturaEsperada);
            g.setColor(l.confere() ? Ui.VERDE : Ui.VERMELHO);
            g.fillRect(x0 + largBarra + 4, base - alturaObtida, largBarra, alturaObtida);

            g.setColor(Color.DARK_GRAY);
            String rotulo = "Conta " + l.contaId();
            g.drawString(rotulo, x0 + largBarra + 2 - fm.stringWidth(rotulo) / 2, base + 15);
            String valor = compacto(l.obtido());
            g.drawString(valor, x0 + largBarra + 4 + (largBarra - fm.stringWidth(valor)) / 2, base - alturaObtida - 4);
        }
        g.setColor(Color.GRAY);
        g.drawLine(esquerda, base, direita, base);

        g.setFont(Ui.fonte(Font.BOLD, 13));
        boolean ok = relatorio.consistente();
        g.setColor(ok ? Ui.VERDE : Ui.VERMELHO);
        g.drawString(ok ? "Saldos corretos: nenhuma operação perdida" : "Saldos corrompidos pela race condition", 12, 42);
        g.setFont(Ui.fonte(Font.PLAIN, 11));
        g.setColor(Ui.CINZA);
        g.drawString(String.format("%d ops em %d ms (%.0f ops/s)  ·  esperado (ciano)  ×  obtido (%s)", relatorio.totalOperacoes(),
                relatorio.tempoMs(), relatorio.operacoesPorSegundo(), ok ? "verde" : "vermelho"), 12, 58);
        g.dispose();
    }

    private static int escala(BigDecimal valor, BigDecimal maior, int alturaMaxima) {
        if (valor.signum() <= 0) return 0;
        return valor.multiply(BigDecimal.valueOf(alturaMaxima)).divide(maior, 0, java.math.RoundingMode.HALF_UP).intValue();
    }

    private static String compacto(BigDecimal v) {
        return v.abs().compareTo(BigDecimal.valueOf(1000)) >= 0
                ? v.divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.HALF_UP) + " mil" : Dinheiro.numero(v);
    }
}
