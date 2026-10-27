package domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cliente do banco. Um cliente pode ter várias contas. */
public class Cliente {
    private final int id;
    private final String nome;
    private final List<Conta> contas = new CopyOnWriteArrayList<>();

    public Cliente(int id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public List<Conta> getContas() { return List.copyOf(contas); }
    public void adicionarConta(Conta conta) { contas.add(conta); }

    /** Soma o saldo de todas as contas do cliente. */
    public BigDecimal saldoConsolidado() {
        return contas.stream().map(Conta::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public String toString() { return "Cliente{" + id + ", " + nome + "}"; }
}
