package com.akeyless.teamcity.common

data class AkeylessGetSecretValueRequest(
    val name: String,
    val version: Int? = null,
    val token: String
)

data class AkeylessGetSecretValueResponse(
    val value: String? = null,
    val error: String? = null
)

data class AkeylessListItem(
    val name: String,
    val type: String,
    val path: String
)

data class AkeylessListItemsRequest(
    val token: String,
    val path: String? = null,
    val type: String? = null
)

data class AkeylessListItemsResponse(
    val items: List<AkeylessListItem> = emptyList(),
    val error: String? = null
)
