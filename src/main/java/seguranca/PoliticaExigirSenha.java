package seguranca;

import domain.BancoException;
import domain.Conta;
import java.math.BigDecimal;
import notificacao.Notificador;
import notificacao.TipoNotificacao;
import util.Dinheiro;

/** Autenticação reforçada: acima do limite, a operação só passa com a senha de transação correta. */
public class PoliticaExigirSenha implements PoliticaDeValorAlto {
    private final BigDecimal limite;
    private final Notificador notificador;

    public PoliticaExigirSenha(BigDecimal limite, Notificador notificador) {
        this.limite = limite;
        this.notificador = notificador;
    }

    @Override public boolean exigeSenha(BigDecimal valor) { return valor.compareTo(limite) >= 0; }

    @Override
    public void validar(Conta conta, BigDecimal valor, String senhaTransacao) {
        if (!exigeSenha(valor)) return;
        notificador.notificar(TipoNotificacao.VALOR_ALTO, conta.getId(),
                "Operação de valor alto: " + Dinheiro.formatar(valor) + " exige senha de transação.");
        if (!conta.possuiSenhaTransacao()) {
            throw new BancoException.Autenticacao("A conta não tem senha de transação cadastrada: operação de valor alto recusada.");
        }
        if (senhaTransacao == null || !conta.verificarSenhaTransacao(senhaTransacao)) {
            throw new BancoException.Autenticacao("Operações acima de " + Dinheiro.formatar(limite)
                    + " exigem a senha de transação correta. Operação recusada.");
        }
    }
}
