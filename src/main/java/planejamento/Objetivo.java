package planejamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Meta de economia: juntar {@code valorMeta} a partir do saldo que a conta tinha ao criar o objetivo. */
public class Objetivo {
    private final int id;
    private final int contaId;
    private final String nome;
    private final BigDecimal valorMeta;
    private final LocalDate prazo;
    private final BigDecimal saldoBase;
    private boolean atingido;

    public Objetivo(int id, int contaId, String nome, BigDecimal valorMeta, LocalDate prazo, BigDecimal saldoBase) {
        this.id = id;
        this.contaId = contaId;
        this.nome = nome;
        this.valorMeta = valorMeta;
        this.prazo = prazo;
        this.saldoBase = saldoBase;
    }

    public int getId() { return id; }
    public int getContaId() { return contaId; }
    public String getNome() { return nome; }
    public BigDecimal getValorMeta() { return valorMeta; }
    public LocalDate getPrazo() { return prazo; }
    public boolean isAtingido() { return atingido; }
    void marcarAtingido() { this.atingido = true; }

    /** Quanto já foi economizado desde a criação do objetivo (nunca negativo). */
    public BigDecimal economizado(BigDecimal saldoAtual) {
        return saldoAtual.subtract(saldoBase).max(BigDecimal.ZERO);
    }

    /** Progresso em percentual, de 0 a 100. */
    public BigDecimal progressoPercentual(BigDecimal saldoAtual) {
        BigDecimal pct = economizado(saldoAtual).multiply(BigDecimal.valueOf(100)).divide(valorMeta, 1, RoundingMode.HALF_UP);
        return pct.min(BigDecimal.valueOf(100));
    }
}
