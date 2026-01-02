#!/bin/bash

# Build script for all Docker images
# Usage: ./build-all.sh <repository> <version>
# Example: ./build-all.sh 123456789012.dkr.ecr.us-east-1.amazonaws.com/bizevents v1.0.0

if [ -z "$1" ] || [ -z "$2" ]; then
    echo "Usage: $0 <repository> <version>"
    echo "Example: $0 123456789012.dkr.ecr.us-east-1.amazonaws.com/bizevents v1.0.0"
    echo ""
    echo "Note: Make sure to login to your container registry before running this script"
    echo "For AWS ECR: aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com"
    exit 1
fi

REPO=$1
VERSION=$2

echo "Building all Docker images for BizEvents Lab..."
echo "Repository: $REPO"
echo "Version: $VERSION"
echo ""

# Build load generator
echo "Building load-generator..."
docker build -t load-generator:latest -t $REPO/load-generator:$VERSION -t $REPO/load-generator:latest ./load-generator
echo "Pushing load-generator..."
docker push $REPO/load-generator:$VERSION
docker push $REPO/load-generator:latest

# Build loan checker
echo "Building loan-checker..."
docker build -t loan-checker:latest -t $REPO/loan-checker:$VERSION -t $REPO/loan-checker:latest ./loan-checker
echo "Pushing loan-checker..."
docker push $REPO/loan-checker:$VERSION
docker push $REPO/loan-checker:latest

# Build loan router
echo "Building loan-router..."
docker build -t loan-router:latest -t $REPO/loan-router:$VERSION -t $REPO/loan-router:latest ./loan-router
echo "Pushing loan-router..."
docker push $REPO/loan-router:$VERSION
docker push $REPO/loan-router:latest

# Build processors
echo "Building loan-processor..."
docker build -t loan-processor:latest -t $REPO/loan-processor:$VERSION -t $REPO/loan-processor:latest ./processors
echo "Pushing loan-processor..."
docker push $REPO/loan-processor:$VERSION
docker push $REPO/loan-processor:latest

# Build loan approver
echo "Building loan-approver..."
docker build -t loan-approver:latest -t $REPO/loan-approver:$VERSION -t $REPO/loan-approver:latest ./loan-approver
echo "Pushing loan-approver..."
docker push $REPO/loan-approver:$VERSION
docker push $REPO/loan-approver:latest

# Build loan notifier
echo "Building loan-notifier..."
docker build -t loan-notifier:latest -t $REPO/loan-notifier:$VERSION -t $REPO/loan-notifier:latest ./loan-notifier
echo "Pushing loan-notifier..."
docker push $REPO/loan-notifier:$VERSION
docker push $REPO/loan-notifier:latest

echo ""
echo "All images built and pushed successfully!"
echo ""
echo "Images created:"
docker images | grep -E "load-generator|loan-checker|loan-router|loan-processor|loan-approver|loan-notifier"
