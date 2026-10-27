package seguranca;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evita aplicar duas vezes a mesma operação. Cada operação tem uma "chave":
 *  - chave explícita (ex.: coluna "chave" do CSV importado): vale por tempo praticamente ilimitado;
 *  - assinatura (conta + tipo + valor + destino): vale só por uma janela curta de segundos.
 * A reserva é atômica, então duas threads com a mesma chave nunca passam as duas.
 */
public class DetectorDuplicidade {
    private final Map<String, Instant> reservas = new ConcurrentHashMap<>();

    /** @return true se a chave estava livre (e agora está reservada); false se é uma duplicata. */
    public boolean reservar(String chave, Duration janela) {
        Instant agora = Instant.now();
        boolean[] reservou = {false};
        reservas.compute(chave, (k, expiraEm) -> {
            if (expiraEm == null || expiraEm.isBefore(agora)) {
                reservou[0] = true;
                return agora.plus(janela);
            }
            return expiraEm;
        });
        return reservou[0];
    }

    /** Libera a chave (usado quando a operação falhou e pode ser tentada de novo). */
    public void liberar(String chave) { reservas.remove(chave); }
}
