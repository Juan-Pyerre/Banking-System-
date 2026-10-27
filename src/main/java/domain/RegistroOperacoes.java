package domain;

import java.util.List;

/**
 * Porta pela qual o domínio avisa que uma operação foi aplicada em memória e precisa ser gravada.
 * É chamada DENTRO da seção crítica da conta, para que a ordem de gravação seja a mesma ordem das operações.
 * Se lançar exceção, a conta desfaz a alteração feita em memória (nada fica pela metade).
 *
 * A implementação JDBC grava transações + saldos das contas na MESMA transação de banco (commit/rollback).
 */
@FunctionalInterface
public interface RegistroOperacoes {
    RegistroOperacoes NENHUM = (transacoes, contas) -> { };

    void registrar(List<Transacao> transacoes, List<Conta> contasAfetadas);
}
