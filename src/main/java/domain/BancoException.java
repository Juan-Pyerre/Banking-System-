package domain;

/** Exceção base do sistema (não verificada, para poder ser usada dentro de threads). */
public class BancoException extends RuntimeException {
    public BancoException(String mensagem) { super(mensagem); }
    public BancoException(String mensagem, Throwable causa) { super(mensagem, causa); }

    public static class SaldoInsuficiente extends BancoException {
        public SaldoInsuficiente(String m) { super(m); }
    }
    public static class ContaIndisponivel extends BancoException {
        public ContaIndisponivel(String m) { super(m); }
    }
    public static class LimiteExcedido extends BancoException {
        public LimiteExcedido(String m) { super(m); }
    }
    public static class OperacaoInvalida extends BancoException {
        public OperacaoInvalida(String m) { super(m); }
    }
    public static class Autenticacao extends BancoException {
        public Autenticacao(String m) { super(m); }
    }
    public static class Duplicidade extends BancoException {
        public Duplicidade(String m) { super(m); }
    }
    public static class FalhaDePersistencia extends BancoException {
        public FalhaDePersistencia(String m, Throwable causa) { super(m, causa); }
    }
}
