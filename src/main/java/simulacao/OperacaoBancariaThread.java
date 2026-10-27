package simulacao;

import domain.BancoException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

/**
 * Simula um cliente fazendo operações (depósito, saque, transferência) sobre contas compartilhadas.
 * Cada operação que DEU CERTO é anotada em um livro-razão independente das contas ({@code deltas}); no fim, o saldo
 * esperado = saldo inicial + soma dos deltas. Se o saldo da conta não bate com isso, houve corrupção.
 */
public class OperacaoBancariaThread implements Runnable {
    private final List<ContaSimulada> contas;
    private final int operacoes;
    private final Random aleatorio;
    private final CountDownLatch largada;
    private final Map<Integer, LongAdder> deltas;
    private final LongAdder sucessos;
    private final LongAdder falhas;
    private final Map<String, LongAdder> motivosDeFalha;

    public OperacaoBancariaThread(List<ContaSimulada> contas, int operacoes, long semente, CountDownLatch largada,
                                  Map<Integer, LongAdder> deltas, LongAdder sucessos, LongAdder falhas,
                                  Map<String, LongAdder> motivosDeFalha) {
        this.contas = contas;
        this.operacoes = operacoes;
        this.aleatorio = new Random(semente);
        this.largada = largada;
        this.deltas = deltas;
        this.sucessos = sucessos;
        this.falhas = falhas;
        this.motivosDeFalha = motivosDeFalha;
    }

    @Override
    public void run() {
        try {
            largada.await(); // todas as threads largam juntas, para maximizar a concorrência
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        for (int i = 0; i < operacoes; i++) {
            ContaSimulada origem = contas.get(aleatorio.nextInt(contas.size()));
            BigDecimal valor = BigDecimal.valueOf(1 + aleatorio.nextInt(20_000), 2); // R$ 0,01 a R$ 200,00
            long centavos = valor.unscaledValue().longValue();
            int sorteio = aleatorio.nextInt(100);
            try {
                if (sorteio < 40) {
                    origem.depositar(valor);
                    deltas.get(origem.id()).add(centavos);
                    sucessos.increment();
                } else if (sorteio < 70) {
                    if (origem.sacar(valor)) {
                        deltas.get(origem.id()).add(-centavos);
                        sucessos.increment();
                    } else {
                        registrarFalha("SALDO_INSUFICIENTE");
                    }
                } else {
                    ContaSimulada destino = outraConta(origem);
                    if (origem.transferir(destino, valor)) {
                        deltas.get(origem.id()).add(-centavos);
                        deltas.get(destino.id()).add(centavos);
                        sucessos.increment();
                    } else {
                        registrarFalha("SALDO_INSUFICIENTE");
                    }
                }
            } catch (BancoException e) {
                registrarFalha(e.getClass().getSimpleName());
            }
        }
    }

    private ContaSimulada outraConta(ContaSimulada origem) {
        ContaSimulada destino;
        do {
            destino = contas.get(aleatorio.nextInt(contas.size()));
        } while (destino.id() == origem.id());
        return destino;
    }

    private void registrarFalha(String motivo) {
        falhas.increment();
        motivosDeFalha.computeIfAbsent(motivo, k -> new LongAdder()).increment();
    }
}
