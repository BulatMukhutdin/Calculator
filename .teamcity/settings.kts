import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2019.2"

project {

    vcsRoot(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

    buildType(PitestVerification)
    buildType(Deploy)
    buildType(Pull_Request)
    buildType(UnitTest)
    buildType(Build)
    buildType(InstrumentedTest)
    buildType(TestCoverageVerification)

    params {
        param("env.ANDROID_SDK_ROOT", "/Users/bulat/Library/Android/sdk")
    }

    features {
        feature {
            id = "PROJECT_EXT_3"
            type = "IssueTracker"
            param("secure:password", "")
            param("name", "BulatMukhutdin/Calculator")
            param("pattern", """#(\d+)""")
            param("authType", "anonymous")
            param("repository", "https://github.com/BulatMukhutdin/Calculator")
            param("type", "GithubIssues")
            param("secure:accessToken", "")
            param("username", "")
        }
    }
    buildTypesOrder = arrayListOf(Build, UnitTest, PitestVerification, TestCoverageVerification, InstrumentedTest, Deploy)
}

object Build : BuildType({
    name = "Build"
    description = "assemble signed apk"

    artifactRules = "app/build/outputs/apk/release/*.apk => ."

    vcs {
        root(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

        cleanCheckout = true
        excludeDefaultBranchChanges = true
    }

    steps {
        gradle {
            name = "Assemble signed apk"
            tasks = "assembleRelease"
        }
    }

    features {
        pullRequests {
            vcsRootExtId = "${HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster.id}"
            provider = github {
                authType = vcsRoot()
                filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            }
        }
    }
})

object Deploy : BuildType({
    name = "Deploy"
    description = "Deploy to testers"

    allowExternalStatus = true
    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    detectHangingBuilds = false
    maxRunningBuilds = 1
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

        cleanCheckout = true
        branchFilter = "+:<default>"
        excludeDefaultBranchChanges = true
        showDependenciesChanges = true
    }

    steps {
        gradle {
            name = "firebase app distribution"
            tasks = "appDistributionUploadRelease"
        }
    }

    triggers {
        vcs {
            branchFilter = "+:<default>"
        }
    }

    dependencies {
        dependency(Build) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                artifactRules = "*.apk => ."
            }
        }
        snapshot(InstrumentedTest) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(UnitTest) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})

object InstrumentedTest : BuildType({
    name = "Instrumented test"
    description = "Start emulator and run UI tests"

    artifactRules = """
        app/build/outputs/code_coverage/**/connected/*.ec=>app/build/outputs/code_coverage/
        app/build/tmp/kotlin-classes/debug => app/build/tmp/kotlin-classes/debug
    """.trimIndent()

    vcs {
        root(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

        checkoutMode = CheckoutMode.ON_SERVER
        cleanCheckout = true
        excludeDefaultBranchChanges = true
        showDependenciesChanges = true
    }

    steps {
        script {
            name = "start emulator"
            scriptContent = """
                #!/usr/bin/env bash
                avdmanager=${'$'}ANDROID_SDK_ROOT/tools/bin/avdmanager
                sdkmanager=${'$'}ANDROID_SDK_ROOT/tools/bin/sdkmanager
                emulator=${'$'}ANDROID_SDK_ROOT/tools/emulator
                adb=${'$'}ANDROID_SDK_ROOT/platform-tools/adb
                
                name=CI_primary_29_x86_64
                
                echo "downloading system-images"
                ${'$'}sdkmanager "system-images;android-29;google_apis_playstore;x86_64"
                
                # chech available AVDs
                emulators="${'$'}(${'$'}avdmanager list avd)"
                
                if [[ ${'$'}emulators != *${'$'}name* ]]; then
                  echo "creating new AVD"
                  echo no | ${'$'}avdmanager --verbose create avd --name ${'$'}name --device "pixel" --package "system-images;android-29;google_apis_playstore;x86_64" --abi "x86_64"
                fi
                
                echo "starting AVD"
                ${'$'}emulator -avd ${'$'}name -no-snapshot -wipe-data -accel auto -no-boot-anim -delay-adb &
                
                echo "waining for boot"
                ${'$'}adb wait-for-device
                
                echo "booted"
            """.trimIndent()
        }
        gradle {
            name = "instrumented test"
            tasks = "connectedAndroidTest"
            gradleWrapperPath = "."
        }
        script {
            name = "close emulator"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                #!/usr/bin/env bash
                adb -s emulator-5554 emu kill
            """.trimIndent()
        }
    }

    failureConditions {
        executionTimeoutMin = 15
    }

    features {
        pullRequests {
            vcsRootExtId = "${HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster.id}"
            provider = github {
                authType = vcsRoot()
                filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            }
        }
    }
})

object PitestVerification : BuildType({
    name = "Pitest verification"
    description = "Run pitest and verify mutation threshold"

    artifactRules = "app/build/reports/pitest/release/"

    vcs {
        root(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

        cleanCheckout = true
        excludeDefaultBranchChanges = true
        showDependenciesChanges = true
    }

    steps {
        gradle {
            name = "pitest"
            tasks = "pitestRelease"
            gradleWrapperPath = "."
        }
    }

    failureConditions {
        executionTimeoutMin = 15
    }

    features {
        pullRequests {
            vcsRootExtId = "${HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster.id}"
            provider = github {
                authType = vcsRoot()
                filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            }
        }
    }
})

object Pull_Request : BuildType({
    name = "Pull Request"

    type = BuildTypeSettings.Type.COMPOSITE

    vcs {
        root(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

        branchFilter = ""
        excludeDefaultBranchChanges = true
        showDependenciesChanges = true
    }

    triggers {
        vcs {
            branchFilter = "+:pull/*"
        }
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "${HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "zxxfa2683ddb43430805a13442dcb619dd6ab5ee20866b29e1ccf2fc26cbfb2fffa3c4570329bbb47cf775d03cbe80d301b"
                }
            }
            param("github_oauth_user", "BulatMukhutdin")
        }
        pullRequests {
            vcsRootExtId = "${HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster.id}"
            provider = github {
                authType = vcsRoot()
                filterTargetBranch = "+:refs/heads/master"
                filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            }
        }
    }

    dependencies {
        snapshot(Build) {
            reuseBuilds = ReuseBuilds.ANY
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(PitestVerification) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(TestCoverageVerification) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})

object TestCoverageVerification : BuildType({
    name = "Test coverage verification"
    description = "Generate test coverage report and verify minimum test coverage"

    artifactRules = "app/build/reports/jacoco/testCoverageReport/html/ => jacoco"

    vcs {
        root(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

        cleanCheckout = true
        excludeDefaultBranchChanges = true
        showDependenciesChanges = true
    }

    steps {
        gradle {
            name = "test coverage report"
            tasks = "testCoverageReport"
            gradleWrapperPath = "."
        }
        gradle {
            name = "test coverage verification"
            tasks = "testCoverageVerification"
            gradleWrapperPath = "."
        }
    }

    failureConditions {
        executionTimeoutMin = 15
    }

    features {
        pullRequests {
            vcsRootExtId = "${HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster.id}"
            provider = github {
                authType = vcsRoot()
                filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            }
        }
    }

    dependencies {
        dependency(InstrumentedTest) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                artifactRules = """
                    app/build/outputs/code_coverage/**/connected/*.ec => app/build/outputs/code_coverage/
                    app/build/tmp/kotlin-classes/debug => app/build/tmp/kotlin-classes/debug
                """.trimIndent()
            }
        }
        dependency(UnitTest) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                artifactRules = """
                    app/build/jacoco/*.exec => app/build/jacoco/
                    app/build/tmp/kotlin-classes/release => app/build/tmp/kotlin-classes/release
                """.trimIndent()
            }
        }
    }
})

object UnitTest : BuildType({
    name = "Unit test"
    description = "Run unit tests"

    artifactRules = """
        app/build/jacoco/*.exec => app/build/jacoco/
        app/build/tmp/kotlin-classes/release => app/build/tmp/kotlin-classes/release
    """.trimIndent()

    vcs {
        root(HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster)

        cleanCheckout = true
        excludeDefaultBranchChanges = true
        showDependenciesChanges = true
    }

    steps {
        gradle {
            name = "unit test"
            tasks = "testRelease"
            gradleWrapperPath = "."
        }
    }

    failureConditions {
        executionTimeoutMin = 15
    }

    features {
        pullRequests {
            vcsRootExtId = "${HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster.id}"
            provider = github {
                authType = vcsRoot()
                filterAuthorRole = PullRequests.GitHubRoleFilter.EVERYBODY
            }
        }
    }
})

object HttpsGithubComBulatMukhutdinCalculatorRefsHeadsMaster : GitVcsRoot({
    name = "https://github.com/BulatMukhutdin/Calculator#refs/heads/master"
    pollInterval = 5
    url = "https://github.com/BulatMukhutdin/Calculator"
    agentCleanPolicy = GitVcsRoot.AgentCleanPolicy.ALWAYS
    authMethod = password {
        userName = "BulatMukhutdin"
        password = "zxxfa2683ddb43430805a13442dcb619dd6ab5ee20866b29e1ccf2fc26cbfb2fffa3c4570329bbb47cf775d03cbe80d301b"
    }
})
