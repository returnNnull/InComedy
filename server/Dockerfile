FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /home/app appuser \
    && chown -R appuser:app /app
COPY --chown=appuser:app server/build/install/server/ /app/
EXPOSE 8080
ENV PORT=8080
USER appuser
CMD ["bin/server"]
