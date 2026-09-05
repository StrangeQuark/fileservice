# Stage 1: Build the application
FROM eclipse-temurin:21-alpine@sha256:6ea5548706b60ac0a602eaf48af74792cbab012d90e811ca8db6184b16b5c3d6 AS builder

WORKDIR /fileservice

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw
RUN ./mvnw dependency:go-offline

COPY src ./src
RUN ./mvnw clean package

# Stage 2: Create minimal runtime image
FROM eclipse-temurin:21-alpine@sha256:6ea5548706b60ac0a602eaf48af74792cbab012d90e811ca8db6184b16b5c3d6

RUN apk add --no-cache curl
RUN addgroup -S -g 1000 msinit && adduser -S -u 1000 -G msinit msinit

WORKDIR /fileservice

RUN mkdir uploads && chown msinit:msinit uploads

COPY --chown=msinit:msinit --from=builder /fileservice/target/*.jar fileservice.jar

ENV JAVA_OPTS=""

EXPOSE 6010

USER msinit
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar fileservice.jar"]
