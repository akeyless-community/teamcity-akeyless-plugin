package com.akeyless.teamcity.agent

import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.log.Loggers
import org.springframework.stereotype.Component

/**
 * Agent-side support for Akeyless integration
 * Most logic is handled server-side, but this can be used for agent-specific tasks
 */
@Component
class AkeylessAgentSupport : AgentLifeCycleAdapter() {
    
    private val logger = Loggers.AGENT
    
    override fun agentInitialized(agent: BuildAgent) {
        logger.info("Akeyless agent support initialized")
    }
}
