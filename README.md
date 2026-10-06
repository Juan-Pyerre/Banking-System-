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
