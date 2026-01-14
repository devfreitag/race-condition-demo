package com.example.banking.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    @Id
    private String id;
    
    private BigDecimal balance;
    
    @Version  // Optimistic locking - JPA gerencia automaticamente
    private Long version;
    
    public Account(String id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }
}
