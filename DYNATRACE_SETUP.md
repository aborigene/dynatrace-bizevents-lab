# Dynatrace Configuration Guide

This guide explains how to configure Dynatrace to collect and process Business Events from the BizEvents Lab application.

## Table of Contents

1. [OneAgent Installation](#oneagent-installation)
2. [Log Monitoring Configuration](#log-monitoring-configuration)
3. [BizEvent Extraction Rules](#bizevent-extraction-rules)
4. [Dashboards and Visualizations](#dashboards-and-visualizations)
5. [Alerting Configuration](#alerting-configuration)

## OneAgent Installation

### Kubernetes Deployment

1. Go to **Deploy Dynatrace** → **Start Installation** → **Kubernetes**
2. Create a namespace for Dynatrace:
   ```bash
   kubectl create namespace dynatrace
   ```
3. Download and apply the Dynatrace Operator:
   ```bash
   kubectl apply -f https://github.com/Dynatrace/dynatrace-operator/releases/latest/download/kubernetes.yaml
   ```
4. Create a secret with your API and PaaS tokens:
   ```bash
   kubectl -n dynatrace create secret generic dynakube \
     --from-literal="apiToken=YOUR_API_TOKEN" \
     --from-literal="dataIngestToken=YOUR_DATA_INGEST_TOKEN"
   ```
5. Apply the DynaKube custom resource:
   ```yaml
   apiVersion: dynatrace.com/v1beta1
   kind: DynaKube
   metadata:
     name: dynakube
     namespace: dynatrace
   spec:
     apiUrl: https://YOUR_TENANT.live.dynatrace.com/api
     oneAgent:
       classicFullStack:
         tolerations:
           - effect: NoSchedule
             key: node-role.kubernetes.io/master
             operator: Exists
     activeGate:
       capabilities:
         - routing
         - kubernetes-monitoring
   ```

## Log Monitoring Configuration

### Enable Log Monitoring for BizEvents Namespace

1. Navigate to **Settings** → **Log Monitoring** → **Log sources**
2. Add Kubernetes namespace filter:
   - **Namespace**: `bizevents-lab`
   - **Enable**: Log monitoring
3. Configure log ingestion:
   - **Settings** → **Log Monitoring** → **Log storage**
   - Ensure sufficient retention period (30 days recommended)

### Create Log Processing Rules

#### Rule 1: Loan Router Kafka Events

1. Go to **Settings** → **Log Monitoring** → **Log processing**
2. Click **Add processing rule**
3. Configure:
   - **Rule name**: Extract Loan Router Kafka Events
   - **Matcher**: `content contains "KAFKA_ROUTE:"`
   - **Processor**: Parse using pattern
   - **Pattern**:
     ```
     KAFKA_ROUTE: request_id=%{DATA:request_id}, loan_type=%{DATA:loan_type}, topic=%{DATA:topic}, risk_level=%{DATA:risk_level}, item_exists=%{DATA:item_exists}, loan_value=%{NUMBER:loan_value}, customer_id=%{DATA:customer_id}
     ```
4. Add Business Event action:
   - **Event type**: `kafka_route`
   - **Attributes**:
     - `request_id`: `{request_id}`
     - `loan_type`: `{loan_type}`
     - `topic`: `{topic}`
     - `risk_level`: `{risk_level}`
     - `item_exists`: `{item_exists}`
     - `loan_value`: `{loan_value}`
     - `customer_id`: `{customer_id}`

#### Rule 2: Loan Notifier Events

1. Add another processing rule
2. Configure:
   - **Rule name**: Extract Loan Notification Events
   - **Matcher**: `content contains "LOAN_NOTIFICATION:"`
   - **Pattern**:
     ```
     LOAN_NOTIFICATION: request_id=%{DATA:request_id}, customer_id=%{DATA:customer_id}, loan_type=%{DATA:loan_type}, approval_status=%{DATA:approval_status}, final_loan_risk=%{NUMBER:final_loan_risk}, credit_score=%{NUMBER:credit_score}, requested_value=%{NUMBER:requested_value}, final_value=%{NUMBER:final_value}, interest_adjustment=%{DATA:interest_adjustment}
     ```
3. Add Business Event action:
   - **Event type**: `loan_notification`
   - **Attributes**: Map all extracted fields

## BizEvent Extraction Rules

### Loan Checker BizEvents (Auto-captured by OneAgent)

The loan checker service logs structured JSON with a `bizevent` field. OneAgent automatically captures these.

To ensure proper capture:
1. **Settings** → **Monitoring** → **Monitored technologies**
2. Verify **Node.js** monitoring is enabled
3. Check **Deep monitoring** is configured for the loan-checker process

### Processor BizEvents (Auto-captured by OneAgent)

Java processors send data via HTTP to the loan-approver. OneAgent automatically captures these transactions.

Configuration:
1. **Settings** → **Server-side service monitoring** → **Deep monitoring**
2. Ensure **Java** is enabled
3. Enable **Capture request/response bodies** for POST requests
4. Add custom service detection rule:
   - **Technology**: Java
   - **Process group detection**: Match process group name contains "processor"
   - **Service naming**: Use `processor_type` environment variable

## Dashboards and Visualizations

### Create Main BizEvents Dashboard

1. Go to **Dashboards** → **Create dashboard**
2. Name it "Loan Processing - BizEvents Lab"

#### Tile 1: Total Loan Requests

```sql
fetch bizevents
| filter event.type == "newLoanRequest"
| summarize count()
```
- **Visualization**: Single value
- **Title**: Total Loan Requests

#### Tile 2: Requests by Loan Type

```sql
fetch bizevents
| filter event.type == "newLoanRequest"
| summarize count(), by: {loan_type}
```
- **Visualization**: Pie chart
- **Title**: Loan Requests by Type

#### Tile 3: Approval Rate Over Time

```sql
fetch bizevents
| filter event.type == "loan_notification"
| makeTimeseries count(), by: {approval_status}
```
- **Visualization**: Line chart
- **Title**: Loan Approvals Over Time

#### Tile 4: Average Risk Scores

```sql
fetch bizevents
| filter event.type == "loan_notification"
| summarize avg(final_loan_risk), by: {loan_type}
```
- **Visualization**: Bar chart
- **Title**: Average Risk Score by Loan Type

#### Tile 5: Failed Validations

```sql
fetch bizevents
| filter event.type == "loan_check" and status == "failed"
| summarize count(), by: {missing_fields}
```
- **Visualization**: Table
- **Title**: Validation Failures

#### Tile 6: Partner Performance

```sql
fetch bizevents
| filter event.type == "loan_notification"
| summarize 
    total = count(),
    approved = countIf(approval_status == "approved"),
    denied = countIf(approval_status == "denied"),
    approval_rate = approved / total * 100,
    by: {partner_name}
| sort approval_rate desc
```
- **Visualization**: Table
- **Title**: Partner Approval Rates

#### Tile 7: Processing Latency

```sql
fetch bizevents
| filter event.type in ("newLoanRequest", "loan_notification")
| fields timestamp, request_id, event.type
| sort timestamp asc
| summarize 
    start = takeFirst(timestamp, by:{request_id}),
    end = takeLast(timestamp, by:{request_id})
| fieldsAdd duration = (end - start) / 1000000000
| summarize avg(duration), p95(duration), p99(duration)
```
- **Visualization**: Single value
- **Title**: Average Processing Time (seconds)

### Risk Analysis Dashboard

Create a second dashboard focused on risk analysis:

#### High Risk Loans

```sql
fetch bizevents
| filter event.type == "loan_notification" 
    and approval_status == "high_risk"
| fields timestamp, request_id, customer_id, loan_type, 
         final_loan_risk, requested_value
| sort timestamp desc
| limit 100
```

#### Credit Score Distribution

```sql
fetch bizevents
| filter event.type == "loan_notification"
| summarize count(), by: {bin(credit_score, 50)}
```

## Alerting Configuration

### Alert 1: High Denial Rate

1. **Settings** → **Anomaly detection** → **Custom events for alerting**
2. Click **Create custom event for alerting**
3. Configure:
   - **Category**: Custom
   - **Severity**: Warning
   - **Name**: High Loan Denial Rate
   - **Query**:
     ```sql
     fetch bizevents
     | filter event.type == "loan_notification"
     | summarize 
         total = count(),
         denied = countIf(approval_status == "denied"),
         denial_rate = denied / total * 100
     | filter denial_rate > 30
     ```
   - **Threshold**: Execute if any result
   - **Alert description**: "Loan denial rate is above 30%"

### Alert 2: Service Validation Errors Spike

```sql
fetch bizevents
| filter event.type == "loan_check" and status == "failed"
| summarize error_count = count()
| filter error_count > 100
```

### Alert 3: Processing Time Anomaly

Use built-in anomaly detection:
1. Create a metric from the processing time query
2. Enable automatic anomaly detection
3. Configure alerting profile

## Business Event Attribute Reference

### Event Type: newLoanRequest

| Attribute | Type | Description |
|-----------|------|-------------|
| request_id | string | Unique request identifier |
| loan_type | string | Type: personal, real_state, vehicle |
| loan_requested_value | number | Requested loan amount |
| customer_id | string | Customer identifier |
| partner_name | string | Partner who initiated request |
| loan_item | string | Item ID (optional) |
| timestamp | datetime | Request timestamp |

### Event Type: loan_check

| Attribute | Type | Description |
|-----------|------|-------------|
| request_id | string | Request identifier |
| status | string | success or failed |
| missing_fields | string | Comma-separated missing fields (if failed) |
| loan_type | string | Loan type |
| customer_id | string | Customer identifier |

### Event Type: kafka_route

| Attribute | Type | Description |
|-----------|------|-------------|
| request_id | string | Request identifier |
| loan_type | string | Loan type |
| topic | string | Kafka topic name |
| risk_level | string | low_risk or high_risk |
| item_exists | boolean | Whether item exists in DB |
| loan_value | number | Requested loan amount |
| customer_id | string | Customer identifier |

### Event Type: loan_notification

| Attribute | Type | Description |
|-----------|------|-------------|
| request_id | string | Request identifier |
| customer_id | string | Customer identifier |
| loan_type | string | Loan type |
| approval_status | string | approved, high_risk, or denied |
| final_loan_risk | number | Final calculated risk score |
| credit_score | number | Customer credit score |
| requested_value | number | Requested amount |
| final_value | number | Approved amount (requested × 1.4) |
| interest_adjustment | string | Interest rate adjustment |

## Best Practices

1. **Retention**: Set appropriate log and BizEvent retention based on your analysis needs
2. **Sampling**: For high-volume environments, consider sampling rules
3. **Indexing**: Create indexes on frequently queried attributes (customer_id, partner_name)
4. **Costs**: Monitor Grail unit consumption and adjust retention/sampling accordingly
5. **Documentation**: Document custom attributes for your team

## Troubleshooting

### BizEvents Not Appearing

1. Check OneAgent status on all pods
2. Verify log monitoring is enabled for the namespace
3. Check processing rules are active
4. Review logs for parsing errors in Settings → Log Monitoring → Processing rules
5. Verify service is actually logging the expected format

### Missing Attributes

1. Check the parsing pattern in log processing rules
2. Verify the log format matches the expected pattern
3. Test the pattern with sample logs
4. Check for special characters in logs that need escaping

### Query Performance Issues

1. Add time filters to queries (`| filter timestamp > now() - 1h`)
2. Limit result sets (`| limit 1000`)
3. Create metrics for frequently used aggregations
4. Use appropriate visualization types for data size

---

For more information, see the [Dynatrace Documentation](https://www.dynatrace.com/support/help/).
