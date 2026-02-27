# ADR-001: Ktor over Spring Boot

## Decision
Use Ktor as backend framework.

## Why
- Kotlin-native and coroutines-first
- Lower overhead and explicit wiring
- Better fit for lightweight modular services (`api`, `consumer`, `worker`)
