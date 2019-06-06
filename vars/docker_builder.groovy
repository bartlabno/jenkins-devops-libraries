#!/usr/bin/env groovy

def defaults_file = findFiles(glob: '**/infrastructure/jenkins/defaults.y?ml')
def defaults = readYaml file: "${defaults_file[0].path}"
def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage('builder checkout') {
            checkout scm
        }
        stage('test') {
            sh script: "docker build --no-cache -t ${defaults.projectName}-test -f Dockerfile.test .", label: "build test docker image"
            sh script: "docker run ${defaults.projectName}-test", label: "run test docker image"
        }
        stage('build') {
            parallel (
                "image build": { sh script: "docker build --no-cache -t ${defaults.projectName} -f Dockerfile .", label: "build image" },
                "registry login": { sh script: "\$(aws ecr get-login --region ${defaults.awsRegion} --no-include-email)", label: "login to docker registry" }
            )
        }
        stage('publish') {
            sh script: "docker tag ${defaults.projectName} \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}/${defaults.projectName}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "tag image"
            sh script: "(aws ecr list-images --region ${defaults.awsRegion} --repository-name ${defaults.projectName}/${defaults.projectName}) || (aws ecr create-repository --region ${defaults.awsRegion} --repository-name ${projectName}/${projectName})", label: "check if repository exist"
            sh script: "docker push \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}/${defaults.projectName}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "push image to registry"
            when { branch: master }
            steps { 
                sh script: "docker tag ${defaults.projectName} \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}/${defaults.projectName}:latest", label: "tag image latest"
                sh script: "docker push \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}/${defaults.projectName}:latest", label: "push latest image to registry"
            }
        }
    }
}
