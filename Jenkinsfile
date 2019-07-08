#!groovy

@Library('devops_libraries') _

properties([
  [$class: 'GithubProjectProperty', projectUrlStr: 'git@github.com:organisation/repository_name.git'],
  pipelineTriggers([[$class: 'GitHubPushTrigger']])
])

docker_builder()
ecs_deploy()
