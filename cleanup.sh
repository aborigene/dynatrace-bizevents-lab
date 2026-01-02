#!/bin/bash

# Cleanup script - removes all Kubernetes resources
echo "Cleaning up BizEvents Lab from Kubernetes..."

kubectl delete -f k8s/load-generator.yaml --ignore-not-found=true
kubectl delete -f k8s/loan-notifier.yaml --ignore-not-found=true
kubectl delete -f k8s/loan-approver.yaml --ignore-not-found=true
kubectl delete -f k8s/processors.yaml --ignore-not-found=true
kubectl delete -f k8s/loan-router.yaml --ignore-not-found=true
kubectl delete -f k8s/loan-checker.yaml --ignore-not-found=true
kubectl delete -f k8s/kafka.yaml --ignore-not-found=true

echo "Waiting for pods to terminate..."
sleep 10

# Optionally delete namespace (commented out by default)
# echo "Deleting namespace..."
# kubectl delete -f k8s/namespace.yaml

echo ""
echo "Cleanup complete!"
echo ""
echo "To also delete the namespace, run:"
echo "  kubectl delete namespace bizevents-lab"
