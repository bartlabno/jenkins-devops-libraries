#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage('builder checkout') {
            checkout scm
        }
        stage('test') {
            sh script: "docker build --no-cache -t ${projectName}-test -f Dockerfile.test .", label: "build test docker image"
            sh script: "docker run ${projectName}-test", label: "run test docker image"
        }
        stage('build') {
            parallel (
                "image build": { sh script: "docker build --no-cache -t ${projectName} -f Dockerfile .", label: "build image" },
                "registry login": { sh script: "\$(aws ecr get-login --region ${awsRegion} --no-include-email)", label: "login to docker registry" }
            )
        }
        stage('publish') {
            sh script: "docker tag ${projectName} \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${awsRegion}.amazonaws.com/${projectName}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "tag image"
            sh script: "aws ecr create-repository --repository-name ${projectName}  || aws ecr list-images --repository-name ${projectName}", label: "check if repository exist"
            sh script: "docker push \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.eu-west-2.amazonaws.com/${projectName}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "push image to registry"
        }
    }
}
