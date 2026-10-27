package simulacao;

import domain.Cliente;
import domain.Conta;
import domain.ContaCorrente;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import persistence.ClienteRepository;
import persistence.ContaRepository;

/** Dispara N threads de operação sobre as mesmas contas e confere se o saldo final bate com o esperado. */
public class SimulacaoConcorrente {
    public static final BigDecimal SALDO_INICIAL = new BigDecimal("1000.00");

    /**
     * @param sincronizada true = contas reais (domain.Conta, sincronizadas); false = ContaInsegura (race condition)
     * @param persistir    true = grava cada operação no banco via JDBC (só com contas reais); false = só memória
     */
    public record Parametros(int contas, int threads, int operacoesPorThread, boolean sincronizada, boolean persistir) {
        public Parametros {
            if (contas < 2) throw new IllegalArgumentException("Use pelo menos 2 contas (as transferências precisam de duas).");
            if (threads < 1 || operacoesPorThread < 1) throw new IllegalArgumentException("Threads e operações devem ser positivas.");
            if (persistir && !sincronizada) throw new IllegalArgumentException("A versão insegura nunca é gravada no banco.");
        }
    }

    private final ClienteRepository clientes;   // só usados quando persistir = true
    private final ContaRepository repositorioContas;

    public SimulacaoConcorrente() { this(null, null); }

    public SimulacaoConcorrente(ClienteRepository clientes, ContaRepository repositorioContas) {
        this.clientes = clientes;
        this.repositorioContas = repositorioContas;
    }

    public RelatorioDesempenho executar(Parametros p) throws InterruptedException {
        List<ContaSimulada> contas = criarContas(p);
        Map<Integer, LongAdder> deltas = new ConcurrentHashMap<>();
        for (ContaSimulada c : contas) deltas.put(c.id(), new LongAdder());
        LongAdder sucessos = new LongAdder();
        LongAdder falhas = new LongAdder();
        Map<String, LongAdder> motivos = new ConcurrentHashMap<>();

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(p.threads());
        for (int i = 0; i < p.threads(); i++) {
            pool.submit(new OperacaoBancariaThread(contas, p.operacoesPorThread(), 1_000L + i, largada, deltas, sucessos, falhas, motivos));
        }
        long inicio = System.nanoTime();
        largada.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
            pool.shutdownNow();
            throw new IllegalStateException("A simulação não terminou no tempo esperado (possível deadlock).");
        }
        long tempoMs = (System.nanoTime() - inicio) / 1_000_000;

        List<RelatorioDesempenho.LinhaConta> linhas = new ArrayList<>();
        boolean bancoConfere = true;
        for (ContaSimulada c : contas) {
            BigDecimal esperado = SALDO_INICIAL.add(BigDecimal.valueOf(deltas.get(c.id()).sum(), 2));
            linhas.add(new RelatorioDesempenho.LinhaConta(c.id(), esperado, c.saldo()));
            if (p.persistir() && c.saldo().compareTo(repositorioContas.lerSaldoDoBanco(c.id())) != 0) bancoConfere = false;
        }
        Map<String, Long> motivosOrdenados = new TreeMap<>();
        motivos.forEach((k, v) -> motivosOrdenados.put(k, v.sum()));
        String titulo = p.sincronizada() ? "COM sincronização (synchronized)" + (p.persistir() ? " + JDBC" : "")
                : "SEM sincronização (race condition)";
        return new RelatorioDesempenho(titulo, p.contas(), p.threads(), p.operacoesPorThread(), sucessos.sum(), falhas.sum(),
                motivosOrdenados, tempoMs, linhas, p.persistir() ? bancoConfere : null);
    }

    private List<ContaSimulada> criarContas(Parametros p) {
        List<ContaSimulada> contas = new ArrayList<>();
        if (!p.sincronizada()) {
            for (int i = 1; i <= p.contas(); i++) contas.add(new ContaInsegura(i, SALDO_INICIAL));
            return contas;
        }
        Cliente cliente = p.persistir() ? clientes.salvar("Simulação " + System.currentTimeMillis()) : null;
        for (int i = 1; i <= p.contas(); i++) {
            Conta conta;
            if (p.persistir()) {
                conta = repositorioContas.criar(cliente.getId(), "CORRENTE", BigDecimal.ZERO, null, null);
                conta.depositar(SALDO_INICIAL, "Saldo inicial (simulação)");
            } else {
                conta = new ContaCorrente(i, 0, SALDO_INICIAL, BigDecimal.ZERO); // sem cheque especial: sem tarifas
            }
            conta.definirLimiteDiarioSaque(new BigDecimal("1000000000")); // a simulação não deve esbarrar no limite diário
            contas.add(new ContaSegura(conta));
        }
        return contas;
    }
}
