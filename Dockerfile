FROM adoptopenjdk:11-hotspot
LABEL maintainer="jugarriza10@gmail.com"
COPY target/bank.account-0.0.1-SNAPSHOT.jar bank.account.jar
CMD ["java", "-jar", "bank.account.jar"]
EXPOSE 9959