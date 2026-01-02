# Quick Reference Card

## 📋 Service URLs (Docker Compose)

| Service | Port | Health Check |
|---------|------|--------------|
| Loan Checker | 3000 | http://localhost:3000/health |
| Loan Router | 5000 | http://localhost:5000/health |
| Loan Approver | 8090 | http://localhost:8090/health |
| Loan Notifier | 5001 | http://localhost:5001/health |
| Kafka | 9092 | - |
| Zookeeper | 2181 | - |

## 🚀 Common Commands

### Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs (all services)
docker-compose logs -f

# View logs (specific service)
docker-compose logs -f loan-notifier

# Restart a service
docker-compose restart loan-checker

# Stop all services
docker-compose down

# Rebuild and restart
docker-compose up -d --build
```

### Kubernetes

```bash
# Deploy everything
./deploy.sh

# Check pod status
kubectl get pods -n bizevents-lab

# View logs
kubectl logs -f deployment/loan-checker -n bizevents-lab

# Scale a service
kubectl scale deployment/personal-processor --replicas=3 -n bizevents-lab

# Restart a deployment
kubectl rollout restart deployment/loan-approver -n bizevents-lab

# Port forward to local
kubectl port-forward deployment/loan-checker 3000:3000 -n bizevents-lab

# Delete everything
./cleanup.sh
```

### Building

```bash
# Build all images
./build-all.sh

# Build specific service
docker build -t loan-checker:latest ./loan-checker

# Build without cache
docker build --no-cache -t loan-router:latest ./loan-router
```

## 🔧 Configuration Quick Reference

### Load Generator

```bash
# Environment variables
LOAN_CHECKER_URL=http://loan-checker:3000/check
TRANSACTIONS_PER_SECOND=2
INVALID_REQUESTS_PCT=10
INVALID_ITEMS_PCT=15
PARTNER_NAMES=BankCorp,LoanMasters,QuickCredit,PrimeLending
```

### Test Request

```bash
# Send test loan request
curl -X POST http://localhost:3000/check \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "TEST-001",
    "loan_type": "personal",
    "loan_requested_value": 5000,
    "customer_id": "CUST-001",
    "partner_name": "TestBank"
  }'
```

## 📊 Kafka Topics

| Topic | Purpose |
|-------|---------|
| loans-personal | Personal loan requests |
| loans-real-state | Real estate loan requests |
| loans-vehicle | Vehicle loan requests |

### Check Kafka Topics

```bash
# Docker Compose
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Kubernetes
kubectl exec -it deployment/kafka -n bizevents-lab -- \
  kafka-topics --bootstrap-server localhost:9092 --list

# View messages in topic
kubectl exec -it deployment/kafka -n bizevents-lab -- \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic loans-personal --from-beginning --max-messages 10
```

## 🎯 Loan Types & Value Ranges

| Type | Min Value | Max Value | Valid Items |
|------|-----------|-----------|-------------|
| Personal | $100 | $10,000 | None |
| Real Estate | $300,000 | $3,000,000 | HOUSE-001 to HOUSE-005 |
| Vehicle | $20,000 | $200,000 | CAR-001 to CAR-005 |

## 📈 Dynatrace DQL Queries

### Total Loan Requests
```sql
fetch bizevents
| filter event.type == "newLoanRequest"
| summarize count()
```

### Approval Rate by Type
```sql
fetch bizevents
| filter event.type == "loan_notification"
| summarize 
    total = count(),
    approved = countIf(approval_status == "approved"),
    by: {loan_type}
| fieldsAdd approval_rate = approved / total * 100
```

### Average Risk Scores
```sql
fetch bizevents
| filter event.type == "loan_notification"
| summarize avg(final_loan_risk), by: {loan_type}
```

### Failed Validations
```sql
fetch bizevents
| filter event.type == "loan_check" and status == "failed"
| summarize count(), by: {missing_fields}
```

## 🔍 Debugging Tips

### Check Service Health

```bash
# All services at once (K8s)
kubectl get pods -n bizevents-lab | grep -v Running

# Individual health checks (Docker Compose)
curl http://localhost:3000/health  # loan-checker
curl http://localhost:5000/health  # loan-router
curl http://localhost:8090/health  # loan-approver
curl http://localhost:5001/health  # loan-notifier
```

### View Real-Time Logs

```bash
# Docker Compose - All services
docker-compose logs -f --tail=100

# Kubernetes - Specific service
kubectl logs -f deployment/loan-notifier -n bizevents-lab --tail=100

# Kubernetes - Multiple services
kubectl logs -f -l app=personal-processor -n bizevents-lab
```

### Restart Services

```bash
# Docker Compose
docker-compose restart <service-name>

# Kubernetes
kubectl rollout restart deployment/<service-name> -n bizevents-lab
```

## 📦 File Locations

| Component | Location |
|-----------|----------|
| Dockerfiles | `<service>/Dockerfile` |
| K8s Manifests | `k8s/*.yaml` |
| Data Files | `data/*.json` |
| Scripts | `*.sh` (root directory) |
| Documentation | `*.md` (root directory) |

## 🛠️ Common Issues & Solutions

### Issue: Kafka not ready
```bash
# Wait for Kafka
kubectl wait --for=condition=ready pod -l app=kafka -n bizevents-lab --timeout=300s
```

### Issue: Services can't connect
```bash
# Check service DNS
kubectl exec -it deployment/loan-checker -n bizevents-lab -- nslookup loan-router
```

### Issue: Out of memory
```bash
# Increase memory limits in k8s/*.yaml
resources:
  limits:
    memory: "1Gi"  # Increase this
```

### Issue: Image pull errors
```bash
# Set imagePullPolicy
imagePullPolicy: IfNotPresent  # or Never for local images
```

## 📞 Quick Links

- **Full Documentation**: [README.md](README.md)
- **Dynatrace Setup**: [DYNATRACE_SETUP.md](DYNATRACE_SETUP.md)
- **Development Guide**: [DEVELOPMENT.md](DEVELOPMENT.md)
- **Getting Started**: [GETTING_STARTED.md](GETTING_STARTED.md)

---

Keep this card handy for quick reference! 🚀
