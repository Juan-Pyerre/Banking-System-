package notificacao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Padrão Observer (sujeito): distribui cada notificação para todos os observadores registrados. */
public class Notificador {
    private final List<ObservadorDeNotificacao> observadores = new CopyOnWriteArrayList<>();

    public void adicionar(ObservadorDeNotificacao observador) { observadores.add(observador); }

    public void remover(ObservadorDeNotificacao observador) { observadores.remove(observador); }

    public void notificar(TipoNotificacao tipo, Integer contaId, String mensagem) {
        Notificacao n = new Notificacao(LocalDateTime.now(), tipo, contaId, mensagem);
        for (ObservadorDeNotificacao o : observadores) {
            try {
                o.aoNotificar(n);
            } catch (RuntimeException e) {
                // um observador com problema não pode derrubar a operação bancária
                System.err.println("Falha em observador de notificação: " + e.getMessage());
            }
        }
    }
}
