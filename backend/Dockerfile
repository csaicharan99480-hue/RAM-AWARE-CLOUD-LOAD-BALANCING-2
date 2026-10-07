FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/ram-aware-load-balancer-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
