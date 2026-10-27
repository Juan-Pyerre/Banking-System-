package domain;

import config.Configuracao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import seguranca.Credencial;

/**
 * Conta bancária. É o ÚNICO lugar do sistema onde o saldo pode ser alterado:
 * todo acesso concorrente passa pelos métodos desta classe.
 *
 * Concorrência:
 *  - depositar/sacar (e demais operações de uma conta só) são {@code synchronized} no próprio objeto;
 *  - transferirPara trava as DUAS contas sempre na mesma ordem (menor id primeiro), então duas
 *    transferências em sentidos opostos nunca se travam uma à outra (sem deadlock);
 *  - nenhum lock é mantido esperando o usuário: confirmações/senhas são resolvidas ANTES de chamar estes métodos.
 */
public abstract class Conta {
    private final int id;
    private final int clienteId;

    // Estado protegido pelo monitor (synchronized) desta conta
    private BigDecimal saldo;
    private StatusConta status = StatusConta.ATIVA;
    private BigDecimal limiteDiarioSaque = Configuracao.LIMITE_DIARIO_SAQUE_PADRAO;
    private BigDecimal sacadoHoje = BigDecimal.ZERO;
    private LocalDate diaDoControle = LocalDate.now();
    private int tentativasLogin;
    private Credencial senhaLogin;
    private Credencial senhaTransacao;

    private volatile RegistroOperacoes registro = RegistroOperacoes.NENHUM;

    protected Conta(int id, int clienteId, BigDecimal saldoInicial) {
        if (saldoInicial == null) {
            throw new BancoException.OperacaoInvalida("Informe o saldo inicial.");
        }
        // Se o saldo pode ser negativo (cheque especial) é regra de cada tipo de conta: veja os construtores das subclasses.
        this.id = id;
        this.clienteId = clienteId;
        this.saldo = saldoInicial.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ regras de cada tipo de conta

    /** Nome do tipo (usado na persistência): CORRENTE ou POUPANCA. */
    public abstract String tipo();

    /** Quanto a conta pode ficar negativa (cheque especial). Poupança: zero. */
    public BigDecimal limiteChequeEspecial() { return BigDecimal.ZERO; }

    /** Tarifa cobrada quando o débito usa crédito. Por padrão não há tarifa. */
    protected BigDecimal calcularTarifaCredito(BigDecimal valor, BigDecimal saldoAtual) { return BigDecimal.ZERO; }

    // ------------------------------------------------------------------ operações

    public synchronized Transacao depositar(BigDecimal valor) {
        return depositar(valor, "Depósito");
    }

    public synchronized Transacao depositar(BigDecimal valor, String descricao) {
        return creditar(valor, TipoTransacao.DEPOSITO, null, descricao);
    }

    public synchronized Transacao sacar(BigDecimal valor) {
        exigirOperacional();
        BigDecimal v = normalizar(valor);
        renovarControleDiario();
        if (sacadoHoje.add(v).compareTo(limiteDiarioSaque) > 0) {
            throw new BancoException.LimiteExcedido("Limite diário de saque excedido (limite R$ " + limiteDiarioSaque
                    + ", já sacado hoje R$ " + sacadoHoje + ").");
        }
        BigDecimal saldoAntes = saldo;
        BigDecimal sacadoAntes = sacadoHoje;
        List<Transacao> transacoes = debitar(v, TipoTransacao.SAQUE, null, null, novoProtocolo(), "Saque");
        sacadoHoje = sacadoHoje.add(v);
        gravar(transacoes, List.of(this), () -> { saldo = saldoAntes; sacadoHoje = sacadoAntes; });
        return transacoes.get(0);
    }

    /**
     * Transfere para outra conta. As duas contas são bloqueadas SEMPRE na mesma ordem (menor id primeiro).
     * Débito e crédito são gravados juntos: ou os dois persistem ou nenhum.
     */
    public Transacao transferirPara(Conta destino, BigDecimal valor) {
        if (destino == null || destino == this || destino.id == this.id) {
            throw new BancoException.OperacaoInvalida("Escolha uma conta de destino diferente da conta de origem.");
        }
        Conta primeira = this.id < destino.id ? this : destino;
        Conta segunda = (primeira == this) ? destino : this;
        synchronized (primeira) {
            synchronized (segunda) {
                this.exigirOperacional();
                destino.exigirOperacional();
                BigDecimal v = normalizar(valor);
                BigDecimal saldoAntes = this.saldo;
                BigDecimal saldoDestinoAntes = destino.saldo;
                String protocoloEnviada = novoProtocolo();
                String protocoloRecebida = novoProtocolo();

                List<Transacao> transacoes = new ArrayList<>(
                        debitar(v, TipoTransacao.TRANSFERENCIA_ENVIADA, destino.id, protocoloRecebida, protocoloEnviada,
                                "Transferência para a conta " + destino.id));
                destino.saldo = destino.saldo.add(v);
                transacoes.add(Transacao.nova(protocoloRecebida, destino.id, TipoTransacao.TRANSFERENCIA_RECEBIDA, v,
                        this.id, protocoloEnviada, "Transferência recebida da conta " + this.id));

                gravar(transacoes, List.of(this, destino), () -> {
                    this.saldo = saldoAntes;
                    destino.saldo = saldoDestinoAntes;
                });
                return transacoes.get(0);
            }
        }
    }

    // ------------------------------------------------------------------ estornos (o histórico original é mantido)

    /** Devolve dinheiro à conta (estorno de saque, tarifa ou transferência enviada). */
    public synchronized Transacao estornarCredito(BigDecimal valor, String protocoloOriginal, String descricao) {
        return creditar(valor, TipoTransacao.ESTORNO_CREDITO, protocoloOriginal, descricao);
    }

    /** Retira dinheiro da conta (estorno de depósito, rendimento ou transferência recebida). */
    public synchronized Transacao estornarDebito(BigDecimal valor, String protocoloOriginal, String descricao) {
        exigirOperacional();
        BigDecimal v = normalizar(valor);
        if (saldo.add(limiteChequeEspecial()).compareTo(v) < 0) {
            throw new BancoException.SaldoInsuficiente("Saldo insuficiente para estornar esta operação.");
        }
        BigDecimal saldoAntes = saldo;
        saldo = saldo.subtract(v);
        Transacao t = Transacao.nova(id, TipoTransacao.ESTORNO_DEBITO, v, null, protocoloOriginal, descricao);
        gravar(List.of(t), List.of(this), () -> saldo = saldoAntes);
        return t;
    }

    /** Estorna uma transferência: esta conta (origem) recebe de volta e o destino devolve. */
    public Transacao estornarTransferencia(Conta destino, BigDecimal valor, String protocoloEnviada, String protocoloRecebida) {
        Conta primeira = this.id < destino.id ? this : destino;
        Conta segunda = (primeira == this) ? destino : this;
        synchronized (primeira) {
            synchronized (segunda) {
                this.exigirOperacional();
                destino.exigirOperacional();
                BigDecimal v = normalizar(valor);
                if (destino.saldo.add(destino.limiteChequeEspecial()).compareTo(v) < 0) {
                    throw new BancoException.SaldoInsuficiente(
                            "A conta de destino não tem saldo para devolver o valor da transferência.");
                }
                BigDecimal saldoAntes = this.saldo;
                BigDecimal saldoDestinoAntes = destino.saldo;
                this.saldo = this.saldo.add(v);
                destino.saldo = destino.saldo.subtract(v);
                Transacao credito = Transacao.nova(this.id, TipoTransacao.ESTORNO_CREDITO, v, destino.id, protocoloEnviada,
                        "Estorno da transferência para a conta " + destino.id);
                Transacao debito = Transacao.nova(destino.id, TipoTransacao.ESTORNO_DEBITO, v, this.id, protocoloRecebida,
                        "Estorno da transferência recebida da conta " + this.id);
                gravar(List.of(credito, debito), List.of(this, destino), () -> {
                    this.saldo = saldoAntes;
                    destino.saldo = saldoDestinoAntes;
                });
                return credito;
            }
        }
    }

    // ------------------------------------------------------------------ status da conta

    public synchronized void bloquear() {
        if (status == StatusConta.ENCERRADA) throw new BancoException.ContaIndisponivel("A conta já está encerrada.");
        StatusConta antes = status;
        status = StatusConta.BLOQUEADA;
        gravar(List.of(), List.of(this), () -> status = antes);
    }

    public synchronized void desbloquear() {
        if (status == StatusConta.ENCERRADA) throw new BancoException.ContaIndisponivel("Conta encerrada não pode ser desbloqueada.");
        StatusConta antes = status;
        int tentativasAntes = tentativasLogin;
        status = StatusConta.ATIVA;
        tentativasLogin = 0;
        gravar(List.of(), List.of(this), () -> { status = antes; tentativasLogin = tentativasAntes; });
    }

    /** O encerramento só é permitido com saldo zerado. */
    public synchronized void encerrar() {
        if (status == StatusConta.ENCERRADA) throw new BancoException.ContaIndisponivel("A conta já está encerrada.");
        if (saldo.signum() != 0) {
            throw new BancoException.OperacaoInvalida("A conta só pode ser encerrada com saldo zerado (saldo atual: R$ " + saldo + ").");
        }
        StatusConta antes = status;
        status = StatusConta.ENCERRADA;
        gravar(List.of(), List.of(this), () -> status = antes);
    }

    // ------------------------------------------------------------------ segurança

    /** Confere o PIN. Após {@code MAX_TENTATIVAS_LOGIN} erros seguidos a conta é bloqueada. */
    public synchronized void autenticar(String pin) {
        if (status == StatusConta.ENCERRADA) throw new BancoException.ContaIndisponivel("A conta está encerrada.");
        if (status == StatusConta.BLOQUEADA) {
            throw new BancoException.ContaIndisponivel("Conta BLOQUEADA. Procure a administração para desbloquear.");
        }
        if (senhaLogin == null) throw new BancoException.Autenticacao("Esta conta não possui PIN cadastrado.");

        if (senhaLogin.confere(pin)) {
            if (tentativasLogin != 0) {
                int antes = tentativasLogin;
                tentativasLogin = 0;
                gravar(List.of(), List.of(this), () -> tentativasLogin = antes);
            }
            return;
        }
        int tentativasAntes = tentativasLogin;
        StatusConta statusAntes = status;
        tentativasLogin++;
        boolean bloqueou = tentativasLogin >= Configuracao.MAX_TENTATIVAS_LOGIN;
        if (bloqueou) status = StatusConta.BLOQUEADA;
        gravar(List.of(), List.of(this), () -> { tentativasLogin = tentativasAntes; status = statusAntes; });
        throw new BancoException.Autenticacao(bloqueou
                ? "PIN incorreto. Número máximo de tentativas atingido: conta BLOQUEADA."
                : "PIN incorreto. Tentativas restantes: " + (Configuracao.MAX_TENTATIVAS_LOGIN - tentativasLogin) + ".");
    }

    public synchronized boolean possuiSenhaTransacao() { return senhaTransacao != null; }

    public synchronized boolean verificarSenhaTransacao(String senha) {
        return senhaTransacao != null && senhaTransacao.confere(senha);
    }

    // ------------------------------------------------------------------ leitura

    public int getId() { return id; }
    public int getClienteId() { return clienteId; }
    public synchronized BigDecimal getSaldo() { return saldo; }
    public synchronized StatusConta getStatus() { return status; }
    public synchronized int getTentativasLogin() { return tentativasLogin; }
    public synchronized BigDecimal getLimiteDiarioSaque() { return limiteDiarioSaque; }
    public synchronized Credencial getSenhaLogin() { return senhaLogin; }
    public synchronized Credencial getSenhaTransacao() { return senhaTransacao; }
    public synchronized BigDecimal saldoDisponivel() { return saldo.add(limiteChequeEspecial()); }

    // ------------------------------------------------------------------ configuração / reidratação (usado pelos repositórios)

    public void setRegistro(RegistroOperacoes registro) {
        this.registro = (registro == null) ? RegistroOperacoes.NENHUM : registro;
    }

    public synchronized void definirCredenciais(Credencial login, Credencial transacao) {
        this.senhaLogin = login;
        this.senhaTransacao = transacao;
    }

    public synchronized void definirLimiteDiarioSaque(BigDecimal limite) {
        this.limiteDiarioSaque = limite;
    }

    /** Restaura o estado gravado no banco. Não gera transações. */
    public synchronized void restaurar(StatusConta status, int tentativasLogin, BigDecimal limiteDiarioSaque, BigDecimal sacadoHoje) {
        this.status = status;
        this.tentativasLogin = tentativasLogin;
        this.limiteDiarioSaque = limiteDiarioSaque;
        this.sacadoHoje = sacadoHoje;
        this.diaDoControle = LocalDate.now();
    }

    // ------------------------------------------------------------------ internos

    /** Crédito genérico (depósito, rendimento, estorno). Deve ser chamado com o monitor da conta. */
    protected synchronized Transacao creditar(BigDecimal valor, TipoTransacao tipo, String protocoloRelacionado, String descricao) {
        exigirOperacional();
        BigDecimal v = normalizar(valor);
        BigDecimal saldoAntes = saldo;
        saldo = saldo.add(v);
        Transacao t = Transacao.nova(id, tipo, v, null, protocoloRelacionado, descricao);
        gravar(List.of(t), List.of(this), () -> saldo = saldoAntes);
        return t;
    }

    /** Aplica o débito em memória (com tarifa, se houver). Não grava: quem chama grava. */
    private List<Transacao> debitar(BigDecimal valor, TipoTransacao tipo, Integer contaRelacionada,
                                    String protocoloRelacionado, String protocolo, String descricao) {
        BigDecimal tarifa = calcularTarifaCredito(valor, saldo);
        BigDecimal total = valor.add(tarifa);
        if (saldo.add(limiteChequeEspecial()).compareTo(total) < 0) {
            throw new BancoException.SaldoInsuficiente("Saldo insuficiente. Disponível: R$ " + saldoDisponivel()
                    + (tarifa.signum() > 0 ? " (necessário R$ " + total + " com tarifa de crédito)" : "") + ".");
        }
        saldo = saldo.subtract(total);
        List<Transacao> transacoes = new ArrayList<>(2);
        transacoes.add(Transacao.nova(protocolo, id, tipo, valor, contaRelacionada, protocoloRelacionado, descricao));
        if (tarifa.signum() > 0) {
            transacoes.add(Transacao.nova(id, TipoTransacao.TARIFA, tarifa, null, protocolo,
                    "Tarifa por uso de crédito (cheque especial)"));
        }
        return transacoes;
    }

    /** Pede ao registro que grave; se falhar, desfaz a alteração em memória e propaga o erro. */
    private void gravar(List<Transacao> transacoes, List<Conta> contas, Runnable desfazer) {
        try {
            registro.registrar(transacoes, contas);
        } catch (RuntimeException e) {
            desfazer.run();
            throw e;
        }
    }

    private void exigirOperacional() {
        if (status == StatusConta.BLOQUEADA) {
            throw new BancoException.ContaIndisponivel("A conta " + id + " está bloqueada e recusa operações.");
        }
        if (status == StatusConta.ENCERRADA) {
            throw new BancoException.ContaIndisponivel("A conta " + id + " está encerrada.");
        }
    }

    private void renovarControleDiario() {
        LocalDate hoje = LocalDate.now();
        if (!hoje.equals(diaDoControle)) {
            diaDoControle = hoje;
            sacadoHoje = BigDecimal.ZERO;
        }
    }

    private static String novoProtocolo() { return UUID.randomUUID().toString(); }

    private static BigDecimal normalizar(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new BancoException.OperacaoInvalida("O valor deve ser maior que zero.");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new BancoException.OperacaoInvalida("Use no máximo duas casas decimais.");
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    @Override
    public String toString() { return tipo() + " #" + id; }
}
