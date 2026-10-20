# Stage 1: Build JAR bằng Maven
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Tải trước dependencies để tận dụng Docker cache
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Chạy ứng dụng bằng JRE 21 gọn nhẹ
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
# Tối ưu JVM cho môi trường CPU/RAM hạn chế: C1 compiler (-XX:TieredStopAtLevel=1) giúp boot cực nhanh
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-Xss256k", "-jar", "app.jar"]
