package simulacao;

import domain.BancoException;
import domain.Conta;
import java.math.BigDecimal;

/** Adaptador que coloca a {@link Conta} real (sincronizada) dentro da simulação. */
public class ContaSegura implements ContaSimulada {
    private final Conta conta;

    public ContaSegura(Conta conta) { this.conta = conta; }

    public Conta conta() { return conta; }

    @Override public int id() { return conta.getId(); }

    @Override public BigDecimal saldo() { return conta.getSaldo(); }

    @Override public void depositar(BigDecimal valor) { conta.depositar(valor); }

    @Override
    public boolean sacar(BigDecimal valor) {
        try {
            conta.sacar(valor);
            return true;
        } catch (BancoException.SaldoInsuficiente e) {
            return false;
        }
    }

    @Override
    public boolean transferir(ContaSimulada destino, BigDecimal valor) {
        try {
            conta.transferirPara(((ContaSegura) destino).conta, valor);
            return true;
        } catch (BancoException.SaldoInsuficiente e) {
            return false;
        }
    }
}
