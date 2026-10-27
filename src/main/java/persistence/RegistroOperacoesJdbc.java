package persistence;

import domain.BancoException;
import domain.Conta;
import domain.RegistroOperacoes;
import domain.Transacao;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Grava transações e saldos na MESMA transação JDBC: o débito e o crédito de uma transferência
 * persistem juntos (commit) ou não persistem (rollback).
 */
public class RegistroOperacoesJdbc implements RegistroOperacoes {
    private final ConexaoJdbc conexao;

    public RegistroOperacoesJdbc(ConexaoJdbc conexao) { this.conexao = conexao; }

    @Override
    public void registrar(List<Transacao> transacoes, List<Conta> contasAfetadas) {
        try (Connection c = conexao.abrir()) {
            c.setAutoCommit(false);
            try {
                for (Transacao t : transacoes) TransacaoRepository.inserir(c, t);
                for (Conta conta : contasAfetadas) ContaRepository.atualizarEstado(c, conta);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BancoException.FalhaDePersistencia("Falha ao gravar a operação (nada foi alterado): " + e.getMessage(), e);
        }
    }
}
