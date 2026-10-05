# Usage: docker build --build-arg SERVICE=ts-fraud -t telesentinel/ts-fraud .
FROM maven:3-eclipse-temurin-24 AS build
ARG SERVICE
WORKDIR /src
COPY pom.xml .
COPY services services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /src/services/${SERVICE}/target/${SERVICE}-*.jar app.jar
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
