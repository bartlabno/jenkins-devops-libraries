#!/usr/bin/env groovy

def call(Map buildParams) {
    if (BRANCH_NAME == "master" || BRANCH_NAME.contains("PR-")) {
        node ( label: 'linux' ) {
            try {
                checkout scm
                def defaults = readYaml file: "./jenkins.yaml"
                defaults.project_env.each { env, env_values ->
                    env_values.each { is_deploy, is_true ->
                        if ((env == "dev" && is_deploy == "deploy" && is_true && BRANCH_NAME == "master") || (env == "sandbox" && is_deploy == "deploy" && is_true && BRANCH_NAME.contains("PR-")) || (env != "dev" && env != "sandbox" && is_deploy == "deploy" && is_true && BRANCH_NAME == "master" && TAG_NAME.contains("release-*"))) {
                            stage("gathering facts for ${env}") {
                                ansibleextras = sh (
                                    script: "mktemp -p ${WORKSPACE}/",
                                    returnStdout: true
                                ).trim()
                                sh "(for variable in \$(aws secretsmanager list-secrets --output text --query SecretList[].[Name] | grep \"/${defaults.project_name}/${env}/ansible\" | cut -d \"/\" -f 5); do echo \"\$variable: \$(aws secretsmanager get-secret-value --secret-id /${defaults.project_name}/${env}/ansible/\$variable --output text --query SecretString)\" >> ${ansibleextras}; done)"
                            }
                            stage("deploy to ${env}") {
                                sh "ansible-pull -U https://github.com/bartlabno/cloudformation-templates.git --extra-vars @${WORKSPACE}/jenkins.yaml --extra-vars @${ansibleextras} --extra-vars \"current_env=${env}\" --extra-vars \"branch_name=${BRANCH_NAME}\" --extra-vars \"build_number=${BUILD_NUMBER}\" site.yaml"
                            }
                            if (defaults.fastly) {
                                stage("cofigure fastly for ${env}") {
                                    sh "ansible-pull -U https://github.com/bartlabno/fastly-ansible.git --extra-vars @${WORKSPACE}/jenkins.yaml --extra-vars @${ansibleextras} --extra-vars \"current_env=${env}\" site.yaml"
                                }
                            }
                        }
                    }
                }
            } catch (err) {
                slackSend(
                    channel: '#jenkins',
                    color: 'danger',
                    message: "${env.JOB_NAME}:  <${env.BUILD_URL}console|Build ${env.BUILD_DISPLAY_NAME}> has FAILED"
                )
                throw err
            }
            slackSend(
                channel: '#jenkins',
                color: 'good',
                message: "${env.JOB_NAME}:  <${env.BUILD_URL}console|Build ${env.BUILD_DISPLAY_NAME}> is DONE"
            )
        }
    }
}
