package servico;

import config.Configuracao;
import domain.BancoException;
import domain.Cliente;
import domain.Conta;
import domain.ContaPoupanca;
import domain.FiltroExtrato;
import domain.StatusConta;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import notificacao.Notificador;
import notificacao.TipoNotificacao;
import persistence.ClienteRepository;
import persistence.ContaRepository;
import persistence.TransacaoRepository;
import planejamento.PlanejamentoService;
import seguranca.Credencial;
import seguranca.DetectorDuplicidade;
import seguranca.DetectorFraude;
import seguranca.PoliticaDeValorAlto;
import util.Dinheiro;

/**
 * Casos de uso do banco. Orquestra as regras que ficam FORA da conta (duplicidade, fraude, política de valor alto,
 * notificações, planejamento) e delega à {@link Conta} tudo o que altera saldo.
 *
 * Ordem em cada operação: validações e senhas (sem nenhum lock) -> reserva anti-duplicidade -> análise de fraude
 * -> operação na Conta (seção crítica curta) -> avisos.
 */
public class BancoService {
    private static final Duration CHAVE_EXPLICITA = Duration.ofDays(36_500);

    private final ClienteRepository clientes;
    private final ContaRepository contas;
    private final TransacaoRepository transacoes;
    private final Notificador notificador;
    private final PoliticaDeValorAlto politica;
    private final PlanejamentoService planejamento;
    private final DetectorFraude fraude = new DetectorFraude();
    private final DetectorDuplicidade duplicidade = new DetectorDuplicidade();
    private final Set<String> estornosEmCurso = ConcurrentHashMap.newKeySet();

    public BancoService(ClienteRepository clientes, ContaRepository contas, TransacaoRepository transacoes,
                        Notificador notificador, PoliticaDeValorAlto politica, PlanejamentoService planejamento) {
        this.clientes = clientes;
        this.contas = contas;
        this.transacoes = transacoes;
        this.notificador = notificador;
        this.politica = politica;
        this.planejamento = planejamento;
    }

    // ------------------------------------------------------------------ clientes e contas

    public Cliente criarCliente(String nome) {
        if (nome == null || nome.isBlank()) throw new BancoException.OperacaoInvalida("Informe o nome do cliente.");
        return clientes.salvar(nome.trim());
    }

    public Cliente obterCliente(int id) {
        return clientes.buscarPorId(id).orElseThrow(() -> new BancoException.OperacaoInvalida("Cliente " + id + " não encontrado."));
    }

    /** Abre uma conta. O saldo inicial entra como um depósito, para o extrato sempre fechar com o saldo. */
    public Conta abrirConta(int clienteId, String tipo, BigDecimal saldoInicial, String pin, String senhaTransacao) {
        obterCliente(clienteId);
        String t = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!t.equals("CORRENTE") && !t.equals("POUPANCA")) {
            throw new BancoException.OperacaoInvalida("Tipo de conta inválido (use CORRENTE ou POUPANCA).");
        }
        if (saldoInicial == null || saldoInicial.signum() < 0) {
            throw new BancoException.OperacaoInvalida("O saldo inicial não pode ser negativo.");
        }
        exigirSenhaNumerica(pin, "PIN");
        exigirSenhaNumerica(senhaTransacao, "Senha de transação");
        if (pin.equals(senhaTransacao)) {
            throw new BancoException.OperacaoInvalida("A senha de transação deve ser diferente do PIN.");
        }
        Conta conta = contas.criar(clienteId, t,
                t.equals("CORRENTE") ? Configuracao.LIMITE_CHEQUE_ESPECIAL_PADRAO : BigDecimal.ZERO,
                Credencial.criar(pin), Credencial.criar(senhaTransacao));
        if (saldoInicial.signum() > 0) conta.depositar(saldoInicial, "Saldo inicial");
        return conta;
    }

    public Conta obterConta(int id) {
        return contas.buscarPorId(id).orElseThrow(() -> new BancoException.OperacaoInvalida("Conta " + id + " não encontrada."));
    }

    public List<Conta> contasDoCliente(int clienteId) { return contas.listarPorCliente(clienteId); }

    public BigDecimal saldoConsolidado(int clienteId) {
        return contas.listarPorCliente(clienteId).stream().map(Conta::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Login: confere o PIN; erros seguidos bloqueiam a conta. */
    public Conta autenticar(int contaId, String pin) {
        Conta conta = obterConta(contaId);
        try {
            conta.autenticar(pin);
        } catch (BancoException.Autenticacao e) {
            if (conta.getStatus() == StatusConta.BLOQUEADA) {
                notificador.notificar(TipoNotificacao.CONTA_BLOQUEADA, contaId, "Conta bloqueada por excesso de tentativas de login.");
            }
            throw e;
        }
        planejamento.verificarLembretes(contaId, LocalDate.now());
        return conta;
    }

    public void bloquear(int contaId) {
        obterConta(contaId).bloquear();
        notificador.notificar(TipoNotificacao.CONTA_BLOQUEADA, contaId, "Conta bloqueada pela administração.");
    }

    public void desbloquear(int contaId) { obterConta(contaId).desbloquear(); }

    public void encerrar(int contaId) { obterConta(contaId).encerrar(); }

    // ------------------------------------------------------------------ operações

    public Transacao depositar(int contaId, BigDecimal valor) { return depositar(contaId, valor, null); }

    /** @param chave chave explícita de idempotência (ex.: do CSV); null usa a assinatura da operação */
    public Transacao depositar(int contaId, BigDecimal valor, String chave) {
        exigirValor(valor);
        Conta conta = obterConta(contaId);
        String reserva = reservar(contaId, TipoTransacao.DEPOSITO, valor, null, chave);
        try {
            analisarFraude(conta, valor);
            Transacao t = conta.depositar(valor);
            aposOperacao(conta);
            return t;
        } catch (RuntimeException e) {
            duplicidade.liberar(reserva);
            throw e;
        }
    }

    public Transacao sacar(int contaId, BigDecimal valor, String senhaTransacao) {
        return sacar(contaId, valor, senhaTransacao, null);
    }

    public Transacao sacar(int contaId, BigDecimal valor, String senhaTransacao, String chave) {
        exigirValor(valor);
        Conta conta = obterConta(contaId);
        politica.validar(conta, valor, senhaTransacao);
        String reserva = reservar(contaId, TipoTransacao.SAQUE, valor, null, chave);
        try {
            analisarFraude(conta, valor);
            Transacao t = conta.sacar(valor);
            aposOperacao(conta);
            return t;
        } catch (RuntimeException e) {
            duplicidade.liberar(reserva);
            throw e;
        }
    }

    public Transacao transferir(int origemId, int destinoId, BigDecimal valor, String senhaTransacao) {
        return transferir(origemId, destinoId, valor, senhaTransacao, null);
    }

    public Transacao transferir(int origemId, int destinoId, BigDecimal valor, String senhaTransacao, String chave) {
        return transferirInterno(origemId, destinoId, valor, senhaTransacao, chave, true);
    }

    /** Execução de uma transferência agendada: a autorização (senha) já foi dada no momento do agendamento. */
    public Transacao transferirAgendada(int origemId, int destinoId, BigDecimal valor) {
        return transferirInterno(origemId, destinoId, valor, null, null, false);
    }

    private Transacao transferirInterno(int origemId, int destinoId, BigDecimal valor, String senha, String chave, boolean verificarPolitica) {
        exigirValor(valor);
        Conta origem = obterConta(origemId);
        Conta destino = obterConta(destinoId);
        String reserva = null;
        if (verificarPolitica) {
            politica.validar(origem, valor, senha);
            reserva = reservar(origemId, TipoTransacao.TRANSFERENCIA_ENVIADA, valor, destinoId, chave);
        }
        try {
            analisarFraude(origem, valor);
            Transacao t = origem.transferirPara(destino, valor);
            aposOperacao(origem);
            planejamento.aposOperacao(destino);
            return t;
        } catch (RuntimeException e) {
            duplicidade.liberar(reserva);
            throw e;
        }
    }

    /** A autorização reforçada é exigida para este valor? (o menu pergunta a senha ANTES de chamar a operação) */
    public boolean exigeSenhaReforcada(BigDecimal valor) { return politica.exigeSenha(valor); }

    /** Valida a senha de transação já no momento do agendamento (quem executa depois é uma thread, sem usuário). */
    public void autorizarAgendamento(int origemId, BigDecimal valor, String senhaTransacao) {
        politica.validar(obterConta(origemId), valor, senhaTransacao);
    }

    /**
     * Estorna uma transação mantendo o histórico: cria transações de estorno ligadas à original, sem apagar nada.
     * @return a transação de estorno criada na conta da transação original
     */
    public Transacao estornar(String protocolo) {
        if (protocolo == null || protocolo.isBlank()) throw new BancoException.OperacaoInvalida("Informe o protocolo.");
        String chave = protocolo.trim();
        Transacao informada = transacoes.buscarPorProtocolo(chave)
                .orElseThrow(() -> new BancoException.OperacaoInvalida("Protocolo não encontrado: " + chave));
        // estornar a perna "recebida" equivale a estornar a transferência inteira (perna "enviada")
        Transacao original = informada.tipo() == TipoTransacao.TRANSFERENCIA_RECEBIDA
                ? transacoes.buscarPorProtocolo(informada.protocoloRelacionado())
                        .orElseThrow(() -> new BancoException.OperacaoInvalida("Transferência de origem não encontrada."))
                : informada;

        if (!estornosEmCurso.add(original.protocolo())) {
            throw new BancoException.OperacaoInvalida("Já existe um estorno em andamento para esta transação.");
        }
        try {
            if (transacoes.existeEstornoDe(original.protocolo())) {
                throw new BancoException.OperacaoInvalida("Esta transação já foi estornada.");
            }
            Conta conta = obterConta(original.contaId());
            Transacao estorno;
            switch (original.tipo()) {
                case DEPOSITO, RENDIMENTO -> estorno = conta.estornarDebito(original.valor(), original.protocolo(),
                        "Estorno: " + original.descricao());
                case SAQUE, TARIFA -> estorno = conta.estornarCredito(original.valor(), original.protocolo(),
                        "Estorno: " + original.descricao());
                case TRANSFERENCIA_ENVIADA -> {
                    String protocoloRecebida = original.protocoloRelacionado();
                    if (transacoes.existeEstornoDe(protocoloRecebida)) {
                        throw new BancoException.OperacaoInvalida("Esta transferência já foi estornada.");
                    }
                    Conta destino = obterConta(original.contaRelacionadaId());
                    estorno = conta.estornarTransferencia(destino, original.valor(), original.protocolo(), protocoloRecebida);
                    planejamento.aposOperacao(destino);
                }
                default -> throw new BancoException.OperacaoInvalida("Esta transação não pode ser estornada.");
            }
            planejamento.aposOperacao(conta);
            return estorno;
        } finally {
            estornosEmCurso.remove(original.protocolo());
        }
    }

    public List<Transacao> extrato(int contaId, FiltroExtrato filtro) {
        obterConta(contaId);
        return transacoes.consultar(contaId, filtro == null ? FiltroExtrato.SEM_FILTRO : filtro);
    }

    /** Aplica o rendimento em todas as poupanças ativas (chamado pelo agendador). */
    public int aplicarRendimentoEmTodas(BigDecimal taxa) {
        int aplicados = 0;
        for (Conta c : contas.listarPoupancas()) {
            if (c instanceof ContaPoupanca poupanca && poupanca.aplicarRendimento(taxa) != null) {
                planejamento.aposOperacao(poupanca);
                aplicados++;
            }
        }
        return aplicados;
    }

    public Optional<Transacao> buscarTransacao(String protocolo) { return transacoes.buscarPorProtocolo(protocolo); }

    // ------------------------------------------------------------------ internos

    /** Reserva a chave anti-duplicidade. Sem chave explícita, usa a assinatura da operação numa janela curta. */
    private String reservar(int contaId, TipoTransacao tipo, BigDecimal valor, Integer destino, String chaveExplicita) {
        boolean explicita = chaveExplicita != null && !chaveExplicita.isBlank();
        String chave = explicita
                ? "chave:" + chaveExplicita.trim()
                : "assinatura:" + contaId + "|" + tipo + "|" + valor.stripTrailingZeros().toPlainString() + "|" + destino;
        Duration janela = explicita ? CHAVE_EXPLICITA : Duration.ofSeconds(Configuracao.JANELA_DUPLICIDADE_SEGUNDOS);
        if (!duplicidade.reservar(chave, janela)) {
            String motivo = explicita
                    ? "A operação com a chave \"" + chaveExplicita.trim() + "\" já foi processada."
                    : "Operação idêntica enviada há menos de " + Configuracao.JANELA_DUPLICIDADE_SEGUNDOS
                            + " segundos: possível duplicidade. Aguarde alguns segundos e repita, se for intencional.";
            notificador.notificar(TipoNotificacao.DUPLICIDADE, contaId, motivo);
            throw new BancoException.Duplicidade(motivo);
        }
        return chave;
    }

    private void analisarFraude(Conta conta, BigDecimal valor) {
        fraude.analisar(conta.getId(), valor).ifPresent(motivo ->
                notificador.notificar(TipoNotificacao.FRAUDE_SUSPEITA, conta.getId(), "Possível fraude: " + motivo + "."));
    }

    private void aposOperacao(Conta conta) {
        BigDecimal saldo = conta.getSaldo();
        if (saldo.compareTo(Configuracao.SALDO_BAIXO) < 0) {
            notificador.notificar(TipoNotificacao.SALDO_BAIXO, conta.getId(),
                    "Saldo baixo: " + Dinheiro.formatar(saldo) + ".");
        }
        planejamento.aposOperacao(conta);
    }

    private static void exigirValor(BigDecimal valor) {
        if (valor == null) throw new BancoException.OperacaoInvalida("Informe o valor.");
    }

    private static void exigirSenhaNumerica(String senha, String nome) {
        if (senha == null || !senha.matches("\\d{4,8}")) {
            throw new BancoException.OperacaoInvalida(nome + " deve ter de 4 a 8 dígitos numéricos.");
        }
    }
}
