FROM gradle:8.9-jdk11 AS build
WORKDIR /workspace
COPY . .
RUN gradle :echo-notify-api:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/echo-notify-api/build/install/echo-notify-api /app
EXPOSE 8080
CMD ["/app/bin/echo-notify-api"]
