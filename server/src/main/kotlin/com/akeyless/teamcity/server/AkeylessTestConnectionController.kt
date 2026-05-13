package com.akeyless.teamcity.server

import com.akeyless.teamcity.common.AkeylessConstants
import jetbrains.buildServer.controllers.BaseFormXmlController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.XmlResponseUtil
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.serverSide.IOGuard
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import org.jdom.Element
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AkeylessTestConnectionController(
    server: SBuildServer,
    wcm: WebControllerManager,
    private val descriptor: PluginDescriptor
) : BaseFormXmlController(server) {

    private val logger = Loggers.SERVER

    init {
        wcm.registerController("/admin/akeyless-test-connection.html", this)
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) = null

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse, xmlResponse: Element) {
        val propertiesBean = BasePropertiesBean(emptyMap())
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propertiesBean)
        val properties = propertiesBean.properties

        val apiUrl = properties["apiUrl"]?.ifBlank { null } ?: AkeylessConstants.DEFAULT_API_URL
        val authMethod = properties["authMethod"] ?: AkeylessConstants.AUTH_METHOD_ACCESS_KEY
        val accessId = properties["accessId"] ?: ""

        if (accessId.isBlank()) {
            addError(xmlResponse, "Access ID is required")
            return
        }

        val authConfig = mutableMapOf("accessId" to accessId)
        when (authMethod) {
            AkeylessConstants.AUTH_METHOD_ACCESS_KEY -> {
                val accessKey = properties["accessKey"] ?: ""
                if (accessKey.isBlank()) {
                    addError(xmlResponse, "Access Key is required")
                    return
                }
                authConfig["accessKey"] = accessKey
            }
            AkeylessConstants.AUTH_METHOD_K8S -> {
                val k8sConfig = properties["k8sAuthConfigName"] ?: ""
                if (k8sConfig.isBlank()) {
                    addError(xmlResponse, "K8s Auth Config Name is required")
                    return
                }
                authConfig["k8sAuthConfigName"] = k8sConfig
            }
            AkeylessConstants.AUTH_METHOD_CERT -> {
                val certData = properties["certData"] ?: ""
                if (certData.isBlank()) {
                    addError(xmlResponse, "Certificate data is required")
                    return
                }
                authConfig["certData"] = certData
            }
        }

        try {
            val connector = AkeylessConnector(apiUrl, authMethod, authConfig)
            var token: String? = null
            IOGuard.allowNetworkCall<Exception> {
                token = connector.authenticate()
            }
            if (token != null) {
                XmlResponseUtil.writeTestResult(xmlResponse, "")
            } else {
                addError(xmlResponse, "Authentication failed: no token returned")
            }
        } catch (e: Exception) {
            logger.warn("Akeyless test connection failed", e)
            addError(xmlResponse, "Connection failed: ${e.message}")
        }
    }

    private fun addError(xmlResponse: Element, message: String) {
        val errors = Element("errors")
        val error = Element("error")
        error.setAttribute("id", "failedTestConnection")
        error.text = message
        errors.addContent(error)
        xmlResponse.addContent(errors)
    }
}
