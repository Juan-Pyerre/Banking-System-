package persistence;

import domain.BancoException;
import domain.Conta;
import domain.ContaCorrente;
import domain.ContaPoupanca;
import domain.RegistroOperacoes;
import domain.StatusConta;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import seguranca.Credencial;

/**
 * Repositório de contas. Mantém um mapa de identidade: para cada id existe UM ÚNICO objeto Conta em memória,
 * porque o lock (synchronized) é por objeto — duas instâncias da mesma conta quebrariam a sincronização.
 */
public class ContaRepository {
    private final ConexaoJdbc conexao;
    private final RegistroOperacoes registro;
    private final TransacaoRepository transacoes;
    private final Map<Integer, Conta> cache = new ConcurrentHashMap<>();

    public ContaRepository(ConexaoJdbc conexao, RegistroOperacoes registro, TransacaoRepository transacoes) {
        this.conexao = conexao;
        this.registro = registro;
        this.transacoes = transacoes;
    }

    /** Cria a conta no banco (saldo zero; o saldo inicial entra como depósito, para o extrato fechar). */
    public Conta criar(int clienteId, String tipo, BigDecimal limiteChequeEspecial, Credencial login, Credencial transacao) {
        Conta modelo = "POUPANCA".equals(tipo)
                ? new ContaPoupanca(0, clienteId, BigDecimal.ZERO)
                : new ContaCorrente(0, clienteId, BigDecimal.ZERO, limiteChequeEspecial);
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO conta (cliente_id, tipo, saldo, status, limite_cheque_especial, limite_diario_saque, "
                             + "senha_hash, senha_salt, transacao_hash, transacao_salt, tentativas_login) VALUES (?,?,?,?,?,?,?,?,?,?,0)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clienteId);
            ps.setString(2, tipo);
            ps.setBigDecimal(3, BigDecimal.ZERO);
            ps.setString(4, StatusConta.ATIVA.name());
            ps.setBigDecimal(5, modelo.limiteChequeEspecial());
            ps.setBigDecimal(6, modelo.getLimiteDiarioSaque());
            ps.setString(7, login == null ? null : login.getHash());
            ps.setString(8, login == null ? null : login.getSalt());
            ps.setString(9, transacao == null ? null : transacao.getHash());
            ps.setString(10, transacao == null ? null : transacao.getSalt());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                int id = chaves.getInt(1);
                Conta conta = "POUPANCA".equals(tipo)
                        ? new ContaPoupanca(id, clienteId, BigDecimal.ZERO)
                        : new ContaCorrente(id, clienteId, BigDecimal.ZERO, limiteChequeEspecial);
                conta.definirCredenciais(login, transacao);
                conta.setRegistro(registro);
                cache.put(id, conta);
                return conta;
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao criar a conta: " + e.getMessage(), e);
        }
    }

    public Optional<Conta> buscarPorId(int id) {
        Conta emMemoria = cache.get(id);
        if (emMemoria != null) return Optional.of(emMemoria);
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM conta WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(instanciar(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao buscar a conta: " + e.getMessage(), e);
        }
    }

    public List<Conta> listarPorCliente(int clienteId) {
        return listar("SELECT * FROM conta WHERE cliente_id = ? ORDER BY id", clienteId);
    }

    public List<Conta> listarTodas() {
        return listar("SELECT * FROM conta ORDER BY id", null);
    }

    public List<Conta> listarPoupancas() {
        return listar("SELECT * FROM conta WHERE tipo = 'POUPANCA' ORDER BY id", null);
    }

    /** Saldo como está GRAVADO no banco (usado para conferir memória x banco). */
    public BigDecimal lerSaldoDoBanco(int id) {
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT saldo FROM conta WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BancoException.OperacaoInvalida("Conta " + id + " não encontrada.");
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao ler o saldo: " + e.getMessage(), e);
        }
    }

    /** Atualiza saldo, status e tentativas de login (chamado pelo RegistroOperacoesJdbc na mesma transação JDBC). */
    static void atualizarEstado(Connection c, Conta conta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE conta SET saldo = ?, status = ?, tentativas_login = ? WHERE id = ?")) {
            ps.setBigDecimal(1, conta.getSaldo());
            ps.setString(2, conta.getStatus().name());
            ps.setInt(3, conta.getTentativasLogin());
            ps.setInt(4, conta.getId());
            ps.executeUpdate();
        }
    }

    private List<Conta> listar(String sql, Integer parametro) {
        try (Connection c = conexao.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (parametro != null) ps.setInt(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                List<Conta> lista = new ArrayList<>();
                while (rs.next()) {
                    Conta existente = cache.get(rs.getInt("id"));
                    lista.add(existente != null ? existente : instanciar(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao listar contas: " + e.getMessage(), e);
        }
    }

    /** Monta o objeto a partir da linha lida e o registra no mapa de identidade (sem duplicar instâncias). */
    private Conta instanciar(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int clienteId = rs.getInt("cliente_id");
        BigDecimal saldo = rs.getBigDecimal("saldo");
        Conta conta = "POUPANCA".equals(rs.getString("tipo"))
                ? new ContaPoupanca(id, clienteId, saldo)
                : new ContaCorrente(id, clienteId, saldo, rs.getBigDecimal("limite_cheque_especial"));
        Credencial login = rs.getString("senha_hash") == null ? null
                : Credencial.restaurar(rs.getString("senha_salt"), rs.getString("senha_hash"));
        Credencial senhaTransacao = rs.getString("transacao_hash") == null ? null
                : Credencial.restaurar(rs.getString("transacao_salt"), rs.getString("transacao_hash"));
        conta.definirCredenciais(login, senhaTransacao);
        conta.restaurar(StatusConta.valueOf(rs.getString("status")), rs.getInt("tentativas_login"),
                rs.getBigDecimal("limite_diario_saque"), transacoes.somarSaquesDoDia(id, LocalDate.now()));
        conta.setRegistro(registro);
        Conta anterior = cache.putIfAbsent(id, conta);
        return anterior != null ? anterior : conta;
    }
}
