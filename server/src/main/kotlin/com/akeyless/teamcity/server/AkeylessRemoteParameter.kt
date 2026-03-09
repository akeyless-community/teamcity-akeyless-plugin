package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameter
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameterProvider
import jetbrains.buildServer.log.Loggers

class AkeylessRemoteParameter : RemoteParameterProvider {

    private val logger = Loggers.SERVER

    companion object {
        private const val AKEYLESS_PREFIX = "akeyless:"
    }

    override fun getRemoteParameterType(): String = AkeylessConstants.PARAM_TYPE_AKEYLESS

    override fun createRemoteParameter(build: SBuild, parameter: Parameter): RemoteParameter {
        val buildType = build.buildType
        val project = buildType?.project

        var secretValue = parameter.value

        if (project != null && secretValue.startsWith(AKEYLESS_PREFIX)) {
            val secretPath = secretValue.substringAfter(AKEYLESS_PREFIX)
            if (secretPath.isBlank()) {
                logger.warn("Akeyless: empty secret path in parameter '${parameter.name}'")
            } else {
                val connectionFeature = try {
                    project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE)
                        .firstOrNull { it.parameters[OAuthConstants.OAUTH_TYPE_PARAM] == AkeylessConstants.PLUGIN_ID }
                } catch (e: Exception) {
                    logger.warn("Could not access Akeyless connections", e)
                    null
                }

                if (connectionFeature != null) {
                    val apiUrl = connectionFeature.parameters["apiUrl"] ?: AkeylessConstants.DEFAULT_API_URL
                    val authMethod = connectionFeature.parameters["authMethod"] ?: AkeylessConstants.AUTH_METHOD_ACCESS_KEY
                    val authConfig = extractAuthConfig(connectionFeature.parameters, authMethod)

                    try {
                        val connector = AkeylessConnector(apiUrl, authMethod, authConfig)
                        val token = connector.authenticate()
                        if (token != null) {
                            val resolved = connector.resolveSecret(secretPath, token)
                            if (resolved != null) {
                                secretValue = resolved
                            } else {
                                logger.warn("Failed to retrieve Akeyless secret for parameter '${parameter.name}'")
                            }
                        } else {
                            logger.error("Failed to authenticate with Akeyless")
                        }
                    } catch (e: Exception) {
                        logger.error("Error resolving Akeyless secret for parameter '${parameter.name}'", e)
                    }
                }
            }
        }

        val finalValue = secretValue
        val paramName = parameter.name
        return object : RemoteParameter {
            override fun getValue(): String = finalValue
            override fun isSecret(): Boolean = true
            override fun getName(): String = paramName
        }
    }

    private fun extractAuthConfig(properties: Map<String, String>, authMethod: String): Map<String, String> {
        val authConfig = mutableMapOf<String, String>()
        properties["accessId"]?.let { authConfig["accessId"] = it }

        when (authMethod) {
            AkeylessConstants.AUTH_METHOD_ACCESS_KEY -> {
                properties["accessKey"]?.let { authConfig["accessKey"] = it }
            }
            AkeylessConstants.AUTH_METHOD_K8S -> {
                properties["k8sAuthConfigName"]?.let { authConfig["k8sAuthConfigName"] = it }
            }
            AkeylessConstants.AUTH_METHOD_CERT -> {
                properties["certData"]?.let { authConfig["certData"] = it }
            }
        }
        return authConfig
    }
}
