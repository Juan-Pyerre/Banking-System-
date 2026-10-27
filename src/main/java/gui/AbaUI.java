package gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/** Abas planas no estilo da marca: texto cinza, aba ativa em roxo com um traço de destaque. */
final class AbaUI extends BasicTabbedPaneUI {

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(10, 20, 10, 20);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(4, 8, 0, 8);
        contentBorderInsets = new Insets(0, 0, 0, 0);
        tabPane.setOpaque(true); // sem isso o Swing não desenha o fundo/traço da aba
        tabPane.setBackground(Ui.FUNDO);
    }

    /** 1 px de borda junto das abas, onde a linha fina de separação é desenhada (fora da área do conteúdo). */
    @Override
    protected Insets getContentBorderInsets(int placement) {
        return placement == JTabbedPane.LEFT ? new Insets(0, 1, 0, 0) : new Insets(1, 0, 0, 0);
    }

    @Override
    protected void paintTabBackground(Graphics g0, int placement, int i, int x, int y, int w, int h, boolean selecionada) {
        if (!selecionada) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (placement == JTabbedPane.LEFT) {
            g.setColor(new Color(0xECE8FB));
            g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 10, 10);
            g.setColor(Ui.ROXO);
            g.fillRoundRect(x + 2, y + 8, 4, h - 16, 4, 4);
        } else {
            g.setColor(Ui.ROXO);
            g.fillRoundRect(x + 12, y + h - 3, w - 24, 3, 3, 3);
        }
        g.dispose();
    }

    @Override protected void paintTabBorder(Graphics g, int placement, int i, int x, int y, int w, int h, boolean selecionada) { }

    @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int i, Rectangle icon, Rectangle text, boolean selecionada) { }

    @Override
    protected void paintText(Graphics g, int placement, Font fonte, FontMetrics metricas, int i, String titulo,
                             Rectangle area, boolean selecionada) {
        g.setFont(fonte);
        g.setColor(selecionada ? Ui.ROXO_PROFUNDO : Ui.TEXTO_SUAVE);
        g.drawString(titulo, area.x, area.y + metricas.getAscent());
    }

    @Override protected int getTabLabelShiftX(int placement, int i, boolean selecionada) { return 0; }

    @Override protected int getTabLabelShiftY(int placement, int i, boolean selecionada) { return 0; }

    // só a linha fina junto das abas; o resto da borda do conteúdo some
    @Override protected void paintContentBorderTopEdge(Graphics g, int placement, int sel, int x, int y, int w, int h) {
        if (placement == JTabbedPane.TOP) linha(g, x, y, x + w, y);
    }

    @Override protected void paintContentBorderLeftEdge(Graphics g, int placement, int sel, int x, int y, int w, int h) {
        if (placement == JTabbedPane.LEFT) linha(g, x, y, x, y + h);
    }

    @Override protected void paintContentBorderBottomEdge(Graphics g, int placement, int sel, int x, int y, int w, int h) { }

    @Override protected void paintContentBorderRightEdge(Graphics g, int placement, int sel, int x, int y, int w, int h) { }

    private static void linha(Graphics g, int x1, int y1, int x2, int y2) {
        g.setColor(Ui.BORDA);
        g.drawLine(x1, y1, x2, y2);
    }

    static void instalar(JTabbedPane abas) { abas.setUI(new AbaUI()); }
}
