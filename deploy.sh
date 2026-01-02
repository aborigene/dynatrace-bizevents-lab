#!/bin/bash

# Deploy all Kubernetes resources
echo "Deploying BizEvents Lab to Kubernetes..."

# Create namespace
echo "Creating namespace..."
kubectl apply -f k8s/namespace.yaml

# Deploy Kafka and Zookeeper
echo "Deploying Kafka and Zookeeper..."
kubectl apply -f k8s/kafka.yaml

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
kubectl wait --for=condition=ready pod -l app=kafka -n bizevents-lab --timeout=300s

# Deploy services
echo "Deploying loan-checker..."
kubectl apply -f k8s/loan-checker.yaml

echo "Deploying loan-router..."
kubectl apply -f k8s/loan-router.yaml

echo "Deploying processors..."
kubectl apply -f k8s/processors.yaml

echo "Deploying loan-approver..."
kubectl apply -f k8s/loan-approver.yaml

echo "Deploying loan-notifier..."
kubectl apply -f k8s/loan-notifier.yaml

echo "Deploying load-generator..."
kubectl apply -f k8s/load-generator.yaml

echo ""
echo "Deployment complete!"
echo ""
echo "Checking pod status..."
kubectl get pods -n bizevents-lab

echo ""
echo "To view logs from a specific service, use:"
echo "  kubectl logs -f deployment/<service-name> -n bizevents-lab"
echo ""
echo "Available services:"
echo "  - load-generator"
echo "  - loan-checker"
echo "  - loan-router"
echo "  - personal-processor"
echo "  - real-state-processor"
echo "  - vehicle-processor"
echo "  - loan-approver"
echo "  - loan-notifier"
