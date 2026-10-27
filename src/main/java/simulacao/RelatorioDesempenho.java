package simulacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Resultado de uma simulação: tempo, operações por segundo, falhas e conferência dos saldos. */
public record RelatorioDesempenho(String titulo, int contas, int threads, int operacoesPorThread,
                                  long sucessos, long falhas, Map<String, Long> motivosDeFalha, long tempoMs,
                                  List<LinhaConta> linhas, Boolean bancoConfere) {

    public record LinhaConta(int contaId, BigDecimal esperado, BigDecimal obtido) {
        public boolean confere() { return esperado.compareTo(obtido) == 0; }
    }

    public long totalOperacoes() { return sucessos + falhas; }

    public double operacoesPorSegundo() {
        return tempoMs <= 0 ? totalOperacoes() * 1000.0 : totalOperacoes() * 1000.0 / tempoMs;
    }

    public BigDecimal totalEsperado() { return linhas.stream().map(LinhaConta::esperado).reduce(BigDecimal.ZERO, BigDecimal::add); }

    public BigDecimal totalObtido() { return linhas.stream().map(LinhaConta::obtido).reduce(BigDecimal.ZERO, BigDecimal::add); }

    public boolean consistente() {
        return linhas.stream().allMatch(LinhaConta::confere) && (bancoConfere == null || bancoConfere);
    }

    public String formatar() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(titulo).append(" ===\n");
        sb.append(String.format("Contas: %d | Threads: %d | Operações por thread: %d (total tentado: %d)%n",
                contas, threads, operacoesPorThread, totalOperacoes()));
        sb.append(String.format("Tempo total: %d ms | Operações/segundo: %.0f%n", tempoMs, operacoesPorSegundo()));
        sb.append(String.format("Sucessos: %d | Falhas: %d %s%n", sucessos, falhas, motivosDeFalha.isEmpty() ? "" : motivosDeFalha));
        sb.append(String.format("%-8s %18s %18s %14s%n", "Conta", "Saldo esperado", "Saldo obtido", "Diferença"));
        int mostradas = 0;
        for (LinhaConta l : linhas) {
            if (mostradas++ >= 12) { sb.append("... (").append(linhas.size() - 12).append(" contas omitidas)\n"); break; }
            sb.append(String.format("%-8d %18s %18s %14s %s%n", l.contaId(), l.esperado().toPlainString(),
                    l.obtido().toPlainString(), l.obtido().subtract(l.esperado()).toPlainString(), l.confere() ? "OK" : "<-- DIVERGE"));
        }
        sb.append(String.format("TOTAL    %18s %18s %14s%n", totalEsperado().toPlainString(), totalObtido().toPlainString(),
                totalObtido().subtract(totalEsperado()).toPlainString()));
        if (bancoConfere != null) {
            sb.append("Saldo gravado no banco confere com a memória: ").append(bancoConfere ? "SIM" : "NÃO").append('\n');
        }
        sb.append("RESULTADO: ").append(consistente() ? "SALDOS CORRETOS (nenhuma operação perdida)"
                : "SALDOS CORROMPIDOS pela race condition").append('\n');
        return sb.toString();
    }
}
