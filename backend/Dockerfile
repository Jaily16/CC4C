# syntax=docker/dockerfile:1.7
FROM maven:3-eclipse-temurin-26@sha256:166ca19b6b5fe1e924ab2d66b64ba9854c739f16210b94bbe0074b036c5c7992 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package \
    && mkdir -p /workspace/.m2-repository \
    && cp -a /root/.m2/repository/. /workspace/.m2-repository/

FROM build AS performance-tools
RUN mkdir -p /root/.m2/repository \
    && cp -a /workspace/.m2-repository/. /root/.m2/repository/ \
    && mvn -B -ntp -DskipTests test-compile \
    && mvn -B -ntp org.codehaus.mojo:exec-maven-plugin:3.5.1:help \
    && mvn -B -ntp -Pperformance-gatling -DskipTests test-compile \
    && mvn -B -ntp -Pperformance-gatling io.gatling:gatling-maven-plugin:4.21.10:help

FROM eclipse-temurin:21.0.12_8-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf
RUN groupadd --system --gid 10001 cc4c \
    && useradd --system --uid 10001 --gid cc4c --home-dir /app --shell /usr/sbin/nologin cc4c \
    && mkdir -p /app /var/lib/cc4c/uploads/blog /var/lib/cc4c/uploads/avatar \
    && chown -R cc4c:cc4c /app /var/lib/cc4c
WORKDIR /app
COPY --from=build --chown=cc4c:cc4c /workspace/target/cc4c-4.0.0-SNAPSHOT.jar /app/cc4c.jar
COPY --from=build --chown=cc4c:cc4c /workspace/target/cc4c-4.0.0-SNAPSHOT-admin-bootstrap.jar /app/cc4c-admin-bootstrap.jar
COPY --chown=cc4c:cc4c docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod 0555 /app/entrypoint.sh
USER 10001:10001
EXPOSE 4080 4081
ENTRYPOINT ["/app/entrypoint.sh"]
