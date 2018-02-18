# jenkins-init
Jenkins init configuration

This is a collection of groovy init scripts that I have created in order
to be able to fully configure a Jenkins instance running from Docker at
start-up.

- core-jobs.groovy: This is an init file that creates Bitbucket Team/Project jobs
  that are essentially multibranch pipeline jobs but also have a full Bitbucket
  integration that tracks branchs and PRs and so on. Based initially on the sample
  from https://coderanger.net/jenkins/
