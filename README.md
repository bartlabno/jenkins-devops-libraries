# jenkins-devops-libraries
Shared libraries for Jenkins pipelines. To build, test and deploy your application within couple of steps into AWS.

# Description
You can now create your own jenkins jobs, which will automatically build and deploy your application. This is a very simple to achieve as everything you need is squashed into few steps. At the moment, there is no auto-discovery of jenkins groovy files via jenkins from github, but this will be probably added in a future. At the moment only supported deployments are docker (Dockerfile) based applications. Lambdas and everything else you would like to have will be added in next releases.

# Mandatroy files
There are two mandatory files which you need to create and add into your project: [Jenkinsfile](./Jenkinsfile) and [jenkins.yaml](./jenkins.yaml). Put both into main folder of your repository. You don't need to change anythin in Jenkinsfile if this deployment is docker based into AWS ECS cluster. All you need to do is adjust couple of things in jenkins.yaml

# Mandatory section
There are many optional only variables which will adjust your application and deploy to specific environments, vpcs, region or specific number of instances. But some of the options are mandatory and your project will fail if you forgot to provide them:

You need to provide project name. If you are creating project which is based on couple of services `project_name` will be on the top of the all of services - your AWS log group, cluster and few other things will use `project_name-environment` as it is name
- project_name: Project_Name
Project name helps you to create separate services running under one project
- application_name: Application_Name
You need to declare which environments will be in use. Please remember you can define envs only but not deploy into them. This is a default behaviour. To deploy you need to add additional variable `deploy` under [environment](./jenkins.yaml#L10) section 
- project_env: [sandbox, dev, uat, staging, prod]
To build application you need to declare it is kind. At the moment `npm` and `dotnet` are supported. This adds extra layer of unit tests when your application is being build.
- app_type: dotnet

# Environmental variables
All environmental variables are optional only. These section can be completely removed from jenkins.yaml if your application doesn't use any of these ones.
- non-secrets and non-sensitive values put directly into `jenkins.yaml` inside your project. You can do it in two ways:
- If it is global variable create it under `[env_vars](./jenkins.yaml#L26)` section
- If it is specific per env variable create it under [variable](./jenkins.yaml#L14) section.

If variables needs to be hidden (credentials, tokens, connection strings, and anything else) - go to the [AWS Secrets Manager](https://eu-west-2.console.aws.amazon.com/secretsmanager/home?region=eu-west-2#/home) and create a new one under specific path:
```/{{ project_name }}/{{ environment }}/{{ application_name }}/{{ variable_name }}```
like:
```/cobra/dev/job-monitoring/AWS_SECRET_KEY```
project_name and application name are defined at the beginning of jenkins.yaml file under your project. You also need to add {{ variable_name }} into [secret_vars](./jenkins.yaml#L31) section.

# Optional variables
You can go into [jenknis.yaml](./jenkins.yaml) file to look for any additional variables. Some of these are not documented well yet or not working yet (like `create_vpc`) as these will be added into upcoming releases. More documentation will be added here with next releases as well.
