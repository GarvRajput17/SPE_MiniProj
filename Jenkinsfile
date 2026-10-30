pipeline {
    agent any

    environment {
        // Docker Hub repository and notification email
        DOCKER_IMAGE = 'garvrajput/calculator'
        DOCKER_CREDS_ID = 'dockerhub-creds'
        NOTIFICATION_EMAIL = 'Garv.Rajput@iiitb.ac.in'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== Stage 1: Checkout Latest Code ==='
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '=== Stage 2: Compile & Package Application ==='
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                echo '=== Stage 3: Run Automated Tests ==='
                sh 'mvn -B test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo '=== Stage 4: Build Multi-Stage Docker Image ==='
                sh """
                    docker build -t ${DOCKER_IMAGE}:${BUILD_NUMBER} -t ${DOCKER_IMAGE}:latest .
                """
            }
        }

        stage('Push') {
            steps {
                echo '=== Stage 5: Authenticate & Push to Docker Hub ==='
                withCredentials([usernamePassword(
                    credentialsId: "${DOCKER_CREDS_ID}",
                    passwordVariable: 'DOCKER_PASSWORD',
                    usernameVariable: 'DOCKER_USERNAME'
                )]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
                        docker push ${DOCKER_IMAGE}:${BUILD_NUMBER}
                        docker push ${DOCKER_IMAGE}:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                echo '=== Stage 6: Zero-Downtime Deployment with Docker Compose ==='
                sh """
                    export IMAGE_NAME=${DOCKER_IMAGE}
                    export IMAGE_TAG=${BUILD_NUMBER}
                    docker compose up -d --no-build

                    echo "Waiting for services to become healthy..."
                    TIMEOUT=120
                    ELAPSED=0
                    until [ \$(docker inspect --format='{{json .State.Health.Status}}' calculator-app-1 2>/dev/null) = '"healthy"' ] && \
                          [ \$(docker inspect --format='{{json .State.Health.Status}}' calculator-app-2 2>/dev/null) = '"healthy"' ] && \
                          [ \$(docker inspect --format='{{json .State.Health.Status}}' calculator-nginx-1 2>/dev/null) = '"healthy"' ]; do
                        if [ \$ELAPSED -ge \$TIMEOUT ]; then
                            echo "Deployment timed out waiting for healthy status"
                            docker compose logs
                            exit 1
                        fi
                        sleep 3
                        ELAPSED=\$((ELAPSED + 3))
                    done

                    echo "Verifying health endpoint through Nginx..."
                    curl -fsS http://localhost/actuator/health || exit 1
                    echo "Deployment successfully verified!"
                """
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded! Sending notification to ${NOTIFICATION_EMAIL}..."
            mail to: "${NOTIFICATION_EMAIL}",
                 subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                 body: "Job: ${env.JOB_NAME} Build: #${env.BUILD_NUMBER} SUCCESS URL: ${env.BUILD_URL}"
        }
        failure {
            echo "Pipeline failed! Sending notification to ${NOTIFICATION_EMAIL}..."
            mail to: "${NOTIFICATION_EMAIL}",
                 subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                 body: "Job: ${env.JOB_NAME} Build: #${env.BUILD_NUMBER} FAILURE URL: ${env.BUILD_URL}\nConsole: ${env.BUILD_URL}console"
        }
    }
}
