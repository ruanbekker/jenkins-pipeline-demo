void setBuildStatus(String message, String state,String git_repo,String context) {
  step([
      $class: "GitHubCommitStatusSetter",
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: git_repo],
      contextSource: [$class: "ManuallyEnteredCommitContextSource", context: context],
      errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
      statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
  ]);
}

pipeline
{
    agent {
        label 'jenkins-slave-01'
    }
    environment {
        project_name = "demo"
        project_owner = "ruan"
        AWS_REGION = "eu-west-1"
        AWS_DEV_ACCOUNT_NUMBER = credentials('AWS_DEV_ACCOUNT_NUMBER')
        GIT_TOKEN = credentials('GITHUB_TOKEN')
    }
    stages{

        stage('Checkout'){
            when {
                expression { env.GITHUB_BRANCH_NAME != null }
            }
            steps {
                git url: "git@github.com:ruanbekker/jenkins-pipeline-demo.git",
                branch: "${env.GITHUB_BRANCH_NAME}"
            }
        }
        stage('Checkout PR'){
            when {
                expression { env.GITHUB_PR_URL != null }
            }
            steps{
                git url: "${env.GITHUB_REPO_SSH_URL}",
                branch: "${env.GITHUB_PR_SOURCE_BRANCH}"
            }
        }

        stage('Build') {
            steps {
                script {
                    if (env.GITHUB_PR_URL != null ) {
                        setBuildStatus("Jenkins ${env.STAGE_NAME} Executing", "PENDING", "${env.GITHUB_REPO_SSH_URL}",
                        "jenkins/${env.STAGE_NAME}")
                    }
                    docker.image('lambci/lambda:build-python3.7').inside('--user root -e AWS_REGION="${env.AWS_REGION}"'){
                        sh '''export AWS_SHARED_CREDENTIALS_FILE=/tmp/.aws
                              mkdir -p /tmp
                              touch ${AWS_SHARED_CREDENTIALS_FILE}
                              echo "[default]" > $AWS_SHARED_CREDENTIALS_FILE
                              echo "region=$AWS_REGION" >> $AWS_SHARED_CREDENTIALS_FILE
                              apt-get update && apt-get install -y git --no-install-recommends
                              python3 -m ensurepip
                              pip3 install awscli boto3
                              echo "install"
                              echo "build step"''
                    }
                }
            }
            post {
                always {
                    script {
                        if (env.GITHUB_PR_URL != null ) {
                            setBuildStatus( "${currentBuild.currentResult}", "${currentBuild.currentResult}","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");
                        }
                    }
                }
                success {
                    slackSend(channel: "${env.slack_channel}", message: ":white_check_mark: *${env.STAGE_NAME} passed*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    PR: ${env.GITHUB_PR_URL}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
                failure {
                    slackSend(channel: "${env.slack_channel}", message: ":red_circle: *${env.STAGE_NAME} ran into testing issues, probably best to check it out*\n    PR: ${env.GITHUB_PR_URL}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }
        }

        stage('Deploy to Dev') {

            steps {
                script {
                    docker.image('lambci/lambda:build-python3.7').inside('--user root -e AWS_REGION="${env.AWS_REGION} -e AWS_ACC_NR="${env.AWS_DEV_ACCOUNT_NUMBER}"'){
                        sh '''export AWS_SHARED_CREDENTIALS_FILE=/tmp/.aws
                              mkdir -p /tmp
                              touch ${AWS_SHARED_CREDENTIALS_FILE}
                              echo "[dev]" > $AWS_SHARED_CREDENTIALS_FILE
                              echo "role_arn = arn:aws:iam::$AWS_DEV_ACCOUNT_NUMBER:role/DeploymentExecutionRole" >> $AWS_SHARED_CREDENTIALS_FILE
                              echo "credential_source = Ec2InstanceMetadata" >> $AWS_SHARED_CREDENTIALS_FILE
                              echo "region=$AWS_REGION" >> $AWS_SHARED_CREDENTIALS_FILE
                              pip install awscli boto3
                              pip install j2cli-3
                              bash bin/deploy.sh dev '''
                    }
                }
            }
            post {
                always {
                    script {
                        if (env.GITHUB_PR_URL != null ) {
                            setBuildStatus( "${currentBuild.currentResult}", "${currentBuild.currentResult}","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");
                        }
                    }
                }
                success {
                    slackSend(channel: "${env.slack_channel}", message: ":white_check_mark: *${env.STAGE_NAME} passed*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    PR: ${env.GITHUB_PR_URL}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")

                }
                failure {
                    slackSend(channel: "${env.slack_channel}", message: ":red_circle: *This PR ran into issues while deploying to staging, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    PR: ${env.GITHUB_PR_URL}\n", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                }
            }
        }

        stage('Deploy to Prod') {
            when {
                expression {
                    "${env.GITHUB_BRANCH_NAME}" == 'master';
                }
            }
            steps{
                script {
                    docker.image('lambci/lambda:build-python3.7').inside('--user root -e AWS_REGION="${env.AWS_REGION} -e AWS_ACC_NR="${env.AWS_PROD_ACCOUNT_NUMBER}'){
                       sh '''export AWS_SHARED_CREDENTIALS_FILE=/tmp/.aws
                              mkdir -p /tmp
                              touch ${AWS_SHARED_CREDENTIALS_FILE}
                              echo "[prod]" > $AWS_SHARED_CREDENTIALS_FILE
                              echo "role_arn = arn:aws:iam::$AWS_PROD_ACCOUNT_NUMBER:role/DeploymentExecutionRole" >> $AWS_SHARED_CREDENTIALS_FILE
                              echo "credential_source = Ec2InstanceMetadata" >> $AWS_SHARED_CREDENTIALS_FILE
                              echo "region=$AWS_REGION" >> $AWS_SHARED_CREDENTIALS_FILE
                              pip install awscli boto3
                              bash bin/deploy.sh prod '''
                    }
                }
            }
            post {
                  always {
                      script {
                          if (env.GITHUB_PR_URL != null ) {
                              setBuildStatus( "${currentBuild.currentResult}", "${currentBuild.currentResult}","${env.GITHUB_REPO_SSH_URL}", "jenkins/${env.STAGE_NAME}");
                          }
                      }
                  }
                  success {
                      slackSend(channel: "${env.slack_channel}", message: ":white_check_mark: *${env.STAGE_NAME} passed*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")

                  }
                  failure {
                      slackSend(channel: "${env.slack_channel}", message: ":red_circle: *This PR ran into issues while deploying to production, probably best to check it out*\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}", sendAsText: true, tokenCredentialId: "slack-token", botUser: true, iconEmoji: "jenkins", username: "Jenkins")
                  }
            }

        }

    }

}
