# Relatório: antes e depois da correção da race condition

## O problema (antes)

Várias threads fazendo depósitos, saques e transferências sobre as mesmas contas, **sem sincronização**
(`simulacao.ContaInsegura`, que lê o saldo, cede a vez com `Thread.yield()` e escreve por cima). Duas threads leem o
mesmo saldo, cada uma soma o seu valor e a última a escrever apaga a alteração da outra: o "saldo perdido".

Resultado: **o saldo final é errado e muda a cada execução** (veja a coluna "Saldo obtido" nas três rodadas abaixo
contra o "Saldo esperado", que vem de um livro-razão independente das contas).

## A correção (depois)

- `Conta.depositar`, `Conta.sacar` (e as demais operações de uma conta só) passaram a ser `synchronized`.
- `Conta.transferirPara` trava as **duas** contas sempre na mesma ordem (menor id primeiro), então duas transferências
  em sentidos opostos nunca se travam.
- Nenhum lock espera o usuário: senhas e confirmações são resolvidas **antes** de entrar na seção crítica.

Com a mesma carga, o saldo final **bate exatamente** com o esperado em todas as contas, em todas as rodadas.

## Deadlock

A parte 3 do log mostra o deadlock provocado de propósito (A→B e B→A travando na ordem recebida), detectado por
`ThreadMXBean.findDeadlockedThreads()`, e depois a versão corrigida concluindo 40.000 transferências em sentidos
opostos sem travar.

## Como reproduzir

```
java -jar target/sistema-bancario.jar --relatorio
```
(ou, no menu, opção 7 → 4 para o comparativo e 7 → 5 para o deadlock). Os tempos variam conforme a máquina; o que não
varia é o veredito: sem sincronização os saldos divergem, com sincronização nunca.

## Log completo da execução

```
RELATÓRIO: ANTES E DEPOIS DA CORREÇÃO DA RACE CONDITION
Mesma carga nas duas versões: mesmas sementes, mesmas operações sorteadas; só muda a sincronização.
O saldo esperado pode variar um pouco entre rodadas: um saque recusado por saldo insuficiente depende da ordem em que
as threads rodam. O que importa é a coluna "obtido" bater com a coluna "esperado" em TODAS as contas.

PARTE 1 - ANTES (sem sincronização), 3 execuções seguidas: o saldo final VARIA a cada rodada

Execução 1:
=== SEM sincronização (race condition) ===
Contas: 4 | Threads: 8 | Operações por thread: 20000 (total tentado: 160000)
Tempo total: 332 ms | Operações/segundo: 481928
Sucessos: 160000 | Falhas: 0 
Conta        Saldo esperado       Saldo obtido      Diferença
1                 400369.28          243607.29     -156761.99 <-- DIVERGE
2                 421513.99          249553.36     -171960.63 <-- DIVERGE
3                 381027.47          235822.60     -145204.87 <-- DIVERGE
4                 402763.04          221452.99     -181310.05 <-- DIVERGE
TOTAL            1605673.78          950436.24     -655237.54
RESULTADO: SALDOS CORROMPIDOS pela race condition

Execução 2:
=== SEM sincronização (race condition) ===
Contas: 4 | Threads: 8 | Operações por thread: 20000 (total tentado: 160000)
Tempo total: 401 ms | Operações/segundo: 399002
Sucessos: 160000 | Falhas: 0 
Conta        Saldo esperado       Saldo obtido      Diferença
1                 400369.28          256198.34     -144170.94 <-- DIVERGE
2                 421513.99          240325.02     -181188.97 <-- DIVERGE
3                 381027.47          228090.25     -152937.22 <-- DIVERGE
4                 402763.04          215996.75     -186766.29 <-- DIVERGE
TOTAL            1605673.78          940610.36     -665063.42
RESULTADO: SALDOS CORROMPIDOS pela race condition

Execução 3:
=== SEM sincronização (race condition) ===
Contas: 4 | Threads: 8 | Operações por thread: 20000 (total tentado: 160000)
Tempo total: 208 ms | Operações/segundo: 769231
Sucessos: 160000 | Falhas: 0 
Conta        Saldo esperado       Saldo obtido      Diferença
1                 400369.28          237149.15     -163220.13 <-- DIVERGE
2                 421513.99          246001.03     -175512.96 <-- DIVERGE
3                 381027.47          252107.93     -128919.54 <-- DIVERGE
4                 402763.04          234720.51     -168042.53 <-- DIVERGE
TOTAL            1605673.78          969978.62     -635695.16
RESULTADO: SALDOS CORROMPIDOS pela race condition

PARTE 2 - DEPOIS (synchronized em Conta), 3 execuções seguidas: o saldo SEMPRE bate

Execução 1:
=== COM sincronização (synchronized) ===
Contas: 4 | Threads: 8 | Operações por thread: 20000 (total tentado: 160000)
Tempo total: 1184 ms | Operações/segundo: 135135
Sucessos: 159999 | Falhas: 1 {SALDO_INSUFICIENTE=1}
Conta        Saldo esperado       Saldo obtido      Diferença
1                 400517.34          400517.34           0.00 OK
2                 421513.99          421513.99           0.00 OK
3                 381027.47          381027.47           0.00 OK
4                 402763.04          402763.04           0.00 OK
TOTAL            1605821.84         1605821.84           0.00
RESULTADO: SALDOS CORRETOS (nenhuma operação perdida)

Execução 2:
=== COM sincronização (synchronized) ===
Contas: 4 | Threads: 8 | Operações por thread: 20000 (total tentado: 160000)
Tempo total: 497 ms | Operações/segundo: 321932
Sucessos: 159971 | Falhas: 29 {SALDO_INSUFICIENTE=29}
Conta        Saldo esperado       Saldo obtido      Diferença
1                 399900.98          399900.98           0.00 OK
2                 423882.45          423882.45           0.00 OK
3                 381813.56          381813.56           0.00 OK
4                 401991.52          401991.52           0.00 OK
TOTAL            1607588.51         1607588.51           0.00
RESULTADO: SALDOS CORRETOS (nenhuma operação perdida)

Execução 3:
=== COM sincronização (synchronized) ===
Contas: 4 | Threads: 8 | Operações por thread: 20000 (total tentado: 160000)
Tempo total: 426 ms | Operações/segundo: 375587
Sucessos: 159993 | Falhas: 7 {SALDO_INSUFICIENTE=7}
Conta        Saldo esperado       Saldo obtido      Diferença
1                 400369.28          400369.28           0.00 OK
2                 422287.77          422287.77           0.00 OK
3                 380931.84          380931.84           0.00 OK
4                 402614.52          402614.52           0.00 OK
TOTAL            1606203.41         1606203.41           0.00
RESULTADO: SALDOS CORRETOS (nenhuma operação perdida)

PARTE 3 - DEADLOCK EM TRANSFERÊNCIAS

--- ANTES: ordem de bloqueio invertida (deadlock proposital) ---
DEADLOCK DETECTADO pelo ThreadMXBean.findDeadlockedThreads():
  - transferencia-A-para-B está bloqueada esperando domain.ContaCorrente@6438a396, que pertence a transferencia-B-para-A
  - transferencia-B-para-A está bloqueada esperando domain.ContaCorrente@e2144e4, que pertence a transferencia-A-para-B
As duas threads ficaram presas, cada uma segurando a conta que a outra quer.

--- DEPOIS: Conta.transferirPara trava sempre o menor id primeiro ---
Concluído sem deadlock: 40000 transferências em sentidos opostos em 130 ms.
Saldo A = 100000.00 | Saldo B = 100000.00 | Soma = 200000.00 (esperado 200000.00)
```
