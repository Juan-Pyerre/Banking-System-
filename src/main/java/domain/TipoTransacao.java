package domain;

/** Tipo de uma {@link Transacao}. Cada tipo sabe se é crédito (entra dinheiro) ou débito. */
public enum TipoTransacao {
    DEPOSITO(true),
    SAQUE(false),
    TRANSFERENCIA_ENVIADA(false),
    TRANSFERENCIA_RECEBIDA(true),
    TARIFA(false),
    RENDIMENTO(true),
    ESTORNO_CREDITO(true),
    ESTORNO_DEBITO(false);

    private final boolean credito;

    TipoTransacao(boolean credito) { this.credito = credito; }

    public boolean ehCredito() { return credito; }

    /** Tipos que contam como "gasto" no planejamento financeiro. */
    public boolean ehGasto() {
        return this == SAQUE || this == TRANSFERENCIA_ENVIADA || this == TARIFA;
    }
}
