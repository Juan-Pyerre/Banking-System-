package servico;

import domain.BancoException;
import domain.Transacao;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import notificacao.Notificador;
import notificacao.TipoNotificacao;
import util.Dinheiro;

/**
 * Tarefas que rodam em threads separadas, concorrendo com as demais operações:
 *  - transferências agendadas para um horário futuro;
 *  - rendimento periódico da poupança.
 */
public class AgendadorDeTarefas {
    public enum Status { AGENDADA, EXECUTADA, FALHOU }

    /** Uma transferência programada. Os campos de resultado são atualizados pela thread do agendador. */
    public static class TransferenciaAgendada {
        private final String id = UUID.randomUUID().toString();
        private final int origemId;
        private final int destinoId;
        private final BigDecimal valor;
        private final LocalDateTime executarEm;
        private volatile Status status = Status.AGENDADA;
        private volatile String detalhe = "";

        TransferenciaAgendada(int origemId, int destinoId, BigDecimal valor, LocalDateTime executarEm) {
            this.origemId = origemId;
            this.destinoId = destinoId;
            this.valor = valor;
            this.executarEm = executarEm;
        }

        public String getId() { return id; }
        public int getOrigemId() { return origemId; }
        public int getDestinoId() { return destinoId; }
        public BigDecimal getValor() { return valor; }
        public LocalDateTime getExecutarEm() { return executarEm; }
        public Status getStatus() { return status; }
        public String getDetalhe() { return detalhe; }
    }

    private final BancoService banco;
    private final Notificador notificador;
    private final AtomicInteger numeroThread = new AtomicInteger();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "agendador-" + numeroThread.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private final List<TransferenciaAgendada> agendamentos = new CopyOnWriteArrayList<>();
    private final AtomicInteger ciclosRendimento = new AtomicInteger();
    private volatile ScheduledFuture<?> tarefaRendimento;

    public AgendadorDeTarefas(BancoService banco, Notificador notificador) {
        this.banco = banco;
        this.notificador = notificador;
    }

    // ------------------------------------------------------------------ transferências agendadas

    /**
     * Programa uma transferência. Se o valor exigir senha de transação, ela é conferida AGORA
     * (na hora de executar não há ninguém para digitar, e nenhum lock fica esperando o usuário).
     */
    public TransferenciaAgendada agendarTransferencia(int origemId, int destinoId, BigDecimal valor,
                                                      LocalDateTime quando, String senhaTransacao) {
        if (quando.isBefore(LocalDateTime.now().minusSeconds(1))) {
            throw new BancoException.OperacaoInvalida("O horário do agendamento deve estar no futuro.");
        }
        banco.obterConta(origemId);
        banco.obterConta(destinoId);
        if (valor == null || valor.signum() <= 0) throw new BancoException.OperacaoInvalida("O valor deve ser maior que zero.");
        banco.autorizarAgendamento(origemId, valor, senhaTransacao);

        TransferenciaAgendada ag = new TransferenciaAgendada(origemId, destinoId, valor, quando);
        agendamentos.add(ag);
        long atrasoMs = Math.max(0, Duration.between(LocalDateTime.now(), quando).toMillis());
        executor.schedule(() -> executar(ag), atrasoMs, TimeUnit.MILLISECONDS);
        return ag;
    }

    private void executar(TransferenciaAgendada ag) {
        try {
            Transacao t = banco.transferirAgendada(ag.origemId, ag.destinoId, ag.valor);
            ag.detalhe = "protocolo " + t.protocolo() + " (thread " + Thread.currentThread().getName() + ")";
            ag.status = Status.EXECUTADA;
            notificador.notificar(TipoNotificacao.TRANSFERENCIA_AGENDADA, ag.origemId,
                    "Transferência agendada de " + Dinheiro.formatar(ag.valor) + " para a conta " + ag.destinoId + " executada.");
        } catch (RuntimeException e) {
            ag.detalhe = e.getMessage();
            ag.status = Status.FALHOU;
            notificador.notificar(TipoNotificacao.TRANSFERENCIA_AGENDADA, ag.origemId,
                    "Transferência agendada de " + Dinheiro.formatar(ag.valor) + " para a conta " + ag.destinoId
                            + " FALHOU: " + e.getMessage());
        }
    }

    public List<TransferenciaAgendada> listarAgendamentos(int contaId) {
        return agendamentos.stream().filter(a -> a.origemId == contaId).toList();
    }

    // ------------------------------------------------------------------ rendimento da poupança

    public synchronized void iniciarRendimento(long periodoSegundos, BigDecimal taxa) {
        if (tarefaRendimento != null) throw new BancoException.OperacaoInvalida("O rendimento automático já está ativo.");
        tarefaRendimento = executor.scheduleAtFixedRate(() -> {
            try {
                banco.aplicarRendimentoEmTodas(taxa);
                ciclosRendimento.incrementAndGet();
            } catch (RuntimeException e) {
                System.err.println("Falha ao aplicar rendimento: " + e.getMessage());
            }
        }, periodoSegundos, periodoSegundos, TimeUnit.SECONDS);
    }

    public synchronized void pararRendimento() {
        if (tarefaRendimento != null) {
            tarefaRendimento.cancel(false);
            tarefaRendimento = null;
        }
    }

    public boolean rendimentoAtivo() { return tarefaRendimento != null; }

    public int ciclosDeRendimento() { return ciclosRendimento.get(); }

    public void encerrar() { executor.shutdownNow(); }
}
