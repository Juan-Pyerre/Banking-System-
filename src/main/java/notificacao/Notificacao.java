package notificacao;

import java.time.LocalDateTime;

/** Um aviso gerado pelo sistema. {@code contaId} é null para avisos gerais. */
public record Notificacao(LocalDateTime dataHora, TipoNotificacao tipo, Integer contaId, String mensagem) { }
