FROM ubuntu:16.04

# Install tools and supervisor
RUN apt-get update
RUN apt-get install -y software-properties-common libconfuse0 libftdi1 wget supervisor

# Install Tellstick
RUN echo "deb http://download.telldus.com/debian/ stable main" >> /etc/apt/sources.list.d/telldus.list
RUN wget -q http://download.telldus.com/debian/telldus-public.key -O- | apt-key add -
RUN apt-get update && apt-get install -y telldus-core

# Install Oracle Java 8
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update && apt-get install -y oracle-java8-installer
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Clean up
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/oracle-jdk8-installer

RUN rm /etc/tellstick.conf
VOLUME /etc/tellstick.conf

# Add JAR file to be executed
ARG JAR_FILE
ADD ${JAR_FILE} app.jar

COPY docker/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
CMD ["/usr/bin/supervisord"]
