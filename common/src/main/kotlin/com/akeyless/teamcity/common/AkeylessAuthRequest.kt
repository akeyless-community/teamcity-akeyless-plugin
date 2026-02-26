package com.akeyless.teamcity.common

data class AkeylessAuthRequest(
    val accessId: String? = null,
    val accessKey: String? = null,
    val accessType: String,
    val email: String? = null,
    val password: String? = null,
    val samlIdToken: String? = null,
    val ldapUsername: String? = null,
    val ldapPassword: String? = null,
    val k8sAuthConfigName: String? = null,
    val k8sServiceAccountToken: String? = null,
    val awsIamRole: String? = null,
    val awsIamAccessKeyId: String? = null,
    val awsIamSecretAccessKey: String? = null,
    val azureAdObjectId: String? = null,
    val gcpAudience: String? = null,
    val gcpJwt: String? = null,
    val oidcAccessToken: String? = null,
    val certData: String? = null,
    val certFile: String? = null,
    val uidToken: String? = null
)

data class AkeylessAuthResponse(
    val token: String
)
