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
                if (!pipe_vars.vpc) {
                    sh "export DEFAULT_VPC=\$(aws ec2 describe-vpcs --region ${defaults.awsRegion} --filters Name=isDefault,Values=true --output text --query Vpcs[].VpcId)"
                    pipe_vars.vpc = $DEFAULT_VPC
                    sh "echo ${pipe_vars.vpc}"
                }
                if (!pipe_vars.subnets) { 
                    sh "echo \"\$(aws ec2 describe-subnets --region ${defaults.awsRegion} --filters Name=vpc-id,Values=\$(aws ec2 describe-vpcs --region ${defaults.awsRegion} --filters Name=isDefault,Values=true --output text --query Vpcs[].VpcId) --output text --query Subnets[].SubnetId)\" > infrastructure/default_subnets"
                    pipe_vars.subnets = readFile 'infrastructure/default_subnets'
                    pipe_vars.subnets = pipe_vars.subnets.split()
                }
                sh "echo ${pipe_vars.vpc} and ${pipe_vars.subnets}"
                pipe_vars.subnets.each { subnetX ->
                    sh "echo this subnet is ${subnetX}"
                }

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
                                sh script: "(ecs-cli ps --cluster ${defaults.projectName}-${envs} --region ${defaults.awsRegion}) || (ecs-cli up --cluster ${defaults.projectName}-${envs} --region ${defaults.awsRegion} --vpc ${pipe_vars.vpc} --subnets ${pipe_vars.subnets})"
                            }
                            sh script: "(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion}) || (aws ec2 create-security-group --group-name ${defaults.projectName}-${defaults.applicationName}-${envs} --description ${defaults.projectName}-${defaults.applicationName}-${envs}-devops-managed --vpc-id ${pipe_vars.vpc} --region ${defaults.awsRegion})", label: "create security group"
                            sh script: "(aws ec2 authorize-security-group-ingress --region ${defaults.awsRegion} --group-id \$(aws ec2 describe-security-groups --group-name ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion} --output text --query SecurityGroups[].GroupId) --protocol tcp --port ${defaults.portExpose} --cidr \$(aws ec2 describe-vpcs --vpc-ids ${pipe_vars.vpc} --region ${defaults.awsRegion} --output text --query Vpcs[].CidrBlock)) || echo OK", label: "open port for vpc only"
                        }
                        stage("deploy ${envs}") {
                            sh script: "(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${envs}-lb --region ${defaults.awsRegion}) || (aws ec2 create-security-group --group-name ${defaults.projectName}-${envs}-lb --description ${defaults.projectName}-${envs}-lb-devops-managed --vpc-id ${pipe_vars.vpc} --region ${defaults.awsRegion})", label: "create security group"
                            sh script: "(aws ec2 authorize-security-group-ingress --region ${defaults.awsRegion} --group-id \$(aws ec2 describe-security-groups --group-name ${defaults.projectName}-${envs}-lb --region ${defaults.awsRegion} --output text --query SecurityGroups[].GroupId) --protocol tcp --port ${pipe_vars.albPort} --cidr ${pipe_vars.albSource}) || echo OK", label: "open load balancer ports"
                            sh script: "(aws elbv2 describe-load-balancers --name ${defaults.projectName}-${envs} --region ${defaults.awsRegion}) || (aws elbv2 create-load-balancer --name ${defaults.projectName}-${envs} --subnets ${pipe_vars.subnets} --region ${defaults.awsRegion} --type application --scheme internet-facing --security-groups \$(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${envs}-lb --region ${defaults.awsRegion} --output text --query  SecurityGroups[].GroupId))"
                            sh script: "(aws elbv2 describe-target-groups --name ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion}) || (aws elbv2 create-target-group --name ${defaults.projectName}-${defaults.applicationName}-${envs} --protocol HTTP --port ${defaults.portExpose} --vpc-id ${pipe_vars.vpc} --target-type ip --region ${defaults.awsRegion} --health-check-path ${defaults.healthCheck})", label: "configure target group"
                            sh script: "(aws elbv2 create-listener --load-balancer-arn \$(aws elbv2 describe-load-balancers --name ${defaults.projectName}-${envs} --output text --query LoadBalancers[].LoadBalancerArn) --protocol HTTP --port ${defaults.albExtPort} --default-actions Type=forward,TargetGroupArn=\$(aws elbv2 describe-target-groups --region ${defaults.awsRegion} --name ${defaults.projectName}-${defaults.applicationName}-${envs} --output text --query TargetGroups[].TargetGroupArn))"
                            sh script: "(aws logs list-tags-log-group --log-group-name ${defaults.projectName}-${envs} --region ${defaults.awsRegion}) || (aws logs create-log-group --log-group-name ${defaults.projectName}-${envs} --region ${defaults.awsRegion})", label: "create log group"
                            
                            // build ecs-params.yaml
                            sh "echo \"version: 1\" > infrastructure/ecs-params.yaml" 
                            sh "echo \"task_definition:\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"   task_execution_role: ecsTaskExecutionRole\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"   ecs_network_mode: awsvpc\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"   task_size:\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"      mem_limit: ${pipe_vars.memLimit}GB\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"      cpu_limit: ${pipe_vars.cpuLimit}\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"run_params:\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"   network_configuration:\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"      awsvpc_configuration:\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"         subnets:\" >> infrastructure/ecs-params.yaml"
                            pipe_vars.subnets.each { subnet ->
                                sh "echo \"           - ${subnet}\" >> infrastructure/ecs-params.yaml"
                            }
                            sh "echo \"         security_groups:\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"           - \$(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion} --output text --query SecurityGroups[].GroupId)\" >> infrastructure/ecs-params.yaml"
                            sh "echo \"         assign_public_ip: ENABLED\" >> infrastructure/ecs-params.yaml"
                            sh "cat infrastructure/ecs-params.yaml"

                            // build docker-compose.yaml
                            sh "echo \"version: '3'\" > infrastructure/docker-compose.yaml"
                            sh "echo \"services:\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"  ${defaults.applicationName}:\" >> infrastructure/docker-compose.yaml"
                            // if $(BRANCH_NAME == jenkinsfile) {
                                // sh "echo \"    image: \"\$(aws sts get-caller-identity --output text --query Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}-${defaults.applicationName}:latest\"\" >> infrastructure/docker-compose.yaml"
                            // }
                            // else {
                                sh "echo \"    image: \"\$(aws sts get-caller-identity --output text --query Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}-${defaults.applicationName}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)\"\" >> infrastructure/docker-compose.yaml"
                            // }
                            sh "echo \"    ports:\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"      - ${defaults.portExpose}:${defaults.portExpose}\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"    logging:\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"      driver: awslogs\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"      options:\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"        awslogs-group: ${defaults.projectName}-${envs}\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"        awslogs-region: ${defaults.awsRegion}\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"        awslogs-stream-prefix: ${defaults.applicationName}\" >> infrastructure/docker-compose.yaml"
                            sh "echo \"    environment:\" >> infrastructure/docker-compose.yaml"
                            if (pipe_vars.composeEnv) {
                                pipe_vars.composeEnv.each { composeEnv ->
                                  sh "echo \"      - ${composeEnv}\" >> infrastructure/docker-compose.yaml"
                                }
                            }
                            if (pipe_vars.composeSecrets) {
                                pipe_vars.composeSecrets.each { secret ->
                                  sh "echo \"      - ${secret}=\$(aws secretsmanager get-secret-value --secret-id /${defaults.projectName}/${envs}/${defaults.applicationName}/${secret} --output text --query SecretString)\" >> infrastructure/docker-compose.yaml"
                                }
                            }
                            sh "cat infrastructure/docker-compose.yaml"

                            sh script: "ecs-cli compose --project-name ${defaults.projectName}-${defaults.applicationName}-${envs} --file infrastructure/docker-compose.yaml --ecs-params infrastructure/ecs-params.yaml service up --target-group-arn \$(aws elbv2 describe-target-groups --name ${defaults.projectName}-${defaults.applicationName}-${envs} --region ${defaults.awsRegion} --output text --query TargetGroups[].TargetGroupArn) --container-name ${defaults.applicationName} --container-port ${defaults.portExpose} --timeout 15", label: "deploy"
                        }
                        if (defaults.is_frontend) {
                            stage("frontend configuration ${envs}") {
                            }
                        }
                    }
                }
            }
        }
    }
}
