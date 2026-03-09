package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.serverSide.parameters.AbstractBuildParametersProvider

class AkeylessParameterProvider : AbstractBuildParametersProvider() {

    private val logger = Loggers.SERVER

    companion object {
        private const val AKEYLESS_PREFIX = "akeyless:"

        fun isFeatureEnabled(build: SBuild): Boolean {
            val buildType = build.buildType ?: return false
            val project = buildType.project
            return project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE).any {
                AkeylessConstants.PLUGIN_ID == it.parameters[OAuthConstants.OAUTH_TYPE_PARAM]
            }
        }
    }

    override fun getParametersAvailableOnAgent(build: SBuild): Collection<String> {
        build.buildType ?: return emptyList()
        if (build.isFinished) return emptyList()
        if (!isFeatureEnabled(build)) return emptyList()

        val exposed = mutableSetOf<String>()
        val parameters = build.buildOwnParameters

        parameters.forEach { (paramName, paramValue) ->
            if (paramValue.startsWith(AKEYLESS_PREFIX)) {
                exposed.add(paramName)
            }
        }

        return exposed
    }
}
