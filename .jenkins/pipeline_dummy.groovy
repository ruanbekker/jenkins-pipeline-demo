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
        label 'jenkins-master'
    }
    environment {
        project_name = "demo"
        project_owner = "ruan"
        slack_channel = "system_events"
        AWS_REGION = "eu-west-1"
        AWS_DEV_ACCOUNT_NUMBER = credentials('AWS_DEV_ACCOUNT_NUMBER')
        GIT_TOKEN = credentials('GITHUB_TOKEN')
        SLACK_TOKEN_SECRET = credentials('SLACK_TOKEN_SECRET')
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

        stage('Build') {
            steps {
                
                docker.image('lambci/lambda:build-python3.7').inside('--user root -e AWS_REGION="eu-west-1"'){
                    sh '''export AWS_SHARED_CREDENTIALS_FILE=/tmp/.aws
                        mkdir -p /tmp
                        touch ${AWS_SHARED_CREDENTIALS_FILE}
                        echo "[default]" > $AWS_SHARED_CREDENTIALS_FILE
                        echo "region=$AWS_REGION" >> $AWS_SHARED_CREDENTIALS_FILE
                        apt-get update && apt-get install -y git --no-install-recommends
                        python3 -m ensurepip
                        pip3 install awscli boto3
                        echo "install"
                        echo "build step"'''
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
