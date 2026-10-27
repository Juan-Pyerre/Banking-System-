package domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Registro IMUTÁVEL de uma operação já realizada.
 *
 * @param protocolo             identificador único (UUID) usado em comprovantes e na detecção de duplicidade
 * @param contaRelacionadaId    outra conta envolvida (transferências), ou null
 * @param protocoloRelacionado  protocolo de outra transação ligada a esta (perna da transferência, estorno, tarifa), ou null
 */
public record Transacao(String protocolo, int contaId, TipoTransacao tipo, BigDecimal valor,
                        LocalDateTime dataHora, Integer contaRelacionadaId,
                        String protocoloRelacionado, String descricao) {

    public static Transacao nova(String protocolo, int contaId, TipoTransacao tipo, BigDecimal valor,
                                 Integer contaRelacionadaId, String protocoloRelacionado, String descricao) {
        return new Transacao(protocolo, contaId, tipo, valor,
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS),
                contaRelacionadaId, protocoloRelacionado, descricao);
    }

    public static Transacao nova(int contaId, TipoTransacao tipo, BigDecimal valor,
                                 Integer contaRelacionadaId, String protocoloRelacionado, String descricao) {
        return nova(UUID.randomUUID().toString(), contaId, tipo, valor, contaRelacionadaId, protocoloRelacionado, descricao);
    }

    /** Valor positivo para créditos e negativo para débitos. */
    public BigDecimal valorComSinal() {
        return tipo.ehCredito() ? valor : valor.negate();
    }
}
