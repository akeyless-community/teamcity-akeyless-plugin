package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.*
import io.akeyless.client.ApiClient
import io.akeyless.client.ApiException
import io.akeyless.client.Configuration
import io.akeyless.client.api.V2Api
import io.akeyless.client.model.Auth
import io.akeyless.client.model.DescribeItem
import io.akeyless.client.model.GetDynamicSecretValue
import io.akeyless.client.model.GetRotatedSecretValue
import io.akeyless.client.model.GetSecretValue
import io.akeyless.cloudid.CloudProviderFactory
import jetbrains.buildServer.log.Loggers

class AkeylessConnector(
    private val apiUrl: String,
    private val authMethod: String,
    private val authConfig: Map<String, String>
) {
    private val logger = Loggers.SERVER
    private val api: V2Api

    init {
        val client: ApiClient = Configuration.getDefaultApiClient()
        client.basePath = apiUrl.trimEnd('/')
        api = V2Api(client)
    }

    fun authenticate(): String? {
        try {
            val auth = Auth()
            auth.accessType(authMethod)

            when (authMethod) {
                AkeylessConstants.AUTH_METHOD_ACCESS_KEY -> {
                    authConfig["accessId"]?.let { auth.accessId(it) }
                    authConfig["accessKey"]?.let { auth.accessKey(it) }
                }
                AkeylessConstants.AUTH_METHOD_K8S -> {
                    authConfig["accessId"]?.let { auth.accessId(it) }
                    authConfig["k8sAuthConfigName"]?.let { auth.k8sAuthConfigName(it) }
                }
                AkeylessConstants.AUTH_METHOD_AWS_IAM,
                AkeylessConstants.AUTH_METHOD_AZURE_AD,
                AkeylessConstants.AUTH_METHOD_GCP -> {
                    authConfig["accessId"]?.let { auth.accessId(it) }
                    if (authMethod == AkeylessConstants.AUTH_METHOD_GCP) {
                        auth.gcpAudience("akeyless.io")
                    }
                    val provider = CloudProviderFactory.getCloudIdProvider(authMethod)
                    val cloudId = provider.cloudId
                    logger.info("Akeyless: generated $authMethod cloud-id (length=${cloudId.length})")
                    auth.cloudId(cloudId)
                }
                AkeylessConstants.AUTH_METHOD_CERT -> {
                    authConfig["accessId"]?.let { auth.accessId(it) }
                    authConfig["certData"]?.let { auth.certData(it) }
                }
            }

            val result = api.auth(auth)
            val token = result.token

            if (token.isNullOrBlank()) {
                logger.error("Akeyless authentication response missing token")
                return null
            }

            logger.info("Successfully authenticated with Akeyless")
            return token
        } catch (e: ApiException) {
            logger.error("Akeyless authentication failed: code=${e.code}, body=${e.responseBody}", e)
            return null
        } catch (e: Exception) {
            logger.error("Error authenticating with Akeyless", e)
            return null
        }
    }

    fun resolveSecret(secretName: String, token: String): String? {
        val itemType = describeItemType(secretName, token)
        logger.info("Akeyless: item '$secretName' type=$itemType")

        return when {
            itemType.equals("DYNAMIC_SECRET", ignoreCase = true) -> getDynamicSecretValue(secretName, token)
            itemType.equals("ROTATED_SECRET", ignoreCase = true) -> getRotatedSecretValue(secretName, token)
            else -> getStaticSecretValue(secretName, token)
        }
    }

    private fun describeItemType(secretName: String, token: String): String {
        try {
            val body = DescribeItem()
            body.name(secretName)
            body.token(token)

            val result = api.describeItem(body)
            val itemType = result.itemType ?: "STATIC_SECRET"
            return itemType
        } catch (e: ApiException) {
            logger.warn("Akeyless: could not describe item '$secretName' (code=${e.code}), defaulting to static secret")
            return "STATIC_SECRET"
        } catch (e: Exception) {
            logger.warn("Akeyless: error describing item '$secretName', defaulting to static secret", e)
            return "STATIC_SECRET"
        }
    }

    private fun getStaticSecretValue(secretName: String, token: String): String? {
        try {
            val body = GetSecretValue()
            body.names(listOf(secretName))
            body.token(token)

            logger.info("Akeyless: fetching static secret '$secretName'")
            val result = api.getSecretValue(body)

            val rawValue = result[secretName] ?: result[secretName.trimStart('/')]
            if (rawValue != null) {
                val secretValue = rawValue.toString()
                logger.info("Akeyless: resolved static secret '$secretName' (length=${secretValue.length})")
                return secretValue
            }

            logger.warn("Akeyless: static secret '$secretName' not found in response keys: ${result.keys}")
            return null
        } catch (e: ApiException) {
            logger.error("Akeyless API error getting static secret '$secretName': code=${e.code}, body=${e.responseBody}", e)
            return null
        } catch (e: Exception) {
            logger.error("Error getting static secret '$secretName'", e)
            return null
        }
    }

    private fun getDynamicSecretValue(secretName: String, token: String): String? {
        try {
            val body = GetDynamicSecretValue()
            body.name(secretName)
            body.token(token)

            logger.info("Akeyless: fetching dynamic secret '$secretName'")
            val result = api.getDynamicSecretValue(body)

            if (result != null) {
                val secretValue = result.toString()
                logger.info("Akeyless: resolved dynamic secret '$secretName' (length=${secretValue.length})")
                return secretValue
            }

            logger.warn("Akeyless: dynamic secret '$secretName' returned null")
            return null
        } catch (e: ApiException) {
            logger.error("Akeyless API error getting dynamic secret '$secretName': code=${e.code}, body=${e.responseBody}", e)
            return null
        } catch (e: Exception) {
            logger.error("Error getting dynamic secret '$secretName'", e)
            return null
        }
    }

    private fun getRotatedSecretValue(secretName: String, token: String): String? {
        try {
            val body = GetRotatedSecretValue()
            body.names(secretName)
            body.token(token)

            logger.info("Akeyless: fetching rotated secret '$secretName'")
            val result = api.getRotatedSecretValue(body)

            if (result != null) {
                val value = result["value"] ?: result[secretName] ?: result[secretName.trimStart('/')]
                if (value != null) {
                    val secretValue = value.toString()
                    logger.info("Akeyless: resolved rotated secret '$secretName' (length=${secretValue.length})")
                    return secretValue
                }
                // If specific key not found, return the whole response as JSON
                val secretValue = result.toString()
                logger.info("Akeyless: resolved rotated secret '$secretName' as full response (length=${secretValue.length})")
                return secretValue
            }

            logger.warn("Akeyless: rotated secret '$secretName' returned null")
            return null
        } catch (e: ApiException) {
            logger.error("Akeyless API error getting rotated secret '$secretName': code=${e.code}, body=${e.responseBody}", e)
            return null
        } catch (e: Exception) {
            logger.error("Error getting rotated secret '$secretName'", e)
            return null
        }
    }
}
