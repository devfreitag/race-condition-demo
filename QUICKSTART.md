# Quick Start

## Executar os testes

```bash
cd race-condition-demo
mvn clean test
```

## O que vai acontecer

Você vai ver 3 testes sendo executados:

### 1. UNSAFE - mostra o problema
```
=== TESTE: UNSAFE (COM RACE CONDITION) ===
⚠️  RACE CONDITION DETECTADO! Saldo ficou negativo!
```

### 2. OPTIMISTIC - resolve com retry
```
=== TESTE: OPTIMISTIC LOCKING (COM RETRY) ===
✅ Consistência garantida!
```

### 3. PESSIMISTIC - resolve com lock
```
=== TESTE: PESSIMISTIC LOCKING ===
✅ Consistência garantida com pessimistic lock!
```

## Arquivos importantes

- `TransferService.java` - as 3 implementações comentadas
- `RaceConditionTest.java` - testes com output detalhado
- `README.md` - explicação completa

## Dica pro estudo

1. Rode os testes
2. Leia o output no console
3. Veja o código em TransferService.java
4. Leia os comentários explicando cada linha

Tudo que você precisa saber está nos comentários do código!
