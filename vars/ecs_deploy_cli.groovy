#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage("gathering fatcs") {
            checkout scm
            def defaults = readYaml file: "./infrastructure/jenkins/defaults.yaml"
            defaults.environments.each { envs ->
                def pipe_vars = readYaml file: "./infrastructure/jenkins/${envs}.yaml"
                if (!pipe_vars.memLimit) { pipe_vars.memLimit = "0.5" }
                if (!pipe_vars.cpuLimit) { pipe_vars.cpuLimit = "256" }
                if (!pipe_vars.create_vpc) { pipe_vars.create_vpc = false }
                if (pipe_vars.deploy) {
                    node ( label: 'awscli' ) {
                        stage("checkout ${envs}") {
                            checkout scm
                        }
                        stage("create cluster ${envs}") {
                            sh script: "ecs-cli configure --cluster ${defaults.projectName}-${envs} --region ${defaults.awsRegion} --default-launch-type FARGATE --config-name ${defaults.projectName}-${envs}", label: "Cluster configure"
                            if (pipe_vars.create_vpc) {
                                echo "not supported yet"
                                exit 1
                            } else {
                                sh script: "(ecs-cli ps --cluster ${defaults.projectName}-${envs} --region ${defaults.awsRegion}) || (ecs-cli up --cluster ${defaults.projectName}-${envs} --region ${defaults.awsRegion} --vpc ${pipe_vars.vpc} --subnets ${pipe_vars.subnetA} ${pipe_vars.subnetB})"
                            }
                            sh script: "(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion}) || (aws ec2 create-security-group --group-name ${defaults.projectName}-${defaults.applicationName}-${envs} --description ${defaults.projectName}-${defaults.applicationName}-${envs}-devops-managed --vpc-id ${pipe_vars.vpc} --region ${defaults.awsRegion})", label: "create security group"
                            sh script: "(aws ec2 authorize-security-group-ingress --region ${defaults.awsRegion} --group-id \$(aws ec2 describe-security-groups --group-name ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion} --output text --query SecurityGroups[].GroupId) --protocol tcp --port ${defaults.portExpose} --cidr \$(aws ec2 describe-vpcs --vpc-ids ${pipe_vars.vpc} --region ${defaults.awsRegion} --output text --query Vpcs[].CidrBlock)) || echo OK", label: "open port for vpc only"
                        }
                        stage("deploy ${envs}") {
                            sh script: "(aws elbv2 describe-target-groups --name ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion}) || (aws elbv2 create-target-group --name ${defaults.projectName}-${defaults.applicationName}-${envs} --protocol HTTP --port ${defaults.portExpose} --vpc-id ${pipe_vars.vpc} --target-type ip --region ${defaults.awsRegion} --health-check-path ${defaults.healthCheck})", label: "configure target group"
                            sh "echo \"version: 1\" > infrastructure/docker/ecs-params.yaml" 
                            sh "echo \"task_definition:\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"   task_execution_role: ecsTaskExecutionRole\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"   ecs_network_mode: awsvpc\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"   task_size:\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"       mem_limit: ${pipe_vars.memLimit}G\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"       cpu_limit: ${pipe_vars.cpuLimit}\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"run_params:\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"   network_configuration:\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"       awsvpc_configuration:\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"       subnets:\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"           - ${pipe_vars.subnetA}\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"           - ${pipe_vars.subnetB}\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"       security_groups:\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"           - \$(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion} --output text --query SecurityGroups[].GroupId)\" >> infrastructure/docker/ecs-params.yaml"
                            sh "echo \"       assign_public_ip: ENABLED\" >> infrastructure/docker/ecs-params.yaml"
                            sh "cat infrastructure/docker/ecs-params.yaml"
                            sh script: "ecs-cli compose --project-name ${defaults.projectName}-${defaults.applicationName}-${envs} --file infrastructure/docker/docker-compose.yaml --ecs-params infrastructure/docker/ecs-params.yaml service up --target-group-arn \$(aws elbv2 describe-target-groups --name ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion} --output text --query TargetGroups[].TargetGroupArn) --container-name ${defaults.containerName} --container-port ${defaults.portExpose} --timeout 15", label: "deploy"
                        }
                        if (defaults.is_frontend) {
                            stage("frontend configuration ${envs}") {
                                sh script: "(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${envs}-lb --region ${defaults.awsRegion}) || (aws ec2 create-security-group --group-name ${defaults.projectName}-${envs}-lb --description ${defaults.projectName}-${envs}-lb-devops-managed --vpc-id ${pipe_vars.vpc} --region ${defaults.awsRegion})", label: "create security group"
                                sh script: "(aws ec2 authorize-security-group-ingress --region ${defaults.awsRegion} --group-id \$(aws ec2 describe-security-groups --group-name ${defaults.projectName}-${envs}-lb --region ${defaults.awsRegion} --output text --query SecurityGroups[].GroupId) --protocol tcp --port ${pipe_vars.albPort} --cidr ${pipe_vars.albSource}) || echo OK", label: "open load balancer ports"
                            }
                        }
                    }
                }
            }
        }
    }
}
