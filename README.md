# Dynatrace BizEvents Lab - Loan Processing System

A comprehensive microservices-based loan processing system designed to demonstrate Dynatrace Business Events capabilities. This lab simulates a complete loan fulfillment workflow from request generation through approval notification.

## 📋 Table of Contents

- [Architecture](#architecture)
- [Services Overview](#services-overview)
- [Data Flow](#data-flow)
- [Business Events Collection](#business-events-collection)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Deployment Options](#deployment-options)
- [Monitoring with Dynatrace](#monitoring-with-dynatrace)
- [Troubleshooting](#troubleshooting)

## 🏗 Architecture

```
┌─────────────────┐
│ Load Generator  │ (Python)
│  - BizEvent #1  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Loan Checker   │ (NodeJS)
│  - BizEvent #2  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Loan Router    │ (C#)
│  - BizEvent #3  │
└────────┬────────┘
         │
         ▼
    ┌────────┐
    │ Kafka  │
    └───┬────┘
        │
    ┌───┴────────────┬──────────────┐
    ▼                ▼              ▼
┌─────────┐  ┌──────────┐  ┌──────────┐
│Personal │  │Real State│  │ Vehicle  │ (Java)
│Processor│  │Processor │  │Processor │
│BizEvent │  │BizEvent  │  │BizEvent  │
└────┬────┘  └────┬─────┘  └────┬─────┘
     │            │             │
     └────────────┼─────────────┘
                  ▼
         ┌────────────────┐
         │ Loan Approver  │ (Java)
         └────────┬───────┘
                  ▼
         ┌────────────────┐
         │ Loan Notifier  │ (Python)
         │  - BizEvent #5 │
         └────────────────┘
```

## 🔧 Services Overview

### 1. Load Generator (Python)
**Purpose:** Simulates loan requests with configurable parameters

**Features:**
- Generates loan requests with realistic data
- Configurable transaction rate (TPS)
- Configurable invalid request percentage
- Configurable invalid item percentage
- Multiple partner support
- Sends BizEvent `newLoanRequest` to Dynatrace

**Configuration:**
- `TRANSACTIONS_PER_SECOND`: Request rate (default: 1)
- `INVALID_REQUESTS_PCT`: % of requests with missing fields (default: 10)
- `INVALID_ITEMS_PCT`: % of requests with invalid items (default: 15)
- `PARTNER_NAMES`: Comma-separated list of partner names

### 2. Loan Checker (NodeJS)
**Purpose:** Validates loan request fields

**Features:**
- Validates required fields (loan_type, loan_requested_value, customer_id, partner_name)
- Generates BizEvent `loan_check` with status (success/failed)
- Forwards valid requests to Loan Router
- BizEvents automatically collected by Dynatrace OneAgent

### 3. Loan Router (C#)
**Purpose:** Routes loans to appropriate Kafka topics based on type

**Features:**
- Checks loan items against database
- Flags requests as low_risk or high_risk
- Routes to Kafka topics:
  - `loans-personal`
  - `loans-real-state`
  - `loans-vehicle`
- Logs structured data for Dynatrace BizEvent transformation

### 4. Processors (Java - 3 instances)
**Purpose:** Process specific loan types and calculate risk scores

**Processor Types:**
- **Personal Processor**: Handles personal loans ($100-$10,000)
- **Real State Processor**: Handles real estate loans ($300K-$3M)
- **Vehicle Processor**: Handles vehicle loans ($20K-$200K)

**Logic:**
- Retrieves credit scores from preloaded database
- Calculates risk scores based on:
  - Loan value position in valid range (30%, 60%, 100%)
  - Item existence in database (±20%, -35%)
  - Risk level flags (-15% for high risk)
- Validates loan value ranges
- Calculates final loan value (requested × 1.40)
- BizEvents automatically collected by OneAgent

**Risk Score Calculation:**
```
Initial Score:
- 0-30% of range: 70 points
- 31-60% of range: 50 points
- 61-100% of range: 20 points

Adjustments (real_state/vehicle only):
- Item exists: +20%
- Item doesn't exist: -35%

Additional:
- High risk flag: -15%
```

### 5. Loan Approver (Java)
**Purpose:** Makes final loan approval decisions

**Features:**
- Calculates final loan risk: `calculated_risk_score × (1 + credit_score/200)`
- Approval logic:
  - 0-50: **Denied**
  - 51-70: **High Risk** (25% more expensive)
  - 71-100: **Approved** (standard rate)
- BizEvents automatically collected by OneAgent

### 6. Loan Notifier (Python)
**Purpose:** Logs final loan notifications

**Features:**
- Receives approval results
- Logs structured notifications
- BizEvents collected from logs by Dynatrace

## 📊 Data Flow

1. **Load Generator** creates loan request → sends `newLoanRequest` BizEvent
2. **Loan Checker** validates fields → generates `loan_check` BizEvent
3. **Loan Router** checks items DB → routes to Kafka → logs for BizEvent
4. **Kafka** distributes to appropriate processor topic
5. **Processor** calculates risk + retrieves credit score → sends to Approver
6. **Loan Approver** makes final decision → sends to Notifier
7. **Loan Notifier** logs final result → BizEvent from logs

## 📈 Business Events Collection

| Service | BizEvent Type | Collection Method | Key Attributes |
|---------|---------------|-------------------|----------------|
| Load Generator | `newLoanRequest` | Direct API Ingestion | request_id, loan_type, customer_id, partner_name |
| Loan Checker | `loan_check` | OneAgent Auto-capture | request_id, status, missing_fields |
| Loan Router | `kafka_route` | Log Transformation | request_id, topic, risk_level, item_exists |
| Processors | `loan_processed` | OneAgent Auto-capture | request_id, risk_score, credit_score |
| Loan Notifier | `loan_notification` | Log Transformation | request_id, approval_status, final_risk |

## 🚀 Prerequisites

### For Docker Compose (Local Testing)
- Docker 20.10+
- Docker Compose 2.0+
- 8GB RAM minimum

### For Kubernetes
- Kubernetes 1.24+
- kubectl configured
- 4GB RAM minimum per node

### For Development
- Python 3.11+
- Node.js 18+
- .NET 8.0 SDK
- Java 17+
- Maven 3.9+

## ⚡ Quick Start

### Option 1: Docker Compose (Recommended for Testing)

```bash
# 1. Build all images
./build-all.sh

# 2. Start all services
docker-compose up -d

# 3. View logs
docker-compose logs -f

# 4. Stop services
docker-compose down
```

### Option 2: Kubernetes

```bash
# 1. Build all images
./build-all.sh

# 2. Deploy to Kubernetes
./deploy.sh

# 3. Check deployment status
kubectl get pods -n bizevents-lab

# 4. View logs from specific service
kubectl logs -f deployment/loan-checker -n bizevents-lab

# 5. Cleanup
./cleanup.sh
```

## ⚙️ Configuration

### Load Generator Configuration

Edit [k8s/load-generator.yaml](k8s/load-generator.yaml) or set environment variables:

```yaml
env:
  - name: TRANSACTIONS_PER_SECOND
    value: "2"              # Requests per second
  - name: INVALID_REQUESTS_PCT
    value: "10"             # % with missing fields
  - name: INVALID_ITEMS_PCT
    value: "15"             # % with invalid items
  - name: PARTNER_NAMES
    value: "BankCorp,LoanMasters,QuickCredit,PrimeLending"
```

### Dynatrace BizEvents Direct Ingestion (Optional)

For the Load Generator to send BizEvents directly to Dynatrace:

```yaml
env:
  - name: DT_INGEST_URL
    value: "https://your-tenant.live.dynatrace.com"
  - name: DT_API_TOKEN
    valueFrom:
      secretKeyRef:
        name: dynatrace-token
        key: api-token
```

Create the secret:
```bash
kubectl create secret generic dynatrace-token \
  --from-literal=api-token=YOUR_API_TOKEN \
  -n bizevents-lab
```

### Data Files

Preloaded data is in the [data/](data/) directory:

- **loan_items.json**: Valid loan items (cars, houses)
- **credit_scores.json**: Customer credit scores

## 🎯 Deployment Options

### Local Development

Each service can be run independently for development:

```bash
# Loan Checker (NodeJS)
cd loan-checker
npm install
npm start

# Loan Router (C#)
cd loan-router
dotnet run

# Processors (Java)
cd processors
mvn spring-boot:run -DPROCESSOR_TYPE=personal

# Loan Approver (Java)
cd loan-approver
mvn spring-boot:run

# Loan Notifier (Python)
cd loan-notifier
pip install -r requirements.txt
python app.py

# Load Generator (Python)
cd load-generator
pip install -r requirements.txt
python load_generator.py
```

### Production Considerations

1. **Resource Limits**: Adjust CPU/memory limits in K8s manifests
2. **Replicas**: Scale processors for higher throughput
3. **Kafka**: Use external Kafka cluster for production
4. **Persistence**: Add persistent volumes for Kafka/Zookeeper
5. **Monitoring**: Configure Dynatrace OneAgent on all nodes

## 📊 Monitoring with Dynatrace

### Setup Steps

1. **Install OneAgent** on Kubernetes cluster
2. **Configure Log Monitoring**:
   - Enable log ingestion for the namespace
   - Create processing rules for structured logs
3. **Create BizEvent Extraction Rules**:
   - For Loan Router logs: Extract `KAFKA_ROUTE` entries
   - For Loan Notifier logs: Extract `LOAN_NOTIFICATION` entries
4. **Create Dashboards**:
   - Loan request volume by type
   - Approval rates by partner
   - Risk score distributions
   - Processing times per service

### Key Metrics to Track

- Total loan requests
- Approval/denial rates
- Average risk scores by loan type
- Invalid request percentages
- Processing latency per service
- Kafka lag per topic

### Sample DQL Queries

```sql
// All loan requests
fetch bizevents
| filter event.type == "newLoanRequest"
| summarize count(), by: {loan_type, partner_name}

// Approval rates
fetch bizevents
| filter event.type == "loan_notification"
| summarize approvals = countIf(approval_status == "approved"),
            denials = countIf(approval_status == "denied"),
            by: {loan_type}

// Average risk scores
fetch bizevents
| filter event.type == "loan_processed"
| summarize avg(calculated_risk_score), by: {processor_type}
```

## 🔍 Troubleshooting

### Services Not Starting

```bash
# Check pod status
kubectl get pods -n bizevents-lab

# Check pod logs
kubectl logs <pod-name> -n bizevents-lab

# Describe pod for events
kubectl describe pod <pod-name> -n bizevents-lab
```

### Kafka Connection Issues

```bash
# Check Kafka is running
kubectl logs deployment/kafka -n bizevents-lab

# Verify Kafka topics
kubectl exec -it deployment/kafka -n bizevents-lab -- \
  kafka-topics --bootstrap-server localhost:9092 --list
```

### No BizEvents in Dynatrace

1. Verify OneAgent is installed and running
2. Check log monitoring is enabled for the namespace
3. Verify BizEvent extraction rules are configured
4. Check service logs for structured output
5. Validate API token permissions (for direct ingestion)

### Load Generator Not Sending Requests

```bash
# Check load generator logs
kubectl logs deployment/load-generator -n bizevents-lab

# Verify loan-checker is reachable
kubectl exec -it deployment/load-generator -n bizevents-lab -- \
  curl http://loan-checker:3000/health
```

## 📁 Project Structure

```
BizEvents Lab/
├── load-generator/          # Python load generator
│   ├── load_generator.py
│   ├── requirements.txt
│   └── Dockerfile
├── loan-checker/            # NodeJS validation service
│   ├── server.js
│   ├── package.json
│   └── Dockerfile
├── loan-router/             # C# routing service
│   ├── Program.cs
│   ├── LoanRouter.csproj
│   └── Dockerfile
├── processors/              # Java processor services
│   ├── src/main/java/...
│   ├── pom.xml
│   └── Dockerfile
├── loan-approver/           # Java approval service
│   ├── src/main/java/...
│   ├── pom.xml
│   └── Dockerfile
├── loan-notifier/           # Python notification service
│   ├── app.py
│   ├── requirements.txt
│   └── Dockerfile
├── k8s/                     # Kubernetes manifests
│   ├── namespace.yaml
│   ├── kafka.yaml
│   ├── loan-checker.yaml
│   ├── loan-router.yaml
│   ├── processors.yaml
│   ├── loan-approver.yaml
│   ├── loan-notifier.yaml
│   └── load-generator.yaml
├── data/                    # Preloaded data files
│   ├── loan_items.json
│   └── credit_scores.json
├── build-all.sh            # Build all Docker images
├── deploy.sh               # Deploy to Kubernetes
├── cleanup.sh              # Remove from Kubernetes
├── docker-compose.yaml     # Local testing with Docker Compose
└── README.md               # This file
```

## 🤝 Contributing

This is a lab/demo application. Feel free to:
- Add new loan types
- Implement additional processors
- Enhance risk calculation logic
- Add new BizEvent collection points
- Improve Dynatrace dashboards

## 📝 License

This project is for educational and demonstration purposes.

## 🎓 Learning Resources

- [Dynatrace Business Events](https://www.dynatrace.com/support/help/platform/grail/dynatrace-grail-business-events)
- [DQL Query Language](https://www.dynatrace.com/support/help/platform/grail/dynatrace-query-language)
- [OneAgent Deployment](https://www.dynatrace.com/support/help/setup-and-configuration/dynatrace-oneagent)

---

**Happy Learning with Dynatrace BizEvents! 🚀**
