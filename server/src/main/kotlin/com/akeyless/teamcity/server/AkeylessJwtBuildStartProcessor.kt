package com.akeyless.teamcity.server

import jetbrains.buildServer.util.positioning.PositionConstraint

class AkeylessJwtBuildStartProcessor :
    AkeylessBuildStartProcessor(jwtOnly = true) {

    override fun getConstraint(): PositionConstraint =
        PositionConstraint.between(
            listOf(
                "org.jetbrains.teamcity.builds.oidc.injection.OIDCTokenBuildStartContextProcessor"
            ),
            listOf(
                "jetbrains.buildServer.serverSide.parameters.types.PasswordsBuildStartContextProcessor"
            )
        )
}