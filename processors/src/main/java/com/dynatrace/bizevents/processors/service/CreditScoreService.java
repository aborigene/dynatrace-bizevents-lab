package com.dynatrace.bizevents.processors.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CreditScoreService {
    
    private final Map<String, Integer> creditScores;
    
    public CreditScoreService() {
        creditScores = new HashMap<>();
        // Preload credit scores
        creditScores.put("CUST-001", 750);
        creditScores.put("CUST-002", 680);
        creditScores.put("CUST-003", 720);
        creditScores.put("CUST-004", 650);
        creditScores.put("CUST-005", 800);
        creditScores.put("CUST-006", 590);
        creditScores.put("CUST-007", 710);
        creditScores.put("CUST-008", 670);
        creditScores.put("CUST-009", 780);
        creditScores.put("CUST-010", 620);
    }
    
    public int getCreditScore(String customerId) {
        return creditScores.getOrDefault(customerId, -70);
    }
}
