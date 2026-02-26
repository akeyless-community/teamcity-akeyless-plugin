package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.serverSide.oauth.OAuthProvider
import jetbrains.buildServer.web.openapi.PluginDescriptor

class AkeylessOAuthProvider(
    private val descriptor: PluginDescriptor
) : OAuthProvider() {

    private val logger = Loggers.SERVER

    override fun getType(): String = AkeylessConstants.PLUGIN_ID

    override fun getDisplayName(): String = AkeylessConstants.PLUGIN_NAME

    override fun describeConnection(connection: OAuthConnectionDescriptor): String {
        val apiUrl = connection.parameters["apiUrl"] ?: AkeylessConstants.DEFAULT_API_URL
        val authMethod = connection.parameters["authMethod"] ?: AkeylessConstants.AUTH_METHOD_ACCESS_KEY
        return "Akeyless at $apiUrl ($authMethod)"
    }

    override fun getDefaultProperties(): Map<String, String> {
        return mapOf(
            "displayName" to "Akeyless",
            "apiUrl" to AkeylessConstants.DEFAULT_API_URL,
            "authMethod" to AkeylessConstants.AUTH_METHOD_ACCESS_KEY
        )
    }

    override fun getEditParametersUrl(): String {
        return descriptor.getPluginResourcesPath("editAkeylessConnection.jsp")
    }

    override fun getPropertiesProcessor(): PropertiesProcessor {
        return object : PropertiesProcessor {
            override fun process(properties: MutableMap<String, String>): Collection<InvalidProperty> {
                logger.info("AkeylessOAuthProvider.process called with ${properties.size} keys: ${properties.keys}")
                val errors = ArrayList<InvalidProperty>()

                if (properties["displayName"].isNullOrBlank()) {
                    errors.add(InvalidProperty("displayName", "Display name should not be empty"))
                }

                val authMethod = properties["authMethod"] ?: AkeylessConstants.AUTH_METHOD_ACCESS_KEY

                when (authMethod) {
                    AkeylessConstants.AUTH_METHOD_ACCESS_KEY -> {
                        removeUnusedAuthProperties(properties, setOf("accessId", "accessKey", "apiUrl", "authMethod", "displayName"))
                        if (properties["accessId"].isNullOrBlank()) {
                            errors.add(InvalidProperty("accessId", "Access ID should not be empty"))
                        }
                        if (properties["accessKey"].isNullOrBlank()) {
                            errors.add(InvalidProperty("accessKey", "Access Key should not be empty"))
                        }
                    }
                    AkeylessConstants.AUTH_METHOD_K8S -> {
                        removeUnusedAuthProperties(properties, setOf("accessId", "k8sAuthConfigName", "apiUrl", "authMethod", "displayName"))
                        if (properties["accessId"].isNullOrBlank()) {
                            errors.add(InvalidProperty("accessId", "Access ID should not be empty"))
                        }
                        if (properties["k8sAuthConfigName"].isNullOrBlank()) {
                            errors.add(InvalidProperty("k8sAuthConfigName", "K8s Auth Config Name should not be empty"))
                        }
                    }
                    AkeylessConstants.AUTH_METHOD_AWS_IAM -> {
                        removeUnusedAuthProperties(properties, setOf("accessId", "apiUrl", "authMethod", "displayName"))
                        if (properties["accessId"].isNullOrBlank()) {
                            errors.add(InvalidProperty("accessId", "Access ID should not be empty"))
                        }
                    }
                    AkeylessConstants.AUTH_METHOD_AZURE_AD -> {
                        removeUnusedAuthProperties(properties, setOf("accessId", "apiUrl", "authMethod", "displayName"))
                        if (properties["accessId"].isNullOrBlank()) {
                            errors.add(InvalidProperty("accessId", "Access ID should not be empty"))
                        }
                    }
                    AkeylessConstants.AUTH_METHOD_GCP -> {
                        removeUnusedAuthProperties(properties, setOf("accessId", "apiUrl", "authMethod", "displayName"))
                        if (properties["accessId"].isNullOrBlank()) {
                            errors.add(InvalidProperty("accessId", "Access ID should not be empty"))
                        }
                    }
                    AkeylessConstants.AUTH_METHOD_CERT -> {
                        removeUnusedAuthProperties(properties, setOf("accessId", "certData", "certFile", "apiUrl", "authMethod", "displayName"))
                        if (properties["accessId"].isNullOrBlank()) {
                            errors.add(InvalidProperty("accessId", "Access ID should not be empty"))
                        }
                    }
                }

                logger.info("AkeylessOAuthProvider.process returning ${errors.size} errors, remaining keys: ${properties.keys}")
                return errors
            }

            private fun removeUnusedAuthProperties(properties: MutableMap<String, String>, keysToKeep: Set<String>) {
                val keysToRemove = properties.keys.filter { it !in keysToKeep }
                keysToRemove.forEach { properties.remove(it) }
            }
        }
    }
}
