package com.dynatrace.bizevents.approver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ProcessedLoanRequest {
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("loan_type")
    private String loanType;
    
    @JsonProperty("loan_requested_value")
    private double loanRequestedValue;
    
    @JsonProperty("final_loan_value")
    private double finalLoanValue;
    
    @JsonProperty("loan_item")
    private String loanItem;
    
    @JsonProperty("loan_item_name")
    private String loanItemName;
    
    @JsonProperty("customer_id")
    private String customerId;
    
    @JsonProperty("partner_name")
    private String partnerName;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("risk_level")
    private String riskLevel;
    
    @JsonProperty("item_exists")
    private Boolean itemExists;
    
    @JsonProperty("credit_score")
    private int creditScore;
    
    @JsonProperty("calculated_risk_score")
    private double calculatedRiskScore;
    
    @JsonProperty("processor_type")
    private String processorType;
}
