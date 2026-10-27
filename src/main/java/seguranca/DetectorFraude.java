package seguranca;

import config.Configuracao;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import util.Dinheiro;

/**
 * Sinaliza padrões suspeitos (não bloqueia, só avisa):
 *  1) muitas operações em poucos segundos;
 *  2) valor muito acima do habitual da conta (vários múltiplos da média das últimas operações).
 */
public class DetectorFraude {
    private static final int HISTORICO_MAXIMO = 20;
    private static final int MINIMO_PARA_MEDIA = 3;

    private record Registro(Instant quando, BigDecimal valor) { }

    private final Map<Integer, Deque<Registro>> historico = new ConcurrentHashMap<>();

    /** Analisa e registra a operação. Devolve o motivo da suspeita, se houver. */
    public Optional<String> analisar(int contaId, BigDecimal valor) {
        Deque<Registro> fila = historico.computeIfAbsent(contaId, k -> new ArrayDeque<>());
        synchronized (fila) {
            Instant agora = Instant.now();
            Instant limiteJanela = agora.minus(Duration.ofSeconds(Configuracao.FRAUDE_JANELA_SEGUNDOS));
            long recentes = fila.stream().filter(r -> r.quando().isAfter(limiteJanela)).count() + 1; // +1 = esta operação

            List<String> motivos = new ArrayList<>();
            if (recentes >= Configuracao.FRAUDE_MAX_OPERACOES) {
                motivos.add(recentes + " operações em " + Configuracao.FRAUDE_JANELA_SEGUNDOS + " segundos");
            }
            if (fila.size() >= MINIMO_PARA_MEDIA && valor.compareTo(Configuracao.FRAUDE_VALOR_MINIMO) >= 0) {
                BigDecimal soma = fila.stream().map(Registro::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal media = soma.divide(BigDecimal.valueOf(fila.size()), 2, java.math.RoundingMode.HALF_UP);
                if (valor.compareTo(media.multiply(BigDecimal.valueOf(Configuracao.FRAUDE_FATOR_VALOR))) >= 0) {
                    motivos.add("valor " + Dinheiro.formatar(valor) + " muito acima da média recente (" + Dinheiro.formatar(media) + ")");
                }
            }

            fila.addLast(new Registro(agora, valor));
            while (fila.size() > HISTORICO_MAXIMO) fila.removeFirst();
            return motivos.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", motivos));
        }
    }
}
