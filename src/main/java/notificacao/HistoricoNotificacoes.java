package notificacao;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Guarda as notificações em memória para consulta no menu. */
public class HistoricoNotificacoes implements ObservadorDeNotificacao {
    private final List<Notificacao> todas = new CopyOnWriteArrayList<>();

    @Override
    public void aoNotificar(Notificacao n) { todas.add(n); }

    public List<Notificacao> daConta(int contaId) {
        return todas.stream().filter(n -> n.contaId() != null && n.contaId() == contaId).toList();
    }

    public List<Notificacao> todas() { return List.copyOf(todas); }
}
