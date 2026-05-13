package com.akeyless.teamcity

import com.akeyless.teamcity.common.AkeylessConstants
import com.akeyless.teamcity.server.AkeylessBuildStartProcessor
import com.akeyless.teamcity.server.AkeylessConnector
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AkeylessPluginTest {

    // ---------------------------------------------------------------
    // Reference parsing: akeyless:/path and akeyless:connId:/path
    // ---------------------------------------------------------------

    @Test
    fun `parseReference - simple path with leading slash`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:/my/secret")
        assertNull(ref.connectionId)
        assertEquals("/my/secret", ref.secretPath)
    }

    @Test
    fun `parseReference - deeply nested path`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:/production/database/password")
        assertNull(ref.connectionId)
        assertEquals("/production/database/password", ref.secretPath)
    }

    @Test
    fun `parseReference - with connection ID`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:prod:/my/secret")
        assertEquals("prod", ref.connectionId)
        assertEquals("/my/secret", ref.secretPath)
    }

    @Test
    fun `parseReference - with connection ID and nested path`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:staging:/env/api-key")
        assertEquals("staging", ref.connectionId)
        assertEquals("/env/api-key", ref.secretPath)
    }

    @Test
    fun `parseReference - path without leading slash defaults to no connection`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:some-path-no-slash")
        assertNull(ref.connectionId)
        assertEquals("some-path-no-slash", ref.secretPath)
    }

    @Test
    fun `parseReference - connection ID with path without leading slash`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:myconn:secret-name")
        assertEquals("myconn", ref.connectionId)
        assertEquals("secret-name", ref.secretPath)
    }

    @Test
    fun `parseReference - empty after prefix`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:")
        assertNull(ref.connectionId)
        assertEquals("", ref.secretPath)
    }

    @Test
    fun `parseReference - only connection ID with trailing colon but no path`() {
        val ref = AkeylessBuildStartProcessor.parseReference("akeyless:connId:")
        assertNull(ref.connectionId)
        assertEquals("connId:", ref.secretPath)
    }

    // ---------------------------------------------------------------
    // Auth config extraction
    // ---------------------------------------------------------------

    @Test
    fun `extractAuthConfig - access key method extracts accessId and accessKey`() {
        val props = mapOf(
            "accessId" to "p-123",
            "accessKey" to "secret-key",
            "k8sAuthConfigName" to "should-be-ignored",
            "apiUrl" to "https://api.akeyless.io"
        )
        val config = AkeylessBuildStartProcessor.extractAuthConfig(props, AkeylessConstants.AUTH_METHOD_ACCESS_KEY)
        assertEquals("p-123", config["accessId"])
        assertEquals("secret-key", config["accessKey"])
        assertNull(config["k8sAuthConfigName"])
        assertNull(config["apiUrl"])
    }

    @Test
    fun `extractAuthConfig - k8s method extracts accessId and k8sAuthConfigName`() {
        val props = mapOf(
            "accessId" to "p-456",
            "k8sAuthConfigName" to "my-k8s-config",
            "accessKey" to "should-be-ignored"
        )
        val config = AkeylessBuildStartProcessor.extractAuthConfig(props, AkeylessConstants.AUTH_METHOD_K8S)
        assertEquals("p-456", config["accessId"])
        assertEquals("my-k8s-config", config["k8sAuthConfigName"])
        assertNull(config["accessKey"])
    }

    @Test
    fun `extractAuthConfig - AWS IAM only extracts accessId`() {
        val props = mapOf(
            "accessId" to "p-789",
            "accessKey" to "should-be-ignored"
        )
        val config = AkeylessBuildStartProcessor.extractAuthConfig(props, AkeylessConstants.AUTH_METHOD_AWS_IAM)
        assertEquals("p-789", config["accessId"])
        assertNull(config["accessKey"])
        assertEquals(1, config.size)
    }

    @Test
    fun `extractAuthConfig - Azure AD only extracts accessId`() {
        val props = mapOf("accessId" to "p-azure")
        val config = AkeylessBuildStartProcessor.extractAuthConfig(props, AkeylessConstants.AUTH_METHOD_AZURE_AD)
        assertEquals("p-azure", config["accessId"])
        assertEquals(1, config.size)
    }

    @Test
    fun `extractAuthConfig - GCP only extracts accessId`() {
        val props = mapOf("accessId" to "p-gcp")
        val config = AkeylessBuildStartProcessor.extractAuthConfig(props, AkeylessConstants.AUTH_METHOD_GCP)
        assertEquals("p-gcp", config["accessId"])
        assertEquals(1, config.size)
    }

    @Test
    fun `extractAuthConfig - cert method extracts accessId and certData`() {
        val props = mapOf(
            "accessId" to "p-cert",
            "certData" to "-----BEGIN CERTIFICATE-----\ndata\n-----END CERTIFICATE-----"
        )
        val config = AkeylessBuildStartProcessor.extractAuthConfig(props, AkeylessConstants.AUTH_METHOD_CERT)
        assertEquals("p-cert", config["accessId"])
        assertNotNull(config["certData"])
        assertEquals(2, config.size)
    }

    @Test
    fun `extractAuthConfig - missing accessId produces empty config`() {
        val config = AkeylessBuildStartProcessor.extractAuthConfig(emptyMap(), AkeylessConstants.AUTH_METHOD_ACCESS_KEY)
        assertTrue(config.isEmpty())
    }

    // ---------------------------------------------------------------
    // API URL validation
    // ---------------------------------------------------------------

    @Test
    fun `validateApiUrl - valid HTTPS URL passes`() {
        AkeylessConnector.validateApiUrl("https://api.akeyless.io")
    }

    @Test
    fun `validateApiUrl - valid HTTPS URL with path passes`() {
        AkeylessConnector.validateApiUrl("https://gw.mycompany.com/v2")
    }

    @Test
    fun `validateApiUrl - trailing slash is trimmed`() {
        AkeylessConnector.validateApiUrl("https://api.akeyless.io/")
    }

    @Test
    fun `validateApiUrl - HTTP localhost is allowed for development`() {
        AkeylessConnector.validateApiUrl("http://localhost:8080")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateApiUrl - rejects invalid URL`() {
        AkeylessConnector.validateApiUrl("not-a-url")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateApiUrl - rejects FTP scheme`() {
        AkeylessConnector.validateApiUrl("ftp://api.akeyless.io")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateApiUrl - rejects private IP 127_0_0_1`() {
        AkeylessConnector.validateApiUrl("https://127.0.0.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateApiUrl - rejects private IP 10_x`() {
        AkeylessConnector.validateApiUrl("https://10.0.0.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateApiUrl - rejects private IP 192_168_x`() {
        AkeylessConnector.validateApiUrl("https://192.168.1.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateApiUrl - rejects 0_0_0_0`() {
        AkeylessConnector.validateApiUrl("https://0.0.0.0")
    }

    // ---------------------------------------------------------------
    // Secret path validation
    // ---------------------------------------------------------------

    @Test
    fun `validateSecretPath - valid path passes`() {
        AkeylessConnector.validateSecretPath("/my/secret/path")
    }

    @Test
    fun `validateSecretPath - path without leading slash passes`() {
        AkeylessConnector.validateSecretPath("my-secret")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateSecretPath - blank path rejected`() {
        AkeylessConnector.validateSecretPath("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateSecretPath - whitespace-only path rejected`() {
        AkeylessConnector.validateSecretPath("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateSecretPath - path traversal rejected`() {
        AkeylessConnector.validateSecretPath("/my/../secret")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validateSecretPath - control characters rejected`() {
        AkeylessConnector.validateSecretPath("/my/secret\u0000path")
    }

    // ---------------------------------------------------------------
    // Constants sanity checks
    // ---------------------------------------------------------------

    @Test
    fun `constants - all auth method values are distinct`() {
        val methods = listOf(
            AkeylessConstants.AUTH_METHOD_ACCESS_KEY,
            AkeylessConstants.AUTH_METHOD_K8S,
            AkeylessConstants.AUTH_METHOD_AWS_IAM,
            AkeylessConstants.AUTH_METHOD_AZURE_AD,
            AkeylessConstants.AUTH_METHOD_GCP,
            AkeylessConstants.AUTH_METHOD_CERT
        )
        assertEquals(methods.size, methods.toSet().size, "Auth method constants must be unique")
    }

    @Test
    fun `constants - plugin ID is not blank`() {
        assertTrue(AkeylessConstants.PLUGIN_ID.isNotBlank())
    }

    @Test
    fun `constants - default API URL is HTTPS`() {
        assertTrue(AkeylessConstants.DEFAULT_API_URL.startsWith("https://"))
    }
}
