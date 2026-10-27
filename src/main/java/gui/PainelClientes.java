package gui;

import app.Aplicacao;
import domain.BancoException;
import domain.Cliente;
import domain.Conta;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import util.Dinheiro;

/** Tela inicial: cadastro de clientes, abertura de contas, administração e entrada (login) em uma conta. */
class PainelClientes extends JPanel {
    private record ItemCliente(int id, String nome) {
        @Override public String toString() { return "#" + id + " - " + nome; }
    }

    private record Dados(List<ItemCliente> clientes, List<Object[]> linhas, List<Integer> donos) { }

    private final Aplicacao app;
    private final java.util.function.Consumer<Conta> abrirJanelaDaConta;

    private final JTextField nomeCliente = new JTextField(16);
    private final JComboBox<ItemCliente> comboClientes = new JComboBox<>();
    private final JComboBox<String> comboTipo = new JComboBox<>(new String[] {"Conta corrente", "Conta poupança"});
    private final JTextField saldoInicial = new JTextField("0,00", 10);
    private final JPasswordField pin = new JPasswordField(10);
    private final JPasswordField senhaTransacao = new JPasswordField(10);

    private final DefaultTableModel modelo = Ui.modelo("Conta", "Cliente", "Tipo", "Status", "Saldo");
    private final JTable tabela = Ui.tabela(modelo);
    private final List<Integer> donosDasLinhas = new ArrayList<>();
    private final JLabel consolidado = new JLabel(" ");
    private final JLabel mensagem = Ui.mensagem();

    PainelClientes(Aplicacao app, java.util.function.Consumer<Conta> abrirJanelaDaConta) {
        super(new BorderLayout(12, 0));
        this.app = app;
        this.abrirJanelaDaConta = abrirJanelaDaConta;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        add(criarLateral(), BorderLayout.WEST);
        add(criarCentro(), BorderLayout.CENTER);
        add(mensagem, BorderLayout.SOUTH);
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) atualizarConsolidado();
        });
        tabela.getColumnModel().getColumn(0).setMaxWidth(70);
        atualizar();
    }

    private JPanel criarLateral() {
        JPanel lateral = new JPanel();
        lateral.setOpaque(false);
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS));

        JPanel novoCliente = Ui.cartao("1. Novo cliente", Ui.formulario("Nome", nomeCliente),
                Ui.botao("Cadastrar cliente", this::cadastrarCliente));
        JPanel novaConta = Ui.cartao("2. Abrir conta",
                Ui.formulario("Cliente", comboClientes, "Tipo", comboTipo, "Saldo inicial (R$)", saldoInicial,
                        "PIN (4 a 8 dígitos)", pin, "Senha de transação", senhaTransacao),
                Ui.botao("Abrir conta", this::abrirConta));
        JLabel dica = new JLabel("<html><small>A senha de transação é exigida em operações de valor alto<br>(a partir de R$ 5.000,00).</small></html>");
        dica.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        dica.setForeground(Ui.TEXTO_SUAVE);
        dica.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 0));

        lateral.add(novoCliente);
        lateral.add(Box.createVerticalStrut(10));
        lateral.add(novaConta);
        lateral.add(dica);
        lateral.add(Box.createVerticalGlue());
        lateral.setPreferredSize(new java.awt.Dimension(340, lateral.getPreferredSize().height));
        return lateral;
    }

    private JPanel criarCentro() {
        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.setOpaque(false);
        centro.add(Ui.rotuloTitulo("Contas do banco"), BorderLayout.NORTH);
        centro.add(new JScrollPane(tabela), BorderLayout.CENTER);

        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton entrar = Ui.botao("Entrar na conta…", this::entrarNaConta);
        botoes.setOpaque(false);
        botoes.add(entrar);
        botoes.add(Ui.botaoSec("Bloquear", () -> administrar("bloquear")));
        botoes.add(Ui.botaoSec("Desbloquear", () -> administrar("desbloquear")));
        botoes.add(Ui.botaoSec("Encerrar", () -> administrar("encerrar")));
        botoes.add(Ui.botaoSec("Atualizar", this::atualizar));
        rodape.add(botoes, BorderLayout.NORTH);
        rodape.add(consolidado, BorderLayout.SOUTH);
        centro.add(rodape, BorderLayout.SOUTH);
        return centro;
    }

    // ------------------------------------------------------------------ ações

    private void cadastrarCliente() {
        String nome = nomeCliente.getText();
        Ui.executar(this, () -> app.banco.criarCliente(nome), c -> {
            nomeCliente.setText("");
            Ui.definirMensagem(mensagem, "Cliente #" + c.getId() + " (" + c.getNome() + ") cadastrado.", true);
            atualizar();
        });
    }

    private void abrirConta() {
        ItemCliente cliente = (ItemCliente) comboClientes.getSelectedItem();
        if (cliente == null) throw new BancoException.OperacaoInvalida("Cadastre um cliente primeiro.");
        BigDecimal saldo = Ui.lerValor(saldoInicial, "Saldo inicial");
        String tipo = comboTipo.getSelectedIndex() == 1 ? "POUPANCA" : "CORRENTE";
        String p = new String(pin.getPassword());
        String s = new String(senhaTransacao.getPassword());
        Ui.executar(this, () -> app.banco.abrirConta(cliente.id(), tipo, saldo, p, s), conta -> {
            saldoInicial.setText("0,00");
            pin.setText("");
            senhaTransacao.setText("");
            Ui.definirMensagem(mensagem, "Conta " + conta.tipo() + " #" + conta.getId() + " aberta com saldo de "
                    + Dinheiro.formatar(conta.getSaldo()) + ".", true);
            atualizar();
        });
    }

    private int contaSelecionada() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) throw new BancoException.OperacaoInvalida("Selecione uma conta na tabela.");
        return (Integer) modelo.getValueAt(linha, 0);
    }

    private void entrarNaConta() {
        int id = contaSelecionada();
        String digitado = Ui.pedirSenha(this, "PIN da conta #" + id + ":");
        if (digitado == null) return;
        Ui.executar(this, () -> app.banco.autenticar(id, digitado), conta -> {
            Ui.definirMensagem(mensagem, "Conta #" + id + " aberta em uma nova janela.", true);
            abrirJanelaDaConta.accept(conta);
        }, causa -> {
            Ui.erro(this, Ui.mensagem(causa));
            atualizar(); // o status pode ter mudado para BLOQUEADA
        });
    }

    private void administrar(String acao) {
        int id = contaSelecionada();
        if (acao.equals("encerrar") && !Ui.confirmar(this, "Encerrar a conta #" + id + "? (só é possível com saldo zerado)")) return;
        Ui.rodar(this, () -> {
            switch (acao) {
                case "bloquear" -> app.banco.bloquear(id);
                case "desbloquear" -> app.banco.desbloquear(id);
                default -> app.banco.encerrar(id);
            }
        }, () -> {
            Ui.definirMensagem(mensagem, "Conta #" + id + ": ação \"" + acao + "\" concluída.", true);
            atualizar();
        });
    }

    // ------------------------------------------------------------------ dados

    void atualizar() {
        Ui.executar(this, () -> {
            List<ItemCliente> itens = new ArrayList<>();
            List<Object[]> linhas = new ArrayList<>();
            List<Integer> donos = new ArrayList<>();
            for (Cliente c : app.clientes.listar()) {
                itens.add(new ItemCliente(c.getId(), c.getNome()));
                for (Conta conta : app.banco.contasDoCliente(c.getId())) {
                    linhas.add(new Object[] {conta.getId(), c.getNome() + " (#" + c.getId() + ")",
                            conta.tipo().equals("POUPANCA") ? "Poupança" : "Corrente", conta.getStatus().name(),
                            Dinheiro.formatar(conta.getSaldo())});
                    donos.add(c.getId());
                }
            }
            return new Dados(itens, linhas, donos);
        }, dados -> {
            Object selecionado = comboClientes.getSelectedItem();
            comboClientes.removeAllItems();
            dados.clientes().forEach(comboClientes::addItem);
            if (selecionado != null) {
                for (int i = 0; i < comboClientes.getItemCount(); i++) {
                    if (comboClientes.getItemAt(i).id() == ((ItemCliente) selecionado).id()) comboClientes.setSelectedIndex(i);
                }
            }
            modelo.setRowCount(0);
            dados.linhas().forEach(modelo::addRow);
            donosDasLinhas.clear();
            donosDasLinhas.addAll(dados.donos());
            atualizarConsolidado();
        });
    }

    private void atualizarConsolidado() {
        int linha = tabela.getSelectedRow();
        if (linha < 0 || linha >= donosDasLinhas.size()) {
            consolidado.setText(" ");
            return;
        }
        int cliente = donosDasLinhas.get(linha);
        Ui.executar(this, () -> app.banco.saldoConsolidado(cliente),
                saldo -> consolidado.setText("  Saldo consolidado do cliente #" + cliente + " (todas as contas): " + Dinheiro.formatar(saldo)));
    }
}
