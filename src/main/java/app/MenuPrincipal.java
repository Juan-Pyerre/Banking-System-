package app;

import arquivo.ExportadorExtrato;
import arquivo.ExportadorExtrato.Formato;
import arquivo.ImportadorOperacoes.ResultadoImportacao;
import config.Configuracao;
import domain.BancoException;
import domain.Cliente;
import domain.Conta;
import domain.ContaCorrente;
import domain.FiltroExtrato;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import notificacao.Notificacao;
import planejamento.LembretePagamento;
import planejamento.Objetivo;
import planejamento.PlanejamentoService.LinhaGasto;
import servico.AgendadorDeTarefas.TransferenciaAgendada;
import simulacao.DemonstracaoDeadlock;
import simulacao.RelatorioDesempenho;
import simulacao.SimulacaoConcorrente.Parametros;
import util.Dinheiro;

/** Menu interativo no console: tudo do sistema pode ser feito aqui, sem mexer no código. */
public class MenuPrincipal {
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter NOME_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Aplicacao app;
    private final Entrada in = new Entrada();

    public MenuPrincipal(Aplicacao app) { this.app = app; }

    // =================================================================== menu principal

    public void executar() {
        System.out.println("==========================================");
        System.out.println("        JY BANK  -  SISTEMA BANCÁRIO");
        System.out.println("==========================================");
        try {
            boolean sair = false;
            while (!sair) {
                System.out.println("\n--- MENU PRINCIPAL ---");
                System.out.println(" 1. Criar cliente");
                System.out.println(" 2. Abrir conta");
                System.out.println(" 3. Entrar em uma conta (login com PIN)");
                System.out.println(" 4. Listar clientes e contas / saldo consolidado");
                System.out.println(" 5. Administração de contas (bloquear, desbloquear, encerrar)");
                System.out.println(" 6. Importar operações de um arquivo CSV");
                System.out.println(" 7. Simulação concorrente, race condition e deadlock");
                System.out.println(" 8. Rendimento da poupança");
                System.out.println(" 0. Sair");
                String opcao = in.texto("Opção: ");
                switch (opcao) {
                    case "1" -> protegido(this::criarCliente);
                    case "2" -> protegido(this::abrirConta);
                    case "3" -> protegido(this::entrarNaConta);
                    case "4" -> protegido(this::listarClientes);
                    case "5" -> protegido(this::administrar);
                    case "6" -> protegido(this::importar);
                    case "7" -> protegido(this::menuSimulacao);
                    case "8" -> protegido(this::menuRendimento);
                    case "0" -> sair = true;
                    default -> System.out.println("Opção inválida.");
                }
            }
        } catch (Entrada.FimDaEntrada e) {
            System.out.println();
        } finally {
            app.agendador.encerrar();
        }
        System.out.println("Até logo!");
    }

    /** Executa uma ação mostrando o erro (em vez de derrubar o programa) quando uma regra de negócio recusa. */
    private void protegido(Runnable acao) {
        try {
            acao.run();
        } catch (Entrada.FimDaEntrada e) {
            throw e;
        } catch (RuntimeException e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    // =================================================================== cadastro

    private void criarCliente() {
        Cliente c = app.banco.criarCliente(in.texto("Nome do cliente: "));
        System.out.println("Cliente criado: #" + c.getId() + " - " + c.getNome());
    }

    private void abrirConta() {
        int clienteId = in.inteiro("ID do cliente: ");
        String tipo = in.texto("Tipo (1 = corrente, 2 = poupança): ").equals("2") ? "POUPANCA" : "CORRENTE";
        BigDecimal saldoInicial = in.valor("Saldo inicial (R$): ");
        String pin = in.senha("Defina o PIN de acesso (4 a 8 dígitos): ");
        String senhaTransacao = in.senha("Defina a senha de transação, para valores altos (4 a 8 dígitos, diferente do PIN): ");
        Conta conta = app.banco.abrirConta(clienteId, tipo, saldoInicial, pin, senhaTransacao);
        System.out.println("Conta " + conta.tipo() + " criada: #" + conta.getId() + " | saldo " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void listarClientes() {
        List<Cliente> clientes = app.clientes.listar();
        if (clientes.isEmpty()) System.out.println("Nenhum cliente cadastrado.");
        for (Cliente c : clientes) {
            List<Conta> contas = app.banco.contasDoCliente(c.getId());
            System.out.println("Cliente #" + c.getId() + " - " + c.getNome() + " | saldo consolidado: "
                    + Dinheiro.formatar(app.banco.saldoConsolidado(c.getId())));
            for (Conta conta : contas) {
                System.out.println("    conta #" + conta.getId() + " " + conta.tipo() + " [" + conta.getStatus() + "] "
                        + Dinheiro.formatar(conta.getSaldo()));
            }
        }
    }

    private void administrar() {
        int id = in.inteiro("ID da conta: ");
        String acao = in.texto("1 = bloquear, 2 = desbloquear, 3 = encerrar (só com saldo zerado): ");
        switch (acao) {
            case "1" -> { app.banco.bloquear(id); System.out.println("Conta bloqueada: ela recusa qualquer operação."); }
            case "2" -> { app.banco.desbloquear(id); System.out.println("Conta desbloqueada."); }
            case "3" -> { app.banco.encerrar(id); System.out.println("Conta encerrada."); }
            default -> System.out.println("Opção inválida.");
        }
    }

    // =================================================================== login e menu da conta

    private void entrarNaConta() {
        int id = in.inteiro("Número da conta: ");
        String pin = in.senha("PIN: ");
        Conta conta = app.banco.autenticar(id, pin);
        System.out.println("Bem-vindo(a)! Conta #" + conta.getId() + " (" + conta.tipo() + ").");
        menuDaConta(conta);
    }

    private void menuDaConta(Conta conta) {
        boolean sair = false;
        while (!sair) {
            System.out.println("\n--- CONTA #" + conta.getId() + " (" + conta.tipo() + ") | saldo " + Dinheiro.formatar(conta.getSaldo()) + " ---");
            System.out.println(" 1. Saldo e dados da conta");
            System.out.println(" 2. Depositar");
            System.out.println(" 3. Sacar");
            System.out.println(" 4. Transferir");
            System.out.println(" 5. Extrato (com filtros)");
            System.out.println(" 6. Exportar extrato (TXT, CSV ou JSON)");
            System.out.println(" 7. Estornar uma transação");
            System.out.println(" 8. Transferências agendadas");
            System.out.println(" 9. Lembretes de pagamento mensal");
            System.out.println("10. Planejamento de gastos");
            System.out.println("11. Objetivos de economia");
            System.out.println("12. Notificações");
            System.out.println(" 0. Sair da conta");
            switch (in.texto("Opção: ")) {
                case "1" -> protegido(() -> mostrarDados(conta));
                case "2" -> protegido(() -> depositar(conta));
                case "3" -> protegido(() -> sacar(conta));
                case "4" -> protegido(() -> transferir(conta));
                case "5" -> protegido(() -> mostrarExtrato(conta));
                case "6" -> protegido(() -> exportarExtrato(conta));
                case "7" -> protegido(() -> estornar(conta));
                case "8" -> protegido(() -> menuAgendamentos(conta));
                case "9" -> protegido(() -> menuLembretes(conta));
                case "10" -> protegido(() -> menuPlanejamento(conta));
                case "11" -> protegido(() -> menuObjetivos(conta));
                case "12" -> protegido(() -> mostrarNotificacoes(conta));
                case "0" -> sair = true;
                default -> System.out.println("Opção inválida.");
            }
        }
    }

    private void mostrarDados(Conta conta) {
        System.out.println("Tipo: " + conta.tipo() + " | Status: " + conta.getStatus());
        System.out.println("Saldo: " + Dinheiro.formatar(conta.getSaldo()));
        if (conta instanceof ContaCorrente cc) {
            System.out.println("Cheque especial: " + Dinheiro.formatar(cc.limiteChequeEspecial())
                    + " | Disponível: " + Dinheiro.formatar(cc.saldoDisponivel()));
        }
        System.out.println("Limite diário de saque: " + Dinheiro.formatar(conta.getLimiteDiarioSaque()));
        System.out.println("Operações a partir de " + Dinheiro.formatar(Configuracao.LIMITE_VALOR_ALTO) + " exigem a senha de transação.");
        System.out.println("Saldo consolidado do cliente: " + Dinheiro.formatar(app.banco.saldoConsolidado(conta.getClienteId())));
    }

    // =================================================================== operações

    private void depositar(Conta conta) {
        Transacao t = app.banco.depositar(conta.getId(), in.valor("Valor do depósito (R$): "));
        System.out.println("Depósito realizado. Protocolo: " + t.protocolo() + " | novo saldo " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void sacar(Conta conta) {
        BigDecimal valor = in.valor("Valor do saque (R$): ");
        // a senha é pedida ANTES de a operação entrar na seção crítica: nenhum lock fica esperando o usuário
        String senha = app.banco.exigeSenhaReforcada(valor) ? in.senha("Valor alto! Senha de transação: ") : null;
        Transacao t = app.banco.sacar(conta.getId(), valor, senha);
        System.out.println("Saque realizado. Protocolo: " + t.protocolo() + " | novo saldo " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void transferir(Conta conta) {
        int destino = in.inteiro("Conta de destino: ");
        BigDecimal valor = in.valor("Valor da transferência (R$): ");
        String senha = app.banco.exigeSenhaReforcada(valor) ? in.senha("Valor alto! Senha de transação: ") : null;
        Transacao t = app.banco.transferir(conta.getId(), destino, valor, senha);
        System.out.println("Transferência realizada. Protocolo: " + t.protocolo() + " | novo saldo " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void estornar(Conta conta) {
        List<Transacao> recentes = app.banco.extrato(conta.getId(), FiltroExtrato.SEM_FILTRO);
        imprimirExtrato(recentes.subList(Math.max(0, recentes.size() - 10), recentes.size()));
        String protocolo = in.texto("Protocolo da transação a estornar (cole um dos acima): ");
        Transacao estorno = app.banco.estornar(protocolo);
        System.out.println("Estorno realizado (o registro original foi mantido). Protocolo do estorno: " + estorno.protocolo());
        System.out.println("Novo saldo: " + Dinheiro.formatar(conta.getSaldo()));
    }

    // =================================================================== extrato e arquivos

    private FiltroExtrato lerFiltro() {
        System.out.println("Filtros (deixe em branco para ignorar):");
        LocalDate de = in.dataOpcional("  Data inicial (dd/MM/aaaa): ");
        LocalDate ate = in.dataOpcional("  Data final (dd/MM/aaaa): ");
        TipoTransacao tipo = null;
        String textoTipo = in.texto("  Tipo (" + String.join(", ", java.util.Arrays.stream(TipoTransacao.values()).map(Enum::name).toList()) + "): ");
        if (!textoTipo.isEmpty()) tipo = TipoTransacao.valueOf(textoTipo.toUpperCase());
        BigDecimal minimo = in.valorOpcional("  Valor mínimo: ");
        BigDecimal maximo = in.valorOpcional("  Valor máximo: ");
        return new FiltroExtrato(de == null ? null : de.atStartOfDay(), ate == null ? null : ate.plusDays(1).atStartOfDay().minusNanos(1000),
                tipo, minimo, maximo);
    }

    private void mostrarExtrato(Conta conta) {
        FiltroExtrato filtro = in.confirmar("Aplicar filtros?") ? lerFiltro() : FiltroExtrato.SEM_FILTRO;
        List<Transacao> lista = app.banco.extrato(conta.getId(), filtro);
        imprimirExtrato(lista);
        System.out.println("Saldo atual: " + Dinheiro.formatar(conta.getSaldo()));
    }

    private void imprimirExtrato(List<Transacao> lista) {
        if (lista.isEmpty()) {
            System.out.println("(nenhuma transação)");
            return;
        }
        System.out.printf("%-19s  %-22s  %14s  %s%n", "Data/Hora", "Tipo", "Valor", "Protocolo / Descrição");
        for (Transacao t : lista) {
            System.out.printf("%-19s  %-22s  %14s  %s%n    %s%n", t.dataHora().format(DATA_HORA), t.tipo(),
                    (t.tipo().ehCredito() ? "+" : "-") + Dinheiro.numero(t.valor()), t.protocolo(), t.descricao());
        }
    }

    private void exportarExtrato(Conta conta) {
        FiltroExtrato filtro = in.confirmar("Aplicar filtros?") ? lerFiltro() : FiltroExtrato.SEM_FILTRO;
        String escolha = in.texto("Formato (1 = TXT, 2 = CSV, 3 = JSON): ");
        Formato formato = switch (escolha) {
            case "2" -> Formato.CSV;
            case "3" -> Formato.JSON;
            default -> Formato.TXT;
        };
        Path destino = Path.of(in.textoOuPadrao("Arquivo de destino", ExportadorExtrato.caminhoPadrao(conta, formato).toString()));
        Path gravado = ExportadorExtrato.exportar(conta, app.banco.extrato(conta.getId(), filtro), formato, destino);
        System.out.println("Extrato exportado para: " + gravado.toAbsolutePath());
    }

    private void importar() {
        Path arquivo = Path.of(in.textoOuPadrao("Arquivo CSV de operações", "operacoes.csv"));
        Path relatorio = Path.of(Configuracao.PASTA_EXPORTACAO, "relatorio_importacao_" + LocalDateTime.now().format(NOME_ARQUIVO) + ".txt");
        ResultadoImportacao r = app.importador.importar(arquivo, relatorio);
        System.out.print(r.formatar());
        System.out.println("Relatório gravado em: " + relatorio.toAbsolutePath());
    }

    // =================================================================== agendamentos

    private void menuAgendamentos(Conta conta) {
        switch (in.texto("1 = agendar transferência, 2 = listar agendamentos: ")) {
            case "1" -> {
                int destino = in.inteiro("Conta de destino: ");
                BigDecimal valor = in.valor("Valor (R$): ");
                LocalDateTime quando = in.horario("Quando executar (dd/MM/aaaa HH:mm, ou +N segundos a partir de agora): ");
                String senha = app.banco.exigeSenhaReforcada(valor) ? in.senha("Valor alto! Senha de transação: ") : null;
                TransferenciaAgendada ag = app.agendador.agendarTransferencia(conta.getId(), destino, valor, quando, senha);
                System.out.println("Agendada para " + ag.getExecutarEm().format(DATA_HORA) + " (id " + ag.getId() + "). Roda em outra thread.");
            }
            case "2" -> {
                List<TransferenciaAgendada> lista = app.agendador.listarAgendamentos(conta.getId());
                if (lista.isEmpty()) System.out.println("Nenhum agendamento.");
                for (TransferenciaAgendada a : lista) {
                    System.out.println(a.getExecutarEm().format(DATA_HORA) + " | para conta " + a.getDestinoId() + " | "
                            + Dinheiro.formatar(a.getValor()) + " | " + a.getStatus() + (a.getDetalhe().isEmpty() ? "" : " | " + a.getDetalhe()));
                }
            }
            default -> System.out.println("Opção inválida.");
        }
    }

    // =================================================================== planejamento financeiro

    private void menuLembretes(Conta conta) {
        switch (in.texto("1 = cadastrar lembrete mensal, 2 = listar: ")) {
            case "1" -> {
                String descricao = in.texto("Descrição (ex.: Aluguel): ");
                BigDecimal valor = in.valor("Valor (R$): ");
                int dia = in.inteiro("Dia do mês do vencimento (1 a 31): ");
                app.planejamento.cadastrarLembrete(conta.getId(), descricao, valor, dia);
                System.out.println("Lembrete cadastrado. Você será avisado " + Configuracao.LEMBRETE_DIAS_ANTECEDENCIA
                        + " dias antes do vencimento (a cada login).");
                app.planejamento.verificarLembretes(conta.getId(), LocalDate.now());
            }
            case "2" -> {
                List<LembretePagamento> lista = app.planejamento.listarLembretes(conta.getId());
                if (lista.isEmpty()) System.out.println("Nenhum lembrete cadastrado.");
                for (LembretePagamento l : lista) {
                    System.out.println(l.getDescricao() + " | " + Dinheiro.formatar(l.getValor()) + " | todo dia " + l.getDiaDoMes()
                            + " | próximo vencimento: " + l.proximoVencimento(LocalDate.now()));
                }
            }
            default -> System.out.println("Opção inválida.");
        }
    }

    private void menuPlanejamento(Conta conta) {
        switch (in.texto("1 = definir limite mensal por categoria, 2 = comparar com o que foi gasto: ")) {
            case "1" -> {
                String categoria = in.texto("Categoria (SAQUE, TRANSFERENCIA_ENVIADA ou TARIFA): ");
                BigDecimal limite = in.valor("Limite mensal (R$): ");
                app.planejamento.definirLimite(conta.getId(), TipoTransacao.valueOf(categoria.toUpperCase()), limite);
                System.out.println("Limite definido.");
            }
            case "2" -> {
                List<LinhaGasto> linhas = app.planejamento.relatorioDoMes(conta.getId());
                if (linhas.isEmpty()) System.out.println("Nenhum limite definido.");
                for (LinhaGasto l : linhas) {
                    System.out.println(String.format("%-22s limite %14s | gasto %14s | %s%% usado", l.categoria(),
                            Dinheiro.formatar(l.limite()), Dinheiro.formatar(l.gasto()), l.percentual()));
                }
            }
            default -> System.out.println("Opção inválida.");
        }
    }

    private void menuObjetivos(Conta conta) {
        switch (in.texto("1 = criar objetivo, 2 = acompanhar progresso: ")) {
            case "1" -> {
                String nome = in.texto("Nome do objetivo (ex.: Viagem): ");
                BigDecimal valor = in.valor("Quanto quer juntar (R$): ");
                LocalDate prazo = in.data("Prazo (dd/MM/aaaa): ");
                app.planejamento.criarObjetivo(conta, nome, valor, prazo);
                System.out.println("Objetivo criado. O progresso conta o que o saldo da conta crescer a partir de agora.");
            }
            case "2" -> {
                List<Objetivo> lista = app.planejamento.listarObjetivos(conta.getId());
                if (lista.isEmpty()) System.out.println("Nenhum objetivo definido.");
                for (Objetivo o : lista) {
                    System.out.println(o.getNome() + " | meta " + Dinheiro.formatar(o.getValorMeta()) + " até " + o.getPrazo()
                            + " | juntado " + Dinheiro.formatar(o.economizado(conta.getSaldo()))
                            + " (" + o.progressoPercentual(conta.getSaldo()) + "%)" + (o.isAtingido() ? " - ATINGIDO" : ""));
                }
            }
            default -> System.out.println("Opção inválida.");
        }
    }

    private void mostrarNotificacoes(Conta conta) {
        List<Notificacao> lista = app.historico.daConta(conta.getId());
        if (lista.isEmpty()) System.out.println("Nenhuma notificação nesta sessão. (Todas também ficam em " + Configuracao.ARQUIVO_LOG + ")");
        for (Notificacao n : lista) {
            System.out.println(n.dataHora().format(DATA_HORA) + " [" + n.tipo() + "] " + n.mensagem());
        }
    }

    // =================================================================== simulação, deadlock, rendimento

    private void menuSimulacao() throws RuntimeException {
        System.out.println("\n--- SIMULAÇÃO CONCORRENTE ---");
        System.out.println(" 1. SEM sincronização (expõe a race condition)");
        System.out.println(" 2. COM sincronização (synchronized), só memória");
        System.out.println(" 3. COM sincronização + gravação no banco (JDBC)");
        System.out.println(" 4. Comparativo antes e depois (gera relatório em arquivo)");
        System.out.println(" 5. Demonstração de deadlock (antes e depois)");
        String opcao = in.texto("Opção: ");
        try {
            switch (opcao) {
                case "1", "2" -> {
                    Parametros p = lerParametros(opcao.equals("2"), false, 20_000);
                    System.out.println(app.simulacao.executar(p).formatar());
                }
                case "3" -> {
                    Parametros p = lerParametros(true, true, 500);
                    RelatorioDesempenho r = app.simulacao.executar(p);
                    System.out.println(r.formatar());
                }
                case "4" -> {
                    Path destino = Path.of(Configuracao.PASTA_EXPORTACAO, "relatorio_antes_depois_" + LocalDateTime.now().format(NOME_ARQUIVO) + ".txt");
                    app.simulacao.executar(new Parametros(4, 8, 20_000, false, false)); // aquecimento da JVM
                    RelatorioDesempenho antes = app.simulacao.executar(new Parametros(4, 8, 20_000, false, false));
                    RelatorioDesempenho depois = app.simulacao.executar(new Parametros(4, 8, 20_000, true, false));
                    String texto = "ANTES:\n" + antes.formatar() + "\nDEPOIS:\n" + depois.formatar();
                    java.nio.file.Files.createDirectories(destino.getParent());
                    java.nio.file.Files.writeString(destino, texto, java.nio.charset.StandardCharsets.UTF_8);
                    System.out.println(texto + "Relatório gravado em " + destino.toAbsolutePath());
                }
                case "5" -> {
                    DemonstracaoDeadlock.demonstrarDeadlock(System.out);
                    System.out.println();
                    DemonstracaoDeadlock.demonstrarCorrecao(System.out);
                }
                default -> System.out.println("Opção inválida.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Simulação interrompida.");
        } catch (java.io.IOException e) {
            System.out.println("Erro ao gravar o relatório: " + e.getMessage());
        }
    }

    private Parametros lerParametros(boolean sincronizada, boolean persistir, int operacoesPadrao) {
        int contas = in.inteiroOuPadrao("Número de contas", 4);
        int threads = in.inteiroOuPadrao("Número de threads", 8);
        int operacoes = in.inteiroOuPadrao("Operações por thread", operacoesPadrao);
        return new Parametros(contas, threads, operacoes, sincronizada, persistir);
    }

    private void menuRendimento() {
        System.out.println("Rendimento por período: " + Configuracao.RENDIMENTO_POUPANCA_PERIODO.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
                + "% | automático: " + (app.agendador.rendimentoAtivo() ? "ATIVO (" + app.agendador.ciclosDeRendimento() + " ciclos)" : "desligado"));
        switch (in.texto("1 = aplicar agora, 2 = ligar automático, 3 = desligar automático: ")) {
            case "1" -> System.out.println(app.banco.aplicarRendimentoEmTodas(Configuracao.RENDIMENTO_POUPANCA_PERIODO)
                    + " conta(s) poupança receberam rendimento.");
            case "2" -> {
                int segundos = in.inteiroOuPadrao("Período em segundos (use valores pequenos para demonstrar)", 30);
                app.agendador.iniciarRendimento(segundos, Configuracao.RENDIMENTO_POUPANCA_PERIODO);
                System.out.println("Rendimento automático ligado: roda em outra thread a cada " + segundos + " s, junto das demais operações.");
            }
            case "3" -> { app.agendador.pararRendimento(); System.out.println("Rendimento automático desligado."); }
            default -> System.out.println("Opção inválida.");
        }
    }
}
