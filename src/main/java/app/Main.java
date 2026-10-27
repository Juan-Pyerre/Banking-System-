package app;

import config.Configuracao;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import persistence.ConexaoJdbc;
import simulacao.DemonstracaoDeadlock;
import simulacao.RelatorioDesempenho;
import simulacao.SimulacaoConcorrente;

/**
 * Ponto de entrada.
 *   java -cp ... app.Main                       -> interface gráfica (janelas)
 *   java -cp ... app.Main --console             -> menu de texto no terminal
 *   java -cp ... app.Main --relatorio [arquivo] -> roda a simulação "antes e depois" e grava o relatório
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // garante acentos corretos no console mesmo quando o sistema não está em UTF-8
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        if (args.length > 0 && args[0].equals("--relatorio")) {
            gerarRelatorio(args.length > 1 ? Path.of(args[1]) : Path.of("docs", "relatorio_antes_depois.txt"));
            return;
        }
        if (args.length > 0 && args[0].equals("--console")) {
            Aplicacao app = new Aplicacao(new ConexaoJdbc(), true, Path.of(Configuracao.ARQUIVO_LOG));
            new MenuPrincipal(app).executar();
            return;
        }
        iniciarInterfaceGrafica();
    }

    /** Abre a interface gráfica (padrão). */
    private static void iniciarInterfaceGrafica() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            gui.TemaPublico.instalar();
            try {
                Aplicacao app = new Aplicacao(new ConexaoJdbc(), false, Path.of(Configuracao.ARQUIVO_LOG));
                app.notificador.adicionar(new gui.ObservadorToast());
                new gui.JanelaPrincipal(app).setVisible(true);
            } catch (RuntimeException e) {
                javax.swing.JOptionPane.showMessageDialog(null,
                        "Não foi possível abrir o banco de dados:\n" + e.getMessage()
                                + "\n\nO sistema já está aberto em outra janela? Feche-o e tente de novo.",
                        "Sistema Bancário", javax.swing.JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** Relatório "antes e depois" da correção da race condition (e do deadlock), sem precisar de banco de dados. */
    static void gerarRelatorio(Path destino) throws InterruptedException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("RELATÓRIO: ANTES E DEPOIS DA CORREÇÃO DA RACE CONDITION\n");
        sb.append("Mesma carga nas duas versões: mesmas sementes, mesmas operações sorteadas; só muda a sincronização.\n");
        sb.append("O saldo esperado pode variar um pouco entre rodadas: um saque recusado por saldo insuficiente depende da ordem em que\n");
        sb.append("as threads rodam. O que importa é a coluna \"obtido\" bater com a coluna \"esperado\" em TODAS as contas.\n\n");

        SimulacaoConcorrente simulacao = new SimulacaoConcorrente();
        int contas = 4, threads = 8, operacoes = 20_000;

        sb.append("PARTE 1 - ANTES (sem sincronização), 3 execuções seguidas: o saldo final VARIA a cada rodada\n\n");
        for (int i = 1; i <= 3; i++) {
            RelatorioDesempenho r = simulacao.executar(new SimulacaoConcorrente.Parametros(contas, threads, operacoes, false, false));
            sb.append("Execução ").append(i).append(":\n").append(r.formatar()).append('\n');
        }
        sb.append("PARTE 2 - DEPOIS (synchronized em Conta), 3 execuções seguidas: o saldo SEMPRE bate\n\n");
        for (int i = 1; i <= 3; i++) {
            RelatorioDesempenho r = simulacao.executar(new SimulacaoConcorrente.Parametros(contas, threads, operacoes, true, false));
            sb.append("Execução ").append(i).append(":\n").append(r.formatar()).append('\n');
        }

        sb.append("PARTE 3 - DEADLOCK EM TRANSFERÊNCIAS\n\n");
        Path temporario = Files.createTempFile("deadlock", ".txt");
        try (PrintStream out = new PrintStream(Files.newOutputStream(temporario), true, StandardCharsets.UTF_8)) {
            DemonstracaoDeadlock.demonstrarDeadlock(out);
            out.println();
            DemonstracaoDeadlock.demonstrarCorrecao(out);
        }
        sb.append(Files.readString(temporario, StandardCharsets.UTF_8));
        Files.deleteIfExists(temporario);

        if (destino.getParent() != null) Files.createDirectories(destino.getParent());
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        System.out.println(sb);
        System.out.println("Relatório gravado em " + destino);
    }
}
