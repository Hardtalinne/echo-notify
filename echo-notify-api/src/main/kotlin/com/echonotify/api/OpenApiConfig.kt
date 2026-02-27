package com.echonotify.api

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.plugins.openapi.openAPI

fun Application.configureOpenApi() {
    routing {
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
    }
}
