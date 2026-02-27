package com.echonotify.api.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.Config
import io.ktor.server.application.ApplicationCall

data class AuthenticatedClient(
    val clientId: String,
    val scopes: Set<String>
)

sealed interface AuthorizationResult {
    data class Authorized(val client: AuthenticatedClient) : AuthorizationResult
    data object Unauthorized : AuthorizationResult
    data object Forbidden : AuthorizationResult
}

class ApiSecurity(config: Config) {
    private val enabled: Boolean = config.hasPath("echo-notify.security.enabled") && config.getBoolean("echo-notify.security.enabled")

    private val jwtEnabled: Boolean = config.hasPath("echo-notify.security.jwt.enabled") &&
        config.getBoolean("echo-notify.security.jwt.enabled")

    private val jwtVerifier = if (jwtEnabled) {
        val issuer = config.getString("echo-notify.security.jwt.issuer")
        val audience = config.getString("echo-notify.security.jwt.audience")
        val secretEnvVar = if (config.hasPath("echo-notify.security.jwt.secretEnv")) {
            config.getString("echo-notify.security.jwt.secretEnv")
        } else {
            "ECHO_NOTIFY_JWT_SECRET"
        }

        val secret = System.getenv(secretEnvVar)
            ?: if (config.hasPath("echo-notify.security.jwt.secret")) config.getString("echo-notify.security.jwt.secret") else null
            ?: throw IllegalStateException("JWT enabled but no secret configured (env: $secretEnvVar)")

        JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
    } else {
        null
    }

    private val apiKeyToClient: Map<String, AuthenticatedClient> = if (
        config.hasPath("echo-notify.security.clients")
    ) {
        config.getConfigList("echo-notify.security.clients").flatMap { clientConfig ->
            val clientId = clientConfig.getString("clientId")
            val scopes = if (clientConfig.hasPath("scopes")) {
                clientConfig.getStringList("scopes").toSet()
            } else {
                emptySet()
            }
            val keys = when {
                clientConfig.hasPath("apiKeys") -> clientConfig.getStringList("apiKeys")
                clientConfig.hasPath("apiKey") -> listOf(clientConfig.getString("apiKey"))
                else -> emptyList()
            }
            keys.map { apiKey -> apiKey to AuthenticatedClient(clientId = clientId, scopes = scopes) }
        }.toMap()
    } else {
        emptyMap()
    }

    fun authorize(
        apiKey: String?,
        bearerToken: String?,
        requiredScope: String,
        expectedClientId: String?
    ): AuthorizationResult {
        if (!enabled) {
            return AuthorizationResult.Authorized(
                AuthenticatedClient(
                    clientId = expectedClientId ?: "anonymous",
                    scopes = setOf(requiredScope)
                )
            )
        }

        val authenticated = authenticate(apiKey, bearerToken) ?: return AuthorizationResult.Unauthorized

        if (!authenticated.scopes.contains(requiredScope)) {
            return AuthorizationResult.Forbidden
        }

        if (expectedClientId != null && authenticated.clientId != expectedClientId) {
            return AuthorizationResult.Forbidden
        }

        return AuthorizationResult.Authorized(authenticated)
    }

    @Deprecated("Use authorize(apiKey, bearerToken, requiredScope, expectedClientId)")
    fun authorize(
        apiKey: String?,
        requiredScope: String,
        expectedClientId: String?
    ): AuthorizationResult = authorize(apiKey, null, requiredScope, expectedClientId)

    private fun authenticate(apiKey: String?, bearerToken: String?): AuthenticatedClient? {
        val jwtClient = authenticateJwt(bearerToken)
        if (jwtClient != null) {
            return jwtClient
        }

        return apiKey?.let { apiKeyToClient[it] }
    }

    private fun authenticateJwt(bearerToken: String?): AuthenticatedClient? {
        if (!jwtEnabled || bearerToken.isNullOrBlank()) {
            return null
        }

        return runCatching {
            val decoded = jwtVerifier?.verify(bearerToken) ?: return null
            val clientId = decoded.getClaim("client_id").asString() ?: return null
            val scopes = decoded.getClaim("scopes").asList(String::class.java)?.toSet() ?: emptySet()
            AuthenticatedClient(clientId = clientId, scopes = scopes)
        }.getOrNull()
    }
}

object ApiScopes {
    const val CREATE = "notify:create"
    const val READ = "notify:read"
    const val DLQ_REPROCESS = "notify:dlq:reprocess"
}

fun ApplicationCall.apiKeyHeader(): String? = request.headers["X-Api-Key"]

fun ApplicationCall.clientIdHeader(): String? = request.headers["X-Client-Id"]

fun ApplicationCall.bearerTokenHeader(): String? {
    val value = request.headers["Authorization"] ?: return null
    val prefix = "Bearer "
    return if (value.startsWith(prefix, ignoreCase = true)) value.substring(prefix.length).trim() else null
}
