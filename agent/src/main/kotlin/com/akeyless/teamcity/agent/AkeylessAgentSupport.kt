package com.akeyless.teamcity.agent

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint

class AkeylessAgentSupport(
    dispatcher: EventDispatcher<AgentLifeCycleListener>
) : AgentLifeCycleAdapter(), PositionAware {

    private val logger = Loggers.AGENT

    init {
        dispatcher.addListener(this)
        logger.info("Akeyless: agent listener registered")
    }

    override fun getOrderId() = "AkeylessSecretsPasswordMasking"
    override fun getConstraint() = PositionConstraint.first()

    override fun agentInitialized(agent: BuildAgent) {
        logger.info("Akeyless: agent support initialized")
    }

    override fun buildStarted(runningBuild: AgentRunningBuild) {
        val buildLogger = runningBuild.buildLogger

        val resolvedParamsCsv = runningBuild.sharedConfigParameters[AkeylessConstants.RESOLVED_PARAMS_KEY]
        if (resolvedParamsCsv.isNullOrBlank()) {
            logger.debug("Akeyless: no resolved params key found, skipping password masking")
            return
        }

        val paramNames = resolvedParamsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (paramNames.isEmpty()) return

        buildLogger.message("Akeyless: masking ${paramNames.size} resolved secret(s)")
        logger.info("Akeyless: masking ${paramNames.size} resolved secret(s) for build ${runningBuild.buildId}")

        val allParams = runningBuild.sharedConfigParameters + runningBuild.sharedBuildParameters.allParameters
        var masked = 0
        for (name in paramNames) {
            val value = allParams[name]
            if (!value.isNullOrBlank()) {
                runningBuild.passwordReplacer.addPassword(value)
                masked++
            } else {
                logger.warn("Akeyless: parameter '$name' not found or blank, cannot mask")
            }
        }
        buildLogger.message("Akeyless: $masked secret(s) registered for masking")
    }
}
