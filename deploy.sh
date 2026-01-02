#!/bin/bash

# Deploy all Kubernetes resources
# Usage: ./deploy.sh [repository] [version]
# Example: ./deploy.sh 123456789012.dkr.ecr.us-east-1.amazonaws.com/bizevents v1.0.0

REPO=${1:-""}
VERSION=${2:-"latest"}

if [ -n "$REPO" ]; then
    echo "Deploying BizEvents Lab to Kubernetes with custom images..."
    echo "Repository: $REPO"
    echo "Version: $VERSION"
    echo ""
    
    # Create temporary directory for modified manifests
    TMP_DIR=$(mktemp -d)
    trap "rm -rf $TMP_DIR" EXIT
    
    # Copy all k8s files and update image references
    cp -r k8s/* $TMP_DIR/
    
    # Update image references in all YAML files
    find $TMP_DIR -name "*.yaml" -type f -exec sed -i "s|image: load-generator:latest|image: $REPO/load-generator:$VERSION|g" {} \;
    find $TMP_DIR -name "*.yaml" -type f -exec sed -i "s|image: loan-checker:latest|image: $REPO/loan-checker:$VERSION|g" {} \;
    find $TMP_DIR -name "*.yaml" -type f -exec sed -i "s|image: loan-router:latest|image: $REPO/loan-router:$VERSION|g" {} \;
    find $TMP_DIR -name "*.yaml" -type f -exec sed -i "s|image: loan-processor:latest|image: $REPO/loan-processor:$VERSION|g" {} \;
    find $TMP_DIR -name "*.yaml" -type f -exec sed -i "s|image: loan-approver:latest|image: $REPO/loan-approver:$VERSION|g" {} \;
    find $TMP_DIR -name "*.yaml" -type f -exec sed -i "s|image: loan-notifier:latest|image: $REPO/loan-notifier:$VERSION|g" {} \;
    
    # Update imagePullPolicy to Always when using remote registry
    find $TMP_DIR -name "*.yaml" -type f -exec sed -i "s|imagePullPolicy: IfNotPresent|imagePullPolicy: Always|g" {} \;
    
    MANIFEST_DIR=$TMP_DIR
else
    echo "Deploying BizEvents Lab to Kubernetes with local images..."
    MANIFEST_DIR="k8s"
fi

echo ""

# Create namespace
echo "Creating namespace..."
kubectl apply -f $MANIFEST_DIR/namespace.yaml

# Deploy Kafka and Zookeeper
echo "Deploying Kafka and Zookeeper..."
kubectl apply -f $MANIFEST_DIR/kafka.yaml

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
kubectl wait --for=condition=ready pod -l app=kafka -n bizevents-lab --timeout=300s

# Deploy services
echo "Deploying loan-checker..."
kubectl apply -f $MANIFEST_DIR/loan-checker.yaml

echo "Deploying loan-router..."
kubectl apply -f $MANIFEST_DIR/loan-router.yaml

echo "Deploying processors..."
kubectl apply -f $MANIFEST_DIR/processors.yaml

echo "Deploying loan-approver..."
kubectl apply -f $MANIFEST_DIR/loan-approver.yaml

echo "Deploying loan-notifier..."
kubectl apply -f $MANIFEST_DIR/loan-notifier.yaml

echo "Deploying load-generator..."
kubectl apply -f $MANIFEST_DIR/load-generator.yaml

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
