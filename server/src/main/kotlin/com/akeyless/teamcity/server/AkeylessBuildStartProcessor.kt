package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.SimpleParameter
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import jetbrains.buildServer.serverSide.parameters.types.PasswordsProvider
import java.util.concurrent.ConcurrentHashMap

class AkeylessBuildStartProcessor : BuildStartContextProcessor, PasswordsProvider {

    private val logger = Loggers.SERVER

    private val resolvedSecrets = ConcurrentHashMap<Long, MutableList<Parameter>>()

    data class ParsedRef(val connectionId: String?, val secretPath: String)

    companion object {
        const val AKEYLESS_PREFIX = "akeyless:"

        fun parseReference(value: String): ParsedRef {
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

        fun extractAuthConfig(properties: Map<String, String>, authMethod: String): Map<String, String> {
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

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build
        val buildType = build.buildType ?: return
        val project = buildType.project

        val akeylessConnections = try {
            project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE)
                .filter { it.parameters[OAuthConstants.OAUTH_TYPE_PARAM] == AkeylessConstants.PLUGIN_ID }
        } catch (e: Exception) {
            logger.warn("Could not access Akeyless project connections", e)
            emptyList()
        }

        if (akeylessConnections.isEmpty()) return

        val allParams = mutableMapOf<String, String>()
        allParams.putAll(context.sharedParameters)
        allParams.putAll(build.buildOwnParameters)

        val akeylessRefs = allParams.filter { it.value.startsWith(AKEYLESS_PREFIX) }
        if (akeylessRefs.isEmpty()) return

        logger.info("Akeyless: resolving ${akeylessRefs.size} secret references for build ${build.buildId}")

        val groupedRefs = mutableMapOf<String?, MutableList<Pair<String, ParsedRef>>>()
        for ((paramName, paramValue) in akeylessRefs) {
            val parsed = parseReference(paramValue)
            if (parsed.secretPath.isBlank()) {
                logger.warn("Akeyless: empty secret path for parameter '$paramName'")
                continue
            }
            groupedRefs.getOrPut(parsed.connectionId) { mutableListOf() }.add(paramName to parsed)
        }

        val passwordParams = mutableListOf<Parameter>()
        val errors = mutableListOf<String>()

        for ((connId, refs) in groupedRefs) {
            val connection = findConnection(akeylessConnections, connId)
            if (connection == null) {
                val label = connId ?: "default"
                val msg = "Akeyless: no connection found with id '$label'"
                logger.error("$msg for build ${build.buildId}")
                errors.add(msg)
                continue
            }

            val apiUrl = connection.parameters["apiUrl"] ?: AkeylessConstants.DEFAULT_API_URL
            val authMethod = connection.parameters["authMethod"] ?: AkeylessConstants.AUTH_METHOD_ACCESS_KEY
            val authConfig = extractAuthConfig(connection.parameters, authMethod)

            val connector = AkeylessConnector(apiUrl, authMethod, authConfig)
            val token = try {
                connector.authenticate()
            } catch (e: Exception) {
                val msg = "Akeyless: authentication failed for connection '${connId ?: "default"}': ${e.message}"
                logger.error(msg, e)
                errors.add(msg)
                continue
            }

            if (token == null) {
                val msg = "Akeyless: authentication returned null token for connection '${connId ?: "default"}'"
                logger.error(msg)
                errors.add(msg)
                continue
            }

            logger.info("Akeyless: authenticated successfully for connection '${connId ?: "default"}'")

            for ((paramName, parsed) in refs) {
                try {
                    val secretValue = connector.resolveSecret(parsed.secretPath, token)
                    if (secretValue != null) {
                        context.addSharedParameter(paramName, secretValue)
                        passwordParams.add(SimpleParameter(paramName, secretValue))
                        logger.info("Akeyless: resolved secret for parameter '$paramName'")
                    } else {
                        val msg = "Akeyless: secret not found at path '${parsed.secretPath}' (parameter '$paramName')"
                        logger.error(msg)
                        errors.add(msg)
                        context.addSharedParameter(paramName, "")
                    }
                } catch (e: Exception) {
                    val msg = "Akeyless: error resolving secret '${parsed.secretPath}' (parameter '$paramName'): ${e.message}"
                    logger.error(msg, e)
                    errors.add(msg)
                    context.addSharedParameter(paramName, "")
                }
            }
        }

        if (errors.isNotEmpty()) {
            val description = errors.joinToString("; ")
            build.addBuildProblem(
                BuildProblemData.createBuildProblem(
                    "akeyless_secret_resolution_${build.buildId}",
                    "AkeylessSecretResolution",
                    description
                )
            )
        }

        if (passwordParams.isNotEmpty()) {
            resolvedSecrets[build.buildId] = passwordParams
            val resolvedNames = passwordParams.map { it.name }.joinToString(",")
            context.addSharedParameter(AkeylessConstants.RESOLVED_PARAMS_KEY, resolvedNames)
        }
    }

    override fun getPasswordParameters(build: SBuild): Collection<Parameter> {
        return resolvedSecrets.remove(build.buildId) ?: emptyList()
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

}
