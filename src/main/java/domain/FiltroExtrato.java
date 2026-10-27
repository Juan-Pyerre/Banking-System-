package domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Filtros opcionais do extrato (qualquer campo nulo é ignorado). */
public record FiltroExtrato(LocalDateTime inicio, LocalDateTime fim, TipoTransacao tipo,
                            BigDecimal valorMin, BigDecimal valorMax) {
    public static final FiltroExtrato SEM_FILTRO = new FiltroExtrato(null, null, null, null, null);
}
