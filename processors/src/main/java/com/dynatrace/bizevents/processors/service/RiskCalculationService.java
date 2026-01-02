package com.dynatrace.bizevents.processors.service;

import com.dynatrace.bizevents.processors.model.LoanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskCalculationService {
    
    private static final Logger logger = LoggerFactory.getLogger(RiskCalculationService.class);
    
    public double calculateRiskScore(LoanRequest request, String processorType) {
        double loanValue = request.getLoanRequestedValue();
        String loanType = request.getLoanType();
        
        // Determine value ranges based on loan type
        double minValue, maxValue, lowRiskThreshold, highRiskThreshold;
        
        switch (loanType) {
            case "personal":
                minValue = 100;
                maxValue = 10000;
                lowRiskThreshold = 7000;
                highRiskThreshold = maxValue;
                break;
            case "real_state":
                minValue = 300000;
                maxValue = 3000000;
                lowRiskThreshold = 2000000;
                highRiskThreshold = maxValue;
                break;
            case "vehicle":
                minValue = 20000;
                maxValue = 200000;
                lowRiskThreshold = 150000;
                highRiskThreshold = maxValue;
                break;
            default:
                logger.warn("Unknown loan type: {}", loanType);
                return 0;
        }
        
        // Check if value is within valid range
        if (loanValue < minValue || loanValue > maxValue) {
            logger.warn("Loan value {} is outside valid range [{}, {}] for type {}",
                    loanValue, minValue, maxValue, loanType);
            return 0; // Rejected
        }
        
        // Calculate initial risk score based on value position
        double range = maxValue - minValue;
        double valuePosition = (loanValue - minValue) / range;
        
        double initialScore;
        if (valuePosition <= 0.30) {
            initialScore = 70;
        } else if (valuePosition <= 0.60) {
            initialScore = 50;
        } else {
            initialScore = 20;
        }
        
        logger.debug("Initial risk score for {} loan of ${}: {}", loanType, loanValue, initialScore);
        
        // Adjust score based on item existence (only for real_state and vehicle)
        if (loanType.equals("real_state") || loanType.equals("vehicle")) {
            Boolean itemExists = request.getItemExists();
            if (itemExists != null) {
                if (itemExists) {
                    initialScore *= 1.20; // Increase by 20%
                    logger.debug("Item exists - increased score by 20%");
                } else {
                    initialScore *= 0.65; // Decrease by 35%
                    logger.debug("Item doesn't exist - decreased score by 35%");
                }
            }
        }
        
        // Additional decrease for high risk flag
        if ("high_risk".equals(request.getRiskLevel())) {
            initialScore *= 0.85; // Decrease by 15%
            logger.debug("High risk flag - decreased score by 15%");
        }
        
        // Ensure score is within bounds
        double finalScore = Math.max(0, Math.min(100, initialScore));
        
        logger.info("Calculated risk score for request {}: {}", request.getRequestId(), finalScore);
        
        return finalScore;
    }
}
