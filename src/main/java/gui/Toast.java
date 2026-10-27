package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

/** Aviso que aparece no canto da tela e some sozinho (usado pelas notificações do sistema). */
final class Toast {
    private static final List<JWindow> ATIVOS = new ArrayList<>();

    private Toast() { }

    /** Deve ser chamado na thread da interface. */
    static void mostrar(String titulo, String texto, Color cor) {
        if (GraphicsEnvironment.isHeadless()) return;
        JWindow janela = new JWindow();
        janela.setAlwaysOnTop(true);
        JPanel painel = new JPanel(new BorderLayout(0, 3));
        painel.setBackground(Ui.NOITE);
        painel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, cor), BorderFactory.createEmptyBorder(10, 14, 10, 16)));
        JLabel t = new JLabel(titulo);
        t.setForeground(cor.brighter());
        t.setFont(Ui.fonte(java.awt.Font.BOLD, 12));
        JLabel m = new JLabel("<html><div style='width:300px'>" + texto.replace("<", "&lt;") + "</div></html>");
        m.setForeground(Color.WHITE);
        m.setFont(Ui.fonte(java.awt.Font.PLAIN, 13));
        painel.add(t, BorderLayout.NORTH);
        painel.add(m, BorderLayout.CENTER);
        janela.setContentPane(painel);
        janela.pack();

        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int y = area.y + area.height - 16 - (janela.getHeight() + 8) * (ATIVOS.size() + 1);
        janela.setLocation(area.x + area.width - janela.getWidth() - 16, Math.max(area.y, y));
        ATIVOS.add(janela);
        janela.setVisible(true);

        Timer fechar = new Timer(6000, e -> {
            janela.dispose();
            ATIVOS.remove(janela);
        });
        fechar.setRepeats(false);
        fechar.start();
    }
}
