FROM openjdk:11.0.16-bullseye
#Security patches
ENV DEBIAN_FRONTEND noninteractive
RUN echo "deb http://archive.debian.org/debian bullseye-backports main" > /etc/apt/sources.list.d/backports.list \
  && apt-get update && apt-get -y upgrade && apt-get -y autoremove

# Add Sectigo root certificate to Java truststore
COPY sectigo-root-r46.crt /tmp/sectigo-root-r46.crt
RUN keytool -importcert -noprompt -trustcacerts \
  -alias sectigo-root-r46 \
  -file /tmp/sectigo-root-r46.crt \
  -keystore /usr/local/openjdk-11/lib/security/cacerts \
  -storepass changeit \
  && rm /tmp/sectigo-root-r46.crt

EXPOSE 8080
RUN mkdir -p /opt/cprof && \
  wget -q -O- https://storage.googleapis.com/cloud-profiler/java/latest/profiler_java_agent.tar.gz \
  | tar xzv -C /opt/cprof
COPY ./target/viper-$BUILD_VERSION.jar /usr/app/
WORKDIR /usr/app
ENTRYPOINT ["java","-agentpath:/opt/cprof/profiler_java_agent.so=-cprof_service=viper,-cprof_enable_heap_sampling=true,-cprof_service_version=1.0.2", "-XX:MinRAMPercentage=50.0", "-XX:MaxRAMPercentage=75.0","-XX:+PrintGCDetails", "-jar","viper-$BUILD_VERSION.jar"]
