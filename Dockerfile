FROM docker.bhxz.host:12345/center7/center:7.1.1.19

WORKDIR /app
COPY build/libs/obs-all.jar /app/app.jar

EXPOSE 8998

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
