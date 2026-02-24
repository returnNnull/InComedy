FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY server/build/install/server/ /app/
EXPOSE 8080
ENV PORT=8080
CMD ["bin/server"]
