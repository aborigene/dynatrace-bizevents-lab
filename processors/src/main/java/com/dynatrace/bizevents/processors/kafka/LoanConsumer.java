package com.dynatrace.bizevents.processors.kafka;

import com.dynatrace.bizevents.processors.model.LoanRequest;
import com.dynatrace.bizevents.processors.model.ProcessedLoanRequest;
import com.dynatrace.bizevents.processors.service.CreditScoreService;
import com.dynatrace.bizevents.processors.service.RiskCalculationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class LoanConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(LoanConsumer.class);
    
    @Autowired
    private CreditScoreService creditScoreService;
    
    @Autowired
    private RiskCalculationService riskCalculationService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${processor.type}")
    private String processorType;
    
    @Value("${loan.approver.url}")
    private String loanApproverUrl;
    
    private final WebClient webClient;
    
    public LoanConsumer() {
        this.webClient = WebClient.builder().build();
    }
    
    @KafkaListener(topics = "${kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeLoanRequest(String message) {
        logger.info("Received loan request in {} processor", processorType);
        
        try {
            LoanRequest request = objectMapper.readValue(message, LoanRequest.class);
            
            logger.info("Processing loan request {} of type {} for customer {}",
                    request.getRequestId(), request.getLoanType(), request.getCustomerId());
            
            // Get credit score
            int creditScore = creditScoreService.getCreditScore(request.getCustomerId());
            logger.info("Credit score for customer {}: {}", request.getCustomerId(), creditScore);
            
            // Calculate risk score
            double riskScore = riskCalculationService.calculateRiskScore(request, processorType);
            logger.info("Calculated risk score: {}", riskScore);
            
            // Calculate final loan value (40% higher than requested)
            double finalLoanValue = request.getLoanRequestedValue() * 1.40;
            
            // Build processed request
            ProcessedLoanRequest processedRequest = ProcessedLoanRequest.builder()
                    .requestId(request.getRequestId())
                    .loanType(request.getLoanType())
                    .loanRequestedValue(request.getLoanRequestedValue())
                    .finalLoanValue(finalLoanValue)
                    .loanItem(request.getLoanItem())
                    .loanItemName(request.getLoanItemName())
                    .customerId(request.getCustomerId())
                    .partnerName(request.getPartnerName())
                    .timestamp(request.getTimestamp())
                    .riskLevel(request.getRiskLevel())
                    .itemExists(request.getItemExists())
                    .creditScore(creditScore)
                    .calculatedRiskScore(riskScore)
                    .processorType(processorType)
                    .build();
            
            // Send to loan approver
            sendToApprover(processedRequest);
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing loan request message", e);
        }
    }
    
    private void sendToApprover(ProcessedLoanRequest request) {
        logger.info("Sending processed request {} to loan approver", request.getRequestId());
        
        webClient.post()
                .uri(loanApproverUrl + "/approve")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> {
                    logger.info("Successfully sent request {} to approver: {}",
                            request.getRequestId(), response);
                })
                .doOnError(error -> {
                    logger.error("Error sending request {} to approver: {}",
                            request.getRequestId(), error.getMessage());
                })
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
