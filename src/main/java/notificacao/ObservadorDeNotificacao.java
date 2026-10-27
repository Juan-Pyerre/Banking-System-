package notificacao;

/** Padrão Observer: quem quiser ser avisado implementa esta interface e se registra no {@link Notificador}. */
public interface ObservadorDeNotificacao {
    void aoNotificar(Notificacao notificacao);
}
