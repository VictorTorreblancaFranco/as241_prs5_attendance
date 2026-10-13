pipeline {
    agent any

    options {
        timestamps()
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    parameters {
        booleanParam(name: 'RUN_SONAR', defaultValue: true, description: 'Ejecuta el analisis en SonarQube/SonarCloud')
        booleanParam(name: 'RUN_E2E', defaultValue: false, description: 'Ejecuta Selenium contra el frontend y Attendance levantados')
        booleanParam(name: 'RUN_JMETER', defaultValue: false, description: 'Ejecuta los tres planes JMeter')
    }

    environment {
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
        // Jenkins runs in Docker. The Angular dev server is on the host, while
        // Attendance is reachable through the shared Docker network.
        ATTENDANCE_URL = "${env.ATTENDANCE_URL ?: 'http://vg-ms-attendance:5085'}"
        FRONTEND_URL = "${env.FRONTEND_URL ?: 'http://192.168.100.169:4200'}"
        JMETER_HOST = "${env.JMETER_HOST ?: 'vg-ms-attendance'}"
        JMETER_PORT = "${env.JMETER_PORT ?: '5085'}"
        JMETER_DATE = "${env.JMETER_DATE ?: '2026-10-12'}"
        SLACK_CHANNEL = '#ci-attendance'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Compile') {
            steps { sh './mvnw -B -ntp -DskipTests compile' }
        }

        stage('Unit tests') {
            steps {
                sh './mvnw -B -ntp clean test'
            }
            post {
                always {
                    junit allowEmptyResults: false, testResults: 'target/surefire-reports/*.xml'
                    jacoco execPattern: 'target/jacoco.exec', classPattern: 'target/classes', sourcePattern: 'src/main/java'
                }
            }
        }

        stage('SonarQube') {
            when { expression { params.RUN_SONAR } }
            steps {
                withCredentials([string(credentialsId: 'sonarcloud-attendance-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('SonarCloud') {
        sh './mvnw -B -ntp verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar -Dsonar.token="$SONAR_TOKEN" -Dsonar.projectKey=VictorTorreblancaFranco_as241_prs5_attendance -Dsonar.organization=victortorreblancafranco -Dsonar.host.url=https://sonarcloud.io -Dsonar.scanner.skipJreProvisioning=true'
                    }
                }
            }
        }

        stage('Selenium functional tests') {
            when { expression { params.RUN_E2E } }
            steps {
                sh 'test -n "$FRONTEND_URL" && test -n "$ATTENDANCE_URL"'
                sh './mvnw -B -ntp -Pfunctional -Dselenium.headless=true -Dfrontend.url="$FRONTEND_URL" -Dattendance.url="$ATTENDANCE_URL" test'
            }
            post { always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/functional-*.xml' } }
        }

        stage('JMeter load tests') {
            when { expression { params.RUN_JMETER } }
            steps {
                sh 'command -v jmeter >/dev/null'
                sh 'mkdir -p target/jmeter'
                // Jenkins uses the distro JMeter 2.13, which supports JTL logs
                // but not the HTML report flags introduced in later versions.
                sh 'jmeter -n -t qa/jmeter/attendance-verde.jmx -Jhost="$JMETER_HOST" -Jport="$JMETER_PORT" -Jfecha="$JMETER_DATE" -l target/jmeter/verde.jtl -j target/jmeter/verde.log'
                sh 'jmeter -n -t qa/jmeter/attendance-amarillo.jmx -Jhost="$JMETER_HOST" -Jport="$JMETER_PORT" -Jfecha="$JMETER_DATE" -l target/jmeter/amarillo.jtl -j target/jmeter/amarillo.log'
                sh 'jmeter -n -t qa/jmeter/attendance-rojo.jmx -Jhost="$JMETER_HOST" -Jport="$JMETER_PORT" -Jfecha="$JMETER_DATE" -l target/jmeter/rojo.jtl -j target/jmeter/rojo.log'
                sh '''
                    printf 'Escenario\tMuestras\tErrores\tErrorPct\tPromedioMs\tMinMs\tMaxMs\n' > target/jmeter/resumen.tsv
                    for escenario in verde amarillo rojo; do
                      awk -F, -v nombre="$escenario" 'NR > 1 { total++; if ($8 == "false") errores++; suma += $2; if (min == "" || $2 < min) min = $2; if ($2 > max) max = $2 } END { if (total == "") total=0; if (errores == "") errores=0; pct=(total ? errores*100/total : 0); promedio=(total ? suma/total : 0); printf "%s\\t%d\\t%d\\t%.2f\\t%.2f\\t%d\\t%d\\n", nombre,total,errores,pct,promedio,min+0,max+0 }' target/jmeter/$escenario.jtl >> target/jmeter/resumen.tsv
                    done
                '''
            }
            post { always { archiveArtifacts allowEmptyArchive: true, artifacts: 'target/jmeter/**' } }
        }
    }

    post {
        success { slackSend(tokenCredentialId: 'slack-attendance-token', channel: env.SLACK_CHANNEL, color: 'good', message: "Attendance #${env.BUILD_NUMBER}: pipeline EXITOSO - ${env.BUILD_URL}") }
        failure { slackSend(tokenCredentialId: 'slack-attendance-token', channel: env.SLACK_CHANNEL, color: 'danger', message: "Attendance #${env.BUILD_NUMBER}: pipeline FALLIDO - ${env.BUILD_URL}") }
        unstable { slackSend(tokenCredentialId: 'slack-attendance-token', channel: env.SLACK_CHANNEL, color: 'warning', message: "Attendance #${env.BUILD_NUMBER}: pipeline INESTABLE - ${env.BUILD_URL}") }
    }
}
