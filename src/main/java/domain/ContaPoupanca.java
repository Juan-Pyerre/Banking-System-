package domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conta poupança: nunca fica negativa, sem tarifa de crédito, e recebe rendimento periódico. */
public class ContaPoupanca extends Conta {

    public ContaPoupanca(int id, int clienteId, BigDecimal saldoInicial) {
        super(id, clienteId, saldoInicial);
        if (saldoInicial.signum() < 0) {
            throw new BancoException.OperacaoInvalida("A poupança não pode ter saldo negativo.");
        }
    }

    @Override public String tipo() { return "POUPANCA"; }

    /**
     * Aplica o rendimento do período (taxa, ex.: 0.005 = 0,5%) sobre o saldo ATUAL.
     * Roda no monitor da conta, então concorre de forma segura com depósitos e saques.
     *
     * @return a transação de rendimento, ou null se a conta não está ativa ou o rendimento é zero
     */
    public synchronized Transacao aplicarRendimento(BigDecimal taxa) {
        if (getStatus() != StatusConta.ATIVA) return null;
        BigDecimal rendimento = getSaldo().multiply(taxa).setScale(2, RoundingMode.HALF_UP);
        if (rendimento.signum() <= 0) return null;
        return creditar(rendimento, TipoTransacao.RENDIMENTO, null,
                "Rendimento da poupança (" + taxa.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%)");
    }
}
