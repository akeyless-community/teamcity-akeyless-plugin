package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.oauth.OAuthConstants

class AkeylessBuildStartProcessor : BuildStartContextProcessor {

    private val logger = Loggers.SERVER

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build
        val buildType = build.buildType ?: return
        val project = buildType.project

        val connectionFeature = try {
            project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE)
                .firstOrNull { it.parameters[OAuthConstants.OAUTH_TYPE_PARAM] == AkeylessConstants.PLUGIN_ID }
        } catch (e: Exception) {
            logger.warn("Could not access Akeyless project connections", e)
            null
        }

        if (connectionFeature == null) return

        val allParams = mutableMapOf<String, String>()
        allParams.putAll(context.sharedParameters)
        allParams.putAll(build.buildOwnParameters)

        val akeylessRefs = allParams.filter { it.value.startsWith("akeyless:") }
        if (akeylessRefs.isEmpty()) return

        logger.info("Akeyless: resolving ${akeylessRefs.size} secret references for build ${build.buildId}")

        val apiUrl = connectionFeature.parameters["apiUrl"] ?: AkeylessConstants.DEFAULT_API_URL
        val authMethod = connectionFeature.parameters["authMethod"] ?: AkeylessConstants.AUTH_METHOD_ACCESS_KEY
        val authConfig = extractAuthConfig(connectionFeature.parameters, authMethod)

        val connector = AkeylessConnector(apiUrl, authMethod, authConfig)
        val token = try {
            connector.authenticate()
        } catch (e: Exception) {
            logger.error("Akeyless: authentication failed for build ${build.buildId}", e)
            return
        }

        if (token == null) {
            logger.error("Akeyless: authentication returned null token for build ${build.buildId}")
            return
        }

        logger.info("Akeyless: authenticated successfully, resolving secrets")

        akeylessRefs.forEach { (paramName, paramValue) ->
            val secretPath = paramValue.substringAfter("akeyless:")
            try {
                val secretValue = connector.resolveSecret(secretPath, token)
                if (secretValue != null) {
                    context.addSharedParameter(paramName, secretValue)
                    logger.info("Akeyless: resolved secret for parameter '$paramName'")
                } else {
                    logger.warn("Akeyless: failed to retrieve secret '$secretPath' for parameter '$paramName'")
                }
            } catch (e: Exception) {
                logger.error("Akeyless: error resolving secret '$secretPath' for parameter '$paramName'", e)
            }
        }
    }

    private fun extractAuthConfig(properties: Map<String, String>, authMethod: String): Map<String, String> {
        val authConfig = mutableMapOf<String, String>()
        when (authMethod) {
            AkeylessConstants.AUTH_METHOD_ACCESS_KEY -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["accessKey"]?.let { authConfig["accessKey"] = it }
            }
            AkeylessConstants.AUTH_METHOD_K8S -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["k8sAuthConfigName"]?.let { authConfig["k8sAuthConfigName"] = it }
            }
            AkeylessConstants.AUTH_METHOD_AWS_IAM -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
            }
            AkeylessConstants.AUTH_METHOD_AZURE_AD -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
            }
            AkeylessConstants.AUTH_METHOD_GCP -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
            }
            AkeylessConstants.AUTH_METHOD_CERT -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["certData"]?.let { authConfig["certData"] = it }
                properties["certFile"]?.let { authConfig["certFile"] = it }
            }
        }
        return authConfig
    }
}
