FROM openjdk:11-alpine

LABEL description="Basic Proxy Facade for NTLM, Kerberos, SOCKS and Proxy Auto Config file proxies"
LABEL maintainer="ecovaci"

# Add Spring Boot app.jar to Container
ADD target/winfoom.jar winfoom.jar

EXPOSE 3129
EXPOSE 9999

RUN adduser -D winfoom
USER winfoom

ARG FOOM_ARGS

# Fire up our Spring Boot app by default
ENTRYPOINT [ "sh", "-c", "java -server $FOOM_ARGS -Djava.security.egd=file:/dev/./urandom -jar winfoom.jar" ]