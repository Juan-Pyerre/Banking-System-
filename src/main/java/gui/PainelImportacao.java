package gui;

import app.Aplicacao;
import arquivo.ImportadorOperacoes.ResultadoImportacao;
import config.Configuracao;
import domain.BancoException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Importação de operações em lote a partir de um arquivo CSV, com relatório de sucessos e falhas. */
class PainelImportacao extends JPanel {
    private final Aplicacao app;
    private final JTextField arquivo = new JTextField(40);
    private final JTextArea relatorio = new JTextArea();
    private final JLabel mensagem = Ui.mensagem();

    PainelImportacao(Aplicacao app) {
        super(new BorderLayout(0, 8));
        this.app = app;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));

        JPanel topo = new JPanel(new BorderLayout(0, 8));
        topo.setOpaque(false);
        JLabel ajuda = new JLabel("<html>Formato do arquivo (separador <b>;</b>): <code>chave;tipo;conta;destino;valor[;senhaTransacao]</code><br>"
                + "Tipos: DEPOSITO, SAQUE, TRANSFERENCIA. A <b>chave</b> identifica a operação: importar o mesmo arquivo de novo "
                + "não aplica nada duas vezes (linhas repetidas aparecem como duplicadas).</html>");
        ajuda.setForeground(Ui.CINZA);
        topo.add(ajuda, BorderLayout.NORTH);

        JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        linha.setOpaque(false);
        linha.add(new JLabel("Arquivo CSV"));
        linha.add(arquivo);
        linha.add(Ui.botaoSec("Escolher…", this::escolher));
        linha.add(Ui.botao("Importar", this::importar));
        linha.add(Ui.botaoSec("Salvar modelo de CSV…", this::salvarModelo));
        topo.add(linha, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        relatorio.setEditable(false);
        relatorio.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(relatorio), BorderLayout.CENTER);
        add(mensagem, BorderLayout.SOUTH);
    }

    private void escolher() {
        JFileChooser escolha = new JFileChooser();
        if (escolha.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) arquivo.setText(escolha.getSelectedFile().getAbsolutePath());
    }

    private void importar() {
        if (arquivo.getText().isBlank()) throw new BancoException.OperacaoInvalida("Escolha o arquivo CSV.");
        Path origem = Path.of(arquivo.getText().trim());
        Path destino = Path.of(Configuracao.PASTA_EXPORTACAO,
                "relatorio_importacao_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        Ui.executar(this, () -> app.importador.importar(origem, destino), (ResultadoImportacao r) -> {
            relatorio.setText(r.formatar());
            relatorio.setCaretPosition(0);
            Ui.definirMensagem(mensagem, r.sucessos() + " ok, " + r.falhas() + " falhas, " + r.duplicadas()
                    + " duplicadas. Relatório salvo em " + destino.toAbsolutePath(), r.falhas() == 0);
        });
    }

    private void salvarModelo() {
        JFileChooser escolha = new JFileChooser();
        escolha.setSelectedFile(new File("operacoes_modelo.csv"));
        if (escolha.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String modelo = String.join("\n",
                "# Ajuste os números das contas para contas que existam no seu banco.",
                "chave;tipo;conta;destino;valor;senhaTransacao",
                "op-001;DEPOSITO;1;;150,00",
                "op-002;SAQUE;1;;20,00",
                "op-003;TRANSFERENCIA;1;2;100,00",
                "op-001;DEPOSITO;1;;150,00",
                "") ;
        try {
            Files.writeString(escolha.getSelectedFile().toPath(), modelo, StandardCharsets.UTF_8);
            arquivo.setText(escolha.getSelectedFile().getAbsolutePath());
            Ui.definirMensagem(mensagem, "Modelo salvo em " + escolha.getSelectedFile().getAbsolutePath(), true);
        } catch (IOException e) {
            throw new BancoException.OperacaoInvalida("Não foi possível salvar o arquivo: " + e.getMessage());
        }
    }
}
