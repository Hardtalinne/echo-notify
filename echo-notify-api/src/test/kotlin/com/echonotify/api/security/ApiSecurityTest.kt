package com.echonotify.api.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiSecurityTest {
    @Test
    fun `should authorize when security disabled`() {
        val security = ApiSecurity(
            ConfigFactory.parseString(
                """
                echo-notify.security.enabled=false
                """.trimIndent()
            )
        )

        val result = security.authorize(
            apiKey = null,
            bearerToken = null,
            requiredScope = ApiScopes.CREATE,
            expectedClientId = "client-a"
        )

        assertTrue(result is AuthorizationResult.Authorized)
    }

    @Test
    fun `should return unauthorized for missing api key`() {
        val security = ApiSecurity(enabledConfig())

        val result = security.authorize(
            apiKey = null,
            bearerToken = null,
            requiredScope = ApiScopes.CREATE,
            expectedClientId = "client-a"
        )

        assertTrue(result is AuthorizationResult.Unauthorized)
    }

    @Test
    fun `should return forbidden for client mismatch`() {
        val security = ApiSecurity(enabledConfig())

        val result = security.authorize(
            apiKey = "key-a",
            bearerToken = null,
            requiredScope = ApiScopes.CREATE,
            expectedClientId = "client-b"
        )

        assertTrue(result is AuthorizationResult.Forbidden)
    }

    @Test
    fun `should return forbidden for missing scope`() {
        val security = ApiSecurity(enabledConfig())

        val result = security.authorize(
            apiKey = "key-a",
            bearerToken = null,
            requiredScope = ApiScopes.DLQ_REPROCESS,
            expectedClientId = "client-a"
        )

        assertTrue(result is AuthorizationResult.Forbidden)
    }

    @Test
    fun `should authorize valid client and scope`() {
        val security = ApiSecurity(enabledConfig())

        val result = security.authorize(
            apiKey = "key-a",
            bearerToken = null,
            requiredScope = ApiScopes.READ,
            expectedClientId = "client-a"
        )

        assertTrue(result is AuthorizationResult.Authorized)
    }

    @Test
    fun `should authorize rotated api key`() {
        val security = ApiSecurity(enabledConfig())

        val result = security.authorize(
            apiKey = "key-a-rotated",
            bearerToken = null,
            requiredScope = ApiScopes.READ,
            expectedClientId = "client-a"
        )

        assertTrue(result is AuthorizationResult.Authorized)
    }

    @Test
    fun `should authorize valid jwt`() {
        val security = ApiSecurity(jwtEnabledConfig())
        val token = JWT.create()
            .withIssuer("echo-notify")
            .withAudience("echo-notify-api")
            .withClaim("client_id", "client-jwt")
            .withClaim("scopes", listOf("notify:create", "notify:read"))
            .sign(Algorithm.HMAC256("local-dev-secret"))

        val result = security.authorize(
            apiKey = null,
            bearerToken = token,
            requiredScope = ApiScopes.READ,
            expectedClientId = "client-jwt"
        )

        assertTrue(result is AuthorizationResult.Authorized)
    }

    @Test
    fun `should reject jwt without required scope`() {
        val security = ApiSecurity(jwtEnabledConfig())
        val token = JWT.create()
            .withIssuer("echo-notify")
            .withAudience("echo-notify-api")
            .withClaim("client_id", "client-jwt")
            .withClaim("scopes", listOf("notify:read"))
            .sign(Algorithm.HMAC256("local-dev-secret"))

        val result = security.authorize(
            apiKey = null,
            bearerToken = token,
            requiredScope = ApiScopes.DLQ_REPROCESS,
            expectedClientId = "client-jwt"
        )

        assertTrue(result is AuthorizationResult.Forbidden)
    }

    @Test
    fun `should reject invalid jwt`() {
        val security = ApiSecurity(jwtEnabledConfig())
        val token = JWT.create()
            .withIssuer("echo-notify")
            .withAudience("echo-notify-api")
            .withClaim("client_id", "client-jwt")
            .withClaim("scopes", listOf("notify:create", "notify:read"))
            .sign(Algorithm.HMAC256("wrong-secret"))

        val result = security.authorize(
            apiKey = null,
            bearerToken = token,
            requiredScope = ApiScopes.READ,
            expectedClientId = "client-jwt"
        )

        assertTrue(result is AuthorizationResult.Unauthorized)
    }

    private fun enabledConfig() = ConfigFactory.parseString(
        """
        echo-notify.security.enabled=true
        echo-notify.security.clients=[
          {
            clientId="client-a"
            apiKey="key-a"
                        apiKeys=["key-a","key-a-rotated"]
            scopes=["notify:create","notify:read"]
          }
        ]
        """.trimIndent()
    )

        private fun jwtEnabledConfig() = ConfigFactory.parseString(
                """
                echo-notify.security.enabled=true
                echo-notify.security.jwt.enabled=true
                echo-notify.security.jwt.issuer="echo-notify"
                echo-notify.security.jwt.audience="echo-notify-api"
                echo-notify.security.jwt.secret="local-dev-secret"
                """.trimIndent()
        )
}
