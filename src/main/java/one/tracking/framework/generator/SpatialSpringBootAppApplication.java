package one.tracking.framework.generator;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collections;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class SpatialSpringBootAppApplication {

  @Value("${app.envelope.start}")
  private String envStart;

  @Value("${app.envelope.end}")
  private String envEnd;

  public static void main(final String[] args) {
    SpringApplication.run(SpatialSpringBootAppApplication.class, args);
  }

  @Bean
  GeometryFactory geometryFactory() {
    return new GeometryFactory(new PrecisionModel(), 4326);
  }

  @Bean
  RestTemplate restTemplate() {
    final BufferingClientHttpRequestFactory buff =
        new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory());
    final RestTemplate restTemplate = new RestTemplate(buff);
    restTemplate.setInterceptors(Collections.singletonList(new LoggingRequestInterceptor()));

    return restTemplate;
  }

  @Bean
  DateTimeFormatter dateTimeFormatter() {
    return new DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME) // use the existing formatter for date time
        .appendOffset("+HH:MM", "+00:00") // set 'noOffsetText' to desired '+00:00'
        .toFormatter();
  }

  @Bean
  WKTReader wKTReader(final GeometryFactory geometryFactory) {
    return new WKTReader(geometryFactory);
  }

  @Bean
  Envelope envelope(final WKTReader wKTReader) throws ParseException {
    return new Envelope(
        wKTReader.read(this.envStart).getCoordinate(),
        wKTReader.read(this.envEnd).getCoordinate());
  }
}
