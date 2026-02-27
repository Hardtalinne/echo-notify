package com.echonotify.core.infrastructure.config

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun create(
        jdbcUrl: String,
        username: String,
        password: String
    ): Database {
        Flyway.configure()
            .dataSource(jdbcUrl, username, password)
            .load()
            .migrate()

        return Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = username,
            password = password
        )
    }
}
