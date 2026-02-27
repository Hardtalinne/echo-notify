package com.echonotify.api.routes

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Route.healthRoutes(
    meterRegistry: PrometheusMeterRegistry
) {
    route("/") {
        get("health") {
            call.respondText("ok")
        }
        get("health/ready") {
            call.respondText("ready")
        }
        get("metrics") {
            call.respondText(meterRegistry.scrape())
        }
    }
}
