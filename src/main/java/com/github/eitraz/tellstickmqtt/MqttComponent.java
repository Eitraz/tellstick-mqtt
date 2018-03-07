package com.github.eitraz.tellstickmqtt;

import com.eitraz.tellstick.core.util.TimeoutHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class MqttComponent implements Listener {
    private static final Logger logger = LogManager.getLogger();

    private final MQTT mqtt;
    private CallbackConnection callbackConnection;
    private boolean isConnected = false;

    private Map<String, List<Consumer<String>>> subscribers = new ConcurrentHashMap<>();

    private final TimeoutHandler<String> timeoutHandler;

    @Autowired
    public MqttComponent(MQTT mqtt) {
        this.mqtt = mqtt;
        this.timeoutHandler = new TimeoutHandler<>();
    }

    @PostConstruct
    public void connect() throws Exception {
        callbackConnection = mqtt.callbackConnection();
        callbackConnection.listener(this);
        callbackConnection.connect(new Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
            }

            @Override
            public void onFailure(Throwable value) {
            }
        });
    }

    @PreDestroy
    public synchronized void disconnect() throws Exception {
        if (isConnected) {
            callbackConnection.disconnect(new Callback<Void>() {
                @Override
                public void onSuccess(Void value) {
                }

                @Override
                public void onFailure(Throwable value) {
                }
            });
        }
    }

    @SuppressWarnings("WeakerAccess")
    public synchronized void publish(String topic, String data) {
        if (isConnected) {
            // Attempt to prevent spamming the same message to a topic when having multiple instances of tellstick-mqtt
            if (!timeoutHandler.isReady(topic + "::" + data, Duration.ofSeconds(5))) {
                logger.debug(String.format("To soon, wont publish '%s' to topic '%s'", data, topic));
                return;
            }

            logger.debug(String.format("Publishing '%s' to topic '%s'", data, topic));

            callbackConnection.publish(topic, data.getBytes(), QoS.AT_MOST_ONCE, false, new Callback<Void>() {
                @Override
                public void onSuccess(Void value) {
                    logger.info(String.format("Published '%s' to topic '%s'", data, topic));
                }

                @Override
                public void onFailure(Throwable value) {
                    logger.warn(String.format("Failed to published '%s' to topic '%s'", data, topic));
                }
            });
        }
        // Not connected
        else {
            logger.info(String.format("MQTT not connected, wont publish '%s' to topic '%s'", data, topic));
        }
    }

    public void subscribe(String topic, Consumer<String> consumer) {
        subscribers
                .getOrDefault(topic, new CopyOnWriteArrayList<>())
                .add(consumer);
    }

    @Override
    public synchronized void onConnected() {
        logger.info("Connected");
        isConnected = true;
    }

    @Override
    public synchronized void onDisconnected() {
        logger.info("Disconnected");
        isConnected = false;
    }

    @Override
    public void onPublish(UTF8Buffer topicBuffer, Buffer body, Runnable ack) {
        String topic = topicBuffer.toString();
        String data = body.toString();

        logger.debug("Received '" + data + "' from topic '" + topic + "'");

        // Add to timeout handler to prevent publish spamming
        timeoutHandler.isReady(topic + "::" + data, Duration.ofSeconds(2));

        subscribers
                .getOrDefault(topic, new CopyOnWriteArrayList<>())
                .forEach(consumer -> consumer.accept(data));

        ack.run();
    }

    @Override
    public void onFailure(Throwable value) {
        logger.error("Failure", value);
        isConnected = false;
    }
}
