package com.github.eitraz.tellstickmqtt;

import com.eitraz.tellstick.core.Tellstick;
import com.eitraz.tellstick.core.rawdevice.RawDeviceEventListener;
import com.eitraz.tellstick.core.rawdevice.events.RawDeviceEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class TellstickRawEventListener implements RawDeviceEventListener {
    private static final Logger logger = LogManager.getLogger();

    private final Tellstick tellstick;
    private final MqttComponent mqtt;

    @Autowired
    public TellstickRawEventListener(Tellstick tellstick, MqttComponent mqtt) {
        this.tellstick = tellstick;
        this.mqtt = mqtt;
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
        logger.info("Event data: " + event.getParameters());

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
            mqtt.publish(getSensorTopicName(id, "temperature"), temperature);
        }

        // Humidity
        if (isNotBlank(humidity)) {
            mqtt.publish(getSensorTopicName(id, "humidity"), humidity);
        }
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

        return Optional.of(String.format("%s/%s", topic, method));
    }
}
