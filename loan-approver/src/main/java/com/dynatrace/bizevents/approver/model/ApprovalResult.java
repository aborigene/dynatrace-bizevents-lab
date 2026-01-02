package com.dynatrace.bizevents.approver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResult {
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("loan_type")
    private String loanType;
    
    @JsonProperty("loan_requested_value")
    private double loanRequestedValue;
    
    @JsonProperty("final_loan_value")
    private double finalLoanValue;
    
    @JsonProperty("customer_id")
    private String customerId;
    
    @JsonProperty("partner_name")
    private String partnerName;
    
    @JsonProperty("credit_score")
    private int creditScore;
    
    @JsonProperty("calculated_risk_score")
    private double calculatedRiskScore;
    
    @JsonProperty("final_loan_risk")
    private double finalLoanRisk;
    
    @JsonProperty("approval_status")
    private String approvalStatus;
    
    @JsonProperty("interest_adjustment")
    private String interestAdjustment;
    
    @JsonProperty("processor_type")
    private String processorType;
    
    @JsonProperty("timestamp")
    private String timestamp;
}
