package seguranca;

import domain.Conta;
import java.math.BigDecimal;

/**
 * Padrão Strategy: define o que acontece quando uma operação passa do limite de "valor alto".
 * Implementações: {@link PoliticaApenasAvisar} e {@link PoliticaExigirSenha}.
 */
public interface PoliticaDeValorAlto {
    /** Esta operação exigiria senha de transação? (o menu usa isso para pedir a senha ANTES de entrar na seção crítica) */
    boolean exigeSenha(BigDecimal valor);

    /** Valida a operação. Lança {@code BancoException.Autenticacao} se for recusada. */
    void validar(Conta conta, BigDecimal valor, String senhaTransacao);
}
