package gui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** Identidade visual do JY Bank: fonte e imagens (logo e ícones) embutidas no programa. */
final class Marca {
    static final String NOME = "JY Bank";
    static final String SUBTITULO = "Sistema Bancário";

    private static boolean fontesCarregadas;
    private static String familia = Font.SANS_SERIF;

    private Marca() { }

    /** Registra a Roboto (embutida no .jar). Se algo falhar, o sistema segue com a fonte padrão do Java. */
    static synchronized void carregarFontes() {
        if (fontesCarregadas) return;
        fontesCarregadas = true;
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            for (String arquivo : new String[] {"/fontes/Roboto-Regular.ttf", "/fontes/Roboto-Bold.ttf"}) {
                try (InputStream in = Marca.class.getResourceAsStream(arquivo)) {
                    if (in == null) return;
                    Font f = Font.createFont(Font.TRUETYPE_FONT, in);
                    ge.registerFont(f);
                    familia = f.getFamily();
                }
            }
        } catch (Exception e) {
            familia = Font.SANS_SERIF;
        }
    }

    static Font fonte(int estilo, float tamanho) {
        return new Font(familia, estilo, 14).deriveFont(estilo, tamanho);
    }

    static Image icone(int tamanho) {
        try (InputStream in = Marca.class.getResourceAsStream("/imagens/icone-" + tamanho + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Ícones da janela (a barra de tarefas escolhe o tamanho certo). */
    static List<Image> icones() {
        List<Image> lista = new ArrayList<>();
        for (int t : new int[] {16, 32, 48, 64, 128, 256}) {
            Image i = icone(t);
            if (i != null) lista.add(i);
        }
        return lista;
    }

    /** Ícone do app redimensionado com suavização, para o cabeçalho. */
    static ImageIcon iconeEmTamanho(int tamanho) {
        Image base = icone(128);
        return base == null ? null : new ImageIcon(base.getScaledInstance(tamanho, tamanho, Image.SCALE_SMOOTH));
    }
}
