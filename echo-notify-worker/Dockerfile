FROM gradle:8.9-jdk11 AS build
WORKDIR /workspace
COPY . .
RUN gradle :echo-notify-worker:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/echo-notify-worker/build/install/echo-notify-worker /app
CMD ["/app/bin/echo-notify-worker"]
