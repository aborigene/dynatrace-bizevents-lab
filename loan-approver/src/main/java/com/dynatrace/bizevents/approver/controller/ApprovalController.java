package com.dynatrace.bizevents.approver.controller;

import com.dynatrace.bizevents.approver.model.ApprovalResult;
import com.dynatrace.bizevents.approver.model.ProcessedLoanRequest;
import com.dynatrace.bizevents.approver.service.ApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class ApprovalController {
    
    private static final Logger logger = LoggerFactory.getLogger(ApprovalController.class);
    
    @Autowired
    private ApprovalService approvalService;
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"healthy\",\"service\":\"loan-approver\"}");
    }
    
    @PostMapping("/approve")
    public ResponseEntity<ApprovalResult> approveLoan(@RequestBody ProcessedLoanRequest request) {
        logger.info("Received loan approval request {} from {} processor",
                request.getRequestId(), request.getProcessorType());
        
        ApprovalResult result = approvalService.processApproval(request);
        
        logger.info("Loan {} - Status: {}, Final Risk: {}, Credit Score: {}",
                result.getRequestId(),
                result.getApprovalStatus(),
                result.getFinalLoanRisk(),
                result.getCreditScore());
        
        return ResponseEntity.ok(result);
    }
}
