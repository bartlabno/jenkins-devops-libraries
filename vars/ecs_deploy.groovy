#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage("gathering fatcs") {
            checkout scm
            def defaults = readYaml file: "./jenkins.yaml"
            defaults.project_env.each { key, value ->
                println key
                value.each { key_value, value_value ->
                    if (key_value == "deploy") {
                        println key_value + " is " + value_value
                    }
                }
            }
            sh "echo \"${defaults.project_env}\""
            // defaults.project_name { envs ->
            //     sh "echo \"${envs}\""
            //     // sh "echo \"${defaults.app_type}\""
            //     // sh "echo \"${defaults.allowed_ip}\""
            //     // sh "echo \"${defaults.project_name}\""
            //     // if (!defaults.mem_limit) { defaults.mem_limit = "0.5" }
            //     // if (!defaults.cpu_limit) { defaults.cpu_limit = "256" }
            //     // if (!defaults.use_vpc) {
            //     //     sh "echo \"\$(aws ec2 describe-vpcs --region ${defaults.project_region} --filters Name=isDefault,Values=true --output text --query Vpcs[].VpcId)\" > infrastructure/default_vpc"
            //     //     defaults.vpc = readFile 'infrastructure/default_vpc'
            //     // }
            //     // if (!defaults.use_subnets) { 
            //     //     sh "echo \"\$(aws ec2 describe-subnets --region ${defaults.project_region} --filters Name=vpc-id,Values=\$(aws ec2 describe-vpcs --region ${defaults.project_region} --filters Name=isDefault,Values=true --output text --query Vpcs[].VpcId) --output text --query Subnets[].SubnetId)\" > infrastructure/default_subnets"
            //     //     defaults.subnets = readFile 'infrastructure/default_subnets'
            //     //     defaults.subnets = defaults.subnets.split()
            //     // }
            //     // if (defaults.deploy) {
            //     //     node ( label: 'awscli' ) {
            //     //         stage("checkout ${envs}") {
            //     //             checkout scm
            //     //         }
            //     //         if (defaults.is_frontend) {
            //     //             stage("frontend configuration ${envs}") {
            //     //             }
            //     //         }
            //     //     }
            //     // }
            // }
        }
    }
}
