# The backend image (docs/deploy.md). Built from the jar Maven already made, so CI doesn't compile
# twice:  ./mvnw -B -DskipTests package && docker build -t insightshub-backend .
# Pushed to Docker Hub by CI on every push to main; the EC2 instance only pulls and restarts it.

# Split the Boot jar into layers: dependencies change rarely, the app's own classes every build.
FROM eclipse-temurin:21-jre-noble AS layers
WORKDIR /build
COPY target/insightshub-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
USER app

# Sized for the EC2 t3.micro (1 GB): 328 MB peak RSS measured with these flags (docs/deploy.md §6).
# Override with JAVA_TOOL_OPTIONS in compose.yaml without rebuilding.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-Xms400m -Xmx400m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=64m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
