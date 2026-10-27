package seguranca;

import domain.Conta;
import java.math.BigDecimal;
import notificacao.Notificador;
import notificacao.TipoNotificacao;
import util.Dinheiro;

/** Política branda: só gera um aviso de "valor alto" e deixa a operação seguir. */
public class PoliticaApenasAvisar implements PoliticaDeValorAlto {
    private final BigDecimal limite;
    private final Notificador notificador;

    public PoliticaApenasAvisar(BigDecimal limite, Notificador notificador) {
        this.limite = limite;
        this.notificador = notificador;
    }

    @Override public boolean exigeSenha(BigDecimal valor) { return false; }

    @Override
    public void validar(Conta conta, BigDecimal valor, String senhaTransacao) {
        if (valor.compareTo(limite) >= 0) {
            notificador.notificar(TipoNotificacao.VALOR_ALTO, conta.getId(),
                    "Operação de valor alto: " + Dinheiro.formatar(valor) + " (limite " + Dinheiro.formatar(limite) + ").");
        }
    }
}
