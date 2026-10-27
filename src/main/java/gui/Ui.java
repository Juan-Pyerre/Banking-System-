package gui;

import domain.BancoException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import util.Dinheiro;

/** Tema JY Bank e pequenas ferramentas para montar as telas: botões, cartões, tabelas, diálogos e tarefas em segundo plano. */
final class Ui {
    // ---- paleta da marca
    static final Color NOITE = new Color(0x2C0735);
    static final Color ROXO_PROFUNDO = new Color(0x4E148C);
    static final Color ROXO = new Color(0x613DC1);
    static final Color LILAS = new Color(0x858AE3);
    static final Color CIANO = new Color(0x97DFFC);
    // ---- apoio
    static final Color FUNDO = new Color(0xF5F3FB);
    static final Color TEXTO = new Color(0x1E1B2E);
    static final Color TEXTO_SUAVE = new Color(0x6B6785);
    static final Color BORDA = new Color(0xDDD8F0);
    static final Color VERDE = new Color(0x0F8A5F);
    static final Color VERMELHO = new Color(0xD0314F);
    static final Color LARANJA = new Color(0xC97A00);
    // ---- nomes antigos (as telas ainda os usam)
    static final Color AZUL = ROXO_PROFUNDO;
    static final Color AZUL_CLARO = CIANO;
    static final Color CINZA = TEXTO_SUAVE;

    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private Ui() { }

    static Font fonte(int estilo, float tamanho) { return Marca.fonte(estilo, tamanho); }

    // ================================================================== tema

    static void instalarTema() {
        Marca.carregarFontes();
        UIManager.put("control", FUNDO);
        UIManager.put("nimbusBase", ROXO_PROFUNDO);
        UIManager.put("nimbusBlueGrey", new Color(0x9D98BF));
        UIManager.put("nimbusSelectionBackground", ROXO);
        UIManager.put("nimbusFocus", LILAS);
        UIManager.put("nimbusLightBackground", Color.WHITE);
        UIManager.put("nimbusOrange", ROXO);
        UIManager.put("text", TEXTO);
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignorada) {
            // segue com o visual padrão do Java
        }
        FontUIResource base = new FontUIResource(Marca.fonte(Font.PLAIN, 14));
        UIManager.put("defaultFont", base);
        UIManager.put("OptionPane.messageFont", base);
        UIManager.put("OptionPane.buttonFont", base);
        UIManager.put("ToolTip.font", base);
    }

    /** Aplica o estilo nos campos, abas e áreas de rolagem de uma tela inteira (chame depois de montar a tela). */
    static void estilizar(Component c) {
        if (c instanceof JTabbedPane abas) {
            AbaUI.instalar(abas);
            abas.setFont(fonte(Font.BOLD, 13));
        } else if (c instanceof JScrollPane rolagem) {
            rolagem.setBorder(new LineBorder(BORDA));
            rolagem.getViewport().setBackground(Color.WHITE);
            rolagem.getVerticalScrollBar().setUI(new BarraRolagemUI());
            rolagem.getHorizontalScrollBar().setUI(new BarraRolagemUI());
            rolagem.getVerticalScrollBar().setPreferredSize(new Dimension(12, 12));
            rolagem.getHorizontalScrollBar().setPreferredSize(new Dimension(12, 12));
        } else if (c instanceof JSpinner spinner) {
            spinner.setBorder(new LineBorder(BORDA, 1, true));
            if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
                editor.getTextField().setBorder(new EmptyBorder(5, 8, 5, 8));
                editor.getTextField().setBackground(Color.WHITE);
            }
            return;
        } else if (c instanceof JComboBox<?> caixa) {
            caixa.setUI(new CaixaUI());
            caixa.setRenderer(RENDERIZADOR_TIPO);
            caixa.setFont(fonte(Font.PLAIN, 14));
            return;
        } else if (c instanceof JTextField campo) {
            campo.setBorder(bordaCampo());
            campo.setBackground(Color.WHITE);
            campo.setCaretColor(ROXO);
        } else if (c instanceof JTextArea area) {
            area.setBorder(new EmptyBorder(8, 10, 8, 10));
        }
        if (c instanceof Container pai) {
            for (Component filho : pai.getComponents()) estilizar(filho);
        }
    }

    static CompoundBorder bordaCampo() {
        return new CompoundBorder(new LineBorder(BORDA, 1, true), new EmptyBorder(6, 9, 6, 9));
    }

    // ================================================================== componentes

    /** Botão arredondado da marca. Principal: roxo cheio. Secundário: branco com contorno roxo. */
    static final class BotaoJy extends JButton {
        private final boolean principal;

        BotaoJy(String texto, boolean principal) {
            super(texto);
            this.principal = principal;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setFont(fonte(Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 36, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            ButtonModel m = getModel();
            Color fundo, texto, borda = null;
            if (!isEnabled()) {
                fundo = principal ? new Color(0xD3CFE3) : Color.WHITE;
                texto = new Color(0x9A95B3);
                borda = principal ? null : new Color(0xDDD8F0);
            } else if (principal) {
                fundo = m.isPressed() ? ROXO_PROFUNDO : m.isRollover() ? new Color(0x7550D6) : ROXO;
                texto = Color.WHITE;
            } else {
                fundo = m.isPressed() ? new Color(0xE3DEF8) : m.isRollover() ? new Color(0xF1EEFC) : Color.WHITE;
                texto = ROXO_PROFUNDO;
                borda = ROXO;
            }
            g.setColor(fundo);
            g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            if (borda != null) {
                g.setColor(borda);
                g.setStroke(new java.awt.BasicStroke(1.4f));
                g.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            }
            if (isFocusOwner() && isEnabled()) {
                g.setColor(CIANO);
                g.setStroke(new java.awt.BasicStroke(2f));
                g.drawRoundRect(2, 2, w - 5, h - 5, 10, 10);
            }
            g.setFont(getFont());
            g.setColor(texto);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** Cartão branco arredondado, com sombra suave e título. */
    static final class Cartao extends JPanel {
        Cartao(String titulo, JComponent conteudo, JButton botao) {
            super(new BorderLayout(0, 10));
            setOpaque(false);
            setBorder(new EmptyBorder(14, 16, 18, 18));
            JLabel rotulo = new JLabel(titulo);
            rotulo.setFont(fonte(Font.BOLD, 15));
            rotulo.setForeground(ROXO_PROFUNDO);
            add(rotulo, BorderLayout.NORTH);

            JPanel centro = new JPanel(new BorderLayout(0, 8));
            centro.setOpaque(false);
            centro.add(conteudo, BorderLayout.NORTH);
            if (botao != null) {
                JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                rodape.setOpaque(false);
                rodape.add(botao);
                centro.add(rodape, BorderLayout.CENTER);
            }
            add(centro, BorderLayout.CENTER);
        }

        /** O cartão nunca é esticado na vertical além da altura de que precisa. */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(60, 30, 120, 14));
            g.fillRoundRect(2, 4, w - 5, h - 6, 20, 20);
            g.setColor(new Color(60, 30, 120, 10));
            g.fillRoundRect(1, 2, w - 3, h - 4, 20, 20);
            g.setColor(Color.WHITE);
            g.fillRoundRect(0, 0, w - 4, h - 6, 18, 18);
            g.setColor(BORDA);
            g.drawRoundRect(0, 0, w - 5, h - 7, 18, 18);
            g.dispose();
        }
    }

    static JButton botao(String texto, Runnable acao) { return criarBotao(texto, acao, true); }

    /** Botão de ação secundária (contorno roxo). */
    static JButton botaoSec(String texto, Runnable acao) { return criarBotao(texto, acao, false); }

    private static JButton criarBotao(String texto, Runnable acao, boolean principal) {
        JButton b = new BotaoJy(texto, principal);
        b.addActionListener(e -> {
            try {
                acao.run();
            } catch (RuntimeException ex) {
                erro((Component) e.getSource(), mensagem(ex));
            }
        });
        return b;
    }

    static JLabel rotuloTitulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(fonte(Font.BOLD, 20));
        l.setForeground(TEXTO);
        return l;
    }

    /** Formulário "rótulo: campo" em grade. Use: formulario("Valor", campo1, "Outro", campo2). */
    static JPanel formulario(Object... paresRotuloCampo) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        for (int i = 0; i + 1 < paresRotuloCampo.length; i += 2) {
            int linha = i / 2;
            GridBagConstraints r = new GridBagConstraints();
            r.gridx = 0;
            r.gridy = linha;
            r.anchor = GridBagConstraints.EAST;
            r.insets = new Insets(5, 0, 5, 10);
            JLabel rotulo = new JLabel(String.valueOf(paresRotuloCampo[i]));
            rotulo.setForeground(TEXTO_SUAVE);
            rotulo.setFont(fonte(Font.PLAIN, 13));
            p.add(rotulo, r);
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = linha;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(5, 0, 5, 0);
            p.add((JComponent) paresRotuloCampo[i + 1], c);
        }
        return p;
    }

    /** Linha de campos lado a lado: "Rótulo" campo "Rótulo" campo... */
    static JPanel formularioHorizontal(Object... paresRotuloCampo) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        for (int i = 0; i + 1 < paresRotuloCampo.length; i += 2) {
            JLabel rotulo = new JLabel(String.valueOf(paresRotuloCampo[i]));
            rotulo.setForeground(TEXTO_SUAVE);
            p.add(rotulo);
            p.add((JComponent) paresRotuloCampo[i + 1]);
        }
        return p;
    }

    static JPanel cartao(String titulo, JComponent conteudo, JButton botao) {
        return new Cartao(titulo, conteudo, botao);
    }

    /** Impede que um componente seja esticado na vertical além da altura que ele precisa. */
    static <T extends JComponent> T compacto(T componente) {
        componente.setMaximumSize(new Dimension(Integer.MAX_VALUE, componente.getPreferredSize().height));
        return componente;
    }

    static String nomeTipo(domain.TipoTransacao tipo) {
        return switch (tipo) {
            case DEPOSITO -> "Depósito";
            case SAQUE -> "Saque";
            case TRANSFERENCIA_ENVIADA -> "Transferência enviada";
            case TRANSFERENCIA_RECEBIDA -> "Transferência recebida";
            case TARIFA -> "Tarifa";
            case RENDIMENTO -> "Rendimento";
            case ESTORNO_CREDITO -> "Estorno (crédito)";
            case ESTORNO_DEBITO -> "Estorno (débito)";
        };
    }

    /** Mostra os tipos de transação com nomes legíveis dentro de uma caixa de seleção. */
    static final DefaultListCellRenderer RENDERIZADOR_TIPO = new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice, boolean sel, boolean foco) {
            Object texto = valor instanceof domain.TipoTransacao t ? nomeTipo(t) : valor;
            super.getListCellRendererComponent(lista, texto, indice, false, false);
            setOpaque(true);
            setBorder(new EmptyBorder(6, 10, 6, 10));
            setForeground(TEXTO);
            setBackground(sel && indice >= 0 ? new Color(0xE4DFF8) : Color.WHITE);
            return this;
        }
    };

    // ================================================================== tabelas

    /** Célula de tabela da marca: linhas zebradas, margem interna e seleção em lilás. */
    static class RenderizadorTabela extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabela, Object valor, boolean selecionada, boolean foco, int linha, int coluna) {
            super.getTableCellRendererComponent(tabela, valor, false, false, linha, coluna);
            setBorder(new EmptyBorder(0, 12, 0, 12));
            setHorizontalAlignment(SwingConstants.LEFT);
            setForeground(TEXTO);
            setBackground(selecionada ? new Color(0xE4DFF8) : linha % 2 == 0 ? Color.WHITE : new Color(0xFAF8FE));
            return this;
        }
    }

    static DefaultTableModel modelo(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int linha, int coluna) { return false; }
        };
    }

    static JTable tabela(DefaultTableModel modelo) {
        JTable t = new JTable(modelo);
        t.setRowHeight(36);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        RenderizadorTabela celula = new RenderizadorTabela();
        t.setDefaultRenderer(Object.class, celula);
        t.setDefaultRenderer(Integer.class, celula);
        t.setDefaultRenderer(String.class, celula);

        JTableHeader cabecalho = t.getTableHeader();
        cabecalho.setReorderingAllowed(false);
        cabecalho.setPreferredSize(new Dimension(0, 38));
        cabecalho.setDefaultRenderer((tabela, valor, sel, foco, linha, coluna) -> {
            JLabel l = new JLabel(String.valueOf(valor));
            l.setOpaque(true);
            l.setBackground(ROXO_PROFUNDO);
            l.setForeground(Color.WHITE);
            l.setFont(fonte(Font.BOLD, 13));
            l.setBorder(new EmptyBorder(0, 12, 0, 12));
            return l;
        });
        return t;
    }

    static JLabel mensagem() {
        JLabel l = new JLabel(" ");
        l.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        l.setHorizontalAlignment(SwingConstants.LEFT);
        l.setFont(fonte(Font.BOLD, 13));
        return l;
    }

    static void definirMensagem(JLabel rotulo, String texto, boolean sucesso) {
        rotulo.setForeground(sucesso ? VERDE : VERMELHO);
        rotulo.setText(texto);
    }

    // ================================================================== leitura de campos

    static BigDecimal lerValor(JTextField campo, String nome) {
        try {
            return Dinheiro.ler(campo.getText());
        } catch (RuntimeException e) {
            throw new BancoException.OperacaoInvalida("Informe um valor válido em \"" + nome + "\" (exemplo: 150,00).");
        }
    }

    static BigDecimal lerValorOpcional(JTextField campo, String nome) {
        return campo.getText().isBlank() ? null : lerValor(campo, nome);
    }

    static LocalDate lerDataOpcional(JTextField campo, String nome) {
        if (campo.getText().isBlank()) return null;
        try {
            return LocalDate.parse(campo.getText().trim(), DATA);
        } catch (DateTimeParseException e) {
            throw new BancoException.OperacaoInvalida("Data inválida em \"" + nome + "\". Use dd/mm/aaaa.");
        }
    }

    static int lerInteiro(JTextField campo, String nome) {
        try {
            return Integer.parseInt(campo.getText().trim());
        } catch (NumberFormatException e) {
            throw new BancoException.OperacaoInvalida("Informe um número válido em \"" + nome + "\".");
        }
    }

    // ================================================================== diálogos

    static void erro(Component pai, String texto) {
        JOptionPane.showMessageDialog(pai, texto, Marca.NOME, JOptionPane.WARNING_MESSAGE);
    }

    static void info(Component pai, String texto) {
        JOptionPane.showMessageDialog(pai, texto, Marca.NOME, JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirmar(Component pai, String texto) {
        return JOptionPane.showConfirmDialog(pai, texto, Marca.NOME, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    /** Pede uma senha (campo escondido). Devolve null se o usuário cancelar. A senha é pedida ANTES de a operação começar. */
    static String pedirSenha(Component pai, String texto) {
        JPasswordField campo = new JPasswordField(14);
        campo.setBorder(bordaCampo());
        int r = JOptionPane.showConfirmDialog(pai, new Object[] {texto, campo}, Marca.NOME + " - Autenticação",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? new String(campo.getPassword()) : null;
    }

    static String mensagem(Throwable t) {
        if ((t instanceof BancoException || t instanceof IllegalArgumentException) && t.getMessage() != null) {
            return t.getMessage();
        }
        return "Erro inesperado: " + t;
    }

    // ================================================================== tarefas em segundo plano (a tela nunca trava)

    static <T> void executar(Component pai, Callable<T> tarefa, Consumer<T> aoConcluir) {
        executar(pai, tarefa, aoConcluir, causa -> erro(pai, mensagem(causa)));
    }

    static <T> void executar(Component pai, Callable<T> tarefa, Consumer<T> aoConcluir, Consumer<Throwable> aoFalhar) {
        Window janela = pai == null ? null : SwingUtilities.getWindowAncestor(pai);
        if (janela != null) janela.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return tarefa.call();
            }

            @Override
            protected void done() {
                if (janela != null) janela.setCursor(Cursor.getDefaultCursor());
                T resultado;
                try {
                    resultado = get();
                } catch (ExecutionException e) {
                    aoFalhar.accept(e.getCause());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                aoConcluir.accept(resultado);
            }
        }.execute();
    }

    static void rodar(Component pai, Runnable tarefa, Runnable depois) {
        executar(pai, () -> { tarefa.run(); return Boolean.TRUE; }, r -> depois.run());
    }
}
