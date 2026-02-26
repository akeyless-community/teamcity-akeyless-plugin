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

    override fun getRemoteParameterType(): String = AkeylessConstants.PARAM_TYPE_AKEYLESS

    override fun createRemoteParameter(build: SBuild, parameter: Parameter): RemoteParameter {
        val buildType = build.buildType
        val project = buildType?.project

        var secretValue = parameter.value

        if (project != null && secretValue.startsWith("akeyless:")) {
            val secretPath = secretValue.substringAfter("akeyless:")

            val connectionFeature = try {
                project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE)
                    .filter { it.parameters[OAuthConstants.OAUTH_TYPE_PARAM] == AkeylessConstants.PLUGIN_ID }
                    .firstOrNull()
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
                            logger.warn("Failed to retrieve Akeyless secret: $secretPath")
                        }
                    } else {
                        logger.error("Failed to authenticate with Akeyless")
                    }
                } catch (e: Exception) {
                    logger.error("Error resolving Akeyless secret: $secretPath", e)
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
        when (authMethod) {
            AkeylessConstants.AUTH_METHOD_ACCESS_KEY -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["accessKey"]?.let { authConfig["accessKey"] = it }
            }
            AkeylessConstants.AUTH_METHOD_K8S -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["k8sAuthConfigName"]?.let { authConfig["k8sAuthConfigName"] = it }
                properties["k8sServiceAccountToken"]?.let { authConfig["k8sServiceAccountToken"] = it }
            }
            AkeylessConstants.AUTH_METHOD_AWS_IAM -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["awsIamRole"]?.let { authConfig["awsIamRole"] = it }
                properties["awsIamAccessKeyId"]?.let { authConfig["awsIamAccessKeyId"] = it }
                properties["awsIamSecretAccessKey"]?.let { authConfig["awsIamSecretAccessKey"] = it }
            }
            AkeylessConstants.AUTH_METHOD_AZURE_AD -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["azureAdObjectId"]?.let { authConfig["azureAdObjectId"] = it }
            }
            AkeylessConstants.AUTH_METHOD_GCP -> {
                properties["accessId"]?.let { authConfig["accessId"] = it }
                properties["gcpAudience"]?.let { authConfig["gcpAudience"] = it }
                properties["gcpJwt"]?.let { authConfig["gcpJwt"] = it }
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
