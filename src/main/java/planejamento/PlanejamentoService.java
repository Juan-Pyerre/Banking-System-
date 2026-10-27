package planejamento;

import config.Configuracao;
import domain.BancoException;
import domain.Conta;
import domain.TipoTransacao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import notificacao.Notificador;
import notificacao.TipoNotificacao;
import persistence.TransacaoRepository;
import util.Dinheiro;

/**
 * Planejamento financeiro: limites de gasto por categoria, objetivos (metas) e lembretes de pagamento mensal.
 * Os gastos são calculados a partir das transações gravadas no banco; os cadastros de limites, objetivos e
 * lembretes ficam em memória (valem enquanto o programa está aberto).
 */
public class PlanejamentoService {
    /** Uma linha do comparativo "limite x gasto" do mês. */
    public record LinhaGasto(TipoTransacao categoria, BigDecimal limite, BigDecimal gasto) {
        public BigDecimal percentual() {
            return gasto.multiply(BigDecimal.valueOf(100)).divide(limite, 1, RoundingMode.HALF_UP);
        }
    }

    private final TransacaoRepository transacoes;
    private final Notificador notificador;
    private final Map<Integer, Map<TipoTransacao, BigDecimal>> limites = new ConcurrentHashMap<>();
    private final Set<String> alertasEmitidos = ConcurrentHashMap.newKeySet();
    private final List<Objetivo> objetivos = new CopyOnWriteArrayList<>();
    private final List<LembretePagamento> lembretes = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequencia = new AtomicInteger();

    public PlanejamentoService(TransacaoRepository transacoes, Notificador notificador) {
        this.transacoes = transacoes;
        this.notificador = notificador;
    }

    // ---------------------------------------------------------------- limites por categoria

    public void definirLimite(int contaId, TipoTransacao categoria, BigDecimal limite) {
        if (!categoria.ehGasto()) {
            throw new BancoException.OperacaoInvalida("Categorias de gasto válidas: SAQUE, TRANSFERENCIA_ENVIADA e TARIFA.");
        }
        if (limite == null || limite.signum() <= 0) throw new BancoException.OperacaoInvalida("O limite deve ser maior que zero.");
        limites.computeIfAbsent(contaId, k -> new EnumMap<>(TipoTransacao.class)).put(categoria, limite);
    }

    /** Compara, para o mês atual, o limite de cada categoria com o que já foi gasto. */
    public List<LinhaGasto> relatorioDoMes(int contaId) {
        List<LinhaGasto> linhas = new ArrayList<>();
        LocalDateTime inicio = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fim = inicio.plusMonths(1);
        for (Map.Entry<TipoTransacao, BigDecimal> e : limites.getOrDefault(contaId, Map.of()).entrySet()) {
            BigDecimal gasto = transacoes.somarPorTipo(contaId, e.getKey(), inicio, fim);
            linhas.add(new LinhaGasto(e.getKey(), e.getValue(), gasto));
        }
        return linhas;
    }

    // ---------------------------------------------------------------- objetivos

    public Objetivo criarObjetivo(Conta conta, String nome, BigDecimal valorMeta, LocalDate prazo) {
        if (valorMeta == null || valorMeta.signum() <= 0) throw new BancoException.OperacaoInvalida("O valor da meta deve ser maior que zero.");
        if (prazo.isBefore(LocalDate.now())) throw new BancoException.OperacaoInvalida("O prazo não pode estar no passado.");
        Objetivo o = new Objetivo(sequencia.incrementAndGet(), conta.getId(), nome, valorMeta, prazo, conta.getSaldo());
        objetivos.add(o);
        return o;
    }

    public List<Objetivo> listarObjetivos(int contaId) {
        return objetivos.stream().filter(o -> o.getContaId() == contaId).toList();
    }

    // ---------------------------------------------------------------- lembretes

    public LembretePagamento cadastrarLembrete(int contaId, String descricao, BigDecimal valor, int diaDoMes) {
        if (diaDoMes < 1 || diaDoMes > 31) throw new BancoException.OperacaoInvalida("O dia do mês deve estar entre 1 e 31.");
        if (valor == null || valor.signum() <= 0) throw new BancoException.OperacaoInvalida("O valor deve ser maior que zero.");
        LembretePagamento l = new LembretePagamento(sequencia.incrementAndGet(), contaId, descricao, valor, diaDoMes);
        lembretes.add(l);
        return l;
    }

    public List<LembretePagamento> listarLembretes(int contaId) {
        return lembretes.stream().filter(l -> l.getContaId() == contaId).toList();
    }

    /** Avisa sobre pagamentos que vencem nos próximos dias (no máximo um aviso por lembrete por dia). */
    public int verificarLembretes(int contaId, LocalDate hoje) {
        int avisos = 0;
        for (LembretePagamento l : listarLembretes(contaId)) {
            LocalDate vencimento = l.proximoVencimento(hoje);
            long dias = ChronoUnit.DAYS.between(hoje, vencimento);
            if (dias <= Configuracao.LEMBRETE_DIAS_ANTECEDENCIA && !l.jaAvisadoHoje(hoje)) {
                l.marcarAvisado(hoje);
                notificador.notificar(TipoNotificacao.LEMBRETE_PAGAMENTO, contaId,
                        "Pagamento \"" + l.getDescricao() + "\" de " + Dinheiro.formatar(l.getValor())
                                + (dias == 0 ? " vence HOJE." : " vence em " + dias + " dia(s) (" + vencimento + ")."));
                avisos++;
            }
        }
        return avisos;
    }

    // ---------------------------------------------------------------- gancho chamado após cada operação

    /** Confere limites de gasto e metas da conta depois de uma operação. */
    public void aposOperacao(Conta conta) {
        verificarLimites(conta.getId());
        verificarObjetivos(conta);
    }

    private void verificarLimites(int contaId) {
        String mes = LocalDate.now().getYear() + "-" + LocalDate.now().getMonthValue();
        for (LinhaGasto linha : relatorioDoMes(contaId)) {
            BigDecimal pct = linha.percentual();
            if (pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
                avisarUmaVez(contaId + "|" + linha.categoria() + "|" + mes + "|100", contaId,
                        "Limite de " + linha.categoria() + " ULTRAPASSADO: gasto " + Dinheiro.formatar(linha.gasto())
                                + " de " + Dinheiro.formatar(linha.limite()) + " no mês.");
            } else if (pct.compareTo(BigDecimal.valueOf(80)) >= 0) {
                avisarUmaVez(contaId + "|" + linha.categoria() + "|" + mes + "|80", contaId,
                        "Você já usou " + pct + "% do limite de " + linha.categoria() + " do mês.");
            }
        }
    }

    private void avisarUmaVez(String chave, int contaId, String mensagem) {
        if (alertasEmitidos.add(chave)) notificador.notificar(TipoNotificacao.LIMITE_GASTO, contaId, mensagem);
    }

    private void verificarObjetivos(Conta conta) {
        for (Objetivo o : listarObjetivos(conta.getId())) {
            if (!o.isAtingido() && o.economizado(conta.getSaldo()).compareTo(o.getValorMeta()) >= 0) {
                o.marcarAtingido();
                notificador.notificar(TipoNotificacao.META_ATINGIDA, conta.getId(),
                        "Meta \"" + o.getNome() + "\" atingida! Você juntou " + Dinheiro.formatar(o.getValorMeta()) + ".");
            }
        }
    }
}
