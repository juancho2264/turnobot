# Receta para armar el contenedor del backend.
# "Multi-stage": la primera etapa compila con Maven; la segunda solo copia el .jar
# a una imagen chica con Java. La imagen final no lleva Maven ni el código fuente.

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-17 AS compilacion
WORKDIR /app

# Primero solo el pom.xml: Docker guarda en caché las dependencias descargadas
# y, si no cambió el pom, no las vuelve a bajar en cada build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# No correr como root: si alguien vulnera la app, tiene menos permisos
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=compilacion /app/target/turnobot-*.jar app.jar

# El plan gratis de Render tiene 512 MB: limitamos Java para que no se pase.
# MaxRAMPercentage → usa como máximo el 70% de la memoria del contenedor.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
