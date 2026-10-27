package gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/** Desenha uma barra de progresso dentro de uma célula da tabela (valor inteiro de 0 a 100 ou mais). */
final class BarraRenderer extends JComponent implements TableCellRenderer {
    private final boolean altoEhRuim; // limites de gasto: quanto mais cheio, pior; objetivos: quanto mais cheio, melhor
    private int valor;

    BarraRenderer(boolean altoEhRuim) { this.altoEhRuim = altoEhRuim; }

    @Override
    public Component getTableCellRendererComponent(JTable tabela, Object v, boolean selecionada, boolean foco, int linha, int coluna) {
        valor = v instanceof Integer i ? i : 0;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int largura = getWidth() - 12, altura = getHeight() - 10;
        g.setColor(new Color(0xE9E5F6));
        g.fillRoundRect(6, 5, largura, altura, 8, 8);
        Color cor = altoEhRuim ? (valor >= 100 ? Ui.VERMELHO : valor >= 80 ? Ui.LARANJA : Ui.VERDE)
                               : (valor >= 100 ? Ui.VERDE : Ui.ROXO);
        g.setColor(cor);
        g.fillRoundRect(6, 5, Math.max(0, Math.min(largura, largura * valor / 100)), altura, 8, 8);
        String texto = valor + "%";
        int larguraTexto = g.getFontMetrics().stringWidth(texto);
        int baseTexto = 5 + (altura + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
        if (valor >= 60) { // barra larga: porcentagem em branco dentro dela
            g.setColor(Color.WHITE);
            g.drawString(texto, 6 + largura - larguraTexto - 8, baseTexto);
        } else {           // barra curta: porcentagem em cinza, na parte vazia
            g.setColor(Color.DARK_GRAY);
            g.drawString(texto, 6 + largura - larguraTexto - 8, baseTexto);
        }
        g.dispose();
    }
}
