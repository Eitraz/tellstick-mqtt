package com.github.eitraz.tellstickmqtt;

import com.eitraz.tellstick.core.Tellstick;
import org.fusesource.mqtt.client.MQTT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import javax.annotation.PreDestroy;
import java.net.URISyntaxException;

@SpringBootConfiguration
public class TellstickMqttConfiguration {
    private Tellstick tellstick;

    @Bean
    public MQTT mqttClient(
            @Value("${mqtt.host}") String host,
            @Value("${mqtt.port}") int port,
            @Value("${mqtt.username}") String username,
            @Value("${mqtt.password}") String password) throws URISyntaxException {
        MQTT mqtt = new MQTT();
        mqtt.setHost(host, port);
        mqtt.setUserName(username);
        mqtt.setUserName(password);
        return mqtt;
    }

    @Bean
    public Tellstick tellstick() {
        tellstick = new Tellstick();
        tellstick.start();
        return tellstick;
    }

    @PreDestroy
    public void stopListener() {
        System.out.println("Stopping tellstick");
        if (tellstick != null)
            tellstick.stop();
    }
}
