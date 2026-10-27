package simulacao;

import domain.Conta;
import domain.ContaCorrente;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;

/**
 * Demonstração controlada de deadlock, em duas partes:
 *  1) "antes": duas transferências em sentidos opostos travando as contas na ordem em que aparecem
 *     (A->B trava A e depois B; B->A trava B e depois A) => cada thread segura o que a outra quer;
 *  2) "depois": a versão corrigida, {@code Conta.transferirPara}, que sempre trava o menor id primeiro.
 *
 * Na parte 1 as threads ficam presas para sempre (monitores synchronized não podem ser interrompidos), por isso são
 * daemon e usam contas descartáveis, criadas só para a demonstração.
 */
public final class DemonstracaoDeadlock {
    public record Resultado(boolean deadlockDetectado, boolean concluiu, long tempoMs) { }

    private DemonstracaoDeadlock() { }

    public static Resultado demonstrarDeadlock(PrintStream saida) throws InterruptedException {
        Conta a = new ContaCorrente(9001, 0, new BigDecimal("1000.00"), BigDecimal.ZERO);
        Conta b = new ContaCorrente(9002, 0, new BigDecimal("1000.00"), BigDecimal.ZERO);
        CountDownLatch seguraramAPrimeira = new CountDownLatch(2);

        Thread t1 = new Thread(() -> transferirTravandoNaOrdemRecebida(a, b, seguraramAPrimeira), "transferencia-A-para-B");
        Thread t2 = new Thread(() -> transferirTravandoNaOrdemRecebida(b, a, seguraramAPrimeira), "transferencia-B-para-A");
        t1.setDaemon(true);
        t2.setDaemon(true);

        saida.println("--- ANTES: ordem de bloqueio invertida (deadlock proposital) ---");
        long inicio = System.nanoTime();
        t1.start();
        t2.start();

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] presas = null;
        for (int tentativa = 0; tentativa < 40 && presas == null; tentativa++) { // até ~4 segundos
            Thread.sleep(100);
            presas = mx.findDeadlockedThreads();
        }
        long tempoMs = (System.nanoTime() - inicio) / 1_000_000;
        if (presas == null) {
            saida.println("Nenhum deadlock detectado (inesperado).");
            return new Resultado(false, true, tempoMs);
        }
        saida.println("DEADLOCK DETECTADO pelo ThreadMXBean.findDeadlockedThreads():");
        for (ThreadInfo info : mx.getThreadInfo(presas)) {
            saida.println("  - " + info.getThreadName() + " está bloqueada esperando " + info.getLockName()
                    + ", que pertence a " + info.getLockOwnerName());
        }
        saida.println("As duas threads ficaram presas, cada uma segurando a conta que a outra quer.");
        return new Resultado(true, false, tempoMs);
    }

    public static Resultado demonstrarCorrecao(PrintStream saida) throws InterruptedException {
        final int rodadas = 20_000;
        Conta a = new ContaCorrente(9101, 0, new BigDecimal("100000.00"), BigDecimal.ZERO);
        Conta b = new ContaCorrente(9102, 0, new BigDecimal("100000.00"), BigDecimal.ZERO);
        BigDecimal valor = new BigDecimal("1.00");

        Thread t1 = new Thread(() -> { for (int i = 0; i < rodadas; i++) a.transferirPara(b, valor); }, "transferencia-A-para-B");
        Thread t2 = new Thread(() -> { for (int i = 0; i < rodadas; i++) b.transferirPara(a, valor); }, "transferencia-B-para-A");
        t1.setDaemon(true);
        t2.setDaemon(true);

        saida.println("--- DEPOIS: Conta.transferirPara trava sempre o menor id primeiro ---");
        long inicio = System.nanoTime();
        t1.start();
        t2.start();
        t1.join(20_000);
        t2.join(20_000);
        long tempoMs = (System.nanoTime() - inicio) / 1_000_000;
        boolean concluiu = !t1.isAlive() && !t2.isAlive();
        if (concluiu) {
            saida.println("Concluído sem deadlock: " + (2 * rodadas) + " transferências em sentidos opostos em " + tempoMs + " ms.");
            saida.println("Saldo A = " + a.getSaldo() + " | Saldo B = " + b.getSaldo()
                    + " | Soma = " + a.getSaldo().add(b.getSaldo()) + " (esperado 200000.00)");
        } else {
            saida.println("As threads não terminaram no prazo (deadlock?).");
        }
        return new Resultado(false, concluiu, tempoMs);
    }

    /** Versão errada de propósito: trava a conta de origem, espera a outra thread fazer o mesmo, e só então tenta a de destino. */
    private static void transferirTravandoNaOrdemRecebida(Conta de, Conta para, CountDownLatch seguraramAPrimeira) {
        synchronized (de) {
            seguraramAPrimeira.countDown();
            try {
                seguraramAPrimeira.await(); // garante que as duas threads já seguram a primeira conta
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (para) { // <-- aqui as duas travam: cada uma espera a conta que a outra segura
                de.sacar(new BigDecimal("1.00"));
                para.depositar(new BigDecimal("1.00"));
            }
        }
    }
}
