pipeline {
    agent { label 'linux-agent' }

    environment {
        VAULT_URL = credentials('VAULT_URL')
        CICD_TOKEN = credentials('FILE_CICD_TOKEN')
        VAULTSERVICE_ENABLED = credentials('VAULTSERVICE_ENABLED')
        KUBERNETES_CICD_TOKEN = credentials('KUBERNETES_CICD_TOKEN')
    }

    stages {
        stage("Retrieve Env Vars") {
            steps {
                script {
                    if(VAULTSERVICE_ENABLED == "true") {
                        def response = httpRequest(
                            url: VAULT_URL + '/api/vault/cicd',
                            httpMode: 'POST',
                            contentType: 'APPLICATION_JSON',
                            requestBody: '{"serviceName":"fileservice","environmentName":"e3"}',
                            customHeaders: [
                                [name: 'X-CICD-TOKEN', value: CICD_TOKEN, maskValue: true]
                            ],
                            validResponseCodes: '200'
                        )

                        writeFile file: 'fileservice.env', text: response.content
                        echo "Environment variables written to fileservice.env"
                    }
                }
            }
        }
        stage("Deploy & Health Check") {
            steps {
                script {
                    def kubernetesEnabled = env.KUBERNETES_ENABLED == "true"

                    if(kubernetesEnabled) {
                        def imageRepository = env.SERVICE_IMAGE_REPOSITORY
                        def kubernetesServiceUrl = env.KUBERNETESERVICE_URL

                        if(imageRepository.isEmpty() || kubernetesServiceUrl.isEmpty())
                            error("FileService Kubernetes deployment configuration is incomplete")

                        def image = imageRepository + ":" + env.BUILD_NUMBER

                        withEnv(["SERVICE_IMAGE=" + image, "KUBERNETESERVICE_URL=" + kubernetesServiceUrl]) {
                            sh "docker build -t " + image + " ."
                            sh "docker push " + image
                            sh '''
                                curl --fail-with-body -X POST \\
                                    -H "X-CICD-TOKEN: $KUBERNETES_CICD_TOKEN" \\
                                    -F "serviceName=fileservice" \\
                                    -F "image=$SERVICE_IMAGE" \\
                                    -F "environmentFile=@fileservice.env" \\
                                    "$KUBERNETESERVICE_URL/api/kubernetes/deploy"
                            '''
                        }
                        return
                    }

                    try {
                        sh "docker compose --env-file fileservice.env up --build -d"

                        def maxRetries = 4 * 10
                        def retryInterval = 15
                        def success = false

                        for (int i = 0; i < maxRetries; i++) {
                            try {
                                echo "Health check attempt ${i + 1}..."
                                def healthResponse = httpRequest(
                                    url: 'http://localhost:6010/api/file/health',
                                    validResponseCodes: '200'
                                )
                                echo "App is healthy: ${healthResponse.status}"
                                success = true
                                break
                            } catch (err) {
                                echo "Health check failed, retrying in ${retryInterval} seconds..."
                                sleep(retryInterval)
                            }
                        }

                        if (!success) {
                            echo "Health check ultimately failed. Tearing down containers."
                            sh "docker compose down"
                            error("Deployment failed: service not healthy.")
                        }

                    } catch (ex) {
                        echo "Unexpected failure: ${ex.getMessage()}"
                        sh "docker compose down"
                        error("Deployment crashed.")
                    }
                }
            }
        }
    }
    post {
        always {
            sh "rm -f fileservice.env"
            echo "Cleaned up fileservice.env"
        }
    }
}
