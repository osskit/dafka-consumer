FROM openjdk:13-alpine AS base
WORKDIR /service

FROM base as dependencies
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew build || return 0

FROM dependencies AS build
COPY . ./

RUN ./gradlew clean test --info --no-daemon
RUN ./gradlew build --info --no-daemon

FROM base AS release
WORKDIR /service

COPY --from=build /service/build/libs/*-all.jar /service/application.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0","-jar","/service/application.jar"]