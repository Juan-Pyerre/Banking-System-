package util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formatação e leitura de valores em reais. */
public final class Dinheiro {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Dinheiro() { }

    /** Ex.: 1234.5 vira "R$ 1.234,50". */
    public static String formatar(BigDecimal valor) {
        return "R$ " + numero(valor);
    }

    /** Ex.: 1234.5 vira "1.234,50" (sem o símbolo da moeda). */
    public static String numero(BigDecimal valor) {
        // DecimalFormat não é thread-safe, por isso uma instância nova a cada chamada
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR)).format(valor);
    }

    /** Aceita "10", "10.50", "10,50" e "1.234,56". */
    public static BigDecimal ler(String texto) {
        String t = texto.trim().replace("R$", "").replace(" ", "");
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(t);
    }
}
