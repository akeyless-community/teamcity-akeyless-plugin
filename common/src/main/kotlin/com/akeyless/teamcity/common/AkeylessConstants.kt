package com.akeyless.teamcity.common

object AkeylessConstants {
    const val PLUGIN_NAME = "Akeyless Secrets Management"
    const val PLUGIN_ID = "akeyless"

    const val DEFAULT_API_URL = "https://api.akeyless.io"

    const val AUTH_METHOD_ACCESS_KEY = "access_key"
    const val AUTH_METHOD_K8S = "k8s"
    const val AUTH_METHOD_AWS_IAM = "aws_iam"
    const val AUTH_METHOD_AZURE_AD = "azure_ad"
    const val AUTH_METHOD_GCP = "gcp"
    const val AUTH_METHOD_CERT = "cert"

    const val PARAM_TYPE_AKEYLESS = "akeyless"
}
