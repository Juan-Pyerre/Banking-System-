package gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Barra de rolagem fina, com o cursor arredondado em lilás. */
final class BarraRolagemUI extends BasicScrollBarUI {

    @Override protected JButton createDecreaseButton(int orientacao) { return semBotao(); }

    @Override protected JButton createIncreaseButton(int orientacao) { return semBotao(); }

    private static JButton semBotao() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        g.setColor(new Color(0xF2EFFA));
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    @Override
    protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(isThumbRollover() || isDragging ? new Color(0xA79EDB) : new Color(0xC9C2E8));
        g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 10, 10);
        g.dispose();
    }
}
