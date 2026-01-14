# Race Condition Demo - Banking System

Projeto demonstrativo de **Race Conditions** e suas soluções usando **Optimistic** e **Pessimistic Locking** em um sistema bancário simples.

## 🎯 Objetivo

Mostrar na prática como race conditions podem acontecer em sistemas concorrentes e como resolver usando diferentes estratégias de locking.

## 📋 O Problema

**Cenário:** Conta A tem R$ 100,00

Duas transferências simultâneas:
- Thread 1: Transferir R$ 80,00 da conta A para conta B
- Thread 2: Transferir R$ 80,00 da conta A para conta C

**Resultado esperado:** Uma transferência passa, outra falha (saldo insuficiente)

**Resultado com race condition:** Ambas podem passar, deixando o saldo em -R$ 60,00! 💥

### Como acontece?

```
Thread 1: lê saldo A = R$ 100 ✓
Thread 2: lê saldo A = R$ 100 ✓
Thread 1: verifica saldo >= 80 ✓
Thread 2: verifica saldo >= 80 ✓
Thread 1: debita 80 → saldo = R$ 20
Thread 2: debita 80 → saldo = -R$ 60 ❌ PROBLEMA!
```

## 🛠️ As Três Abordagens

### 1️⃣ UNSAFE (sem proteção)

**O que faz:** Nada. Simplesmente lê, verifica e salva.

**Problema:** Race condition! Múltiplas threads podem ler o mesmo valor antes de qualquer atualização.

**Quando acontece:** Alta concorrência na mesma conta.

**Código:**
```java
transferService.transferUnsafe("A", "B", new BigDecimal("80"));
```

**Resultado:** Saldo pode ficar negativo ❌

---

### 2️⃣ OPTIMISTIC LOCKING

**O que faz:** 
- Adiciona campo `@Version` na entidade
- JPA incrementa a version a cada save
- Se a version mudou entre read e write → erro

**Como funciona:**
```
Thread 1: lê conta A (version=1, saldo=100)
Thread 2: lê conta A (version=1, saldo=100)
Thread 1: salva (version 1→2) ✅
Thread 2: tenta salvar (version esperada=1, atual=2) ❌ OptimisticLockException
```

**Vantagens:**
- ✅ Melhor performance (sem lock no banco)
- ✅ Escala melhor
- ✅ Não bloqueia outras threads

**Desvantagens:**
- ❌ Precisa de retry quando dá conflito
- ❌ Pode falhar múltiplas vezes em alta concorrência

**Código:**
```java
// Com retry automático
transferService.transferOptimisticWithRetry("A", "B", new BigDecimal("80"));
```

**Quando usar:** 
- Conflitos são raros (ex: estoque com milhares de unidades)
- Performance é crítica
- Você pode lidar com retries

---

### 3️⃣ PESSIMISTIC LOCKING

**O que faz:**
- Trava o registro no banco (SELECT ... FOR UPDATE)
- Outras threads ficam esperando
- Garante exclusividade

**Como funciona:**
```
Thread 1: SELECT ... FOR UPDATE → TRAVA conta A
Thread 2: tenta ler conta A → ESPERA...
Thread 1: debita, salva, commit → LIBERA lock
Thread 2: agora consegue ler → verifica saldo insuficiente → falha
```

**Vantagens:**
- ✅ Garante consistência total
- ✅ Não precisa de retry
- ✅ Previsível

**Desvantagens:**
- ❌ Mais lento (lock é caro)
- ❌ Pode causar deadlock
- ❌ Escala pior (threads esperando)

**Código:**
```java
transferService.transferPessimistic("A", "B", new BigDecimal("80"));
```

**Quando usar:**
- Sistemas financeiros (saldo, transações)
- Conflitos são frequentes
- Consistência é crítica
- Não pode ter retry

---

## 🚀 Como rodar

### Pré-requisitos
- Java 17+
- Maven

### Executar testes

```bash
mvn clean test
```

### Ver os resultados

Os testes mostram o comportamento de cada abordagem:

```
=== TESTE: UNSAFE (COM RACE CONDITION) ===
Thread 1: transferência OK
Thread 2: transferência OK
Resultado:
- Conta A: -60.00 (esperado: 20 ou mais)
⚠️  RACE CONDITION DETECTADO! Saldo ficou negativo!

=== TESTE: OPTIMISTIC LOCKING (COM RETRY) ===
Thread 1: transferência OK
Thread 2: transferência FALHOU - Insufficient funds
Resultado:
- Conta A: 20.00
✅ Consistência garantida!

=== TESTE: PESSIMISTIC LOCKING ===
Thread 1: transferência OK
Thread 2: transferência FALHOU - Insufficient funds
Resultado:
- Conta A: 20.00
✅ Consistência garantida com pessimistic lock!
```

## 📊 Comparação

| Critério | Unsafe | Optimistic | Pessimistic |
|----------|--------|------------|-------------|
| Consistência | ❌ Não garante | ✅ Com retry | ✅ Garantida |
| Performance | 🚀 Rápido | 🏃 Bom | 🐌 Mais lento |
| Retry | - | ✅ Necessário | ❌ Não precisa |
| Escala | 📈 Muito bem | 📈 Bem | 📉 Pior |
| Deadlock | - | - | ⚠️ Possível |
| Uso | ❌ Nunca | Ecommerce, CMS | Banco, fintech |

## 🎓 Conceitos Importantes

### Race Condition
Quando o resultado depende da ordem de execução de threads/processos.

### Optimistic Locking
Assume que conflitos são raros. Deixa todo mundo ler, mas verifica se alguém alterou antes de salvar.

### Pessimistic Locking
Assume que conflitos vão acontecer. Trava antes de ler para garantir exclusividade.

### Distributed Transactions (próximo nível)
Quando você tem múltiplos bancos de dados e precisa garantir consistência entre eles.
Patterns: Saga, 2PC (Two-Phase Commit).

## 📁 Estrutura do Projeto

```
src/main/java/com/example/banking/
├── entity/Account.java           # Entidade com @Version
├── repository/AccountRepository.java  # Métodos de locking
└── service/TransferService.java  # 3 implementações

src/test/java/com/example/banking/
└── RaceConditionTest.java        # Testes demonstrativos
```

## 🔗 Referências

- [JPA Locking](https://www.baeldung.com/jpa-optimistic-locking)
- [Distributed Transactions](https://microservices.io/patterns/data/saga.html)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
