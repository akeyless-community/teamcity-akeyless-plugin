package com.akeyless.teamcity.common

object AkeylessConstants {
    const val PLUGIN_NAME = "Akeyless Secrets Management"
    const val PLUGIN_ID = "akeyless"
    
    // API endpoints
    const val DEFAULT_API_URL = "https://api.akeyless.io"
    const val AUTH_ENDPOINT = "/auth"
    const val GET_SECRET_VALUE_ENDPOINT = "/get-secret-value"
    const val LIST_ITEMS_ENDPOINT = "/list-items"
    const val DESCRIBE_ITEM_ENDPOINT = "/describe-item"
    
    // Authentication methods
    const val AUTH_METHOD_ACCESS_KEY = "access_key"
    const val AUTH_METHOD_PASSWORD = "password"
    const val AUTH_METHOD_SAML = "saml"
    const val AUTH_METHOD_LDAP = "ldap"
    const val AUTH_METHOD_K8S = "k8s"
    const val AUTH_METHOD_AWS_IAM = "aws_iam"
    const val AUTH_METHOD_AZURE_AD = "azure_ad"
    const val AUTH_METHOD_GCP = "gcp"
    const val AUTH_METHOD_OIDC = "oidc"
    const val AUTH_METHOD_CERT = "cert"
    const val AUTH_METHOD_UNIVERSAL_IDENTITY = "universal_identity"
    
    // Parameter types
    const val PARAM_TYPE_REMOTE = "remote"
    const val PARAM_TYPE_AKEYLESS = "akeyless"
}
