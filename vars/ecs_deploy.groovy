#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage("gathering facts") {
            checkout scm
            def defaults = readYaml file: "./jenkins.yaml"
            defaults.project_env.each { env, env_values ->
                env_values.each { is_deploy, is_true ->
                    if (is_deploy == "deploy" && is_true && BRANCH_NAME == "master") {
                        stage("prepare ansible vars for ${env}")
                            sh "export ansibleextras=$(mktemp -p ./) && (for variable in $(aws secretsmanager list-secrets --output text --query SecretList[].[Name] | grep \"/${defaults.project_name}/${env}/ansible\" | cut -d \"/\" -f 5); do echo \"\$variable: \$(aws secretsmanager get-secret-value --secret-id /${defaults.project_name}/${env}/ansible/\$variable --output text --query SecretString)\" >> \$ansibleextras; done)"
                        stage("deploy to ${env}") {
                            sh "ansible-pull -U https://github.com/bartlabno/cloud-formation-ecs-fargate.git --extra-vars @${WORKSPACE}/jenkins.yaml --extra-vars @${ansibleextras} --extra-vars \"current_env=${env}\" --extra-vars \"branch_name=${BRANCH_NAME}\" --extra-vars \"build_number=${BUILD_NUMBER}\" site.yaml"
                        }
                    }
                }
            }
        }
    }
}