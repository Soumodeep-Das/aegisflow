# Pure verification plan: pipeline classes must never leak into the ordinary Core startup check.
function Get-CoreInboxVerificationPlan {
    param([Parameter(Mandatory)][string]$MavenCache, [switch]$CoreStartupOnly)
    $common = @('-B','-ntp',"-Dmaven.repo.local=$MavenCache")
    if (-not $CoreStartupOnly) {
        [pscustomobject]@{
            Name = 'compile'
            Arguments = $common + @('-Ppipeline-tests','test-compile')
            Suites = @()
        }
        [pscustomobject]@{
            Name = 'pipeline'
            Arguments = $common + @('-pl','control-plane','-Pinfrastructure-tests,pipeline-tests',
                '-Dtest=EventValidatorTests,KafkaInboxConsumerTests,PipelinePortTests,ControlPlaneArchitectureTests',
                '-Dit.test=CorePipelineScenario','test','failsafe:integration-test','failsafe:verify')
            Suites = @(
                [pscustomobject]@{ Kind='surefire'; Name='com.aegisflow.core.eventing.internal.EventValidatorTests'; Tests=23 },
                [pscustomobject]@{ Kind='surefire'; Name='com.aegisflow.core.eventing.internal.KafkaInboxConsumerTests'; Tests=5 },
                [pscustomobject]@{ Kind='surefire'; Name='com.aegisflow.core.eventing.internal.PipelinePortTests'; Tests=4 },
                [pscustomobject]@{ Kind='surefire'; Name='com.aegisflow.core.ControlPlaneArchitectureTests'; Tests=4 },
                [pscustomobject]@{ Kind='failsafe'; Name='com.aegisflow.core.eventing.internal.CorePipelineScenario'; Tests=10 }
            )
        }
    }
    [pscustomobject]@{
        Name = 'core-startup'
        # No pipeline profile: only Core classes/migrations and its production JDBC dependencies.
        Arguments = $common + @('-pl','control-plane','-Pinfrastructure-tests',
            '-Dit.test=PostgresInfrastructureIT','test-compile','failsafe:integration-test','failsafe:verify')
        Suites = @([pscustomobject]@{ Kind='failsafe'; Name='com.aegisflow.core.PostgresInfrastructureIT'; Tests=4 })
    }
}
