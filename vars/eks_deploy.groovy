#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage("gathering fatcs") {
            checkout scm
            def defaults = readYaml file: "./infrastructure/jenkins/defaults.yaml"
            def env_files = findFiles(glob: '**/infrastructure/jenkins/*.yaml') 
            for (file in env_files) {
                if (file.name != "defaults.yaml" ) {
                    def pipe_vars = readYaml file: "${file.path}"
                    if (!pipe_vars.nodeType) { pipe_vars.nodeType = "t2.medium" }
                    if (!pipe_vars.nodes) { pipe_vars.nodes = 4 }
                    if (!pipe_vars.nodesMin) { pipe_vars.nodesMin = pipe_vars.nodes }
                    if (!pipe_vars.nodesMax) { pipe_vars.nodesMax = pipe_vars.nodes }
                    if (!pipe_vars.storageClass) { pipe_vars.storageClass = true }
                    if (!pipe_vars.eksParams) { pipe_vars.eksParams = "" }
                    if (pipe_vars.deploy) {
                        // pipe_vars.each { item ->
                            node ( label: 'awscli' ) {
                                stage("checkout ${pipe_vars.env}") {
                                    checkout scm
                                }
                                stage("create cluster ${pipe_vars.env}") {
                                    sh script: """\
                                    eksctl utils write-kubeconfig --name=${defaults.projectName}-${pipe_vars.env} --region=${defaults.awsRegion} \
                                    || eksctl create cluster \
                                        --name=${defaults.projectName}-${pipe_vars.env} \
                                        --region=${defaults.awsRegion} \
                                        --nodes=${pipe_vars.nodes} \
                                        --node-type=${pipe_vars.nodeType} \
                                        --nodes-min=${pipe_vars.nodesMin} \
                                        --nodes-max=${pipe_vars.nodesMax} \
                                        --storage-class=${pipe_vars.storageClass} \
                                        --alb-ingress-access \
                                        ${pipe_vars.eksParams}""", label: "create cluster if not exist"
                                }
                                stage("deploy ${pipe_vars.env}") {
                                    sh script: "echo -e \"\\nProjectName: ${defaults.projectName}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - project name"
                                    sh script: "echo \"Env: ${pipe_vars.env}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - environment"
                                    sh script: "echo \"AwsRegion: ${defaults.awsRegion}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - AWS region"
                                    sh script: "echo \"BranchName: ${BRANCH_NAME}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - branch name"
                                    sh script: "echo \"BuildNumber: ${BUILD_NUMBER}\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - build number"
                                    sh script: "echo \"Role: \$(if [ \$(kubectl get all | grep \"service/\${projectName}-service\" -c) -eq 0 ]; then echo blue; else if [ \$(kubectl describe service/\${projectName}-service | grep role=green -c) -eq 0 ]; then echo green; else echo blue; fi; fi)\" >> ./infrastructure/k8s/values.yaml", label: "building helm values - role"
                                    sh script: "helm template --values ./infrastructure/k8s/values.yaml --output-dir ./infrastructure/k8s/manifests ./infrastructure/k8s"
                                    sh script: "kubectl apply --recursive --filename ./infrastructure/k8s/manifests/kube/templates/deployment.yaml"
                                }
                                stage("integration tests ${pipe_vars.env}") {
                                    sh "sleep 90"
                                }
                                stage("promote ${pipe_vars.env}") {
                                    sh script: "kubectl apply --recursive --filename ./infrastructure/k8s/manifests/kube/templates/service.yaml"
                                }
                            }
                        // }
                    }
                }
            }

        }

    }
}
