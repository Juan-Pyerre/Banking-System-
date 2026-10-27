package servico;

import static org.junit.jupiter.api.Assertions.*;

import app.Aplicacao;
import arquivo.ExportadorExtrato;
import arquivo.ExportadorExtrato.Formato;
import arquivo.ImportadorOperacoes.ResultadoImportacao;
import domain.BancoException;
import domain.Cliente;
import domain.Conta;
import domain.FiltroExtrato;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.ConexaoJdbc;
import persistence.ContaRepository;
import persistence.RegistroOperacoesJdbc;
import persistence.TransacaoRepository;
import seguranca.Credencial;
import seguranca.DetectorDuplicidade;
import seguranca.DetectorFraude;

/** Testes de integração (H2 em memória): persistência, estorno, segurança, arquivos e agendamento. */
class SistemaTest {
    private Aplicacao app;
    private Cliente cliente;

    private static BigDecimal v(String valor) { return new BigDecimal(valor); }

    @BeforeEach
    void preparar() {
        app = new Aplicacao(new ConexaoJdbc("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"), false, null);
        cliente = app.banco.criarCliente("Cliente de Teste");
    }

    private Conta abrir(String tipo, String saldo) {
        return app.banco.abrirConta(cliente.getId(), tipo, v(saldo), "1234", "987654");
    }

    // ------------------------------------------------------------------ persistência (JDBC)

    @Test
    void contaETransacoesSobrevivemAUmaNovaExecucao() {
        Conta conta = abrir("CORRENTE", "500.00");
        app.banco.depositar(conta.getId(), v("120.50"));
        app.banco.sacar(conta.getId(), v("20.50"), null);

        // "nova execução": repositório novo, sem cache, lendo só do banco
        TransacaoRepository transacoes = new TransacaoRepository(app.conexao);
        ContaRepository outro = new ContaRepository(app.conexao, new RegistroOperacoesJdbc(app.conexao), transacoes);
        Conta recarregada = outro.buscarPorId(conta.getId()).orElseThrow();

        assertNotSame(conta, recarregada);
        assertEquals(0, v("600.00").compareTo(recarregada.getSaldo()));
        assertEquals(3, transacoes.listarPorConta(conta.getId()).size()); // saldo inicial, depósito, saque
    }

    @Test
    void contaComSaldoNegativoNoChequeEspecialPodeSerRecarregadaDoBanco() {
        Conta conta = abrir("CORRENTE", "100.00");
        app.banco.sacar(conta.getId(), v("200.00"), null); // usa o cheque especial: saldo = -102,00 (com tarifa)

        TransacaoRepository transacoes = new TransacaoRepository(app.conexao);
        ContaRepository outro = new ContaRepository(app.conexao, new RegistroOperacoesJdbc(app.conexao), transacoes);
        Conta recarregada = outro.buscarPorId(conta.getId()).orElseThrow();

        assertEquals(0, v("-102.00").compareTo(recarregada.getSaldo()));
    }

    @Test
    void transferenciaGravaDebitoECreditoJuntosEComRollbackSeAlgoFalhar() {
        Conta a = abrir("CORRENTE", "1000.00");
        Conta b = abrir("CORRENTE", "0.00");
        String protocoloRepetido = UUID.randomUUID().toString();
        Transacao debito = Transacao.nova(protocoloRepetido, a.getId(), TipoTransacao.SAQUE, v("10.00"), null, null, "x");
        Transacao credito = Transacao.nova(protocoloRepetido, b.getId(), TipoTransacao.DEPOSITO, v("10.00"), null, null, "y");

        // o segundo INSERT viola a chave primária: a transação JDBC inteira deve voltar atrás
        assertThrows(BancoException.FalhaDePersistencia.class,
                () -> new RegistroOperacoesJdbc(app.conexao).registrar(List.of(debito, credito), List.of(a, b)));

        assertTrue(app.transacoes.buscarPorProtocolo(protocoloRepetido).isEmpty(), "nem o primeiro INSERT pode ter ficado");
    }

    @Test
    void transferenciaNormalAtualizaMemoriaEBanco() {
        Conta a = abrir("CORRENTE", "1000.00");
        Conta b = abrir("POUPANCA", "10.00");
        app.banco.transferir(a.getId(), b.getId(), v("250.00"), null);
        assertEquals(0, v("750.00").compareTo(app.contas.lerSaldoDoBanco(a.getId())));
        assertEquals(0, v("260.00").compareTo(app.contas.lerSaldoDoBanco(b.getId())));
        assertEquals(0, v("1010.00").compareTo(app.banco.saldoConsolidado(cliente.getId())));
    }

    @Test
    void simulacaoComJdbcBateSaldoNaMemoriaENoBanco() throws Exception {
        var r = app.simulacao.executar(new simulacao.SimulacaoConcorrente.Parametros(3, 4, 150, true, true));
        assertTrue(r.consistente(), r.formatar());
        assertEquals(Boolean.TRUE, r.bancoConfere());
    }

    // ------------------------------------------------------------------ estorno

    @Test
    void estornoMantemOHistoricoEEsSoPodeSerFeitoUmaVez() {
        Conta conta = abrir("CORRENTE", "100.00");
        Transacao deposito = app.banco.depositar(conta.getId(), v("40.00"));

        app.banco.estornar(deposito.protocolo());

        assertEquals(0, v("100.00").compareTo(conta.getSaldo()));
        List<Transacao> extrato = app.banco.extrato(conta.getId(), FiltroExtrato.SEM_FILTRO);
        assertEquals(3, extrato.size()); // saldo inicial, depósito ORIGINAL (mantido) e o estorno
        assertEquals(TipoTransacao.DEPOSITO, extrato.get(1).tipo());
        assertEquals(TipoTransacao.ESTORNO_DEBITO, extrato.get(2).tipo());
        assertEquals(deposito.protocolo(), extrato.get(2).protocoloRelacionado());
        assertThrows(BancoException.OperacaoInvalida.class, () -> app.banco.estornar(deposito.protocolo()));
    }

    @Test
    void estornoDeTransferenciaDevolveOValorNasDuasContas() {
        Conta a = abrir("CORRENTE", "1000.00");
        Conta b = abrir("CORRENTE", "0.00");
        Transacao enviada = app.banco.transferir(a.getId(), b.getId(), v("300.00"), null);

        app.banco.estornar(enviada.protocolo());

        assertEquals(0, v("1000.00").compareTo(a.getSaldo()));
        assertEquals(0, v("0.00").compareTo(b.getSaldo()));
        assertThrows(BancoException.OperacaoInvalida.class, () -> app.banco.estornar(enviada.protocolo()));
    }

    // ------------------------------------------------------------------ segurança

    @Test
    void senhaEGuardadaComoHashComSalt() {
        Credencial c1 = Credencial.criar("1234");
        Credencial c2 = Credencial.criar("1234");
        assertNotEquals(c1.getSalt(), c2.getSalt());
        assertNotEquals(c1.getHash(), c2.getHash()); // mesmo PIN, salts diferentes => hashes diferentes
        assertFalse(c1.getHash().contains("1234"));
        assertTrue(c1.confere("1234"));
        assertFalse(c1.confere("1235"));
    }

    @Test
    void contaEBloqueadaAposTentativasDeLoginErradas() {
        Conta conta = abrir("CORRENTE", "10.00");
        assertThrows(BancoException.Autenticacao.class, () -> app.banco.autenticar(conta.getId(), "0000"));
        assertThrows(BancoException.Autenticacao.class, () -> app.banco.autenticar(conta.getId(), "0000"));
        BancoException terceira = assertThrows(BancoException.Autenticacao.class, () -> app.banco.autenticar(conta.getId(), "0000"));
        assertTrue(terceira.getMessage().contains("BLOQUEADA"));
        // mesmo com o PIN certo, a conta bloqueada recusa
        assertThrows(BancoException.ContaIndisponivel.class, () -> app.banco.autenticar(conta.getId(), "1234"));
        app.banco.desbloquear(conta.getId());
        assertDoesNotThrow(() -> app.banco.autenticar(conta.getId(), "1234"));
    }

    @Test
    void valorAltoExigeSenhaDeTransacao() {
        Conta a = abrir("CORRENTE", "20000.00");
        Conta b = abrir("CORRENTE", "0.00");
        assertThrows(BancoException.Autenticacao.class, () -> app.banco.transferir(a.getId(), b.getId(), v("6000.00"), null));
        assertThrows(BancoException.Autenticacao.class, () -> app.banco.transferir(a.getId(), b.getId(), v("6000.00"), "000000"));
        assertEquals(0, v("20000.00").compareTo(a.getSaldo())); // nada foi movimentado
        app.banco.transferir(a.getId(), b.getId(), v("6000.00"), "987654");
        assertEquals(0, v("14000.00").compareTo(a.getSaldo()));
    }

    @Test
    void operacaoDuplicadaNaoEAplicadaDuasVezes() {
        Conta conta = abrir("CORRENTE", "0.00");
        app.banco.depositar(conta.getId(), v("77.00"));
        assertThrows(BancoException.Duplicidade.class, () -> app.banco.depositar(conta.getId(), v("77.00")));
        assertEquals(0, v("77.00").compareTo(conta.getSaldo()));

        app.banco.depositar(conta.getId(), v("5.00"), "chave-abc");
        assertThrows(BancoException.Duplicidade.class, () -> app.banco.depositar(conta.getId(), v("5.00"), "chave-abc"));
        assertEquals(0, v("82.00").compareTo(conta.getSaldo()));
    }

    @Test
    void detectorDeDuplicidadeReservaChaveDeFormaAtomica() {
        DetectorDuplicidade d = new DetectorDuplicidade();
        assertTrue(d.reservar("k", Duration.ofSeconds(5)));
        assertFalse(d.reservar("k", Duration.ofSeconds(5)));
        d.liberar("k");
        assertTrue(d.reservar("k", Duration.ofSeconds(5)));
    }

    @Test
    void detectorDeFraudeSinalizaRajadaEValorForaDoComum() {
        DetectorFraude f = new DetectorFraude();
        for (int i = 0; i < 4; i++) assertTrue(f.analisar(1, v("10.00")).isEmpty());
        assertTrue(f.analisar(1, v("10.00")).isPresent(), "5 operações em poucos segundos");

        DetectorFraude g = new DetectorFraude();
        for (int i = 0; i < 3; i++) g.analisar(2, v("100.00"));
        assertTrue(g.analisar(2, v("4000.00")).isPresent(), "valor muito acima da média");
    }

    // ------------------------------------------------------------------ arquivos

    @Test
    void extratoComFiltroEExportacaoEmTxtCsvEJson(@TempDir Path pasta) throws Exception {
        Conta conta = abrir("CORRENTE", "1000.00");
        app.banco.depositar(conta.getId(), v("10.00"));
        app.banco.sacar(conta.getId(), v("30.00"), null);

        List<Transacao> saques = app.banco.extrato(conta.getId(), new FiltroExtrato(null, null, TipoTransacao.SAQUE, null, null));
        assertEquals(1, saques.size());
        List<Transacao> grandes = app.banco.extrato(conta.getId(), new FiltroExtrato(null, null, null, v("100.00"), null));
        assertEquals(1, grandes.size()); // só o saldo inicial de 1000

        List<Transacao> todas = app.banco.extrato(conta.getId(), FiltroExtrato.SEM_FILTRO);
        String txt = Files.readString(ExportadorExtrato.exportar(conta, todas, Formato.TXT, pasta.resolve("e.txt")));
        String csv = Files.readString(ExportadorExtrato.exportar(conta, todas, Formato.CSV, pasta.resolve("e.csv")));
        String json = Files.readString(ExportadorExtrato.exportar(conta, todas, Formato.JSON, pasta.resolve("e.json")));
        assertTrue(txt.contains("EXTRATO BANCÁRIO") && txt.contains("SAQUE") && txt.contains("Saldo atual"));
        assertTrue(csv.startsWith("protocolo;data_hora;tipo;valor") && csv.contains(";-30.00;"));
        assertTrue(json.contains("\"transacoes\"") && json.contains("\"valor\": -30.00"));
    }

    @Test
    void importacaoDeCsvExecutaEmLoteERelataSucessosFalhasEDuplicadas(@TempDir Path pasta) throws Exception {
        Conta a = abrir("CORRENTE", "100.00");
        Conta b = abrir("CORRENTE", "0.00");
        Path csv = pasta.resolve("operacoes.csv");
        Files.writeString(csv, String.join("\n",
                "chave;tipo;conta;destino;valor",
                "op1;DEPOSITO;" + a.getId() + ";;100,00",
                "op2;DEPOSITO;" + a.getId() + ";;100,00",
                "op1;DEPOSITO;" + a.getId() + ";;100,00",          // chave repetida => duplicada
                "op3;SAQUE;" + a.getId() + ";;99999,00",           // valor alto sem senha => falha
                "op4;TRANSFERENCIA;" + a.getId() + ";" + b.getId() + ";50,00",
                "op5;PIX;" + a.getId() + ";;1,00"));               // tipo desconhecido => falha

        ResultadoImportacao r = app.importador.importar(csv, pasta.resolve("relatorio.txt"));

        assertEquals(6, r.total());
        assertEquals(3, r.sucessos());
        assertEquals(1, r.duplicadas());
        assertEquals(2, r.falhas());
        assertEquals(0, v("250.00").compareTo(a.getSaldo()));
        assertEquals(0, v("50.00").compareTo(b.getSaldo()));
        assertTrue(Files.readString(pasta.resolve("relatorio.txt")).contains("Duplicadas: 1"));
    }

    // ------------------------------------------------------------------ agendamento

    @Test
    void transferenciaAgendadaExecutaEmOutraThreadNoHorario() throws Exception {
        Conta a = abrir("CORRENTE", "500.00");
        Conta b = abrir("CORRENTE", "0.00");
        var ag = app.agendador.agendarTransferencia(a.getId(), b.getId(), v("100.00"), LocalDateTime.now().plusSeconds(1), null);
        assertEquals(AgendadorDeTarefas.Status.AGENDADA, ag.getStatus());

        for (int i = 0; i < 50 && ag.getStatus() == AgendadorDeTarefas.Status.AGENDADA; i++) Thread.sleep(100);

        assertEquals(AgendadorDeTarefas.Status.EXECUTADA, ag.getStatus(), ag.getDetalhe());
        assertTrue(ag.getDetalhe().contains("agendador-"), "deve rodar em thread do agendador");
        assertEquals(0, v("400.00").compareTo(a.getSaldo()));
        assertEquals(0, v("100.00").compareTo(b.getSaldo()));
        app.agendador.encerrar();
    }
}
