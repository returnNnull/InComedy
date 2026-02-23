FROM gradle:8.10.2-jdk17 AS build
WORKDIR /workspace
COPY . .
USER root
RUN chown -R gradle:gradle /workspace
USER gradle
RUN ./gradlew :server:installDist --no-daemon --stacktrace

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/server/build/install/server/ /app/
EXPOSE 8080
ENV PORT=8080
CMD ["bin/server"]
