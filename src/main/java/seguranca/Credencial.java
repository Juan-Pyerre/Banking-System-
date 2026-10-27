package seguranca;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Senha/PIN armazenado como HASH com SALT (PBKDF2-HMAC-SHA256). A senha em si nunca é guardada.
 */
public final class Credencial {
    private static final int ITERACOES = 65_536;
    private static final int TAMANHO_HASH_BITS = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final String salt;  // Base64
    private final String hash;  // Base64

    private Credencial(String salt, String hash) {
        this.salt = salt;
        this.hash = hash;
    }

    /** Cria uma credencial nova (gera um salt aleatório). */
    public static Credencial criar(String segredo) {
        byte[] saltBytes = new byte[16];
        ALEATORIO.nextBytes(saltBytes);
        return new Credencial(Base64.getEncoder().encodeToString(saltBytes), calcular(segredo, saltBytes));
    }

    /** Reconstrói uma credencial já gravada (ex.: lida do banco). */
    public static Credencial restaurar(String salt, String hash) {
        return new Credencial(salt, hash);
    }

    public boolean confere(String segredo) {
        if (segredo == null) return false;
        byte[] esperado = Base64.getDecoder().decode(hash);
        byte[] obtido = Base64.getDecoder().decode(calcular(segredo, Base64.getDecoder().decode(salt)));
        return MessageDigest.isEqual(esperado, obtido); // comparação em tempo constante
    }

    public String getSalt() { return salt; }
    public String getHash() { return hash; }

    private static String calcular(String segredo, byte[] saltBytes) {
        try {
            KeySpec spec = new PBEKeySpec(segredo.toCharArray(), saltBytes, ITERACOES, TAMANHO_HASH_BITS);
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular o hash da senha", e);
        }
    }
}
