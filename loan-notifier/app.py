from flask import Flask, request, jsonify
import logging
import json
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'healthy', 'service': 'loan-notifier'})

@app.route('/notify', methods=['POST'])
def notify():
    """Receive and log loan approval notification"""
    try:
        approval_result = request.json
        request_id = approval_result.get('request_id', 'unknown')
        
        logger.info(f"Received loan notification for request {request_id}")
        
        # Extract key information
        customer_id = approval_result.get('customer_id', 'unknown')
        loan_type = approval_result.get('loan_type', 'unknown')
        requested_value = approval_result.get('loan_requested_value', 0)
        final_value = approval_result.get('final_loan_value', 0)
        approval_status = approval_result.get('approval_status', 'unknown')
        final_risk = approval_result.get('final_loan_risk', 0)
        interest_adjustment = approval_result.get('interest_adjustment', 'unknown')
        credit_score = approval_result.get('credit_score', 0)
        
        # Create notification message
        if approval_status == 'approved':
            message = (
                f"LOAN APPROVED - Customer {customer_id}: Your {loan_type} loan of "
                f"${requested_value:.2f} has been APPROVED! Final loan amount: ${final_value:.2f}. "
                f"Interest rate: {interest_adjustment}. Risk score: {final_risk:.2f}"
            )
        elif approval_status == 'high_risk':
            message = (
                f"LOAN APPROVED WITH CONDITIONS - Customer {customer_id}: Your {loan_type} loan of "
                f"${requested_value:.2f} has been approved as HIGH RISK. Final loan amount: ${final_value:.2f}. "
                f"Interest rate: {interest_adjustment}. Risk score: {final_risk:.2f}"
            )
        else:
            message = (
                f"LOAN DENIED - Customer {customer_id}: Unfortunately, your {loan_type} loan request of "
                f"${requested_value:.2f} has been DENIED. Risk score: {final_risk:.2f}. "
                f"Credit score: {credit_score}"
            )
        
        # Log structured message for Dynatrace bizevent collection
        logger.info(
            f"LOAN_NOTIFICATION: request_id={request_id}, customer_id={customer_id}, "
            f"loan_type={loan_type}, approval_status={approval_status}, "
            f"final_loan_risk={final_risk:.2f}, credit_score={credit_score}, "
            f"requested_value={requested_value:.2f}, final_value={final_value:.2f}, "
            f"interest_adjustment={interest_adjustment}"
        )
        
        # Log user-friendly notification message
        logger.info(message)
        
        return jsonify({
            'status': 'success',
            'request_id': request_id,
            'message': 'Notification logged successfully'
        }), 200
        
    except Exception as e:
        logger.error(f"Error processing notification: {str(e)}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

if __name__ == '__main__':
    port = 5001
    logger.info(f"Starting Loan Notifier Service on port {port}")
    app.run(host='0.0.0.0', port=port, debug=False)
