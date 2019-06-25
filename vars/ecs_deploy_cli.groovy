#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage("gathering fatcs") {
            checkout scm
            def defaults = readYaml file: "./infrastructure/jenkins/defaults.yaml"
            defaults.environments.each { envs ->
                def pipe_vars = readYaml file: "./infrastructure/jenkins/${envs}.yaml"
                if (!pipe_vars.nodeType) { pipe_vars.nodeType = "t2.medium" }
                if (!pipe_vars.create_vpc) { pipe_vars.create_vpc = false }
                if (!pipe_vars.nodesMin) { pipe_vars.nodesMin = pipe_vars.nodes }
                if (!pipe_vars.nodesMax) { pipe_vars.nodesMax = pipe_vars.nodes }
                if (!pipe_vars.storageClass) { pipe_vars.storageClass = true }
                if (!pipe_vars.eksParams) { pipe_vars.eksParams = "" }
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
                            sh script: "(aws ec2 describe-security-groups --group-names ${defaults.projectName}-${envs} --region ${defaults.awsRegion}) || (aws ec2 create-security-group --group-names ${defaults.projectName}-${envs} --description ${defaults.projectName}-${envs}-devops-managed --vpc-id ${pipe_vars.vpc} --region ${defaults.awsRegion})", label: "create security group"
                            sh script: "(aws ec2 authorize-security-group-ingress --region ${defaults.awsRegion} --group-id $(aws ec2 describe-security-groups --group-name ${defaults.projectName}-${envs} --region ${defaults.awsRegion} --output text --query SecurityGroups[].GroupId) --protocol tcp --port ${defaults.portExpose} --cidr $(aws ec2 describe-vpcs --vpc-ids ${defaults.vpc} --region ${defaults.awsRegion} --output text --query Vpcs[].CidrBlock)) || echo OK"
                        }
                        stage("deploy ${envs}") {
                            sh script: """helm template --values ./infrastructure/k8s/values.yaml \
                                --set ProjectName=${defaults.projectName},Env=${envs},AwsRegion=${defaults.awsRegion},BranchName=${BRANCH_NAME},BuildNumber=${BUILD_NUMBER},Role=\$(if [ \$(kubectl get all | grep \"service/\${defaults.projectName}-service\" -c) -eq 0 ]; then echo blue; else if [ \$(kubectl describe service/\${defaults.projectName}-service | grep role=green -c) -ge 1 ]; then echo blue; else echo green; fi; fi) \
                                --output-dir ./infrastructure/k8s/manifests ./infrastructure/k8s"""
                            sh script: "kubectl apply --recursive --filename ./infrastructure/k8s/manifests/kube/templates/deployment.yaml"
                        }
                        stage("integration tests ${envs}") {
                            sh "sleep 5"
                        }
                        stage("promote ${envs}") {
                            sh script: "kubectl apply --recursive --filename ./infrastructure/k8s/manifests/kube/templates/service.yaml"
                        }
                    }
                }
            }
        }
    }
}
