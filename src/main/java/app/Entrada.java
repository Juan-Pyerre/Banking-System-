package app;

import java.io.Console;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import util.Dinheiro;

/** Leitura segura de dados digitados no console (repete a pergunta quando o formato está errado). */
public class Entrada {
    /** A entrada acabou (Ctrl+D / fim do arquivo): o menu encerra com elegância. */
    public static class FimDaEntrada extends RuntimeException { }

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

    public String texto(String pergunta) {
        System.out.print(pergunta);
        System.out.flush();
        if (!scanner.hasNextLine()) throw new FimDaEntrada();
        return scanner.nextLine().trim();
    }

    public String textoOuPadrao(String pergunta, String padrao) {
        String t = texto(pergunta + " [" + padrao + "]: ");
        return t.isEmpty() ? padrao : t;
    }

    public int inteiro(String pergunta) {
        while (true) {
            try {
                return Integer.parseInt(texto(pergunta));
            } catch (NumberFormatException e) {
                System.out.println("Digite um número inteiro.");
            }
        }
    }

    public int inteiroOuPadrao(String pergunta, int padrao) {
        while (true) {
            String t = texto(pergunta + " [" + padrao + "]: ");
            if (t.isEmpty()) return padrao;
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException e) {
                System.out.println("Digite um número inteiro.");
            }
        }
    }

    public BigDecimal valor(String pergunta) {
        while (true) {
            try {
                return Dinheiro.ler(texto(pergunta));
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido. Exemplos: 150, 150.50 ou 1.234,56");
            }
        }
    }

    /** Valor opcional: em branco devolve null. */
    public BigDecimal valorOpcional(String pergunta) {
        while (true) {
            String t = texto(pergunta);
            if (t.isEmpty()) return null;
            try {
                return Dinheiro.ler(t);
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido.");
            }
        }
    }

    /** Pede uma senha sem mostrar na tela quando há um terminal; sem terminal (entrada redirecionada), lê normalmente. */
    public String senha(String pergunta) {
        Console console = System.console();
        if (console != null) {
            char[] digitada = console.readPassword("%s", pergunta);
            if (digitada == null) throw new FimDaEntrada();
            return new String(digitada);
        }
        return texto(pergunta);
    }

    public boolean confirmar(String pergunta) {
        String t = texto(pergunta + " (s/n): ").toLowerCase();
        return t.equals("s") || t.equals("sim");
    }

    /** Data opcional no formato dd/MM/yyyy; em branco devolve null. */
    public LocalDate dataOpcional(String pergunta) {
        while (true) {
            String t = texto(pergunta);
            if (t.isEmpty()) return null;
            try {
                return LocalDate.parse(t, DATA);
            } catch (java.time.format.DateTimeParseException e) {
                System.out.println("Data inválida. Use dd/MM/aaaa.");
            }
        }
    }

    public LocalDate data(String pergunta) {
        while (true) {
            LocalDate d = dataOpcional(pergunta);
            if (d != null) return d;
            System.out.println("Informe a data.");
        }
    }

    /** Aceita "dd/MM/aaaa HH:mm" ou "+N" (daqui a N segundos, bom para demonstração). */
    public LocalDateTime horario(String pergunta) {
        while (true) {
            String t = texto(pergunta);
            try {
                if (t.startsWith("+")) return LocalDateTime.now().plusSeconds(Long.parseLong(t.substring(1).trim()));
                return LocalDateTime.parse(t, DATA_HORA);
            } catch (RuntimeException e) {
                System.out.println("Horário inválido. Use dd/MM/aaaa HH:mm ou +N (segundos a partir de agora).");
            }
        }
    }
}
