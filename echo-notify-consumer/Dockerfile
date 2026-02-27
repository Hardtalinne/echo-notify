FROM gradle:8.9-jdk11 AS build
WORKDIR /workspace
COPY . .
RUN gradle :echo-notify-consumer:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/echo-notify-consumer/build/install/echo-notify-consumer /app
CMD ["/app/bin/echo-notify-consumer"]
