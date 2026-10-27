package persistence;

import config.Configuracao;
import domain.BancoException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/** Fábrica de conexões JDBC e inicializador do esquema (schema.sql). */
public class ConexaoJdbc {
    private final String url;
    private final String usuario;
    private final String senha;

    public ConexaoJdbc() {
        this(Configuracao.JDBC_URL, Configuracao.JDBC_USUARIO, Configuracao.JDBC_SENHA);
    }

    public ConexaoJdbc(String url) {
        this(url, Configuracao.JDBC_USUARIO, Configuracao.JDBC_SENHA);
    }

    public ConexaoJdbc(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    public Connection abrir() {
        try {
            return DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Não foi possível abrir a conexão com o banco: " + e.getMessage(), e);
        }
    }

    /** Executa o schema.sql (criação das tabelas, se ainda não existirem). */
    public void inicializarEsquema() {
        try (InputStream in = ConexaoJdbc.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql não encontrado no classpath");
            String script;
            try (BufferedReader leitor = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                script = leitor.lines().filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
            }
            try (Connection c = abrir(); Statement st = c.createStatement()) {
                for (String comando : script.split(";")) {
                    if (!comando.isBlank()) st.execute(comando);
                }
            }
        } catch (IOException | SQLException e) {
            throw new BancoException.FalhaDePersistencia("Falha ao criar as tabelas: " + e.getMessage(), e);
        }
    }
}
