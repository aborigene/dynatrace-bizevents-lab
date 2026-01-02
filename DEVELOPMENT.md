# Development Guide

This guide provides information for developers who want to modify or extend the BizEvents Lab application.

## Development Setup

### Prerequisites

Install the following tools:

- **Docker Desktop** 20.10+
- **kubectl** (Kubernetes CLI)
- **Python** 3.11+
- **Node.js** 18+
- **.NET SDK** 8.0
- **Java JDK** 17+
- **Maven** 3.9+
- **Visual Studio Code** (recommended)

### Recommended VS Code Extensions

- Python (Microsoft)
- JavaScript and TypeScript (built-in)
- C# (Microsoft)
- Extension Pack for Java (Microsoft)
- Docker (Microsoft)
- Kubernetes (Microsoft)

## Project Architecture

### Service Communication Pattern

```
HTTP REST → Kafka Topics → HTTP REST
```

1. **Synchronous**: Load Generator → Loan Checker → Loan Router
2. **Asynchronous**: Loan Router → Kafka → Processors
3. **Synchronous**: Processors → Loan Approver → Loan Notifier

### Technology Choices

- **Python**: Load generation (flexibility, ease of configuration)
- **Node.js**: Quick validation service (lightweight, fast startup)
- **C#**: Routing with Kafka (excellent Kafka client, .NET performance)
- **Java**: Processors and Approver (enterprise standard, Spring Boot)
- **Python**: Notifier (simple logging service)

## Development Workflow

### 1. Making Changes to a Service

Example: Modifying the Loan Checker

```bash
# Navigate to service directory
cd loan-checker

# Install dependencies
npm install

# Start the service locally (requires other services running)
npm start

# Make your changes to server.js

# Test locally
curl -X POST http://localhost:3000/check \
  -H "Content-Type: application/json" \
  -d '{"request_id":"TEST-001","loan_type":"personal","loan_requested_value":5000,"customer_id":"CUST-001","partner_name":"TestBank"}'

# Rebuild Docker image
docker build -t loan-checker:latest .

# Test with docker-compose
cd ..
docker-compose up loan-checker
```

### 2. Adding New Loan Types

To add a new loan type (e.g., "education"):

#### Step 1: Update Load Generator

Edit `load-generator/load_generator.py`:

```python
LOAN_TYPES = ['personal', 'real_state', 'vehicle', 'education']

LOAN_VALUE_RANGES = {
    'personal': (100, 10000),
    'real_state': (300000, 3000000),
    'vehicle': (20000, 200000),
    'education': (5000, 50000)  # Add new range
}
```

#### Step 2: Update Loan Router

Edit `loan-router/Program.cs`:

```csharp
string topic = request.LoanType switch
{
    "personal" => "loans-personal",
    "real_state" => "loans-real-state",
    "vehicle" => "loans-vehicle",
    "education" => "loans-education",  // Add new topic
    _ => "loans-unknown"
};
```

#### Step 3: Create New Processor

Copy and modify `k8s/processors.yaml` to add an education processor:

```yaml
---
# Education Processor
apiVersion: apps/v1
kind: Deployment
metadata:
  name: education-processor
  namespace: bizevents-lab
spec:
  # ... similar to other processors
  env:
  - name: KAFKA_TOPIC
    value: "loans-education"
  - name: PROCESSOR_TYPE
    value: "education"
```

#### Step 4: Update Risk Calculation

Edit `processors/src/main/java/.../service/RiskCalculationService.java`:

```java
case "education":
    minValue = 5000;
    maxValue = 50000;
    lowRiskThreshold = 30000;
    highRiskThreshold = maxValue;
    break;
```

### 3. Modifying Risk Calculation Logic

The risk calculation is in `RiskCalculationService.java`:

```java
// Current logic
if (valuePosition <= 0.30) {
    initialScore = 70;
} else if (valuePosition <= 0.60) {
    initialScore = 50;
} else {
    initialScore = 20;
}

// Example: Add more granular scoring
if (valuePosition <= 0.20) {
    initialScore = 80;
} else if (valuePosition <= 0.40) {
    initialScore = 70;
} else if (valuePosition <= 0.60) {
    initialScore = 50;
} else if (valuePosition <= 0.80) {
    initialScore = 30;
} else {
    initialScore = 20;
}
```

### 4. Adding New BizEvent Attributes

#### In Load Generator (Direct API)

Edit `load-generator/load_generator.py`:

```python
bizevent = {
    'event.type': 'newLoanRequest',
    'event.provider': 'load-generator',
    'request_id': request_data['request_id'],
    # ... existing attributes ...
    'new_attribute': 'value',  # Add new attribute
}
```

#### In Loan Checker (Auto-captured)

Edit `loan-checker/server.js`:

```javascript
const bizEventAttributes = {
    'event.type': 'loan_check',
    // ... existing attributes ...
    'new_attribute': 'value'  // Add new attribute
};

logger.info({
    message: 'BIZEVENT',
    bizevent: bizEventAttributes
});
```

#### In Loan Notifier (From logs)

Edit `loan-notifier/app.py`:

```python
logger.info(
    f"LOAN_NOTIFICATION: request_id={request_id}, "
    # ... existing attributes ...
    f"new_attribute={new_value}"  # Add new attribute
)
```

Then update the Dynatrace log processing rule to extract the new attribute.

## Testing

### Unit Testing

Each service should have unit tests. Example for adding tests to the processor:

```java
// processors/src/test/java/.../service/RiskCalculationServiceTest.java

@SpringBootTest
class RiskCalculationServiceTest {
    
    @Autowired
    private RiskCalculationService riskCalculationService;
    
    @Test
    void testPersonalLoanLowValueHighScore() {
        LoanRequest request = new LoanRequest();
        request.setLoanType("personal");
        request.setLoanRequestedValue(1000);  // Low value
        request.setItemExists(true);
        request.setRiskLevel("low_risk");
        
        double score = riskCalculationService.calculateRiskScore(request, "personal");
        
        assertTrue(score >= 70, "Low value loans should have high risk score");
    }
}
```

### Integration Testing

Use the provided docker-compose setup for integration testing:

```bash
# Start all services
docker-compose up -d

# Wait for services to be ready
sleep 30

# Send test request
curl -X POST http://localhost:3000/check \
  -H "Content-Type: application/json" \
  -d @test-data/valid-request.json

# Check logs
docker-compose logs loan-notifier | tail -20

# Cleanup
docker-compose down
```

### Load Testing

For load testing, increase the transaction rate:

```bash
# Scale load generator
docker-compose up -d --scale load-generator=3

# Or update TPS
docker-compose exec load-generator bash -c \
  "export TRANSACTIONS_PER_SECOND=10 && python load_generator.py"
```

## Debugging

### Debugging Node.js Service (Loan Checker)

```bash
cd loan-checker

# Run with debug flag
node --inspect server.js

# In VS Code, attach debugger (port 9229)
```

### Debugging .NET Service (Loan Router)

```bash
cd loan-router

# Run with debugger
dotnet run --launch-profile Development

# VS Code will attach automatically
```

### Debugging Java Services

```bash
cd processors  # or loan-approver

# Run with remote debugging enabled
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# Attach VS Code debugger to port 5005
```

### Debugging in Kubernetes

```bash
# Port-forward to local machine
kubectl port-forward deployment/loan-checker 3000:3000 -n bizevents-lab

# Now you can access http://localhost:3000 directly

# View real-time logs
kubectl logs -f deployment/loan-checker -n bizevents-lab

# Get shell access
kubectl exec -it deployment/loan-checker -n bizevents-lab -- /bin/sh
```

## Performance Optimization

### Scaling Services

```yaml
# In K8s manifests, adjust replicas
spec:
  replicas: 3  # Scale to 3 instances
```

Or dynamically:

```bash
kubectl scale deployment/personal-processor --replicas=3 -n bizevents-lab
```

### Kafka Tuning

For higher throughput, adjust Kafka configuration:

```yaml
env:
- name: KAFKA_NUM_PARTITIONS
  value: "6"
- name: KAFKA_DEFAULT_REPLICATION_FACTOR
  value: "1"
```

### Java Service Memory

Adjust JVM heap size:

```dockerfile
# In Dockerfile
ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-jar", "app.jar"]
```

## Code Style Guidelines

### Python

Follow PEP 8:
- Use 4 spaces for indentation
- Max line length: 88 characters (Black formatter)
- Use type hints where applicable

```python
def calculate_score(value: float, max_value: float) -> float:
    """Calculate risk score based on value."""
    return (value / max_value) * 100
```

### JavaScript/Node.js

Follow StandardJS:
- Use 2 spaces for indentation
- Use semicolons
- Use single quotes for strings

```javascript
const calculateTotal = (items) => {
  return items.reduce((sum, item) => sum + item.value, 0);
};
```

### C#

Follow Microsoft C# conventions:
- PascalCase for classes and methods
- camelCase for local variables
- Use `var` when type is obvious

```csharp
public class LoanProcessor
{
    public async Task<LoanResult> ProcessLoan(LoanRequest request)
    {
        var result = await _service.Process(request);
        return result;
    }
}
```

### Java

Follow Google Java Style Guide:
- Use 2 spaces for indentation
- Braces on same line
- Use `@Override` annotation

```java
@Service
public class RiskCalculator {
    
    @Override
    public double calculateRisk(LoanRequest request) {
        double score = baseScore * multiplier;
        return Math.min(100, score);
    }
}
```

## Git Workflow

### Branch Naming

- `feature/add-education-loans` - New features
- `bugfix/fix-risk-calculation` - Bug fixes
- `refactor/improve-logging` - Code refactoring
- `docs/update-readme` - Documentation updates

### Commit Messages

Follow conventional commits:

```
feat: add education loan type support
fix: correct risk calculation for vehicle loans
docs: update deployment instructions
refactor: simplify loan validation logic
test: add unit tests for risk calculator
```

## Troubleshooting Common Issues

### Maven Build Fails

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
mvn clean install
```

### Docker Build Fails

```bash
# Clear Docker cache
docker system prune -a

# Rebuild without cache
docker build --no-cache -t service-name:latest .
```

### Services Can't Connect

Check service names match between docker-compose and code:

```yaml
# docker-compose.yaml
services:
  loan-checker:  # This is the hostname

# In other services
LOAN_CHECKER_URL=http://loan-checker:3000
```

## Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [ASP.NET Core Documentation](https://docs.microsoft.com/en-us/aspnet/core/)
- [Node.js Best Practices](https://github.com/goldbergyoni/nodebestpractices)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)

---

Happy coding! 🚀
