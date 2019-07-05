#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage("deploy") {
            checkout scm
            def defaults = readYaml file: "./jenkins.yaml"
            defaults.project_env.each { env, env_values ->
                env_values.each { is_deploy, is_true ->
                    if (is_deploy == "deploy" && is_true) {
                        sh "ansible-pull -U https://github.com/bartlabno/cloud-formation-ecs-fargate.git --extra-vars @${WORKSPACE}/jenkins.yaml --extra-vars \"current_env=${env}\" --extra-vars \"branch_name=${BRANCH_NAME}\" --extra-vars \"build_number=${BUILD_NUMBER}\" site.yaml"
                    }
                }
            }
        }
    }
}