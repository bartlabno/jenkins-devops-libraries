#!/usr/bin/env groovy

def call(Map buildParams) {
    if (!buildParams.hasProperty("nodeType") && !buildParams.nodeType) { buildParams.nodeType = "t2.medium" }
    if (!buildParams.hasProperty("nodes") && !buildParams.nodes) { buildParams.nodes = 4 }
    if (!buildParams.hasProperty("nodesMin") && !buildParams.nodesMin) { buildParams.nodesMin = buildParams.nodes }
    if (!buildParams.hasProperty("nodesMax") && !buildParams.nodesMax) { buildParams.nodesMax = buildParams.nodes }
    if (!buildParams.hasProperty("storageClass") && !buildParams.storageClass) { buildParams.storageClass = true }
    if (!buildParams.hasProperty("eksParams") && !buildParams.eskParams) { buildParams.eksParams = "" }

    node ( label: 'awscli' ) {
        stage("checkout ${buildParams.env}") {
            checkout scm
        }
        stage("create cluster ${buildParams.env}") {
            sh script: """\
            eksctl utils write-kubeconfig --name=${projectName}-${buildParams.env} --region=${awsRegion} \
            || eksctl create cluster \
                --name=${projectName}-${buildParams.env} \
                --region=${awsRegion} \
                --nodes=${buildParams.nodes} \
                --node-type=${buildParams.nodeType} \
                --nodes-min=${buildParams.nodesMin} \
                --nodes-max=${buildParams.nodesMax} \
                --storage-class=${buildParams.storageClass} \
                --alb-ingress-access \
                ${buildParams.eksParams}""", label: "create cluster if not exist"
        }
        stage("deploy ${buildParams.env}") {
            sh script: "(kubectl describe service/${projectName}-service | grep role=green -c && export role=blue && export alterRole=green) || (export role=green && export alterRole=blue)"
            sh script: "echo \"ProjectName: ${projectName}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - project name"
            sh script: "echo \"Env: ${buildParams.env}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - environment"
            sh script: "echo \"AwsRegion: ${awsRegion}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - AWS region"
            sh script: "echo \"BranchName: ${BRANCH_NAME}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - branch name"
            sh script: "echo \"BuildNumber: ${BUILD_NUMBER}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - build number"
            sh script: "echo \"Role: \$(if [ \$(kubectl get all | grep \"service/\${projectName}-service\" -c) -eq 0 ]; then echo blue; else if [ \$(kubectl describe service/\${projectName}-service | grep role=green -c) -eq 0 ]; then echo blue; else
 echo green; fi; fi)\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - role"
            sh script: "echo \"AlterRole: \$alterRole\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - alter role"
            sh script: "cat ./infrastructure/k8s/values.yaml"
        }
        stage("integration tests ${buildParams.env}") {
            sh "sleep 10"
        }
        stage("promote ${buildParams.env}") {
            sh script: "sed -i \"s/AlterRole.*/AlterRole: \$role/g\" ./infrastructure/k8s/values.yaml", label: "building helm values - alter role swap"
            sh script: "cat ./infrastructure/k8s/values.yaml"
        }
    }
}
