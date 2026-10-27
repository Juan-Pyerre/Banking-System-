package domain;

import config.Configuracao;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conta corrente: pode ficar negativa até o limite do cheque especial e paga tarifa ao usar esse crédito. */
public class ContaCorrente extends Conta {
    private final BigDecimal limiteChequeEspecial;

    public ContaCorrente(int id, int clienteId, BigDecimal saldoInicial) {
        this(id, clienteId, saldoInicial, Configuracao.LIMITE_CHEQUE_ESPECIAL_PADRAO);
    }

    public ContaCorrente(int id, int clienteId, BigDecimal saldoInicial, BigDecimal limiteChequeEspecial) {
        super(id, clienteId, saldoInicial);
        this.limiteChequeEspecial = limiteChequeEspecial.setScale(2, RoundingMode.HALF_UP);
        // saldo negativo é permitido, mas só até o limite do cheque especial (também vale ao recarregar do banco)
        if (saldoInicial.add(this.limiteChequeEspecial).signum() < 0) {
            throw new BancoException.OperacaoInvalida("O saldo não pode ficar abaixo do limite do cheque especial.");
        }
    }

    @Override public String tipo() { return "CORRENTE"; }

    @Override public BigDecimal limiteChequeEspecial() { return limiteChequeEspecial; }

    /** Tarifa = percentual sobre a parte do débito que NÃO é coberta pelo saldo positivo (ou seja, que usa o limite). */
    @Override
    protected BigDecimal calcularTarifaCredito(BigDecimal valor, BigDecimal saldoAtual) {
        BigDecimal coberto = saldoAtual.max(BigDecimal.ZERO);
        BigDecimal usoDoLimite = valor.subtract(coberto).max(BigDecimal.ZERO);
        return usoDoLimite.multiply(Configuracao.TARIFA_CREDITO_PERCENTUAL).setScale(2, RoundingMode.HALF_UP);
    }
}
