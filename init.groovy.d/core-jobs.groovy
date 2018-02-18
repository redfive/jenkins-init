import static jenkins.model.Jenkins.instance as jenkins

import com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator
import com.cloudbees.jenkins.plugins.bitbucket.OriginPullRequestDiscoveryTrait
import com.cloudbees.jenkins.plugins.bitbucket.BranchDiscoveryTrait
import com.cloudbees.jenkins.plugins.bitbucket.ForkPullRequestDiscoveryTrait

import jenkins.branch.NoTriggerOrganizationFolderProperty
import jenkins.branch.OrganizationFolder
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy
import jenkins.scm.impl.trait.RegexSCMHeadFilterTrait
import jenkins.scm.impl.trait.RegexSCMSourceFilterTrait

import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectFactory

// Pull the name of the instance from environment
def name = System.getenv('JENKINS_NAME')
def displayName = name.capitalize()
def owner = name.toUpperCase()

println("--> Setting up Core Jobs Jenkins J20 server ${name}...")

// Define parameters for all the core jobs we want to create
core_jobs = [ new Expando(jobname: "${name}-ci",
                          displayName: "${displayName} CI",
                          owner: "${owner}",
                          description: "Component Continuous Integration Builds for Bitbucket Project ${owner}",
                          jenkinsfile: "Jenkinsfile",
                          branchFilter: ".*",
                          autoBuild: ".*"),
              new Expando(jobname: "${name}-release",
                          displayName: "${displayName} Release",
                          owner: "${owner}",
                          description: "Component Release Builds for Bitbucket Project ${owner}",
                          jenkinsfile: "Jenkinsfile.release",
                          branchFilter: "master",
                          autoBuild: ""),
              new Expando(jobname: "${name}-docker-ci-build",
                          displayName: "${displayName} Docker CI Build",
                          owner: "${owner}",
                          description: "Docker Continuous Integration Builds for Bitbucket Project ${owner}",
                          jenkinsfile: "Jenkinsfile.docker.ci",
                          branchFilter: ".*",
                          autoBuild: ""),
              new Expando(jobname: "${name}-docker-ci-deploy",
                          displayName: "${displayName} Docker CI Deploy",
                          owner: "${owner}",
                          description: "Docker Continuous Integration Deploys for Bitbucket Project ${owner}",
                          jenkinsfile: "Jenkinsfile.docker.ci.deploy",
                          branchFilter: ".*",
                          autoBuild: ""),
              new Expando(jobname: "${name}-docker-release-build",
                          displayName: "${displayName} Docker Release Build",
                          owner: "${owner}",
                          description: "Docker Release Builds for Bitbucket Project ${owner}",
                          jenkinsfile: "Jenkinsfile.docker.release",
                          branchFilter: "master",
                          autoBuild: ""),
              new Expando(jobname: "${name}-docker-release-deploy",
                          displayName: "${displayName} Docker Release Deploy",
                          owner: "${owner}",
                          description: "Docker Release Deploys for Bitbucket Project ${owner}",
                          jenkinsfile: "Jenkinsfile.docker.release.deploy",
                          branchFilter: "master",
                          autoBuild: ""),
            ]

// Get this once to check for existing jobs
jobs = jenkins.getAllItems()

for (core_job in core_jobs) {

    println '----> checking for existence of job ' + core_job.jobname

    def shouldCreate = true
    // check to see if we've created this job already
    jobs.each { j ->  
        if (j instanceof jenkins.branch.OrganizationFolder &&
            j.fullName.contains(core_job.jobname)) {
            println '----> Already have a job for ' + j.fullName + ' of type:' + j.getClass()
            shouldCreate = false
        }
    }

    // If we found the job by name, move along to the next job
    if (!shouldCreate) {
        continue
    }

    println '----> configuring job ' + core_job.jobname

    // start by creating the toplevel folder 
    def folder = jenkins.createProject(OrganizationFolder, core_job.jobname)

    // Configure the Bitbucket SCM integration
    def navigator = new BitbucketSCMNavigator(core_job.owner)
    navigator.credentialsId = "jenkins-bitbucket-login"
    navigator.serverUrl = "https://bitbucket.example.com"
    navigator.traits = [
        //new RegexSCMSourceFilterTrait(core_job.repoFilter),   // build only these repos
        new RegexSCMHeadFilterTrait(core_job.branchFilter),     // only inspect branches of this form
        new BranchDiscoveryTrait(3),                            // discover all branches, including PRs
        new OriginPullRequestDiscoveryTrait(1),                 // Merge
        new ForkPullRequestDiscoveryTrait([ChangeRequestCheckoutStrategy.MERGE].toSet(),
                                          new ForkPullRequestDiscoveryTrait.TrustEveryone())
    ]
    folder.navigators.replace(navigator)

    folder.description = core_job.description
    folder.displayName = core_job.displayName

    // Delete orphan items after 5 days
    folder.orphanedItemStrategy = new DefaultOrphanedItemStrategy(true, "5", "")

    // Configure what Jenkinsfile we should be looking for
    WorkflowMultiBranchProjectFactory factory = new WorkflowMultiBranchProjectFactory()
    factory.scriptPath = core_job.jenkinsfile
    folder.projectFactories.replace(factory)

    // configure the repos to automatically build
    folder.addProperty(new NoTriggerOrganizationFolderProperty(core_job.autoBuild))

    jenkins.save()

    println '----> configured job ' + core_job.jobname

    Thread.start {
        sleep 3000 // 3 seconds
        println '----> Running Bitbucket organization scan for job ' + core_job.jobname
        folder.scheduleBuild()
    }

}

println '----> all jobs configured'
println("--> Setting up Core Jobs Jenkins J20 server ${name} - DONE")
