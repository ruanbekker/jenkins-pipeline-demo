#!/usr/bin/env bash

export AWS_SHARED_CREDENTIALS_FILE=/tmp/.aws
mkdir -p /tmp
touch ${AWS_SHARED_CREDENTIALS_FILE}

cat > ${AWS_SHARED_CREDENTIALS_FILE} << EOF
[master]
aws_access_key_id=${AWS_ACCESS_KEY}
aws_secret_access_key=${AWS_SECRET_KEY}
region=${AWS_REGION}

[dev]
role_arn=${AWS_CROSS_ACCOUNT_ROLE_ARN}
source_profile=master
region=${AWS_REGION}
EOF

python3 -m ensurepip
pip3 install awscli boto3
