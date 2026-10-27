package domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import simulacao.RelatorioDesempenho;
import simulacao.SimulacaoConcorrente;
import simulacao.SimulacaoConcorrente.Parametros;

/** Testes de concorrência: o saldo final precisa bater EXATAMENTE com a soma esperada das operações. */
class ConcorrenciaTest {

    @Test
    void depositosESaquesConcorrentesNaMesmaContaNaoPerdemNenhumValor() throws Exception {
        Conta conta = new ContaCorrente(1, 1, new BigDecimal("1000000.00"), BigDecimal.ZERO);
        conta.definirLimiteDiarioSaque(new BigDecimal("1000000000"));
        final int threadsDeposito = 8, threadsSaque = 8, repeticoes = 10_000;

        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadsDeposito; i++) {
            threads.add(new Thread(() -> {
                aguardar(largada);
                for (int k = 0; k < repeticoes; k++) conta.depositar(new BigDecimal("1.00"));
            }));
        }
        for (int i = 0; i < threadsSaque; i++) {
            threads.add(new Thread(() -> {
                aguardar(largada);
                for (int k = 0; k < repeticoes; k++) conta.sacar(new BigDecimal("0.50"));
            }));
        }
        threads.forEach(Thread::start);
        largada.countDown();
        for (Thread t : threads) t.join();

        // 1.000.000 + 8*10.000*1,00 - 8*10.000*0,50 = 1.040.000,00
        assertEquals(0, new BigDecimal("1040000.00").compareTo(conta.getSaldo()));
    }

    @Test
    void transferenciasEmSentidosOpostosNaoEntramEmDeadlock() {
        Conta a = new ContaCorrente(1, 1, new BigDecimal("50000.00"), BigDecimal.ZERO);
        Conta b = new ContaCorrente(2, 1, new BigDecimal("50000.00"), BigDecimal.ZERO);
        final int rodadas = 20_000;

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            Thread t1 = new Thread(() -> { for (int i = 0; i < rodadas; i++) a.transferirPara(b, new BigDecimal("1.00")); });
            Thread t2 = new Thread(() -> { for (int i = 0; i < rodadas; i++) b.transferirPara(a, new BigDecimal("1.00")); });
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        });

        assertEquals(0, new BigDecimal("100000.00").compareTo(a.getSaldo().add(b.getSaldo())));
        assertEquals(0, new BigDecimal("50000.00").compareTo(a.getSaldo())); // mesmas quantidades nos dois sentidos
    }

    @Test
    void simulacaoComSincronizacaoFechaOSaldoDeCadaConta() throws Exception {
        RelatorioDesempenho r = new SimulacaoConcorrente().executar(new Parametros(4, 8, 10_000, true, false));
        assertTrue(r.consistente(), r.formatar());
        assertEquals(80_000, r.totalOperacoes());
    }

    @Test
    void simulacaoSemSincronizacaoExpoeARaceCondition() throws Exception {
        RelatorioDesempenho r = new SimulacaoConcorrente().executar(new Parametros(2, 8, 50_000, false, false));
        assertFalse(r.consistente(), "Sem sincronização o saldo deveria divergir:\n" + r.formatar());
    }

    private static void aguardar(CountDownLatch largada) {
        try {
            largada.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
