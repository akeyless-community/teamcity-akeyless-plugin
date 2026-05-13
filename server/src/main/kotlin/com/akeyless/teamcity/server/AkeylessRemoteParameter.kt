package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor
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
            val parsed = parseReference(secretValue)
            if (parsed.secretPath.isBlank()) {
                logger.warn("Akeyless: empty secret path in parameter '${parameter.name}'")
            } else {
                val connections = try {
                    project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE)
                        .filter { it.parameters[OAuthConstants.OAUTH_TYPE_PARAM] == AkeylessConstants.PLUGIN_ID }
                } catch (e: Exception) {
                    logger.warn("Could not access Akeyless connections", e)
                    emptyList()
                }

                val connection = findConnection(connections, parsed.connectionId)

                if (connection != null) {
                    val apiUrl = connection.parameters["apiUrl"] ?: AkeylessConstants.DEFAULT_API_URL
                    val authMethod = connection.parameters["authMethod"] ?: AkeylessConstants.AUTH_METHOD_ACCESS_KEY
                    val authConfig = extractAuthConfig(connection.parameters, authMethod)

                    try {
                        val connector = AkeylessConnector(apiUrl, authMethod, authConfig)
                        val token = connector.authenticate()
                        if (token != null) {
                            val resolved = connector.resolveSecret(parsed.secretPath, token)
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
                } else {
                    logger.warn("Akeyless: no matching connection found for parameter '${parameter.name}'")
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

    private data class ParsedRef(val connectionId: String?, val secretPath: String)

    private fun parseReference(value: String): ParsedRef {
        val afterPrefix = value.substringAfter(AKEYLESS_PREFIX)
        if (afterPrefix.startsWith("/")) {
            return ParsedRef(null, afterPrefix)
        }
        val colonIdx = afterPrefix.indexOf(':')
        if (colonIdx > 0 && colonIdx < afterPrefix.length - 1) {
            return ParsedRef(afterPrefix.substring(0, colonIdx), afterPrefix.substring(colonIdx + 1))
        }
        return ParsedRef(null, afterPrefix)
    }

    private fun findConnection(
        connections: List<SProjectFeatureDescriptor>,
        connectionId: String?
    ): SProjectFeatureDescriptor? {
        if (connectionId == null) {
            return connections.firstOrNull()
        }
        return connections.firstOrNull {
            it.parameters["connectionId"] == connectionId
        } ?: connections.firstOrNull {
            it.parameters["displayName"] == connectionId
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
