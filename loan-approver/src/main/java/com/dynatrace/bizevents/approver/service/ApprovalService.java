package com.dynatrace.bizevents.approver.service;

import com.dynatrace.bizevents.approver.model.ApprovalResult;
import com.dynatrace.bizevents.approver.model.ProcessedLoanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ApprovalService {
    
    private static final Logger logger = LoggerFactory.getLogger(ApprovalService.class);
    
    @Value("${loan.notifier.url}")
    private String loanNotifierUrl;
    
    private final WebClient webClient;
    
    public ApprovalService() {
        this.webClient = WebClient.builder().build();
    }
    
    public ApprovalResult processApproval(ProcessedLoanRequest request) {
        // Calculate final loan risk
        // Formula: calculated_loan_score * (1 + credit_score/200)
        double calculatedRiskScore = request.getCalculatedRiskScore();
        int creditScore = request.getCreditScore();
        
        double finalLoanRisk = calculatedRiskScore * (1 + creditScore / 200.0);
        
        // Cap the final risk at 100
        finalLoanRisk = Math.min(100, finalLoanRisk);
        
        logger.info("Calculated final loan risk for {}: {} (risk_score: {}, credit_score: {})",
                request.getRequestId(), finalLoanRisk, calculatedRiskScore, creditScore);
        
        // Determine approval status
        String approvalStatus;
        String interestAdjustment;
        
        if (finalLoanRisk <= 50) {
            approvalStatus = "denied";
            interestAdjustment = "N/A";
        } else if (finalLoanRisk <= 70) {
            approvalStatus = "high_risk";
            interestAdjustment = "25% more expensive";
        } else {
            approvalStatus = "approved";
            interestAdjustment = "standard rate";
        }
        
        logger.info("Loan {} approval decision: {} (final_risk: {})",
                request.getRequestId(), approvalStatus, finalLoanRisk);
        
        // Build approval result
        ApprovalResult result = ApprovalResult.builder()
                .requestId(request.getRequestId())
                .loanType(request.getLoanType())
                .loanRequestedValue(request.getLoanRequestedValue())
                .finalLoanValue(request.getFinalLoanValue())
                .customerId(request.getCustomerId())
                .partnerName(request.getPartnerName())
                .creditScore(creditScore)
                .calculatedRiskScore(calculatedRiskScore)
                .finalLoanRisk(finalLoanRisk)
                .approvalStatus(approvalStatus)
                .interestAdjustment(interestAdjustment)
                .processorType(request.getProcessorType())
                .timestamp(request.getTimestamp())
                .build();
        
        // Send to notifier
        sendToNotifier(result);
        
        return result;
    }
    
    private void sendToNotifier(ApprovalResult result) {
        logger.info("Sending approval result {} to notifier", result.getRequestId());
        
        webClient.post()
                .uri(loanNotifierUrl + "/notify")
                .bodyValue(result)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> {
                    logger.info("Successfully sent result {} to notifier", result.getRequestId());
                })
                .doOnError(error -> {
                    logger.error("Error sending result {} to notifier: {}",
                            result.getRequestId(), error.getMessage());
                })
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
