pipeline {
    agent any

    parameters {
        // 保留参数，方便测试
        string(name: 'VERSION', defaultValue: '1.0.0', description: '测试版本号')
    }

    stages {
        stage('1. 打印环境信息') {
            steps {
                echo "✅ Jenkins 环境正常"
                echo "🔢 当前测试版本: ${params.VERSION}"
                sh 'whoami' // 看看是谁在运行
                sh 'pwd'    // 看看在哪个目录
            }
        }

        stage('2. 拉取代码 (关键测试)') {
            steps {
                echo "🚀 开始尝试从 GitHub 拉取代码..."
                // 默认 checkout scm 会使用你在 Jenkins 任务配置里写的 Git 地址和凭据
                checkout scm
                echo "🎉 代码拉取成功！"
            }
        }

        stage('3. 验证文件') {
            steps {
                echo "📂 列出当前目录文件："
                // 列出文件，确认代码真的在这里
                sh 'ls -l'
            }
        }
    }

    post {
        success {
            echo "🟢 流水线执行成功！恭喜！"
        }
        failure {
            echo "🔴 流水线执行失败，请检查上面的报错。"
        }
    }
}
