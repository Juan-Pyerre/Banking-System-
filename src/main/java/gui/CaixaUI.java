package gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;

/** Caixa de seleção plana: fundo branco, contorno lilás e setinha roxa. */
final class CaixaUI extends BasicComboBoxUI {

    @Override
    protected JButton createArrowButton() {
        JButton b = new JButton() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Ui.ROXO_PROFUNDO);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                g.drawLine(cx - 4, cy - 2, cx, cy + 2);
                g.drawLine(cx, cy + 2, cx + 4, cy - 2);
                g.dispose();
            }
        };
        b.setContentAreaFilled(false);
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(28, 28));
        return b;
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean temFoco) {
        g.setColor(Color.WHITE);
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        comboBox.setBorder(new CompoundBorder(new LineBorder(Ui.BORDA, 1, true), new EmptyBorder(3, 4, 3, 0)));
        comboBox.setBackground(Color.WHITE);
    }
}
