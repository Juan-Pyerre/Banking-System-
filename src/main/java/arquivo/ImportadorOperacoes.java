package arquivo;

import domain.BancoException;
import domain.Transacao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import servico.BancoService;
import util.Dinheiro;

/**
 * Lê um CSV de operações, executa em lote e gera um relatório de sucessos e falhas.
 *
 * Formato (separador ";", linhas começando com # são comentários):
 *   chave;tipo;conta;destino;valor[;senhaTransacao]
 *   op-001;DEPOSITO;1;;150,00
 *   op-002;SAQUE;1;;50,00
 *   op-003;TRANSFERENCIA;1;2;300,00;123456
 * A "chave" identifica a operação: se o mesmo arquivo for importado de novo, as linhas já processadas são
 * recusadas como duplicadas em vez de serem aplicadas duas vezes.
 */
public class ImportadorOperacoes {

    public record ResultadoImportacao(int total, int sucessos, int falhas, int duplicadas, List<String> detalhes) {
        public String formatar() {
            StringBuilder sb = new StringBuilder("RELATÓRIO DE IMPORTAÇÃO\n");
            sb.append("Linhas processadas: ").append(total).append(" | Sucessos: ").append(sucessos)
              .append(" | Falhas: ").append(falhas).append(" | Duplicadas: ").append(duplicadas).append("\n\n");
            detalhes.forEach(d -> sb.append(d).append('\n'));
            return sb.toString();
        }
    }

    private final BancoService banco;

    public ImportadorOperacoes(BancoService banco) { this.banco = banco; }

    public ResultadoImportacao importar(Path arquivo, Path relatorio) {
        List<String> linhas;
        try {
            linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo " + arquivo, e);
        }
        int total = 0, sucessos = 0, falhas = 0, duplicadas = 0;
        List<String> detalhes = new ArrayList<>();

        for (int i = 0; i < linhas.size(); i++) {
            String linha = linhas.get(i).trim();
            if (linha.isEmpty() || linha.startsWith("#") || linha.toLowerCase().startsWith("chave;")) continue;
            total++;
            String rotulo = "Linha " + (i + 1) + ": ";
            try {
                String[] c = linha.split(";", -1);
                if (c.length < 5) {
                    throw new BancoException.OperacaoInvalida("linha incompleta (esperado chave;tipo;conta;destino;valor[;senha])");
                }
                String chave = c[0].trim();
                String tipo = c[1].trim().toUpperCase();
                int conta = Integer.parseInt(c[2].trim());
                BigDecimal valor = Dinheiro.ler(c[4]);
                String senha = c.length > 5 && !c[5].isBlank() ? c[5].trim() : null;
                Transacao t = switch (tipo) {
                    case "DEPOSITO" -> banco.depositar(conta, valor, chave);
                    case "SAQUE" -> banco.sacar(conta, valor, senha, chave);
                    case "TRANSFERENCIA" -> banco.transferir(conta, Integer.parseInt(c[3].trim()), valor, senha, chave);
                    default -> throw new BancoException.OperacaoInvalida("tipo desconhecido: " + tipo);
                };
                sucessos++;
                detalhes.add("OK       " + rotulo + chave + " " + tipo + " " + Dinheiro.formatar(valor) + " -> protocolo " + t.protocolo());
            } catch (BancoException.Duplicidade e) {
                duplicadas++;
                detalhes.add("DUPLICADA " + rotulo + e.getMessage());
            } catch (BancoException | IllegalArgumentException e) {
                falhas++;
                detalhes.add("FALHA    " + rotulo + e.getMessage());
            }
        }
        ResultadoImportacao resultado = new ResultadoImportacao(total, sucessos, falhas, duplicadas, detalhes);
        try {
            if (relatorio.getParent() != null) Files.createDirectories(relatorio.getParent());
            Files.writeString(relatorio, resultado.formatar(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o relatório em " + relatorio, e);
        }
        return resultado;
    }
}
