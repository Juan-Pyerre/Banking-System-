package persistence;

import domain.BancoException;
import domain.FiltroExtrato;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Todo o SQL de transações fica aqui. */
public class TransacaoRepository {
    private static final String COLUNAS =
            "protocolo, conta_id, tipo, valor, data_hora, conta_relacionada_id, protocolo_relacionado, descricao";

    private final ConexaoJdbc conexao;

    public TransacaoRepository(ConexaoJdbc conexao) { this.conexao = conexao; }

    /** Insere usando uma conexão já aberta (para participar de uma transação JDBC maior). */
    static void inserir(Connection c, Transacao t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO transacao (" + COLUNAS + ") VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, t.protocolo());
            ps.setInt(2, t.contaId());
            ps.setString(3, t.tipo().name());
            ps.setBigDecimal(4, t.valor());
            ps.setTimestamp(5, Timestamp.valueOf(t.dataHora()));
            if (t.contaRelacionadaId() == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setInt(6, t.contaRelacionadaId());
            ps.setString(7, t.protocoloRelacionado());
            ps.setString(8, t.descricao());
            ps.executeUpdate();
        }
    }

    public List<Transacao> listarPorConta(int contaId) {
        return consultar(contaId, FiltroExtrato.SEM_FILTRO);
    }

    /** Extrato com filtros opcionais por período, tipo e faixa de valor. */
    public List<Transacao> consultar(int contaId, FiltroExtrato f) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUNAS + " FROM transacao WHERE conta_id = ?");
        List<Object> parametros = new ArrayList<>();
        parametros.add(contaId);
        if (f.inicio() != null) { sql.append(" AND data_hora >= ?"); parametros.add(Timestamp.valueOf(f.inicio())); }
        if (f.fim() != null) { sql.append(" AND data_hora <= ?"); parametros.add(Timestamp.valueOf(f.fim())); }
        if (f.tipo() != null) { sql.append(" AND tipo = ?"); parametros.add(f.tipo().name()); }
        if (f.valorMin() != null) { sql.append(" AND valor >= ?"); parametros.add(f.valorMin()); }
        if (f.valorMax() != null) { sql.append(" AND valor <= ?"); parametros.add(f.valorMax()); }
        sql.append(" ORDER BY seq");

        try (Connection c = conexao.abrir(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) ps.setObject(i + 1, parametros.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Transacao> lista = new ArrayList<>();
                while (rs.next()) lista.add(mapear(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao consultar o extrato: " + e.getMessage(), e);
        }
    }

    public Optional<Transacao> buscarPorProtocolo(String protocolo) {
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM transacao WHERE protocolo = ?")) {
            ps.setString(1, protocolo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao buscar a transação: " + e.getMessage(), e);
        }
    }

    /** Já existe um estorno apontando para esta transação? */
    public boolean existeEstornoDe(String protocolo) {
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM transacao WHERE protocolo_relacionado = ? AND tipo IN ('ESTORNO_CREDITO','ESTORNO_DEBITO')")) {
            ps.setString(1, protocolo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao verificar estornos: " + e.getMessage(), e);
        }
    }

    /** Soma dos saques de um dia (para o limite diário de saque). */
    public BigDecimal somarSaquesDoDia(int contaId, LocalDate dia) {
        return somarPorTipo(contaId, TipoTransacao.SAQUE, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
    }

    /** Soma dos valores de um tipo de transação no intervalo [inicio, fim). */
    public BigDecimal somarPorTipo(int contaId, TipoTransacao tipo, LocalDateTime inicio, LocalDateTime fim) {
        try (Connection c = conexao.abrir();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(valor), 0) FROM transacao WHERE conta_id = ? AND tipo = ? AND data_hora >= ? AND data_hora < ?")) {
            ps.setInt(1, contaId);
            ps.setString(2, tipo.name());
            ps.setTimestamp(3, Timestamp.valueOf(inicio));
            ps.setTimestamp(4, Timestamp.valueOf(fim));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Erro ao somar transações: " + e.getMessage(), e);
        }
    }

    private static Transacao mapear(ResultSet rs) throws SQLException {
        int relacionada = rs.getInt("conta_relacionada_id");
        Integer contaRelacionada = rs.wasNull() ? null : relacionada;
        return new Transacao(rs.getString("protocolo"), rs.getInt("conta_id"), TipoTransacao.valueOf(rs.getString("tipo")),
                rs.getBigDecimal("valor"), rs.getTimestamp("data_hora").toLocalDateTime(), contaRelacionada,
                rs.getString("protocolo_relacionado"), rs.getString("descricao"));
    }
}
