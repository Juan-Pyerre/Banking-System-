package gui;

import app.Aplicacao;
import arquivo.ExportadorExtrato;
import arquivo.ExportadorExtrato.Formato;
import config.Configuracao;
import domain.BancoException;
import domain.Conta;
import domain.FiltroExtrato;
import domain.TipoTransacao;
import domain.Transacao;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import notificacao.Notificacao;
import planejamento.LembretePagamento;
import planejamento.Objetivo;
import planejamento.PlanejamentoService.LinhaGasto;
import servico.AgendadorDeTarefas.TransferenciaAgendada;
import util.Dinheiro;

/** Tudo o que se faz dentro de uma conta: operações, extrato, estorno, exportação, agendamentos, planejamento e avisos. */
class PainelConta extends JPanel {
    private final Aplicacao app;
    private final Conta conta;
    private final Timer relogio;
    private final JLabel mensagem = Ui.mensagem();

    // cabeçalho
    private final JLabel titulo = new JLabel();
    private final JLabel saldo = new JLabel();
    private final JLabel detalhes = new JLabel();

    // operações
    private final JTextField depositoValor = new JTextField(10);
    private final JTextField saqueValor = new JTextField(10);
    private final JTextField transfDestino = new JTextField(10);
    private final JTextField transfValor = new JTextField(10);

    // extrato
    private final JTextField filtroDe = new JTextField(8);
    private final JTextField filtroAte = new JTextField(8);
    private final JComboBox<Object> filtroTipo = new JComboBox<>();
    private final JTextField filtroMin = new JTextField(7);
    private final JTextField filtroMax = new JTextField(7);
    private final DefaultTableModel modeloExtrato = Ui.modelo("Data/Hora", "Tipo", "Valor", "Descrição", "Protocolo");
    private final JTable tabelaExtrato = Ui.tabela(modeloExtrato);
    private final JLabel totaisExtrato = new JLabel(" ");
    private final JComboBox<Formato> formatoExportacao = new JComboBox<>(Formato.values());
    private volatile FiltroExtrato filtroAtual = FiltroExtrato.SEM_FILTRO;
    private volatile List<Transacao> ultimoExtrato = List.of();

    // agendamentos
    private final JTextField agendaDestino = new JTextField(10);
    private final JTextField agendaValor = new JTextField(10);
    private final JSpinner agendaQuando = new JSpinner(new SpinnerDateModel(new Date(System.currentTimeMillis() + 30_000), null, null, Calendar.SECOND));
    private final DefaultTableModel modeloAgenda = Ui.modelo("Quando", "Destino", "Valor", "Situação", "Detalhe");
    private String assinaturaAgenda = "";

    // planejamento
    private final JTextField lembreteDescricao = new JTextField(12);
    private final JTextField lembreteValor = new JTextField(8);
    private final JSpinner lembreteDia = new JSpinner(new SpinnerNumberModel(10, 1, 31, 1));
    private final DefaultTableModel modeloLembretes = Ui.modelo("Descrição", "Valor", "Dia do mês", "Próximo vencimento");
    private final JComboBox<TipoTransacao> limiteCategoria = new JComboBox<>(
            new TipoTransacao[] {TipoTransacao.SAQUE, TipoTransacao.TRANSFERENCIA_ENVIADA, TipoTransacao.TARIFA});
    private final JTextField limiteValor = new JTextField(10);
    private final DefaultTableModel modeloLimites = Ui.modelo("Categoria", "Limite do mês", "Gasto no mês", "Uso do limite");
    private final JTextField objetivoNome = new JTextField(12);
    private final JTextField objetivoValor = new JTextField(8);
    private final JSpinner objetivoPrazo = new JSpinner(new SpinnerDateModel(
            Date.from(LocalDate.now().plusMonths(6).atStartOfDay(ZoneId.systemDefault()).toInstant()), null, null, Calendar.DAY_OF_MONTH));
    private final DefaultTableModel modeloObjetivos = Ui.modelo("Objetivo", "Meta", "Prazo", "Juntado", "Progresso");

    // notificações
    private final DefaultTableModel modeloNotificacoes = Ui.modelo("Hora", "Tipo", "Mensagem");
    private int notificacoesVistas = -1;

    PainelConta(Aplicacao app, Conta conta) {
        super(new BorderLayout());
        this.app = app;
        this.conta = conta;

        filtroTipo.setRenderer(Ui.RENDERIZADOR_TIPO);
        limiteCategoria.setRenderer(Ui.RENDERIZADOR_TIPO);
        filtroTipo.addItem("Todos os tipos");
        for (TipoTransacao t : TipoTransacao.values()) filtroTipo.addItem(t);
        filtroDe.setToolTipText("dd/mm/aaaa");
        filtroAte.setToolTipText("dd/mm/aaaa");
        agendaQuando.setEditor(new JSpinner.DateEditor(agendaQuando, "dd/MM/yyyy HH:mm:ss"));
        objetivoPrazo.setEditor(new JSpinner.DateEditor(objetivoPrazo, "dd/MM/yyyy"));

        add(criarCabecalho(), BorderLayout.NORTH);
        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Operações", abaOperacoes());
        abas.addTab("Extrato", abaExtrato());
        abas.addTab("Agendamentos", abaAgendamentos());
        abas.addTab("Planejamento", abaPlanejamento());
        abas.addTab("Notificações", abaNotificacoes());
        abas.addChangeListener(e -> {
            if (abas.getSelectedIndex() == 3) recarregarLimites();
        });
        add(abas, BorderLayout.CENTER);
        add(mensagem, BorderLayout.SOUTH);

        atualizarRapido();
        recarregarExtrato();
        recarregarLembretes();
        relogio = new Timer(1000, e -> atualizarRapido());
        relogio.start();
    }

    void parar() { relogio.stop(); }

    // ================================================================== cabeçalho

    private JPanel criarCabecalho() {
        PainelGradiente p = new PainelGradiente(new BorderLayout(0, 2), Ui.NOITE, Ui.ROXO_PROFUNDO);
        p.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        titulo.setForeground(Ui.CIANO);
        titulo.setFont(Ui.fonte(Font.BOLD, 14));
        saldo.setFont(Ui.fonte(Font.BOLD, 38));
        detalhes.setForeground(new Color(0xD4D2F5));
        detalhes.setFont(Ui.fonte(Font.PLAIN, 13));
        p.add(titulo, BorderLayout.NORTH);
        p.add(saldo, BorderLayout.CENTER);
        p.add(detalhes, BorderLayout.SOUTH);
        return p;
    }

    /** Atualiza o que vive só em memória (barato): cabeçalho, agendamentos, objetivos e notificações. */
    private void atualizarRapido() {
        BigDecimal s = conta.getSaldo();
        titulo.setText("Conta #" + conta.getId() + "  ·  " + (conta.tipo().equals("POUPANCA") ? "Poupança" : "Corrente")
                + "  ·  " + conta.getStatus());
        saldo.setText(Dinheiro.formatar(s));
        saldo.setForeground(s.signum() < 0 ? new Color(0xFF9DB0) : Color.WHITE);
        detalhes.setText("Disponível para uso: " + Dinheiro.formatar(conta.saldoDisponivel())
                + (conta.limiteChequeEspecial().signum() > 0 ? "  (cheque especial de " + Dinheiro.formatar(conta.limiteChequeEspecial()) + ")" : "")
                + "   ·   Limite diário de saque: " + Dinheiro.formatar(conta.getLimiteDiarioSaque()));
        recarregarAgendamentos();
        recarregarObjetivos();
        recarregarNotificacoes();
    }

    /** Depois de uma operação que mexeu no saldo: recarrega também o que vem do banco. */
    private void aposOperacao() {
        atualizarRapido();
        recarregarExtrato();
        recarregarLimites();
    }

    private void sucesso(String texto) { Ui.definirMensagem(mensagem, texto, true); }

    private void falha(Throwable causa) {
        Ui.definirMensagem(mensagem, Ui.mensagem(causa), false);
        Ui.erro(this, Ui.mensagem(causa));
    }

    /** Valores altos exigem a senha de transação: ela é pedida AQUI, antes de a operação tocar na conta (nenhum lock espera o usuário). */
    private String senhaSeNecessario(BigDecimal valor, boolean[] cancelou) {
        if (!app.banco.exigeSenhaReforcada(valor)) return null;
        String senha = Ui.pedirSenha(this, "Valor alto (" + Dinheiro.formatar(valor) + "). Digite a senha de transação:");
        if (senha == null) cancelou[0] = true;
        return senha;
    }

    // ================================================================== operações

    private JPanel abaOperacoes() {
        JPanel linha = new JPanel(new GridLayout(1, 3, 14, 0));
        linha.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        linha.add(Ui.cartao("Depósito", Ui.formulario("Valor (R$)", depositoValor), Ui.botao("Depositar", this::depositar)));
        linha.add(Ui.cartao("Saque", Ui.formulario("Valor (R$)", saqueValor), Ui.botao("Sacar", this::sacar)));
        linha.add(Ui.cartao("Transferência", Ui.formulario("Conta de destino", transfDestino, "Valor (R$)", transfValor),
                Ui.botao("Transferir", this::transferir)));
        JPanel p = new JPanel(new BorderLayout());
        JPanel topo = new JPanel(new BorderLayout());
        topo.add(linha, BorderLayout.NORTH);
        JLabel dica = new JLabel("<html>Operações a partir de <b>" + Dinheiro.formatar(Configuracao.LIMITE_VALOR_ALTO)
                + "</b> pedem a senha de transação.<br>Abra a mesma conta em duas janelas e opere nas duas ao mesmo tempo: o saldo continua exato.</html>");
        dica.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        dica.setForeground(Ui.CINZA);
        topo.add(dica, BorderLayout.CENTER);
        p.add(topo, BorderLayout.NORTH);
        return p;
    }

    private void depositar() {
        BigDecimal v = Ui.lerValor(depositoValor, "Valor do depósito");
        Ui.executar(this, () -> app.banco.depositar(conta.getId(), v), t -> {
            depositoValor.setText("");
            sucesso("Depósito de " + Dinheiro.formatar(v) + " realizado. Protocolo " + t.protocolo());
            aposOperacao();
        }, this::falha);
    }

    private void sacar() {
        BigDecimal v = Ui.lerValor(saqueValor, "Valor do saque");
        boolean[] cancelou = {false};
        String senha = senhaSeNecessario(v, cancelou);
        if (cancelou[0]) return;
        Ui.executar(this, () -> app.banco.sacar(conta.getId(), v, senha), t -> {
            saqueValor.setText("");
            sucesso("Saque de " + Dinheiro.formatar(v) + " realizado. Protocolo " + t.protocolo());
            aposOperacao();
        }, this::falha);
    }

    private void transferir() {
        int destino = Ui.lerInteiro(transfDestino, "Conta de destino");
        BigDecimal v = Ui.lerValor(transfValor, "Valor da transferência");
        boolean[] cancelou = {false};
        String senha = senhaSeNecessario(v, cancelou);
        if (cancelou[0]) return;
        Ui.executar(this, () -> app.banco.transferir(conta.getId(), destino, v, senha), t -> {
            transfValor.setText("");
            sucesso("Transferência de " + Dinheiro.formatar(v) + " para a conta #" + destino + " realizada. Protocolo " + t.protocolo());
            aposOperacao();
        }, this::falha);
    }

    // ================================================================== extrato

    private JPanel abaExtrato() {
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtros.add(new JLabel("De"));
        filtros.add(filtroDe);
        filtros.add(new JLabel("até"));
        filtros.add(filtroAte);
        filtros.add(filtroTipo);
        filtros.add(new JLabel("Valor"));
        filtros.add(filtroMin);
        filtros.add(new JLabel("a"));
        filtros.add(filtroMax);
        filtros.add(Ui.botao("Filtrar", () -> {
            filtroAtual = lerFiltro();
            recarregarExtrato();
        }));
        filtros.add(Ui.botaoSec("Limpar", () -> {
            filtroDe.setText("");
            filtroAte.setText("");
            filtroMin.setText("");
            filtroMax.setText("");
            filtroTipo.setSelectedIndex(0);
            filtroAtual = FiltroExtrato.SEM_FILTRO;
            recarregarExtrato();
        }));

        tabelaExtrato.getColumnModel().getColumn(0).setPreferredWidth(170);
        tabelaExtrato.getColumnModel().getColumn(1).setPreferredWidth(180);
        tabelaExtrato.getColumnModel().getColumn(2).setPreferredWidth(110);
        tabelaExtrato.getColumnModel().getColumn(3).setPreferredWidth(260);
        tabelaExtrato.getColumnModel().getColumn(4).setPreferredWidth(270);
        tabelaExtrato.getColumnModel().getColumn(2).setCellRenderer(new Ui.RenderizadorTabela() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int l, int c) {
                super.getTableCellRendererComponent(t, v, sel, foco, l, c);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setForeground(String.valueOf(v).startsWith("-") ? Ui.VERMELHO : Ui.VERDE);
                setFont(Ui.fonte(Font.BOLD, 14));
                return this;
            }
        });

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        acoes.add(Ui.botaoSec("Estornar transação selecionada", this::estornar));
        acoes.add(new JLabel("      Exportar como"));
        acoes.add(formatoExportacao);
        acoes.add(Ui.botaoSec("Exportar extrato…", this::exportar));

        JPanel rodape = new JPanel(new BorderLayout());
        rodape.add(totaisExtrato, BorderLayout.NORTH);
        rodape.add(acoes, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        p.add(filtros, BorderLayout.NORTH);
        p.add(new JScrollPane(tabelaExtrato), BorderLayout.CENTER);
        p.add(rodape, BorderLayout.SOUTH);
        return p;
    }

    private FiltroExtrato lerFiltro() {
        LocalDate de = Ui.lerDataOpcional(filtroDe, "De");
        LocalDate ate = Ui.lerDataOpcional(filtroAte, "até");
        TipoTransacao tipo = filtroTipo.getSelectedItem() instanceof TipoTransacao t ? t : null;
        return new FiltroExtrato(de == null ? null : de.atStartOfDay(),
                ate == null ? null : ate.plusDays(1).atStartOfDay().minusNanos(1000),
                tipo, Ui.lerValorOpcional(filtroMin, "Valor mínimo"), Ui.lerValorOpcional(filtroMax, "Valor máximo"));
    }

    private void recarregarExtrato() {
        FiltroExtrato filtro = filtroAtual;
        Ui.executar(this, () -> app.banco.extrato(conta.getId(), filtro), lista -> {
            ultimoExtrato = lista;
            modeloExtrato.setRowCount(0);
            BigDecimal creditos = BigDecimal.ZERO, debitos = BigDecimal.ZERO;
            for (Transacao t : lista) {
                modeloExtrato.addRow(new Object[] {t.dataHora().format(Ui.DATA_HORA), Ui.nomeTipo(t.tipo()),
                        (t.tipo().ehCredito() ? "+" : "-") + Dinheiro.numero(t.valor()), t.descricao(), t.protocolo()});
                if (t.tipo().ehCredito()) creditos = creditos.add(t.valor()); else debitos = debitos.add(t.valor());
            }
            totaisExtrato.setText("  " + lista.size() + " transações   ·   créditos " + Dinheiro.formatar(creditos)
                    + "   ·   débitos " + Dinheiro.formatar(debitos));
        });
    }

    private void estornar() {
        int linha = tabelaExtrato.getSelectedRow();
        if (linha < 0) throw new BancoException.OperacaoInvalida("Selecione uma transação na tabela do extrato.");
        String protocolo = (String) modeloExtrato.getValueAt(linha, 4);
        String resumo = modeloExtrato.getValueAt(linha, 1) + " de " + modeloExtrato.getValueAt(linha, 2);
        if (!Ui.confirmar(this, "Estornar " + resumo + "?\nO registro original será mantido no histórico.")) return;
        Ui.executar(this, () -> app.banco.estornar(protocolo), estorno -> {
            sucesso("Estorno realizado. Protocolo do estorno " + estorno.protocolo());
            aposOperacao();
        }, this::falha);
    }

    private void exportar() {
        Formato formato = (Formato) formatoExportacao.getSelectedItem();
        JFileChooser escolha = new JFileChooser(new File(Configuracao.PASTA_EXPORTACAO).isDirectory()
                ? new File(Configuracao.PASTA_EXPORTACAO) : new File(System.getProperty("user.home")));
        escolha.setSelectedFile(new File(escolha.getCurrentDirectory(), "extrato_conta_" + conta.getId() + "." + formato.extensao()));
        if (escolha.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destino = escolha.getSelectedFile().toPath();
        List<Transacao> lista = ultimoExtrato;
        Ui.executar(this, () -> ExportadorExtrato.exportar(conta, lista, formato, destino),
                caminho -> sucesso("Extrato exportado para " + caminho.toAbsolutePath()), this::falha);
    }

    // ================================================================== agendamentos

    private JPanel abaAgendamentos() {
        JPanel form = Ui.formulario("Conta de destino", agendaDestino, "Valor (R$)", agendaValor, "Executar em", agendaQuando);
        JPanel cartao = Ui.cartao("Agendar transferência (executada em outra thread, no horário escolhido)", form,
                Ui.botao("Agendar", this::agendar));
        JTable tabela = Ui.tabela(modeloAgenda);
        tabela.getColumnModel().getColumn(4).setPreferredWidth(350);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 14));
        p.add(cartao, BorderLayout.NORTH);
        p.add(new JScrollPane(tabela), BorderLayout.CENTER);
        return p;
    }

    private void agendar() {
        int destino = Ui.lerInteiro(agendaDestino, "Conta de destino");
        BigDecimal v = Ui.lerValor(agendaValor, "Valor");
        LocalDateTime quando = LocalDateTime.ofInstant(((Date) agendaQuando.getValue()).toInstant(), ZoneId.systemDefault());
        boolean[] cancelou = {false};
        String senha = senhaSeNecessario(v, cancelou);
        if (cancelou[0]) return;
        Ui.executar(this, () -> app.agendador.agendarTransferencia(conta.getId(), destino, v, quando, senha), ag -> {
            agendaValor.setText("");
            sucesso("Transferência agendada para " + ag.getExecutarEm().format(Ui.DATA_HORA) + ".");
            recarregarAgendamentos();
        }, this::falha);
    }

    private void recarregarAgendamentos() {
        List<TransferenciaAgendada> lista = app.agendador.listarAgendamentos(conta.getId());
        String assinatura = lista.stream().map(a -> a.getId() + a.getStatus()).collect(Collectors.joining());
        if (assinatura.equals(assinaturaAgenda)) return;
        assinaturaAgenda = assinatura;
        modeloAgenda.setRowCount(0);
        for (TransferenciaAgendada a : lista) {
            modeloAgenda.addRow(new Object[] {a.getExecutarEm().format(Ui.DATA_HORA), "Conta #" + a.getDestinoId(),
                    Dinheiro.formatar(a.getValor()), switch (a.getStatus()) {
                        case AGENDADA -> "Aguardando";
                        case EXECUTADA -> "Executada";
                        case FALHOU -> "Falhou";
                    }, a.getDetalhe()});
        }
    }

    // ================================================================== planejamento

    private JTabbedPane abaPlanejamento() {
        JTabbedPane abas = new JTabbedPane(JTabbedPane.LEFT);
        abas.addTab("Lembretes", painelLembretes());
        abas.addTab("Limites de gasto", painelLimites());
        abas.addTab("Objetivos", painelObjetivos());
        return abas;
    }

    private JPanel painelLembretes() {
        JPanel form = Ui.formulario("Descrição", lembreteDescricao, "Valor (R$)", lembreteValor, "Vence todo dia", lembreteDia);
        JPanel cartao = Ui.cartao("Novo lembrete de pagamento mensal", form, Ui.botao("Cadastrar lembrete", () -> {
            String descricao = lembreteDescricao.getText();
            BigDecimal v = Ui.lerValor(lembreteValor, "Valor");
            int dia = (Integer) lembreteDia.getValue();
            app.planejamento.cadastrarLembrete(conta.getId(), descricao, v, dia);
            lembreteDescricao.setText("");
            lembreteValor.setText("");
            sucesso("Lembrete cadastrado. Você será avisado " + Configuracao.LEMBRETE_DIAS_ANTECEDENCIA + " dias antes do vencimento.");
            app.planejamento.verificarLembretes(conta.getId(), LocalDate.now());
            recarregarLembretes();
        }));
        return painelComTabela(cartao, modeloLembretes);
    }

    private void recarregarLembretes() {
        modeloLembretes.setRowCount(0);
        for (LembretePagamento l : app.planejamento.listarLembretes(conta.getId())) {
            modeloLembretes.addRow(new Object[] {l.getDescricao(), Dinheiro.formatar(l.getValor()), l.getDiaDoMes(),
                    l.proximoVencimento(LocalDate.now()).format(Ui.DATA)});
        }
    }

    private JPanel painelLimites() {
        JPanel form = Ui.formulario("Categoria", limiteCategoria, "Limite mensal (R$)", limiteValor);
        JPanel cartao = Ui.cartao("Definir limite de gasto do mês", form, Ui.botao("Definir limite", () -> {
            BigDecimal v = Ui.lerValor(limiteValor, "Limite mensal");
            app.planejamento.definirLimite(conta.getId(), (TipoTransacao) limiteCategoria.getSelectedItem(), v);
            limiteValor.setText("");
            sucesso("Limite definido. Você será avisado ao chegar em 80% e ao ultrapassar.");
            recarregarLimites();
        }));
        modeloLimites.setRowCount(0);
        JPanel p = painelComTabela(cartao, modeloLimites);
        JTable tabela = (JTable) ((JScrollPane) p.getComponent(1)).getViewport().getView();
        tabela.getColumnModel().getColumn(3).setCellRenderer(new BarraRenderer(true));
        return p;
    }

    private void recarregarLimites() {
        Ui.executar(this, () -> app.planejamento.relatorioDoMes(conta.getId()), linhas -> {
            modeloLimites.setRowCount(0);
            for (LinhaGasto l : linhas) {
                modeloLimites.addRow(new Object[] {Ui.nomeTipo(l.categoria()), Dinheiro.formatar(l.limite()), Dinheiro.formatar(l.gasto()),
                        l.percentual().intValue()});
            }
        }, causa -> { });
    }

    private JPanel painelObjetivos() {
        JPanel form = Ui.formulario("Objetivo", objetivoNome, "Quanto juntar (R$)", objetivoValor, "Prazo", objetivoPrazo);
        JPanel cartao = Ui.cartao("Novo objetivo de economia", form, Ui.botao("Criar objetivo", () -> {
            String nome = objetivoNome.getText();
            BigDecimal v = Ui.lerValor(objetivoValor, "Quanto juntar");
            LocalDate prazo = ((Date) objetivoPrazo.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            app.planejamento.criarObjetivo(conta, nome, v, prazo);
            objetivoNome.setText("");
            objetivoValor.setText("");
            sucesso("Objetivo criado. O progresso conta o quanto o saldo desta conta crescer a partir de agora.");
            recarregarObjetivos();
        }));
        JPanel p = painelComTabela(cartao, modeloObjetivos);
        JTable tabela = (JTable) ((JScrollPane) p.getComponent(1)).getViewport().getView();
        tabela.getColumnModel().getColumn(4).setCellRenderer(new BarraRenderer(false));
        tabela.getColumnModel().getColumn(0).setPreferredWidth(230);
        return p;
    }

    private void recarregarObjetivos() {
        List<Objetivo> lista = app.planejamento.listarObjetivos(conta.getId());
        BigDecimal saldoAtual = conta.getSaldo();
        modeloObjetivos.setRowCount(0);
        for (Objetivo o : lista) {
            modeloObjetivos.addRow(new Object[] {o.getNome() + (o.isAtingido() ? "  (atingido)" : ""), Dinheiro.formatar(o.getValorMeta()),
                    o.getPrazo().format(Ui.DATA), Dinheiro.formatar(o.economizado(saldoAtual)),
                    o.progressoPercentual(saldoAtual).intValue()});
        }
    }

    private static JPanel painelComTabela(JPanel formulario, DefaultTableModel modelo) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        p.add(formulario, BorderLayout.NORTH);
        p.add(new JScrollPane(Ui.tabela(modelo)), BorderLayout.CENTER);
        return p;
    }

    // ================================================================== notificações

    private JPanel abaNotificacoes() {
        JTable tabela = Ui.tabela(modeloNotificacoes);
        tabela.getColumnModel().getColumn(0).setMaxWidth(90);
        tabela.getColumnModel().getColumn(1).setPreferredWidth(170);
        tabela.getColumnModel().getColumn(2).setPreferredWidth(560);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JLabel dica = new JLabel("Avisos de saldo baixo, valor alto, fraude suspeita, duplicidade, lembretes, limites e metas. "
                + "Também ficam no arquivo " + Configuracao.ARQUIVO_LOG);
        dica.setForeground(Ui.CINZA);
        p.add(dica, BorderLayout.NORTH);
        p.add(new JScrollPane(tabela), BorderLayout.CENTER);
        return p;
    }

    private void recarregarNotificacoes() {
        List<Notificacao> lista = app.historico.daConta(conta.getId());
        if (lista.size() == notificacoesVistas) return;
        notificacoesVistas = lista.size();
        modeloNotificacoes.setRowCount(0);
        for (int i = lista.size() - 1; i >= 0; i--) { // mais recentes primeiro
            Notificacao n = lista.get(i);
            modeloNotificacoes.addRow(new Object[] {n.dataHora().toLocalTime().withNano(0).toString(), n.tipo().name().replace('_', ' '), n.mensagem()});
        }
    }
}
