The Temperature Monitoring Sensor Network is a network consisting of three different types of nodes: admin, sink and sensors. The Sensor nodes are responsible for measuring periodically the temperature of the environment. They forward these measurements to the Sink node, in which they are stored. If a temperature exceeds a predefined threshold then the Sensors send an alarm message to the Sink node. The Sink forwards all the alarm messages to the Admin node, so that the user will be informed about them. Finally, the user has the possibility to request through the admin for some information.
The user can use the Admin user interface to request a change to the temperature threshold and the measurement period of the Sensors.

Technology used: Java and TCP sockets

Properties files: The sockets read from the properties files

Remaining Bug: Messages from the Admin node suddenly stop reaching the Sink node, the Sink doesn’t receive the requests for change and information that the user is sending from the Admin console.

