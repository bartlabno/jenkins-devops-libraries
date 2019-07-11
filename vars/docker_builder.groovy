#!/usr/bin/env groovy

def call(Map buildParams) {
    node ( label: 'linux' ) {
        stage('builder checkout') {
            checkout scm
            def defaults = readYaml file: "./jenkins.yaml"
            defaults.each { item -> 
            }    
            stage('test') {
                sh script: "cp ~/scripts/unitTests.sh ."
                sh script: "cp ~/scripts/Dockerfile.sdk-2.2 ./Dockerfile.test"
                if (defaults.build_arg) {
                        defaults.build_arg.each { arg ->
                            sh "echo \"${arg}=\$(aws secretsmanager get-secret-value --secret-id /${defaults.projectName}/docker/${defaults.applicationName}/${arg} --output text --query SecretString)\" >> ./docker-build.args"
                    }
                    sh script: "docker build --no-cache -t ${defaults.project_name}-${defaults.application_name}-test \$(for i in 'cat docker-build.args'; do out+=\"--build-arg \$i \" ; done; echo \$out;out=\"\") -f Dockerfile.test  .", label: "build test docker image"
                } else {
                    sh script: "docker build --no-cache -t ${defaults.project_name}-${defaults.application_name}-test -f Dockerfile.test .", label: "build test docker image"
                }
                sh script: "docker run ${defaults.project_name}-${defaults.application_name}-test", label: "run test docker image"
            }
            stage('build') {
                if (defaults.build_arg) {
                    sh script: "docker build --no-cache -t ${defaults.project_name}-${defaults.application_name} \$(for i in 'cat docker-build.args'; do out+=\"--build-arg \$i \" ; done; echo \$out;out=\"\") -f Dockerfile .", label: "build image"
                } else {
                    sh script: "docker build --no-cache -t ${defaults.project_name}-${defaults.application_name} -f Dockerfile .", label: "build image"
                }
                sh script: "\$(aws ecr get-login --region ${defaults.project_region} --no-include-email)", label: "login to docker registry"
            }
            if (BRANCH_NAME.startsWith('PR') || (BRANCH_NAME == "master")) {
                stage('publish') {
                    sh script: "docker tag ${defaults.project_name}-${defaults.application_name} \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.project_region}.amazonaws.com/${defaults.project_name}-${defaults.application_name}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "tag image"
                    sh script: "(aws ecr list-images --region ${defaults.project_region} --repository-name ${defaults.project_name}-${defaults.application_name}) || (aws ecr create-repository --region ${defaults.project_region} --repository-name ${defaults.project_name}-${defaults.application_name})", label: "check if repository exist"
                    sh script: "docker push \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.project_region}.amazonaws.com/${defaults.project_name}-${defaults.application_name}:\$(echo $BRANCH_NAME)-\$(echo $BUILD_NUMBER)", label: "push image to registry"
                    if (BRANCH_NAME == "master") {
                        sh script: "docker tag ${defaults.project_name}-${defaults.application_name} \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.project_region}.amazonaws.com/${defaults.project_name}-${defaults.application_name}:latest", label: "tag image latest"
                        sh script: "docker push \$(aws sts get-caller-identity | jq -r .Account).dkr.ecr.${defaults.project_region}.amazonaws.com/${defaults.project_name}-${defaults.application_name}:latest", label: "push latest image to registry"
                    }
                }
            }
        }
    }
}
