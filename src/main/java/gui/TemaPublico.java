package gui;

/** Ponte pública para instalar o visual (a classe Ui é interna ao pacote). */
public final class TemaPublico {
    private TemaPublico() { }

    public static void instalar() { Ui.instalarTema(); }
}
