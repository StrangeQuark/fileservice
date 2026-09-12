pipeline {
    agent { label 'linux-agent' }

    environment {
        VAULT_URL = credentials('VAULT_URL')
        CICD_TOKEN = credentials('FILE_CICD_TOKEN')
        VAULTSERVICE_ENABLED = credentials('VAULTSERVICE_ENABLED')
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
