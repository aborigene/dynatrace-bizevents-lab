const express = require('express');
const axios = require('axios');
const winston = require('winston');

// Configure logger
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  transports: [
    new winston.transports.Console()
  ]
});

const app = express();
app.use(express.json());

// Configuration
const PORT = process.env.PORT || 3000;
const LOAN_ROUTER_URL = process.env.LOAN_ROUTER_URL || 'http://loan-router:5000/route';

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'healthy', service: 'loan-checker' });
});

// Validate loan request
function validateRequest(request) {
  const requiredFields = ['loan_type', 'loan_requested_value', 'customer_id', 'partner_name'];
  const missingFields = [];
  
  for (const field of requiredFields) {
    if (!request[field]) {
      missingFields.push(field);
    }
  }
  
  return {
    isValid: missingFields.length === 0,
    missingFields
  };
}

// Main endpoint to check loan requests
app.post('/check', async (req, res) => {
  const request = req.body;
  const requestId = request.request_id || 'unknown';
  
  logger.info({
    message: 'Received loan request',
    request_id: requestId,
    loan_type: request.loan_type
  });
  
  // Validate request
  const validation = validateRequest(request);
  
  // Generate business event attributes
  const bizEventAttributes = {
    'event.type': 'loan_check',
    'event.provider': 'loan-checker',
    'request_id': requestId,
    'loan_type': request.loan_type || 'unknown',
    'customer_id': request.customer_id || 'unknown',
    'partner_name': request.partner_name || 'unknown',
    'status': validation.isValid ? 'success' : 'failed',
    'timestamp': new Date().toISOString()
  };
  
  if (!validation.isValid) {
    bizEventAttributes.missing_fields = validation.missingFields.join(',');
    bizEventAttributes.error_message = `Missing required fields: ${validation.missingFields.join(', ')}`;
  }
  
  // Log business event (will be collected by OneAgent automatically)
  logger.info({
    message: 'BIZEVENT',
    bizevent: bizEventAttributes
  });
  
  if (!validation.isValid) {
    logger.warn({
      message: 'Loan request validation failed',
      request_id: requestId,
      missing_fields: validation.missingFields
    });
    
    return res.status(400).json({
      status: 'failed',
      message: 'Invalid request',
      missing_fields: validation.missingFields
    });
  }
  
  // Forward to loan router
  try {
    const routerResponse = await axios.post(LOAN_ROUTER_URL, request, {
      headers: { 'Content-Type': 'application/json' },
      timeout: 10000
    });
    
    logger.info({
      message: 'Request forwarded to loan router',
      request_id: requestId,
      router_status: routerResponse.status
    });
    
    res.json({
      status: 'success',
      message: 'Request validated and forwarded',
      request_id: requestId
    });
    
  } catch (error) {
    logger.error({
      message: 'Error forwarding to loan router',
      request_id: requestId,
      error: error.message
    });
    
    res.status(500).json({
      status: 'error',
      message: 'Failed to forward request',
      error: error.message
    });
  }
});

// Start server
app.listen(PORT, () => {
  logger.info({
    message: 'Loan Checker Service started',
    port: PORT,
    loan_router_url: LOAN_ROUTER_URL
  });
});
