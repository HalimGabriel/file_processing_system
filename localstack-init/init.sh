#!/bin/bash
awslocal s3 mb s3://notas-bucket
awslocal sqs create-queue --queue-name notas-dlq
awslocal sqs create-queue --queue-name notas-queue
awslocal dynamodb create-table \
  --table-name notas \
  --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S \
  --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST
  
  QUEUE_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/notas-queue \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal s3api put-bucket-notification-configuration \
  --bucket notas-bucket \
  --notification-configuration "{\"QueueConfigurations\":[{\"QueueArn\":\"$QUEUE_ARN\",\"Events\":[\"s3:ObjectCreated:*\"]}]}"