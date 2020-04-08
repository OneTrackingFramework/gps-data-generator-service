# gps-data-generator-service
Generates userId and gps data of pedestrians moving around randomly within an envelope.

This project is based on: https://github.com/mstahv/spring-boot-spatial-example utilizing the flexible polyline encoder/decoder implementation of: https://github.com/heremaps/flexible-polyline for Java.

![Example data](https://github.com/OneTrackingFramework/gps-data-generator-service/blob/master/image.png)

# Configuration

| Property | Description |
| --- | --- |
| app.apiKey | The API key to be used for the HERE Routing API |
| app.storage.file | The location of the CSV file to store already calculated routes to on server shutdown and load the at the next server startup. For example: ${java.io.tmpdir}/db.csv |
| app.envelope.start | The starting point of the envelope to generate points within. For example: POINT (lat lon) |
| app.envelope.end | The ending point of the envelope to generate points within. For example: POINT (lat lon) |
| app.create.amount.users | The amount of UUIDs to generate used as userIds. Minimum: 0 (no generation of points) |
| app.create.amount.travels | The amount of travels to generate per userId. Minimum: 1 (this will generate two points and calculate the route between these two points) |
