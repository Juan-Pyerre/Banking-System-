package simulacao;

import java.math.BigDecimal;

/** Visão mínima de uma conta para a simulação: permite rodar o MESMO teste na versão insegura e na segura. */
public interface ContaSimulada {
    int id();

    BigDecimal saldo();

    void depositar(BigDecimal valor);

    /** @return false se foi recusado por saldo insuficiente */
    boolean sacar(BigDecimal valor);

    /** @return false se foi recusado por saldo insuficiente */
    boolean transferir(ContaSimulada destino, BigDecimal valor);
}
