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
        label 'docker'
    }
    environment {
        project_name = "demo"
        project_owner = "ruan"
        slack_channel = "system_events"
        AWS_REGION = "eu-west-1"
        AWS_DEV_ACCOUNT_NUMBER = credentials('AWS_DEV_ACCOUNT_NUMBER')
        AWS_ACCESS_KEY = credentials('AWS_MASTER_JENKINS_AK')
        AWS_SECRET_KEY = credentials('AWS_MASTER_JENKINS_SK')
        AWS_CROSS_ACCOUNT_ROLE_ARN = "arn:aws:iam::$AWS_DEV_ACCOUNT_NUMBER:role/SystemCrossAccountAccess"
        GIT_TOKEN = credentials('GITHUB_TOKEN')
        SLACK_TOKEN_SECRET = credentials('SLACK_TOKEN_SECRET')
    }
    stages{

        stage('Checkout'){
            steps {
                git url: "git@github.com:ruanbekker/jenkins-pipeline-demo.git",
                branch: "${env.GITHUB_BRANCH_NAME}"
            }
        }

        stage('Build') {
            steps {
              script {
                docker.image('lambci/lambda:build-python3.7').inside('--privileged --user root -e AWS_REGION="eu-west-1"'){
                    sh '''export AWS_SHARED_CREDENTIALS_FILE=/tmp/.aws
                        mkdir -p /tmp
                        touch ${AWS_SHARED_CREDENTIALS_FILE}
                        echo "[master]" > $AWS_SHARED_CREDENTIALS_FILE
                        echo "aws_access_key_id=$AWS_ACCESS_KEY" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "aws_secret_access_key=$AWS_SECRET_KEY" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "region=$AWS_REGION" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "[dev]" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "role_arn=$AWS_CROSS_ACCOUNT_ROLE_ARN" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "source_profile=master" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "region=$AWS_REGION" >> $AWS_SHARED_CREDENTIALS_FILE
                        echo "" >> $AWS_SHARED_CREDENTIALS_FILE
                        apt-get update && apt-get install -y git --no-install-recommends
                        python3 -m ensurepip
                        pip3 install awscli boto3
                        echo "install"
                        echo "build step"
                        aws --profile dev sts get-caller-identity'''
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
                    slackSend(channel: "${env.slack_channel}", message: "\n:white_check_mark: *${env.STAGE_NAME} passed*\n\n    Job URL: ${env.JOB_URL}${env.BUILD_NUMBER}\n    PR: ${env.GITHUB_PR_URL}\n", iconEmoji: "jenkins", username: "Jenkins")
                }
                failure {
                    slackSend(channel: "${env.slack_channel}", message: "\n:red_circle: *${env.STAGE_NAME} ran into testing issues, probably best to check it out*\n\n    PR: ${env.GITHUB_PR_URL}\n", iconEmoji: "jenkins", username: "Jenkins")
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
                    
                       sh '''export AWS_SHARED_CREDENTIALS_FILE=/tmp/.aws
                              mkdir -p /tmp
                              bash bin/deploy_dummy.sh prod '''
                    
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
            }

        }

    }

}
