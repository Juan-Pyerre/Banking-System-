package gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

/** Painel com degradê da marca e ondas finas por cima (o mesmo desenho dos cartões e do app do JY Bank). */
class PainelGradiente extends JPanel {
    private final Color de;
    private final Color para;

    PainelGradiente(LayoutManager layout, Color de, Color para) {
        super(layout);
        this.de = de;
        this.para = para;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, de, w, h, para));
        g.fillRect(0, 0, w, h);

        g.setStroke(new BasicStroke(1.3f));
        for (int k = 0; k < 6; k++) {
            Path2D onda = new Path2D.Double();
            for (double x = 0; x <= w; x += 4) {
                double y = h * 0.58 + (k - 2.5) * h * 0.075
                        + Math.sin(x / (110.0 + k * 22) + k * 0.8) * h * 0.16
                        + Math.sin(x / 41.0 + k) * h * 0.02;
                if (x == 0) onda.moveTo(x, y); else onda.lineTo(x, y);
            }
            g.setColor(k % 2 == 0 ? new Color(151, 223, 252, 60) : new Color(133, 138, 227, 80));
            g.draw(onda);
        }
        g.dispose();
    }
}
