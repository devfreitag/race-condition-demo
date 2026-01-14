package com.example.banking.service;

import com.example.banking.entity.Account;
import com.example.banking.repository.AccountRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    
    private final AccountRepository accountRepository;
    
    /**
     * VERSÃO 1 - SEM PROTEÇÃO (TEM RACE CONDITION)
     * 
     * Problema: entre verificar o saldo e salvar, outra thread pode alterar a conta.
     * Resultado: saldo pode ficar negativo em transferências simultâneas.
     */
    @Transactional
    public void transferUnsafe(String fromId, String toId, BigDecimal amount) {
        log.info("Transfer UNSAFE: {} -> {} amount {}", fromId, toId, amount);
        
        Account from = accountRepository.findById(fromId)
            .orElseThrow(() -> new RuntimeException("Account " + fromId + " not found"));
        
        Account to = accountRepository.findById(toId)
            .orElseThrow(() -> new RuntimeException("Account " + toId + " not found"));
        
        // RACE CONDITION AQUI!
        // Thread 1 lê saldo=100
        // Thread 2 lê saldo=100
        // Thread 1 debita 80 -> saldo=20
        // Thread 2 debita 80 -> saldo=-60 (PROBLEMA!)
        
        if (from.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }
        
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        
        accountRepository.save(from);
        accountRepository.save(to);
        
        log.info("Transfer completed: from={} to={}", from.getBalance(), to.getBalance());
    }
    
    /**
     * VERSÃO 2 - OPTIMISTIC LOCKING
     * 
     * Como funciona:
     * - JPA usa o campo @Version automaticamente
     * - Quando salva, verifica se a version mudou
     * - Se mudou, lança OptimisticLockException
     * 
     * Vantagem: melhor performance, sem trava
     * Desvantagem: precisa de retry quando dá conflito
     */
    @Transactional
    public void transferOptimistic(String fromId, String toId, BigDecimal amount) {
        log.info("Transfer OPTIMISTIC: {} -> {} amount {}", fromId, toId, amount);
        
        Account from = accountRepository.findById(fromId)
            .orElseThrow(() -> new RuntimeException("Account " + fromId + " not found"));
        
        Account to = accountRepository.findById(toId)
            .orElseThrow(() -> new RuntimeException("Account " + toId + " not found"));
        
        if (from.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }
        
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        
        // Se a version mudou desde a leitura, JPA lança OptimisticLockException
        accountRepository.save(from);
        accountRepository.save(to);
        
        log.info("Transfer completed: from={} to={}", from.getBalance(), to.getBalance());
    }
    
    /**
     * VERSÃO 2b - OPTIMISTIC LOCKING COM RETRY
     * 
     * Wrapper que tenta novamente quando dá OptimisticLockException
     */
    public void transferOptimisticWithRetry(String fromId, String toId, BigDecimal amount) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                transferOptimistic(fromId, toId, amount);
                return; // sucesso
            } catch (OptimisticLockException e) {
                attempt++;
                log.warn("Optimistic lock conflict, attempt {}/{}", attempt, maxRetries);
                
                if (attempt >= maxRetries) {
                    throw new RuntimeException("Failed after " + maxRetries + " attempts", e);
                }
                
                // pequeno delay antes de tentar de novo
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
    }
    
    /**
     * VERSÃO 3 - PESSIMISTIC LOCKING
     * 
     * Como funciona:
     * - Trava o registro no banco (SELECT ... FOR UPDATE)
     * - Outras threads ficam esperando até a transação terminar
     * - Garante que não há conflito
     * 
     * Vantagem: garante consistência, sem retry
     * Desvantagem: menos performático, pode causar deadlock
     */
    @Transactional
    public void transferPessimistic(String fromId, String toId, BigDecimal amount) {
        log.info("Transfer PESSIMISTIC: {} -> {} amount {}", fromId, toId, amount);
        
        // Trava as contas imediatamente (SELECT ... FOR UPDATE)
        Account from = accountRepository.findByIdWithPessimisticLock(fromId)
            .orElseThrow(() -> new RuntimeException("Account " + fromId + " not found"));
        
        Account to = accountRepository.findByIdWithPessimisticLock(toId)
            .orElseThrow(() -> new RuntimeException("Account " + toId + " not found"));
        
        if (from.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }
        
        // Enquanto essa transação não terminar, ninguém acessa essas contas
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        
        accountRepository.save(from);
        accountRepository.save(to);
        
        log.info("Transfer completed: from={} to={}", from.getBalance(), to.getBalance());
    }
}
