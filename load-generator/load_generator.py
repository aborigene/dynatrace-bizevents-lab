#!/usr/bin/env python3
import os
import sys
import json
import time
import random
import requests
from datetime import datetime
import logging
import oneagent

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize Dynatrace SDK
sdk = oneagent.get_sdk()

# Configuration from environment variables
LOAN_CHECKER_URL = os.getenv('LOAN_CHECKER_URL', 'http://loan-checker:3000/check')
TRANSACTIONS_PER_SECOND = float(os.getenv('TRANSACTIONS_PER_SECOND', '1'))
INVALID_REQUESTS_PCT = float(os.getenv('INVALID_REQUESTS_PCT', '10'))
INVALID_ITEMS_PCT = float(os.getenv('INVALID_ITEMS_PCT', '15'))
PARTNER_NAMES = os.getenv('PARTNER_NAMES', 'BankCorp,LoanMasters,QuickCredit,PrimeLending').split(',')

# Dynatrace ingestion (if configured)
DT_INGEST_URL = os.getenv('DT_INGEST_URL', '')
DT_API_TOKEN = os.getenv('DT_API_TOKEN', '')

# Load data
VALID_ITEMS = {
    'vehicle': ['CAR-001', 'CAR-002', 'CAR-003', 'CAR-004', 'CAR-005'],
    'real_state': ['HOUSE-001', 'HOUSE-002', 'HOUSE-003', 'HOUSE-004', 'HOUSE-005'],
    'personal': []  # Personal loans don't have items
}

VALID_ITEM_NAMES = {
    'CAR-001': 'Tesla Model 3',
    'CAR-002': 'Toyota Camry',
    'CAR-003': 'Ford F-150',
    'CAR-004': 'Honda Civic',
    'CAR-005': 'BMW X5',
    'HOUSE-001': 'Downtown Apartment',
    'HOUSE-002': 'Suburban House',
    'HOUSE-003': 'Beach Condo',
    'HOUSE-004': 'Mountain Cabin',
    'HOUSE-005': 'City Loft'
}

LOAN_TYPES = ['personal', 'real_state', 'vehicle']

# Value ranges by loan type
LOAN_VALUE_RANGES = {
    'personal': (100, 10000),
    'real_state': (300000, 3000000),
    'vehicle': (20000, 200000)
}

CUSTOMER_IDS = [f'CUST-{str(i).zfill(3)}' for i in range(1, 51)]

def send_bizevent(event_data):
    """Send business event to Dynatrace"""
    if not DT_INGEST_URL or not DT_API_TOKEN:
        return
    
    try:
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Api-Token {DT_API_TOKEN}'
        }
        response = requests.post(
            f'{DT_INGEST_URL}/api/v2/bizevents/ingest',
            headers=headers,
            json=event_data,
            timeout=5
        )
        if response.status_code not in [200, 201, 202]:
            logger.warning(f'Failed to send bizevent: {response.status_code}')
    except Exception as e:
        logger.error(f'Error sending bizevent: {e}')

def generate_loan_request():
    """Generate a loan request with configured probabilities"""
    loan_type = random.choice(LOAN_TYPES)
    customer_id = random.choice(CUSTOMER_IDS)
    partner_name = random.choice(PARTNER_NAMES)
    
    # Determine if request should be invalid
    is_invalid = random.random() < (INVALID_REQUESTS_PCT / 100)
    
    # Generate loan value
    min_val, max_val = LOAN_VALUE_RANGES[loan_type]
    loan_value = round(random.uniform(min_val, max_val), 2)
    
    request_data = {
        'request_id': f'REQ-{int(time.time() * 1000)}-{random.randint(1000, 9999)}',
        'timestamp': datetime.utcnow().isoformat() + 'Z',
        'customer_id': customer_id,
        'partner_name': partner_name
    }
    
    # Add required fields (or make them invalid)
    if is_invalid:
        # Randomly omit some required fields
        if random.random() > 0.5:
            request_data['loan_type'] = loan_type
        if random.random() > 0.5:
            request_data['loan_requested_value'] = loan_value
    else:
        request_data['loan_type'] = loan_type
        request_data['loan_requested_value'] = loan_value
    
    # Add loan item if applicable
    if loan_type in ['vehicle', 'real_state']:
        is_invalid_item = random.random() < (INVALID_ITEMS_PCT / 100)
        
        if is_invalid_item:
            # Generate fake item ID
            if loan_type == 'vehicle':
                request_data['loan_item'] = f'CAR-{random.randint(100, 999)}'
            else:
                request_data['loan_item'] = f'HOUSE-{random.randint(100, 999)}'
        else:
            # Use valid item from database
            valid_items = VALID_ITEMS[loan_type]
            item_id = random.choice(valid_items)
            request_data['loan_item'] = item_id
            request_data['loan_item_name'] = VALID_ITEM_NAMES[item_id]
    
    return request_data

def send_request_to_checker(request_data):
    """Send loan request to loan checker service"""
    try:
        response = requests.post(
            LOAN_CHECKER_URL,
            json=request_data,
            headers={'Content-Type': 'application/json'},
            timeout=10
        )
        
        if response.status_code == 200:
            logger.info(f'Request {request_data["request_id"]} sent successfully')
            return True
        else:
            logger.warning(f'Request {request_data["request_id"]} failed: {response.status_code}')
            return False
    except Exception as e:
        logger.error(f'Error sending request: {e}')
        return False

def main():
    logger.info('Starting Load Generator')
    logger.info(f'Target: {LOAN_CHECKER_URL}')
    logger.info(f'Rate: {TRANSACTIONS_PER_SECOND} req/s')
    logger.info(f'Invalid requests: {INVALID_REQUESTS_PCT}%')
    logger.info(f'Invalid items: {INVALID_ITEMS_PCT}%')
    logger.info(f'Partners: {PARTNER_NAMES}')
    
    sleep_time = 1.0 / TRANSACTIONS_PER_SECOND
    request_count = 0
    
    try:
        while True:
            start_time = time.time()
            
            # Generate and send request
            request_data = generate_loan_request()
            
            # Create custom service for this transaction
            with sdk.trace_custom_service('process_loan_request', 'LoadGenerator'):
                # Add custom tags for better visibility
                sdk.add_custom_request_attribute('request_id', request_data['request_id'])
                sdk.add_custom_request_attribute('loan_type', request_data.get('loan_type', 'unknown'))
                sdk.add_custom_request_attribute('customer_id', request_data.get('customer_id', 'unknown'))
                sdk.add_custom_request_attribute('partner_name', request_data.get('partner_name', 'unknown'))
                
                # Send bizevent to Dynatrace
                bizevent = {
                    'event.type': 'newLoanRequest',
                    'event.provider': 'load-generator',
                    'request_id': request_data['request_id'],
                    'loan_type': request_data.get('loan_type', 'unknown'),
                    'loan_requested_value': request_data.get('loan_requested_value', 0),
                    'customer_id': request_data.get('customer_id', 'unknown'),
                    'partner_name': request_data.get('partner_name', 'unknown'),
                    'timestamp': request_data['timestamp']
                }
                
                if 'loan_item' in request_data:
                    bizevent['loan_item'] = request_data['loan_item']
                
                send_bizevent(bizevent)
                
                # Send to loan checker
                send_request_to_checker(request_data)
            
            request_count += 1
            
            # Sleep to maintain rate
            elapsed = time.time() - start_time
            sleep_duration = max(0, sleep_time - elapsed)
            time.sleep(sleep_duration)
            
            if request_count % 100 == 0:
                logger.info(f'Sent {request_count} requests')
                
    except KeyboardInterrupt:
        logger.info(f'\nStopping. Total requests sent: {request_count}')
        sys.exit(0)

if __name__ == '__main__':
    main()
