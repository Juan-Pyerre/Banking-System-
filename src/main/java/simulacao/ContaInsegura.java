package simulacao;

import java.math.BigDecimal;

/**
 * ATENÇÃO: classe de DEMONSTRAÇÃO, propositalmente SEM sincronização, para expor a race condition clássica
 * do "saldo perdido" (lê o saldo, outra thread altera, e a escrita seguinte sobrescreve a alteração dela).
 * O Thread.yield() entre a leitura e a escrita só aumenta a chance de o problema aparecer.
 * Nunca use esta classe fora da simulação: a conta de verdade é {@code domain.Conta}.
 */
public class ContaInsegura implements ContaSimulada {
    private final int id;
    private long centavos; // sem synchronized, sem volatile, sem atômicos

    public ContaInsegura(int id, BigDecimal saldoInicial) {
        this.id = id;
        this.centavos = saldoInicial.movePointRight(2).longValueExact();
    }

    @Override public int id() { return id; }

    @Override public BigDecimal saldo() { return BigDecimal.valueOf(centavos, 2); }

    @Override
    public void depositar(BigDecimal valor) {
        long lido = centavos;            // 1) lê
        Thread.yield();                  // (outra thread pode alterar o saldo aqui)
        centavos = lido + valor.movePointRight(2).longValueExact(); // 2) escreve por cima
    }

    @Override
    public boolean sacar(BigDecimal valor) {
        long lido = centavos;
        long v = valor.movePointRight(2).longValueExact();
        if (lido < v) return false;
        Thread.yield();
        centavos = lido - v;
        return true;
    }

    @Override
    public boolean transferir(ContaSimulada destino, BigDecimal valor) {
        if (!sacar(valor)) return false;
        destino.depositar(valor);
        return true;
    }
}
