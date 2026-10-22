
# 1. Spring Boot 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradle 설정
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# 소스 복사
COPY src/ src/

# 실행 권한 및 JAR 빌드
RUN chmod +x gradlew

RUN ./gradlew --no-daemon bootJar -x test

# 실행할 JAR 복사
RUN cp "$(find build/libs -maxdepth 1 -type f \
    -name '*.jar' ! -name '*-plain.jar' \
    -print -quit)" /app/app.jar

# 2. 실제 실행 이미지
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
