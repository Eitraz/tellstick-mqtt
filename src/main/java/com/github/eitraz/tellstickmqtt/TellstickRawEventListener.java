package com.github.eitraz.tellstickmqtt;

import com.eitraz.tellstick.core.Tellstick;
import com.eitraz.tellstick.core.rawdevice.RawDeviceEventListener;
import com.eitraz.tellstick.core.rawdevice.events.RawDeviceEvent;
import com.eitraz.tellstick.core.util.TimeoutHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class TellstickRawEventListener implements RawDeviceEventListener {
    private static final Logger logger = LogManager.getLogger();

    private final Tellstick tellstick;
    private final MqttComponent mqtt;
    private final TimeoutHandler<String> timeoutHandler;

    private final Map<String, Double> lastValues;

    @Autowired
    public TellstickRawEventListener(Tellstick tellstick, MqttComponent mqtt) {
        this.tellstick = tellstick;
        this.mqtt = mqtt;
        this.timeoutHandler = new TimeoutHandler<>();
        this.lastValues = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void startListener() {
        tellstick.getRawDeviceHandler().addRawDeviceEventListener(this);
    }

    @PreDestroy
    public void stopListener() {
        tellstick.getRawDeviceHandler().removeRawDeviceEventListener(this);
    }

    @Override
    public void rawDeviceEvent(RawDeviceEvent event) {
        logger.debug("Event data: " + event.getParameters());

        // Don't spam burst events
        if (!timeoutHandler.isReady(event.getParameters().toString(), Duration.ofSeconds(5))) {
            logger.debug(String.format("To soon, won't handle '%s'", event));
            return;
        }

        String eventClass = event.get_Class();

        // Sensor
        if (RawDeviceEvent.SENSOR.equals(eventClass)) {
            handleSensorEvent(event);
        }
        // Command
        else if (RawDeviceEvent.COMMAND.equals(eventClass)) {
            handleCommand(event);
        }
    }

    /**
     * Handle sensor event (temperature/humidity)
     */
    private void handleSensorEvent(RawDeviceEvent sensorEvent) {
        String id = sensorEvent.get(RawDeviceEvent.ID);

        String temperature = sensorEvent.get(RawDeviceEvent.TEMP);
        String humidity = sensorEvent.get(RawDeviceEvent.HUMIDITY);

        // Temperature
        if (isNotBlank(temperature)) {
            publishTemperatureOrHumidity(getSensorTopicName(id, "temperature"), temperature);
        }

        // Humidity
        if (isNotBlank(humidity)) {
            publishTemperatureOrHumidity(getSensorTopicName(id, "humidity"), humidity);

        }
    }

    private void publishTemperatureOrHumidity(String topicName, String value) {
        try {
            double number = Double.parseDouble(value);

            Double previousNumber = lastValues.getOrDefault(topicName, number);

            lastValues.put(topicName, number);

            // Don't publish to large steps
            if (Math.abs(previousNumber - number) > 10d) {
                return;
            }
        } catch (NumberFormatException e) {
            logger.debug(String.format("Unable to parse numeric value from '%s'", value));
        }

        mqtt.publish(topicName, value);
    }

    /**
     * Get topic name for sensor
     */
    private String getSensorTopicName(String id, String type) {
        return String.format("sensor/%s/%s", id, type);
    }

    /**
     * Handle command event (motion sensor on/off, ...)
     */
    private void handleCommand(RawDeviceEvent event) {
        String house = event.get(RawDeviceEvent.HOUSE);
        String unit = event.get(RawDeviceEvent.UNIT);
        String group = event.get(RawDeviceEvent.GROUP);
        String method = event.get(RawDeviceEvent.METHOD);
        String level = event.get("level");

        getCommandTopicName(house, unit, group, method)
                .ifPresent(topic ->
                        mqtt.publish(
                                topic,
                                isNotBlank(level) ? level : method
                        ));
    }

    @SuppressWarnings("ConstantConditions")
    private Optional<String> getCommandTopicName(String house, String unit, String group, String method) {
        boolean hasHouse = isNotBlank(house);
        boolean hasUnit = isNotBlank(unit);
        boolean hasGroup = isNotBlank(group);

        String topic;

        // house
        if (hasHouse && !hasUnit && !hasGroup) {
            topic = house.trim();
        }
        // house/unit
        else if (hasHouse && hasUnit && !hasGroup) {
            topic = String.format("%s/%s", house.trim(), unit.trim());
        }
        // house/unit/group
        else if (hasHouse && hasUnit && hasGroup) {
            topic = String.format("%s/%s/%s", house.trim(), unit.trim(), group.trim());
        }
        // no match
        else {
            logger.warn(String.format(
                    "Could not build topic for house=%s, unit=%s, group=%s, method=%s",
                    house, unit, group, method));
            return Optional.empty();
        }

        return Optional.of(String.format("command/%s/%s", topic, method));
    }
}
