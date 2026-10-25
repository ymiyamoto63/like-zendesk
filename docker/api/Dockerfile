# ビルドコンテキストはリポジトリのルート（compose.yaml を参照）
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/target/like-zendesk-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
