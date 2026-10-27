package arquivo;

import config.Configuracao;
import domain.Conta;
import domain.Transacao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import util.Dinheiro;

/** Exporta o extrato de uma conta para arquivo (I/O): texto, CSV ou JSON. */
public final class ExportadorExtrato {
    public enum Formato {
        TXT("txt"), CSV("csv"), JSON("json");

        private final String extensao;
        Formato(String extensao) { this.extensao = extensao; }
        public String extensao() { return extensao; }
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter NOME_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ExportadorExtrato() { }

    /** Caminho padrão: exportacoes/extrato_conta_<id>_<data>.<ext> */
    public static Path caminhoPadrao(Conta conta, Formato formato) {
        return Path.of(Configuracao.PASTA_EXPORTACAO,
                "extrato_conta_" + conta.getId() + "_" + LocalDateTime.now().format(NOME_ARQUIVO) + "." + formato.extensao());
    }

    public static Path exportar(Conta conta, List<Transacao> transacoes, Formato formato, Path destino) {
        String conteudo = switch (formato) {
            case TXT -> montarTxt(conta, transacoes);
            case CSV -> montarCsv(transacoes);
            case JSON -> montarJson(conta, transacoes);
        };
        try {
            if (destino.getParent() != null) Files.createDirectories(destino.getParent());
            Files.writeString(destino, conteudo, StandardCharsets.UTF_8);
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o extrato em " + destino, e);
        }
    }

    // ------------------------------------------------------------------ TXT

    static String montarTxt(Conta conta, List<Transacao> transacoes) {
        String linha = "=".repeat(118);
        StringBuilder sb = new StringBuilder();
        sb.append(linha).append('\n');
        sb.append(" EXTRATO BANCÁRIO - Conta ").append(conta.getId()).append(" (").append(conta.tipo()).append(")\n");
        sb.append(" Emitido em: ").append(LocalDateTime.now().format(DATA_HORA)).append('\n');
        sb.append(linha).append('\n');
        sb.append(String.format(" %-19s  %-22s  %14s  %-36s  %s%n", "Data/Hora", "Tipo", "Valor (R$)", "Protocolo", "Descrição"));
        sb.append("-".repeat(118)).append('\n');

        BigDecimal creditos = BigDecimal.ZERO;
        BigDecimal debitos = BigDecimal.ZERO;
        for (Transacao t : transacoes) {
            String sinal = t.tipo().ehCredito() ? "+" : "-";
            sb.append(String.format(" %-19s  %-22s  %14s  %-36s  %s%n", t.dataHora().format(DATA_HORA), t.tipo(),
                    sinal + Dinheiro.numero(t.valor()), t.protocolo(), t.descricao()));
            if (t.tipo().ehCredito()) creditos = creditos.add(t.valor()); else debitos = debitos.add(t.valor());
        }
        if (transacoes.isEmpty()) sb.append(" (nenhuma transação encontrada)\n");
        sb.append("-".repeat(118)).append('\n');
        sb.append(String.format(" Transações: %d | Total de créditos: R$ %s | Total de débitos: R$ %s%n",
                transacoes.size(), Dinheiro.numero(creditos), Dinheiro.numero(debitos)));
        sb.append(" Saldo atual da conta: ").append(Dinheiro.formatar(conta.getSaldo())).append('\n');
        sb.append(linha).append('\n');
        return sb.toString();
    }

    // ------------------------------------------------------------------ CSV

    static String montarCsv(List<Transacao> transacoes) {
        StringBuilder sb = new StringBuilder("protocolo;data_hora;tipo;valor;conta_relacionada;protocolo_relacionado;descricao\n");
        for (Transacao t : transacoes) {
            sb.append(t.protocolo()).append(';')
              .append(t.dataHora().format(ISO)).append(';')
              .append(t.tipo()).append(';')
              .append(t.valorComSinal().toPlainString()).append(';')
              .append(t.contaRelacionadaId() == null ? "" : t.contaRelacionadaId()).append(';')
              .append(t.protocoloRelacionado() == null ? "" : t.protocoloRelacionado()).append(';')
              .append(campoCsv(t.descricao())).append('\n');
        }
        return sb.toString();
    }

    private static String campoCsv(String texto) {
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    // ------------------------------------------------------------------ JSON

    static String montarJson(Conta conta, List<Transacao> transacoes) {
        String itens = transacoes.stream().map(t -> "    {"
                + "\"protocolo\": " + texto(t.protocolo()) + ", "
                + "\"dataHora\": " + texto(t.dataHora().format(ISO)) + ", "
                + "\"tipo\": " + texto(t.tipo().name()) + ", "
                + "\"valor\": " + t.valorComSinal().toPlainString() + ", "
                + "\"contaRelacionada\": " + (t.contaRelacionadaId() == null ? "null" : t.contaRelacionadaId()) + ", "
                + "\"protocoloRelacionado\": " + (t.protocoloRelacionado() == null ? "null" : texto(t.protocoloRelacionado())) + ", "
                + "\"descricao\": " + texto(t.descricao()) + "}")
                .collect(Collectors.joining(",\n"));
        return "{\n"
                + "  \"conta\": {\"id\": " + conta.getId() + ", \"tipo\": " + texto(conta.tipo())
                + ", \"saldo\": " + conta.getSaldo().toPlainString() + "},\n"
                + "  \"geradoEm\": " + texto(LocalDateTime.now().format(ISO)) + ",\n"
                + "  \"transacoes\": [\n" + itens + (itens.isEmpty() ? "" : "\n") + "  ]\n"
                + "}\n";
    }

    private static String texto(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
