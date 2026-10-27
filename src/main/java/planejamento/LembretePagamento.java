package planejamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Pagamento recorrente mensal (ex.: aluguel todo dia 10). */
public class LembretePagamento {
    private final int id;
    private final int contaId;
    private final String descricao;
    private final BigDecimal valor;
    private final int diaDoMes;
    private LocalDate ultimoAviso;

    public LembretePagamento(int id, int contaId, String descricao, BigDecimal valor, int diaDoMes) {
        this.id = id;
        this.contaId = contaId;
        this.descricao = descricao;
        this.valor = valor;
        this.diaDoMes = diaDoMes;
    }

    public int getId() { return id; }
    public int getContaId() { return contaId; }
    public String getDescricao() { return descricao; }
    public BigDecimal getValor() { return valor; }
    public int getDiaDoMes() { return diaDoMes; }

    /** Próxima data de vencimento a partir de {@code hoje} (inclusive). Meses curtos usam o último dia. */
    public LocalDate proximoVencimento(LocalDate hoje) {
        LocalDate neste = hoje.withDayOfMonth(Math.min(diaDoMes, hoje.lengthOfMonth()));
        if (!neste.isBefore(hoje)) return neste;
        LocalDate proximoMes = hoje.plusMonths(1).withDayOfMonth(1);
        return proximoMes.withDayOfMonth(Math.min(diaDoMes, proximoMes.lengthOfMonth()));
    }

    boolean jaAvisadoHoje(LocalDate hoje) { return hoje.equals(ultimoAviso); }
    void marcarAvisado(LocalDate hoje) { this.ultimoAviso = hoje; }
}
