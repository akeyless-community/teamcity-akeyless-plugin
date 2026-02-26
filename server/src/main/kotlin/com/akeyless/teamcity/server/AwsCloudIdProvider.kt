package com.akeyless.teamcity.server

import jetbrains.buildServer.log.Loggers
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates an Akeyless-compatible AWS cloud-id using only Java standard library.
 * Matches the format produced by https://github.com/akeylesslabs/akeyless-go-cloud-id
 */
object AwsCloudIdProvider {

    private val logger = Loggers.SERVER
    private const val STS_HOST = "sts.amazonaws.com"
    private const val STS_URL = "https://sts.amazonaws.com/"
    private const val STS_BODY = "Action=GetCallerIdentity&Version=2011-06-15"
    private const val CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
    // Go library hardcodes us-east-1 for the STS global endpoint
    private const val REGION = "us-east-1"
    private const val SERVICE = "sts"

    data class AwsCredentials(
        val accessKeyId: String,
        val secretAccessKey: String,
        val sessionToken: String?
    )

    fun getCloudId(): String {
        val creds = getCredentials()
        logger.info("Akeyless: obtained AWS credentials for access key ${creds.accessKeyId.take(8)}...")
        return buildCloudId(creds)
    }

    private fun getCredentials(): AwsCredentials {
        val envAccessKey = System.getenv("AWS_ACCESS_KEY_ID")
        val envSecretKey = System.getenv("AWS_SECRET_ACCESS_KEY")
        if (!envAccessKey.isNullOrBlank() && !envSecretKey.isNullOrBlank()) {
            logger.info("Akeyless: using AWS credentials from environment variables")
            return AwsCredentials(envAccessKey, envSecretKey, System.getenv("AWS_SESSION_TOKEN"))
        }

        val ecsUri = System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI")
        if (!ecsUri.isNullOrBlank()) {
            logger.info("Akeyless: fetching AWS credentials from ECS task role")
            return fetchCredentialsFromUrl("http://169.254.170.2$ecsUri", null)
        }

        val ecsFullUri = System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI")
        if (!ecsFullUri.isNullOrBlank()) {
            logger.info("Akeyless: fetching AWS credentials from ECS full URI")
            return fetchCredentialsFromUrl(ecsFullUri, System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN"))
        }

        val webIdentityTokenFile = System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE")
        val roleArn = System.getenv("AWS_ROLE_ARN")
        if (!webIdentityTokenFile.isNullOrBlank() && !roleArn.isNullOrBlank()) {
            logger.info("Akeyless: fetching AWS credentials from web identity token (IRSA)")
            return fetchWebIdentityCredentials(webIdentityTokenFile, roleArn)
        }

        logger.info("Akeyless: fetching AWS credentials from EC2 instance metadata (IMDSv2)")
        return fetchEc2Credentials()
    }

    private fun fetchCredentialsFromUrl(url: String, authToken: String?): AwsCredentials {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        authToken?.let { conn.setRequestProperty("Authorization", it) }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return parseCredentialsJson(body)
    }

    private fun fetchWebIdentityCredentials(tokenFile: String, roleArn: String): AwsCredentials {
        val token = java.io.File(tokenFile).readText().trim()
        val sessionName = "akeyless-teamcity-${System.currentTimeMillis()}"
        val stsUrl = "https://sts.$REGION.amazonaws.com/" +
            "?Action=AssumeRoleWithWebIdentity" +
            "&Version=2011-06-15" +
            "&RoleArn=${java.net.URLEncoder.encode(roleArn, "UTF-8")}" +
            "&RoleSessionName=${java.net.URLEncoder.encode(sessionName, "UTF-8")}" +
            "&WebIdentityToken=${java.net.URLEncoder.encode(token, "UTF-8")}"

        val conn = URI(stsUrl).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val respBody = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        return AwsCredentials(
            extractXmlValue(respBody, "AccessKeyId"),
            extractXmlValue(respBody, "SecretAccessKey"),
            extractXmlValue(respBody, "SessionToken")
        )
    }

    private fun fetchEc2Credentials(): AwsCredentials {
        val tokenConn = URI("http://169.254.169.254/latest/api/token").toURL().openConnection() as HttpURLConnection
        tokenConn.requestMethod = "PUT"
        tokenConn.setRequestProperty("X-aws-ec2-metadata-token-ttl-seconds", "21600")
        tokenConn.connectTimeout = 2000
        tokenConn.readTimeout = 2000
        val imdsToken = tokenConn.inputStream.bufferedReader().readText()
        tokenConn.disconnect()

        val roleConn = URI("http://169.254.169.254/latest/meta-data/iam/security-credentials/").toURL().openConnection() as HttpURLConnection
        roleConn.setRequestProperty("X-aws-ec2-metadata-token", imdsToken)
        roleConn.connectTimeout = 2000
        roleConn.readTimeout = 2000
        val roleName = roleConn.inputStream.bufferedReader().readText().trim()
        roleConn.disconnect()

        val credsConn = URI("http://169.254.169.254/latest/meta-data/iam/security-credentials/$roleName").toURL().openConnection() as HttpURLConnection
        credsConn.setRequestProperty("X-aws-ec2-metadata-token", imdsToken)
        credsConn.connectTimeout = 2000
        credsConn.readTimeout = 2000
        val body = credsConn.inputStream.bufferedReader().readText()
        credsConn.disconnect()

        return parseCredentialsJson(body)
    }

    private fun buildCloudId(creds: AwsCredentials): String {
        val now = Date()
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val amzDate = dateFormat.format(now)

        val dateStampFormat = SimpleDateFormat("yyyyMMdd")
        dateStampFormat.timeZone = TimeZone.getTimeZone("UTC")
        val dateStamp = dateStampFormat.format(now)

        val credentialScope = "$dateStamp/$REGION/$SERVICE/aws4_request"
        val bodyHash = sha256Hex(STS_BODY)

        // Build canonical headers and signed headers to match the Go SDK output.
        // Go's http.Header does NOT store Content-Length; the AWS Go SDK v4 signer
        // signs: content-type, host, x-amz-date, and x-amz-security-token (if present).
        val canonicalHeadersBuilder = StringBuilder()
        val signedHeadersList = mutableListOf<String>()

        canonicalHeadersBuilder.append("content-type:$CONTENT_TYPE\n")
        signedHeadersList.add("content-type")

        canonicalHeadersBuilder.append("host:$STS_HOST\n")
        signedHeadersList.add("host")

        canonicalHeadersBuilder.append("x-amz-date:$amzDate\n")
        signedHeadersList.add("x-amz-date")

        if (!creds.sessionToken.isNullOrBlank()) {
            canonicalHeadersBuilder.append("x-amz-security-token:${creds.sessionToken}\n")
            signedHeadersList.add("x-amz-security-token")
        }

        val signedHeaders = signedHeadersList.joinToString(";")
        val canonicalHeaders = canonicalHeadersBuilder.toString()

        val canonicalRequest = "POST\n/\n\n$canonicalHeaders\n$signedHeaders\n$bodyHash"

        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val signingKey = getSignatureKey(creds.secretAccessKey, dateStamp, REGION, SERVICE)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authorization = "AWS4-HMAC-SHA256 Credential=${creds.accessKeyId}/$credentialScope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        // Build headers JSON matching Go's http.Header marshal format (Title-Case keys, array values)
        val headersMap = TreeMap<String, List<String>>()
        headersMap["Authorization"] = listOf(authorization)
        headersMap["Content-Type"] = listOf(CONTENT_TYPE)
        headersMap["Host"] = listOf(STS_HOST)
        headersMap["X-Amz-Date"] = listOf(amzDate)
        if (!creds.sessionToken.isNullOrBlank()) {
            headersMap["X-Amz-Security-Token"] = listOf(creds.sessionToken)
        }

        val headersJson = buildHeadersJson(headersMap)

        val urlB64 = b64(STS_URL)
        val bodyB64 = b64(STS_BODY)
        val headersB64 = b64(headersJson)

        val cloudIdJson = """{"sts_request_method":"POST","sts_request_url":"$urlB64","sts_request_body":"$bodyB64","sts_request_headers":"$headersB64"}"""

        logger.info("Akeyless: cloud-id headers keys=${headersMap.keys}, signedHeaders=$signedHeaders")

        return Base64.getEncoder().encodeToString(cloudIdJson.toByteArray(StandardCharsets.UTF_8))
    }

    private fun buildHeadersJson(headers: TreeMap<String, List<String>>): String {
        val entries = headers.entries.joinToString(",") { (key, values) ->
            val valuesArray = values.joinToString(",") { "\"${escapeJson(it)}\"" }
            "\"${escapeJson(key)}\":[$valuesArray]"
        }
        return "{$entries}"
    }

    private fun b64(s: String): String =
        Base64.getEncoder().encodeToString(s.toByteArray(StandardCharsets.UTF_8))

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun sha256Hex(data: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String =
        hmacSha256(key, data).joinToString("") { "%02x".format(it) }

    private fun getSignatureKey(secretKey: String, dateStamp: String, region: String, service: String): ByteArray {
        val kDate = hmacSha256("AWS4$secretKey".toByteArray(StandardCharsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "aws4_request")
    }

    private fun parseCredentialsJson(json: String): AwsCredentials {
        val accessKeyId = extractJsonValue(json, "AccessKeyId")
        val secretAccessKey = extractJsonValue(json, "SecretAccessKey")
        val sessionToken = try { extractJsonValue(json, "Token") } catch (_: Exception) {
            try { extractJsonValue(json, "SessionToken") } catch (_: Exception) { null }
        }
        return AwsCredentials(accessKeyId, secretAccessKey, sessionToken)
    }

    private fun extractJsonValue(json: String, key: String): String {
        val match = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)
            ?: throw RuntimeException("Key '$key' not found in JSON response")
        return match.groupValues[1]
    }

    private fun extractXmlValue(xml: String, tag: String): String {
        val match = Regex("<$tag>([^<]*)</$tag>").find(xml)
            ?: throw RuntimeException("Tag '$tag' not found in XML response")
        return match.groupValues[1]
    }
}
