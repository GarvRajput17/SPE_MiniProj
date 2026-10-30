pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '25'))
        // Two builds deploying the same Compose project at once would fight over
        // container names and port 80 — serialise them so Deploy stays idempotent.
        disableConcurrentBuilds()
    }

    // Auto-trigger: GitHub webhook is primary, Poll SCM is the fallback for when
    // the tunnel exposing Jenkins to GitHub is down.
    triggers {
        githubPush()
        pollSCM('H/2 * * * *')
    }

    environment {
        DOCKER_IMAGE       = 'garvrajput17/calculator'
        DOCKER_CREDS_ID    = 'dockerhub-creds'
        NOTIFICATION_EMAIL = 'garvgrez@gmail.com'
        COMPOSE_PROJECT    = 'spe_miniproj'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== Stage 1: Checkout Latest Code ==='
                checkout scm
                sh 'git log -1 --pretty="commit %h by %an: %s"'
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
                sh 'docker build -t "$DOCKER_IMAGE:$BUILD_NUMBER" -t "$DOCKER_IMAGE:latest" .'
                sh 'docker image inspect "$DOCKER_IMAGE:$BUILD_NUMBER" --format "built {{.Id}} ({{.Size}} bytes)"'
            }
        }

        stage('Push') {
            steps {
                echo '=== Stage 5: Authenticate & Push to Docker Hub ==='
                // Credentials come from the Jenkins store, never from the repo.
                // Single-quoted sh + shell-level expansion keeps the secret out of
                // the Groovy-interpolated command string (and out of the console log).
                withCredentials([usernamePassword(
                    credentialsId: "${DOCKER_CREDS_ID}",
                    passwordVariable: 'DOCKER_PASSWORD',
                    usernameVariable: 'DOCKER_USERNAME'
                )]) {
                    sh '''
                        set -e
                        echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
                        docker push "$DOCKER_IMAGE:$BUILD_NUMBER"
                        docker push "$DOCKER_IMAGE:latest"
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                echo '=== Stage 6: Deploy Stack with Docker Compose & Verify Health ==='
                // Re-runnable: `compose up -d` reconciles the existing stack in place
                // rather than recreating it from scratch.
                sh '''
                    set -e
                    export IMAGE_NAME="$DOCKER_IMAGE"
                    export IMAGE_TAG="$BUILD_NUMBER"

                    docker compose -p "$COMPOSE_PROJECT" up -d --no-build --remove-orphans

                    echo "Waiting for all services to report healthy..."
                    TIMEOUT=150
                    ELAPSED=0
                    while :; do
                        UNHEALTHY=0
                        for c in calculator-app-1 calculator-app-2 calculator-nginx-1; do
                            STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$c" 2>/dev/null || echo missing)
                            if [ "$STATUS" != "healthy" ]; then
                                UNHEALTHY=1
                                echo "  $c => $STATUS"
                            fi
                        done
                        [ "$UNHEALTHY" -eq 0 ] && break
                        if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
                            echo "Deployment timed out waiting for healthy containers"
                            docker compose -p "$COMPOSE_PROJECT" ps
                            docker compose -p "$COMPOSE_PROJECT" logs --tail=100
                            exit 1
                        fi
                        sleep 5
                        ELAPSED=$((ELAPSED + 5))
                    done

                    # Recreating the app containers gives them new IPs, but Nginx
                    # resolves its upstreams once at worker startup and caches them
                    # forever — so it would keep proxying to the dead containers and
                    # return 502. A graceful reload re-resolves them without dropping
                    # any in-flight connection.
                    echo "Reloading Nginx so it re-resolves the recreated upstreams..."
                    docker exec calculator-nginx-1 nginx -s reload

                    echo "Smoke-testing the API through Nginx..."
                    # Bounded retry: the reload swaps worker processes, so the very
                    # first request after it can still land on a retiring worker.
                    ATTEMPT=0
                    until curl -fsS http://localhost/actuator/health >/dev/null 2>&1; do
                        ATTEMPT=$((ATTEMPT + 1))
                        if [ "$ATTEMPT" -ge 20 ]; then
                            echo "API did not become reachable through Nginx"
                            docker compose -p "$COMPOSE_PROJECT" ps
                            docker compose -p "$COMPOSE_PROJECT" logs --tail=100 nginx
                            exit 1
                        fi
                        sleep 2
                    done

                    curl -fsS http://localhost/actuator/health
                    echo ""
                    curl -fsS "http://localhost/api/v1/add?a=2&b=3"
                    echo ""
                    curl -fsS "http://localhost/api/v1/multiply?a=6&b=7"
                    echo ""
                    # Divide-by-zero must come back as HTTP 400
                    CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost/api/v1/divide?a=1&b=0")
                    [ "$CODE" = "400" ] || { echo "expected 400 for divide-by-zero, got $CODE"; exit 1; }
                    echo "Divide-by-zero correctly returned HTTP 400"

                    # Prove both replicas are actually reachable through the proxy,
                    # not just that one of them answered.
                    for i in 1 2 3 4 5 6; do
                        curl -fsS -o /dev/null "http://localhost/api/v1/add?a=$i&b=1"
                    done
                    UPSTREAMS=$(docker logs --tail 20 calculator-nginx-1 2>&1 \
                        | grep -o 'upstream: [0-9.]*:[0-9]*' | sort -u | wc -l | tr -d ' ')
                    echo "Distinct healthy upstreams serving traffic: $UPSTREAMS"
                    [ "$UPSTREAMS" -ge 2 ] || { echo "load balancing degraded"; exit 1; }

                    docker compose -p "$COMPOSE_PROJECT" ps
                    echo "Deployment successfully verified!"
                '''
            }
        }
    }

    post {
        success {
            echo "Pipeline succeeded — notifying ${NOTIFICATION_EMAIL}"
            mail to: "${NOTIFICATION_EMAIL}",
                 subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                 body: """Build SUCCEEDED

Job     : ${env.JOB_NAME}
Build   : #${env.BUILD_NUMBER}
Image   : ${env.DOCKER_IMAGE}:${env.BUILD_NUMBER}
Details : ${env.BUILD_URL}

The calculator stack is deployed and healthy at http://localhost/
"""
        }
        failure {
            echo "Pipeline failed — notifying ${NOTIFICATION_EMAIL}"
            mail to: "${NOTIFICATION_EMAIL}",
                 subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                 body: """Build FAILED

Job     : ${env.JOB_NAME}
Build   : #${env.BUILD_NUMBER}
Details : ${env.BUILD_URL}
Console : ${env.BUILD_URL}console
"""
        }
        always {
            echo "Build #${env.BUILD_NUMBER} finished with status: ${currentBuild.currentResult}"
        }
    }
}
