package gui;

import java.awt.Color;
import javax.swing.SwingUtilities;
import notificacao.Notificacao;
import notificacao.ObservadorDeNotificacao;

/** Padrão Observer: transforma cada notificação do sistema em um aviso visual no canto da tela. */
public class ObservadorToast implements ObservadorDeNotificacao {
    @Override
    public void aoNotificar(Notificacao n) {
        Color cor = switch (n.tipo()) {
            case FRAUDE_SUSPEITA, CONTA_BLOQUEADA, DUPLICIDADE -> Ui.VERMELHO;
            case VALOR_ALTO, SALDO_BAIXO, LIMITE_GASTO -> Ui.LARANJA;
            case META_ATINGIDA, TRANSFERENCIA_AGENDADA -> Ui.VERDE;
            default -> Ui.LILAS;
        };
        String titulo = n.tipo().name().replace('_', ' ') + (n.contaId() == null ? "" : "  ·  conta " + n.contaId());
        SwingUtilities.invokeLater(() -> Toast.mostrar(titulo, n.mensagem(), cor));
    }
}
