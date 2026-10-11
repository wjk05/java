 pipeline {
      agent any

      environment {
          IMAGE = 'wjktime/ruici-ai-platform-master-app'
          DOCKER_CREDS = credentials('dockerhub-wjktime')
      }

      stages {
          stage('准备') {
              steps {
                  script {
                      env.TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
                  }
                  sh 'echo "本次构建版本: $IMAGE:$TAG"'
                  sh 'docker version'
                  sh 'kubectl version --client'
              }
          }

          stage('构建镜像') {
              steps {
                  sh 'docker build -t $IMAGE:$TAG .'
              }
          }

          stage('推送镜像') {
              steps {
                  sh 'echo $DOCKER_CREDS_PSW | docker login -u $DOCKER_CREDS_USR --password-stdin'
                  sh 'docker push $IMAGE:$TAG'
              }
          }

          stage('部署') {
              steps {
                  sh 'kubectl set image deployment/app ruici-app=$IMAGE:$TAG'
                  sh 'kubectl rollout status deployment/app --timeout=180s'
              }
          }
      }
  }
