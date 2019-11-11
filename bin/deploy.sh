#!/usr/bin/env bash

function clean_up {
    printf "\nCleaning up...\n"
    rm -rf "${1}"
}

function error_exit {
    printf "\n${1:-\"Unknown Error\"}\n" 1>&2
    exit 1
}

printf "=====================================\n"
printf "|        Demo  Deployment            |\n"
printf "**This is a demo deployment example**\n"
printf "=====================================\n"

DIR="$(pwd)"
lambda_code_path="${DIR}/src"
template="lambda_demo_stack.yaml"
cfn_template_path=${DIR}/templates/cloudformation/${template}

virtual_env="${DIR}/.pkg"
rm -rf "${lambda_code_path}/.pkg"
mkdir -p "${lambda_code_path}/.pkg"

default_aws_profile="dev"
default_aws_profile=${1:=$default_aws_profile}
default_tag_environment="dev"
default_package_code_bucket="ruan-jenkins-demo-packagebucket-dev"

if [ ! -f ${cfn_template_path} ]; then
    error_exit "${cfn_template_path} couldn't be found"
fi

printf "\nPreparing Deployment Package"

cp -r ${lambda_code_path}/* ${virtual_env}/
pip install -q -r ${DIR}/requirements/base -t ${virtual_env}
find ${virtual_env} | grep -E "(__pycache__|\.pyc|\.pyo|\.dist-info|\.egg-info$)" | xargs rm -rf
cp ${cfn_template_path} ${virtual_env}/${template}

region="$(aws --profile ${default_aws_profile} configure get region)"
account_id="$(aws --profile ${default_aws_profile} sts get-caller-identity --output text --query 'Account')"

if [ "$account_id" == "x" ]; then
    # Prod account
    default_package_code_bucket="ruan-jenkins-demo-packagebucket-prod"
    default_tag_environment="production"
elif [ "$account_id" == "y" ]; then
    # Dev account
    default_package_code_bucket="ruan-jenkins-demo-packagebucket-dev"
    default_tag_environment="development"

fi

echo "\n=============================="
echo "Bucket for lambda codes: ${default_package_code_bucket}"
echo "Environment: ${default_tag_environment}"
echo "==============================\n"

aws --profile "${default_aws_profile}" \
    cloudformation package \
    --template-file "${virtual_env}/${template}" \
    --output-template-file "${virtual_env}/out_${template}" \
    --s3-bucket "${default_package_code_bucket}"

aws --profile "${default_aws_profile}" \
    cloudformation deploy \
    --template-file "${virtual_env}/out_${template}" \
    --stack-name "lambda-with-jenkins-cfn-stack" \
    --parameter-overrides \
        TagEnvironment="${default_tag_environment}" \
    --capabilities CAPABILITY_IAM \
    --tags \
        Name="lambda-with-jenkins-cfn-stack" \
        Owner=engineering \
        Environment="${default_tag_environment}" \
        Status=active

# Clean up
clean_up "${virtual_env}"
printf "\nAll done!\n"

exit 0
