package notificacao;

import java.time.format.DateTimeFormatter;

/** Imprime cada notificação no console. */
public class ObservadorConsole implements ObservadorDeNotificacao {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void aoNotificar(Notificacao n) {
        String conta = n.contaId() == null ? "" : " (conta " + n.contaId() + ")";
        System.out.println("  >> [" + n.tipo() + "]" + conta + " " + n.mensagem() + "  " + n.dataHora().format(HORA));
    }
}
