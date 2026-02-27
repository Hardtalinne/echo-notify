package com.echonotify.api.routes

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Route.healthRoutes(
    meterRegistry: PrometheusMeterRegistry,
    dbProbe: suspend () -> Boolean,
    kafkaProbe: suspend () -> Boolean
) {
    route("/") {
        get("health") {
            call.respondText("ok")
        }
        get("health/ready") {
            val dbReady = dbProbe()
            val kafkaReady = kafkaProbe()
            if (dbReady && kafkaReady) {
                call.respondText("ready")
            } else {
                call.respond(
                    io.ktor.http.HttpStatusCode.ServiceUnavailable,
                    mapOf("ready" to false, "db" to dbReady, "kafka" to kafkaReady)
                )
            }
        }
        get("metrics") {
            call.respondText(meterRegistry.scrape())
        }
    }
}
