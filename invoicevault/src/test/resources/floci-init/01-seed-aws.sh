#!/bin/bash
set -euo pipefail

export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
ENDPOINT="${AWS_ENDPOINT_URL:-http://localhost:4566}"

if command -v awslocal >/dev/null 2>&1; then
  QUEUE_URL="$(awslocal sqs create-queue \
    --queue-name invoices-received \
    --query QueueUrl \
    --output text)"

  awslocal ssm put-parameter \
    --name /invoicevault/customers/cust-001/max-size-mb \
    --value 10 \
    --type String \
    --overwrite

  awslocal ssm put-parameter \
    --name /invoicevault/sqs-url \
    --value "${QUEUE_URL}" \
    --type String \
    --overwrite
else
  QUEUE_URL="$(aws --endpoint-url="${ENDPOINT}" sqs create-queue \
    --queue-name invoices-received \
    --query QueueUrl \
    --output text)"

  aws --endpoint-url="${ENDPOINT}" ssm put-parameter \
    --name /invoicevault/customers/cust-001/max-size-mb \
    --value 10 \
    --type String \
    --overwrite

  aws --endpoint-url="${ENDPOINT}" ssm put-parameter \
    --name /invoicevault/sqs-url \
    --value "${QUEUE_URL}" \
    --type String \
    --overwrite
fi
