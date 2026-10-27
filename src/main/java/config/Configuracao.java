package config;

import java.math.BigDecimal;

/** Parâmetros configuráveis do sistema, concentrados em um único lugar. */
public final class Configuracao {
    private Configuracao() { }

    // Pasta onde ficam os dados, extratos e logs: <pasta do usuário>/SistemaBancario (não depende de onde o programa foi aberto)
    public static final String PASTA_BASE =
            System.getProperty("banco.pasta", System.getProperty("user.home") + "/SistemaBancario").replace('\\', '/');

    // Banco de dados (H2 em modo arquivo)
    public static final String JDBC_URL = System.getProperty("banco.url", "jdbc:h2:" + PASTA_BASE + "/dados/banco");
    public static final String JDBC_USUARIO = "sa";
    public static final String JDBC_SENHA = "";

    // Contas
    public static final BigDecimal LIMITE_CHEQUE_ESPECIAL_PADRAO = new BigDecimal("500.00");
    public static final BigDecimal LIMITE_DIARIO_SAQUE_PADRAO = new BigDecimal("2000.00");
    public static final BigDecimal TARIFA_CREDITO_PERCENTUAL = new BigDecimal("0.02");   // 2% sobre o valor que usa o limite
    public static final BigDecimal RENDIMENTO_POUPANCA_PERIODO = new BigDecimal("0.005"); // 0,5% por período do agendador

    // Segurança
    public static final int MAX_TENTATIVAS_LOGIN = 3;
    public static final BigDecimal LIMITE_VALOR_ALTO = new BigDecimal("5000.00");

    // Notificações
    public static final BigDecimal SALDO_BAIXO = new BigDecimal("100.00");
    public static final String ARQUIVO_LOG = PASTA_BASE + "/notificacoes.log";

    // Duplicidade e fraude
    public static final int JANELA_DUPLICIDADE_SEGUNDOS = 5;
    public static final int FRAUDE_MAX_OPERACOES = 5;
    public static final int FRAUDE_JANELA_SEGUNDOS = 10;
    public static final int FRAUDE_FATOR_VALOR = 5;
    public static final BigDecimal FRAUDE_VALOR_MINIMO = new BigDecimal("1000.00");

    // Planejamento
    public static final int LEMBRETE_DIAS_ANTECEDENCIA = 3;

    // Arquivos
    public static final String PASTA_EXPORTACAO = PASTA_BASE + "/exportacoes";
}
