# Tellstick Duo MQTT bridge

Spring boot application to publish raw Tellstick event data to MQTT topics.

## Sensor values

Temperature and humidity are published to:
* sensor/[sensor-id]/temperature
* sensor/[sensor-id]/humidity

The data is temperature or humidity value.

For example, `21.0` would be published to `sensor/115/temperature` when the temperature for sensor 115 changes to 21.0.

## Command

Depending on the available properties.
* command/[house]/[method]
* command/[house]/[unit]/[method]
* command/[house]/[unit]/[group]/[method]

The data is either the same as method, or the `level` property value if available.

For example:
* `turnon` would be published to `command/8040222/10/0/turnon` when a raw event for house 8040222, unit 10, group 0 with method `turnon` is received by the Tellstick.
* `155` would be published to `command/8040222/10/0/dim` when a raw event with method `dim` and value `155` is received by the Tellstick.

## Properties

```properties
# MQTT
mqtt.host=MQTT server host
mqtt.port=MQTT server port
mqtt.username=MQTT server username
mqtt.password=MQTT server password
```