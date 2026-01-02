#!/bin/bash

# Build script for all Docker images
echo "Building all Docker images for BizEvents Lab..."

# Build load generator
echo "Building load-generator..."
docker build -t load-generator:latest ./load-generator

# Build loan checker
echo "Building loan-checker..."
docker build -t loan-checker:latest ./loan-checker

# Build loan router
echo "Building loan-router..."
docker build -t loan-router:latest ./loan-router

# Build processors
echo "Building loan-processor..."
docker build -t loan-processor:latest ./processors

# Build loan approver
echo "Building loan-approver..."
docker build -t loan-approver:latest ./loan-approver

# Build loan notifier
echo "Building loan-notifier..."
docker build -t loan-notifier:latest ./loan-notifier

echo "All images built successfully!"
echo ""
echo "Images created:"
docker images | grep -E "load-generator|loan-checker|loan-router|loan-processor|loan-approver|loan-notifier"
