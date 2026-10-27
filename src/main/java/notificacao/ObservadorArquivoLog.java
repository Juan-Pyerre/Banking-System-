package notificacao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;

/** Grava cada notificação em um arquivo de log (uma linha por aviso). */
public class ObservadorArquivoLog implements ObservadorDeNotificacao {
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path arquivo;

    public ObservadorArquivoLog(Path arquivo) { this.arquivo = arquivo; }

    @Override
    public synchronized void aoNotificar(Notificacao n) {
        String linha = n.dataHora().format(DATA_HORA) + " | " + n.tipo() + " | conta=" + n.contaId() + " | " + n.mensagem()
                + System.lineSeparator();
        try {
            if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, linha, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gravar o log de notificações", e);
        }
    }
}
