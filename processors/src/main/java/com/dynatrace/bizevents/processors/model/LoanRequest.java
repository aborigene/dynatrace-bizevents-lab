package com.dynatrace.bizevents.processors.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LoanRequest {
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("loan_type")
    private String loanType;
    
    @JsonProperty("loan_requested_value")
    private double loanRequestedValue;
    
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
}
