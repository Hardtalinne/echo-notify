package com.echonotify.api.security

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
            requiredScope = ApiScopes.READ,
            expectedClientId = "client-a"
        )

        assertTrue(result is AuthorizationResult.Authorized)
    }

    private fun enabledConfig() = ConfigFactory.parseString(
        """
        echo-notify.security.enabled=true
        echo-notify.security.clients=[
          {
            clientId="client-a"
            apiKey="key-a"
            scopes=["notify:create","notify:read"]
          }
        ]
        """.trimIndent()
    )
}
