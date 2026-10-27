# 🏦 Sistema Bancário com Transações Concorrentes

Sistema bancário em **Java** onde várias operações (depósitos, saques e transferências) acontecem ao mesmo tempo sobre as mesmas contas, simuladas por threads concorrentes. O projeto expõe a *race condition* clássica do saldo perdido, corrige-a com sincronização e garante que o saldo final bata exatamente com a soma esperada das operações, mesmo sob carga pesada.

---

## 🔒 Concorrência: regras do jogo

1. **Depósito e saque** usam `synchronized` (ou lock equivalente) no objeto `Conta`.
2. **Transferência** envolve duas contas, então as duas são bloqueadas sempre na mesma ordem (por exemplo, pelo menor ID primeiro). Assim, duas transferências em sentidos opostos nunca se travam uma à outra.
3. **Nenhum lock é mantido enquanto se espera o usuário** (confirmação, senha). Operações que dependem de confirmação ficam pendentes e só entram na seção crítica depois de confirmadas.
4. **Persistência atômica:** débito e crédito de uma transferência são gravados na mesma transação JDBC. Ou os dois persistem, ou nenhum.
5. **O saldo final de uma simulação é sempre verificável:** `saldo inicial + depósitos − saques ± transferências`.

---

## ✨ Funcionalidades

### 👤 Contas e clientes

- **Tipos de conta:** `ContaCorrente` e `ContaPoupanca` estendem `Conta`, cada uma com regras próprias (herança e polimorfismo).
- **Cheque especial:** a conta corrente pode ficar negativa até um limite configurável.
- **Cliente com várias contas:** um cliente pode ter N contas, com consulta de saldo consolidado.
- **Bloqueio e encerramento de conta:** conta bloqueada recusa operações; o encerramento só é permitido com saldo zerado.

### 💸 Operações e regras de negócio

- **Tarifa por operação de crédito:** cobra uma taxa em operações que usam crédito (como o uso do limite do cheque especial), registrada como uma `Transacao` própria.
- **Rendimento da poupança:** um agendador aplica juros periodicamente, concorrendo com as demais operações.
- **Limite diário de saque:** soma os saques do dia e recusa o que ultrapassar o limite.
- **Estorno de transação:** reverte uma operação mantendo o histórico, sem apagar o registro original.
- **Transferência agendada:** programa uma transferência para um horário futuro, executada em thread separada.
- **Protocolo único por operação:** cada transação recebe um identificador único (UUID), usado em comprovantes e na detecção de duplicidade.

### 🛡️ Segurança

- **Limite de tentativas de login:** após um número de erros seguidos, a conta é bloqueada. A senha/PIN é armazenada como hash com salt.
- **Autenticação reforçada para valores altos:** operações acima de um limite configurável exigem uma senha de transação adicional. Sem ela, a operação é recusada. O comportamento é definido por uma política (`PoliticaDeValorAlto`, padrão *Strategy*), que pode apenas avisar ou exigir senha.
- **Detecção de fraude:** sinaliza padrões suspeitos, como muitas operações em poucos segundos ou valores fora do comum.
- **Alerta de duplicidade de operações:** identifica a mesma operação enviada mais de uma vez e evita aplicá-la duas vezes.

### 🧵 Concorrência e desempenho

- **Relatório de desempenho da simulação:** mede tempo total, operações por segundo e quantidade de falhas (por exemplo, saldo insuficiente).
- **Demonstração controlada de deadlock:** provoca o deadlock de propósito (ordem de bloqueio invertida), detecta-o e depois mostra a versão corrigida. Serve de base para o relatório "antes e depois".
- **Transferência atômica no banco:** `commit`/`rollback` JDBC garantem que débito e crédito persistam juntos.

### 📄 Extrato e arquivos

- **Filtro de extrato:** consulta por período, tipo de transação ou faixa de valor.
- **Exportação em outros formatos:** além de `.txt`, o extrato pode ser exportado em CSV e JSON.
- **Importação de operações via arquivo:** lê um CSV com operações, executa em lote e gera um relatório de sucessos e falhas.

### 🗓️ Planejamento financeiro e avisos

- **Lembrete de pagamento mensal:** cadastra pagamentos recorrentes e avisa quando estiverem próximos.
- **Planejamento de gastos:** define limites por categoria de transação e compara com o que foi gasto.
- **Definir objetivos:** metas de economia (por exemplo, juntar um valor até uma data), com acompanhamento do progresso.
- **Sistema de notificações:** alertas (console e/ou arquivo de log) para saldo baixo, valor alto, fraude suspeita, lembretes e metas atingidas (padrão *Observer*).

### 🖥️ Interface

- **Menu interativo no console:** criar conta, operar, consultar extrato, exportar, agendar e rodar a simulação sem mexer no código.

---

## ▶️ Como executar

O sistema tem **interface gráfica** (janelas, botões, tabelas e gráfico). Requisito: **JDK 17 ou mais novo**. O banco (H2) é embutido: não há nada para instalar.
Os dados (contas, extratos, log de notificações) ficam em `<sua pasta de usuário>/SistemaBancario`.

### Opção A: gerar o `.exe` (Windows)

1. Dê dois cliques em **`gerar-exe.bat`** (usa o JDK que você já tem; baixa o H2 só na primeira vez).
2. Quando terminar, abra **`dist\SistemaBancario\SistemaBancario.exe`**.
3. Para levar para outro computador, copie a pasta inteira `dist\SistemaBancario` (o Java vai junto: não precisa estar instalado lá).

### Opção B: pelo VS Code

Abra `src/main/java/app/Main.java` e use **Run** (ou F5). A janela do sistema abre sozinha.

### Opção C: com Maven (se tiver instalado)

```bash
mvn test                              # roda os testes (inclui os de concorrência)
mvn package                           # gera target/sistema-bancario.jar
java -jar target/sistema-bancario.jar # abre a interface gráfica
```

### Outros modos

```bash
java -jar sistema-bancario.jar --console     # menu de texto, no terminal
java -jar sistema-bancario.jar --relatorio   # gera o relatório "antes e depois" em docs/
```

## 🎨 Identidade visual (JY Bank)

O programa segue a identidade do **JY Bank**: monograma JY (ícone da janela, da barra de tarefas e do `.exe`), paleta
`#97dffc` · `#858ae3` · `#613dc1` · `#4e148c` · `#2c0735` e a fonte **Roboto** (sem serifa, embutida no programa; licença Apache 2.0).
Os arquivos da marca ficam em `src/main/resources/imagens` e `src/main/resources/fontes`; o ícone do `.exe` está em `assets/icone.ico`.
O tema (cores, botões, cartões, tabelas) fica em `src/main/java/gui/Ui.java`.

## 🖼️ Telas

| Tela | O que faz |
|---|---|
| **Clientes e contas** | cadastra clientes, abre contas (corrente/poupança), bloqueia, desbloqueia, encerra e entra na conta com PIN |
| **Janela da conta** | saldo grande no topo; abas: Operações, Extrato (filtros, estorno, exportação), Agendamentos, Planejamento (lembretes, limites, objetivos) e Notificações |
| **Simulação concorrente** | roda a corrida de threads sem e com sincronização, com gráfico de barras *esperado × obtido*, e demonstra o deadlock |
| **Importar operações** | escolhe o CSV, executa em lote e mostra o relatório |
| **Poupança** | aplica o rendimento na hora ou liga o agendador automático |

Os avisos do sistema (fraude, saldo baixo, meta atingida…) aparecem como cartões no canto da tela. Dica: abra a **mesma conta em duas janelas** e opere nas duas ao mesmo tempo.

## 🗂️ Estrutura

| Pacote | O que tem |
|---|---|
| `domain` | `Conta`, `ContaCorrente`, `ContaPoupanca`, `Cliente`, `Transacao` (imutável), `BancoException` — sem threads e sem SQL |
| `persistence` | `ContaRepository`, `TransacaoRepository`, `ClienteRepository` (JDBC), `RegistroOperacoesJdbc` (commit/rollback) |
| `servico` | `BancoService` (casos de uso), `AgendadorDeTarefas` (transferências agendadas e rendimento) |
| `seguranca` | `Credencial` (hash + salt), `PoliticaDeValorAlto` (*Strategy*), detectores de fraude e duplicidade |
| `notificacao` | `Notificador` + observadores de console, arquivo e histórico (*Observer*) |
| `planejamento` | limites de gasto, objetivos, lembretes mensais |
| `arquivo` | exportação (TXT/CSV/JSON) e importação de operações |
| `simulacao` | `OperacaoBancariaThread`, `SimulacaoConcorrente`, `ContaInsegura` (race condition de propósito), `DemonstracaoDeadlock` |
| `gui` | interface gráfica (Swing): `JanelaPrincipal`, `PainelClientes`, `PainelConta`, `PainelSimulacao`, `GraficoSaldos`… |
| `app` | `Main`, `MenuPrincipal` (modo texto), `Aplicacao` (liga as peças) |

## 📦 Entregáveis da disciplina

- **Código-fonte** organizado em `domain`, `persistence`, `simulacao` e `app` (mais os pacotes de apoio acima): `src/main/java`
- **Script de criação das tabelas:** `src/main/resources/schema.sql` (também executado automaticamente na inicialização)
- **Relatório "antes e depois":** `docs/RELATORIO.md` (log completo em `docs/relatorio_antes_depois.txt`)
- **Extrato exportado de exemplo:** `docs/extrato_exemplo.txt`
- **Capturas das telas:** `docs/telas/`
- **Testes JUnit:** `src/test/java` (30 testes, incluindo concorrência e montagem das telas)

## 📥 Formato do CSV de importação

```
chave;tipo;conta;destino;valor[;senhaTransacao]
op-001;DEPOSITO;1;;150,00
op-002;TRANSFERENCIA;1;2;100,00
```

A `chave` identifica a operação: importar o mesmo arquivo de novo não aplica nada duas vezes (as linhas já processadas aparecem como duplicadas no relatório). Veja `operacoes_exemplo.csv`.

## ⚠️ Limites conhecidos

- Limites de gasto, objetivos, lembretes e agendamentos ficam **em memória** (valem enquanto o programa está aberto). Contas, clientes e transações são persistidos.
- A detecção de duplicidade também é em memória: ela protege contra reenvio durante a execução, não entre execuções.
