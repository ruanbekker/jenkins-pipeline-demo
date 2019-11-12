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
        GITHUB_BRANCH_NAME = "master"
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
              script {
                docker.image('lambci/lambda:build-python3.7').inside('--privileged --user root -e AWS_REGION="eu-west-1"'){
                    sh '''source bin/setup_aws_environment.sh
                        echo "calling aws dev"
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
