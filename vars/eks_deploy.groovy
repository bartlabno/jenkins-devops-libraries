#!/usr/bin/env groovy

def call(Map buildParams) {
    if (!buildParams.hasProperty("nodeType") && !buildParams.nodeType) { buildParams.nodeType = "t2.medium" }
    if (!buildParams.hasProperty("nodes") && !buildParams.nodes) { buildParams.nodes = 4 }
    if (!buildParams.hasProperty("nodeMin") && !buildParams.nodeMin) { buildParams.nodeMin = "${buildParams.nodes}" }
    if (!buildParams.hasProperty("nodeMax") && !buildParams.nodeMax) { buildParams.nodeMax = "${buildParams.nodes}" }
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
    }
}
