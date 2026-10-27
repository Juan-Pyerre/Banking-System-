package app;

import arquivo.ImportadorOperacoes;
import config.Configuracao;
import java.nio.file.Path;
import notificacao.HistoricoNotificacoes;
import notificacao.Notificador;
import notificacao.ObservadorArquivoLog;
import notificacao.ObservadorConsole;
import persistence.ClienteRepository;
import persistence.ConexaoJdbc;
import persistence.ContaRepository;
import persistence.RegistroOperacoesJdbc;
import persistence.TransacaoRepository;
import planejamento.PlanejamentoService;
import seguranca.PoliticaExigirSenha;
import servico.AgendadorDeTarefas;
import servico.BancoService;
import simulacao.SimulacaoConcorrente;

/** Monta (liga) todas as peças do sistema. Usada pelo Main e pelos testes. */
public class Aplicacao {
    public final ConexaoJdbc conexao;
    public final ClienteRepository clientes;
    public final TransacaoRepository transacoes;
    public final ContaRepository contas;
    public final Notificador notificador = new Notificador();
    public final HistoricoNotificacoes historico = new HistoricoNotificacoes();
    public final PlanejamentoService planejamento;
    public final BancoService banco;
    public final AgendadorDeTarefas agendador;
    public final ImportadorOperacoes importador;
    public final SimulacaoConcorrente simulacao;

    /**
     * @param notificacoesNoConsole imprime os avisos no console (o menu usa true; testes usam false)
     * @param arquivoLog            arquivo de log das notificações (null = não grava)
     */
    public Aplicacao(ConexaoJdbc conexao, boolean notificacoesNoConsole, Path arquivoLog) {
        this.conexao = conexao;
        conexao.inicializarEsquema();

        notificador.adicionar(historico);
        if (notificacoesNoConsole) notificador.adicionar(new ObservadorConsole());
        if (arquivoLog != null) notificador.adicionar(new ObservadorArquivoLog(arquivoLog));

        this.clientes = new ClienteRepository(conexao);
        this.transacoes = new TransacaoRepository(conexao);
        this.contas = new ContaRepository(conexao, new RegistroOperacoesJdbc(conexao), transacoes);
        this.planejamento = new PlanejamentoService(transacoes, notificador);
        this.banco = new BancoService(clientes, contas, transacoes, notificador,
                new PoliticaExigirSenha(Configuracao.LIMITE_VALOR_ALTO, notificador), planejamento);
        this.agendador = new AgendadorDeTarefas(banco, notificador);
        this.importador = new ImportadorOperacoes(banco);
        this.simulacao = new SimulacaoConcorrente(clientes, contas);
    }
}
