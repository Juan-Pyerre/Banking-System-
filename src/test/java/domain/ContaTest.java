package domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regras de negócio da Conta, sem banco e sem threads. */
class ContaTest {

    private static BigDecimal v(String valor) { return new BigDecimal(valor); }

    @Test
    void depositoESaqueAtualizamOSaldo() {
        Conta conta = new ContaCorrente(1, 1, v("100.00"));
        conta.depositar(v("50.00"));
        conta.sacar(v("30.00"));
        assertEquals(0, v("120.00").compareTo(conta.getSaldo()));
    }

    @Test
    void saqueMaiorQueODisponivelLancaSaldoInsuficienteESaldoNaoMuda() {
        Conta poupanca = new ContaPoupanca(2, 1, v("100.00"));
        assertThrows(BancoException.SaldoInsuficiente.class, () -> poupanca.sacar(v("100.01")));
        assertEquals(0, v("100.00").compareTo(poupanca.getSaldo()));
    }

    @Test
    void valorInvalidoEhRecusado() {
        Conta conta = new ContaCorrente(3, 1, v("10.00"));
        assertThrows(BancoException.OperacaoInvalida.class, () -> conta.depositar(v("0")));
        assertThrows(BancoException.OperacaoInvalida.class, () -> conta.sacar(v("-5")));
        assertThrows(BancoException.OperacaoInvalida.class, () -> conta.depositar(v("1.005")));
    }

    @Test
    void chequeEspecialCobraTarifaRegistradaComoTransacaoPropria() {
        List<Transacao> gravadas = new ArrayList<>();
        Conta conta = new ContaCorrente(4, 1, v("100.00"), v("500.00"));
        conta.setRegistro((transacoes, contas) -> gravadas.addAll(transacoes));

        conta.sacar(v("200.00")); // 100 do saldo + 100 do limite => tarifa de 2% sobre 100 = 2,00

        assertEquals(0, v("-102.00").compareTo(conta.getSaldo()));
        assertEquals(2, gravadas.size());
        assertEquals(TipoTransacao.SAQUE, gravadas.get(0).tipo());
        assertEquals(TipoTransacao.TARIFA, gravadas.get(1).tipo());
        assertEquals(0, v("2.00").compareTo(gravadas.get(1).valor()));
        assertEquals(gravadas.get(0).protocolo(), gravadas.get(1).protocoloRelacionado());
    }

    @Test
    void naoUltrapassaOLimiteDoChequeEspecialNemComATarifa() {
        Conta conta = new ContaCorrente(5, 1, v("0.00"), v("100.00"));
        assertThrows(BancoException.SaldoInsuficiente.class, () -> conta.sacar(v("100.00"))); // 100 + 2 de tarifa > 100
        conta.sacar(v("98.00")); // 98 + 1,96 = 99,96
        assertEquals(0, v("-99.96").compareTo(conta.getSaldo()));
    }

    @Test
    void limiteDiarioDeSaqueSomaOsSaquesDoDia() {
        Conta conta = new ContaCorrente(6, 1, v("10000.00"));
        conta.definirLimiteDiarioSaque(v("1000.00"));
        conta.sacar(v("600.00"));
        assertThrows(BancoException.LimiteExcedido.class, () -> conta.sacar(v("401.00")));
        conta.sacar(v("400.00")); // exatamente no limite
        assertEquals(0, v("9000.00").compareTo(conta.getSaldo()));
    }

    @Test
    void contaBloqueadaRecusaOperacoesEEncerramentoExigeSaldoZerado() {
        Conta conta = new ContaCorrente(7, 1, v("50.00"));
        conta.bloquear();
        assertThrows(BancoException.ContaIndisponivel.class, () -> conta.depositar(v("10.00")));
        conta.desbloquear();
        assertThrows(BancoException.OperacaoInvalida.class, conta::encerrar); // ainda tem saldo
        conta.sacar(v("50.00"));
        conta.encerrar();
        assertEquals(StatusConta.ENCERRADA, conta.getStatus());
    }

    @Test
    void falhaNaGravacaoDesfazDebitoECreditoDaTransferencia() {
        Conta origem = new ContaCorrente(8, 1, v("100.00"));
        Conta destino = new ContaCorrente(9, 1, v("100.00"));
        origem.setRegistro((t, c) -> { throw new BancoException.FalhaDePersistencia("banco fora do ar", null); });

        assertThrows(BancoException.FalhaDePersistencia.class, () -> origem.transferirPara(destino, v("40.00")));

        assertEquals(0, v("100.00").compareTo(origem.getSaldo()));
        assertEquals(0, v("100.00").compareTo(destino.getSaldo()));
    }

    @Test
    void rendimentoDaPoupancaCreditaSobreOSaldoAtual() {
        ContaPoupanca poupanca = new ContaPoupanca(10, 1, v("1000.00"));
        Transacao t = poupanca.aplicarRendimento(v("0.005"));
        assertEquals(TipoTransacao.RENDIMENTO, t.tipo());
        assertEquals(0, v("1005.00").compareTo(poupanca.getSaldo()));
    }
}
