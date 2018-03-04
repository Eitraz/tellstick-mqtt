package com.github.eitraz.tellstickmqtt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fusesource.mqtt.client.FutureConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Component
public class MqttComponent {
    private static final Logger logger = LogManager.getLogger();

    private final MQTT mqtt;
    private FutureConnection connection;

    @Autowired
    public MqttComponent(MQTT mqtt) {
        this.mqtt = mqtt;
    }

    @PostConstruct
    public void connect() throws Exception {
        connection = mqtt.futureConnection();
        connection.connect().await();
    }

    @PreDestroy
    public void disconnect() throws Exception {
        if (connection != null) {
            connection.disconnect().await(30, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public void publish(String topic, String data) {
        logger.info(String.format("Publishing '%s' to topic '%s'", data, topic));
        connection.publish(topic, data.getBytes(), QoS.AT_MOST_ONCE, false);
    }
}
