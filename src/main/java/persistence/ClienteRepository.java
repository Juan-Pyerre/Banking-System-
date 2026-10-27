package persistence;

import domain.BancoException;
import domain.Cliente;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClienteRepository {
    private final ConexaoJdbc conexao;

    public ClienteRepository(ConexaoJdbc conexao) { this.conexao = conexao; }

    public Cliente salvar(String nome) {
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement("INSERT INTO cliente (nome) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return new Cliente(chaves.getInt(1), nome);
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao salvar o cliente: " + e.getMessage(), e);
        }
    }

    public Optional<Cliente> buscarPorId(int id) {
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT id, nome FROM cliente WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Cliente(rs.getInt("id"), rs.getString("nome"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao buscar o cliente: " + e.getMessage(), e);
        }
    }

    public List<Cliente> listar() {
        List<Cliente> lista = new ArrayList<>();
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT id, nome FROM cliente ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(new Cliente(rs.getInt("id"), rs.getString("nome")));
            return lista;
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao listar clientes: " + e.getMessage(), e);
        }
    }
}
