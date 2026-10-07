 pipeline {
      agent any

      stages {
          stage('Checkout Code') {
              steps {
                  echo "🚀 正在拉取 Ruici 代码..."
                  checkout scm
              }
          }
          stage('Unit Test') {
              steps {
                  echo "🧪 正在执行单元测试..."
                  sh 'mvn test'
              }
              post {
                  always {
                      junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                  }
              }
          }
      }

      post {
          success {
              echo "🟢 流水线执行成功！"
          }
          failure {
              echo "🔴 流水线执行失败，请检查上面的报错。"
          }
      }
  }
