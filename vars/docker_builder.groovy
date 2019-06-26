#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage('builder checkout') {
            checkout scm
            def defaults = readYaml file: "./infrastructure/jenkins/defaults.yaml"
            defaults.each { item -> 
                echo """${item}"""
            }    
            stage('test') {
                sh script: "cp ~/scripts/unitTests.sh ."
                sh script: "cp ~/scripts/Dockerfile.sdk-${defaults.sdkVersion} ./Dockerfile.test"
                sh script: "docker build --no-cache -t ${defaults.projectName}-${defaults.applicationName}-test -f Dockerfile.test .", label: "build test docker image"
                sh script: "docker run ${defaults.projectName}-${defaults.applicationName}-test", label: "run test docker image"
            }
            stage('build') {
                parallel (
                    "image build": { sh script: "docker build --no-cache -t ${defaults.projectName}-${defaults.applicationName} -f Dockerfile .", label: "build image" },
                    "registry login": { sh script: "\$(aws ecr get-login --region ${defaults.awsRegion} --no-include-email)", label: "login to docker registry" }
                )
            }
            stage('publish') {
                sh script: "docker tag ${defaults.projectName}-${defaults.applicationName} \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}-${defaults.applicationName}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "tag image"
                sh script: "(aws ecr list-images --region ${defaults.awsRegion} --repository-name ${defaults.projectName}-${defaults.applicationName}) || (aws ecr create-repository --region ${defaults.awsRegion} --repository-name ${defaults.projectName}-${defaults.applicationName})", label: "check if repository exist"
                sh script: "docker push \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}-${defaults.applicationName}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "push image to registry"
                if (env.BRANCH_NAME == "master") {
                    sh script: "docker tag ${defaults.projectName}-${defaults.applicationName} \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}-${defaults.applicationName}:latest", label: "tag image latest"
                    sh script: "docker push \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.awsRegion}.amazonaws.com/${defaults.projectName}-${defaults.applicationName}:latest", label: "push latest image to registry"
                }
            }
        }
    }
}
