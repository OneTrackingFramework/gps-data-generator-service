/**
 *
 */
package one.tracking.framework.generator.service;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.shape.random.RandomPointsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import one.tracking.framework.generator.entity.SpatialEvent;
import one.tracking.framework.generator.hereapi.Route;
import one.tracking.framework.generator.hereapi.RouteResult;
import one.tracking.framework.generator.hereapi.Section;
import one.tracking.framework.generator.repo.SpatialEventRepository;
import one.tracking.framework.generator.util.PolylineEncoderDecoder;
import one.tracking.framework.generator.util.PolylineEncoderDecoder.LatLngZ;

/**
 * @author Marko Voß
 *
 */
@Service
public class DataService implements DisposableBean {

  private static final Logger LOG = LoggerFactory.getLogger(DataService.class);

  @Autowired
  private SpatialEventRepository repository;

  @Autowired
  private GeometryFactory geometryFactory;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  private DateTimeFormatter formatter;

  @Autowired
  private Envelope envelope;

  @Autowired
  private WKTReader wktReader;

  @Value("${app.apiKey}")
  private String apiKey;

  @Value("${app.storage.file}")
  private String dbFile;

  @Value("${app.create.amount.users}")
  private int amountOfUsers;

  @Value("${app.create.amount.travels}")
  private int amountOfTravels;

  @EventListener
  private void handle(final ApplicationStartedEvent event) throws Exception {
    importData();
    generateData();
    LOG.info("Done.");
  }

  @Override
  public void destroy() throws Exception {
    LOG.info("Destroy...");
    exportData();
  }

  /**
   *
   * @param amount
   * @return
   */
  private List<UUID> createUserIds(final int amount) {
    final List<UUID> uuids = new ArrayList<>();
    for (int i = 0; i < amount; i++) {
      uuids.add(UUID.randomUUID());
    }
    return uuids;
  }

  /**
   * @throws ParseException
   *
   */
  private void generateData() throws ParseException {
    LOG.info("Generating data...");

    final List<UUID> users = createUserIds(this.amountOfUsers);

    /*
     * TODO use application parameter for start time
     */
    final OffsetDateTime startTime = LocalDateTime.of(2020, 3, 10, 6, 0).atOffset(ZoneOffset.UTC);

    for (final UUID uuid : users) {

      final RandomPointsBuilder builder = new RandomPointsBuilder(this.geometryFactory);
      builder.setExtent(this.envelope); // Limit generated point to the envelope
      builder.setNumPoints(this.amountOfTravels + 1); // Generate travels within this envelope

      final MultiPoint multiPoint = (MultiPoint) builder.getGeometry();

      Coordinate previous = null;
      for (final Coordinate coord : multiPoint.getCoordinates()) {

        if (previous == null) {
          previous = coord;
          continue;
        }

        performRouteRequest(uuid, startTime, previous, coord);
        previous = coord;
      }
    }

  }

  /**
   *
   * @param userId
   * @param startTime
   * @param start
   * @param end
   */
  private void performRouteRequest(final UUID userId, final OffsetDateTime startTime, final Coordinate start,
      final Coordinate end) {

    System.out.println(this.formatter.format(startTime));

    final RouteResult result = this.restTemplate.getForObject(
        "https://router.hereapi.com/v8/routes?transportMode={mode}&origin={latA},{lonA}&destination={latB},{lonB}&return={return}&apiKey={apiKey}",
        RouteResult.class,
        "pedestrian",
        start.x,
        start.y,
        end.x,
        end.y,
        "polyline",
        this.apiKey);

    final List<LatLngZ> positions = getPositions(result);

    /*
     * TODO: use application parameter for time increase.
     *
     * Currently we visit each position after 5 minutes. (no real time calculation done)
     */
    int minutesAdd = 0;

    for (final LatLngZ position : positions) {

      final OffsetDateTime current = startTime.plusMinutes(minutesAdd);
      minutesAdd += 5;

      this.repository.save(SpatialEvent.builder()
          .userId(userId.toString())
          .location(this.geometryFactory.createPoint(new Coordinate(position.lng, position.lat)))
          .timestampCreate(current.toInstant())
          .timestampOffset(current.getOffset().getTotalSeconds())
          .build());
    }
  }

  /**
   *
   * @param result
   * @return
   */
  private List<LatLngZ> getPositions(final RouteResult result) {

    if (result.getRoutes() == null || result.getRoutes().isEmpty())
      return Collections.emptyList();

    final Route route = result.getRoutes().get(0);

    if (route.getSections() == null || route.getSections().isEmpty())
      return Collections.emptyList();

    final Section section = route.getSections().get(0);

    return PolylineEncoderDecoder.decode(section.getPolyline());
  }

  /**
   *
   * @throws IOException
   * @throws ParseException
   * @throws NumberFormatException
   */
  private void importData() throws IOException, NumberFormatException, ParseException {

    final Path path = Paths.get(this.dbFile);
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      return;

    LOG.info("Importing data from: {} ...", this.dbFile);

    try (final CSVParser parser = new CSVParser(new FileReader(path.toFile()), CSVFormat.EXCEL)) {

      final List<CSVRecord> records = parser.getRecords();

      boolean first = true;
      for (final CSVRecord record : records) {

        if (first) {
          first = false;
          continue;
        }

        this.repository.save(SpatialEvent.builder()
            .id(Long.parseLong(record.get(0)))
            .version(Integer.parseInt(record.get(1)))
            .userId(record.get(2))
            .timestampCreate(Instant.parse(record.get(3)))
            .timestampOffset(Integer.parseInt(record.get(4)))
            .location((Point) this.wktReader.read(record.get(5)))
            .build());
      }
    }

    LOG.info("Importing done.");
  }

  private void exportData() throws IOException {

    final List<SpatialEvent> events = this.repository.findAll();
    if (events.isEmpty())
      return;

    LOG.info("Exporting data to: {} ...", this.dbFile);

    final Path path = Paths.get(this.dbFile);
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      Files.createFile(path);

    final WKTWriter pointWriter = new WKTWriter(2);

    try (final CSVPrinter printer = new CSVPrinter(new FileWriter(path.toFile()), CSVFormat.EXCEL)) {

      printer.printRecord("id", "version", "userId", "timestamp", "offset", "point");

      for (final SpatialEvent event : events) {

        printer.printRecord(
            event.getId(),
            event.getVersion(),
            event.getUserId(),
            event.getTimestampCreate(),
            event.getTimestampOffset(),
            pointWriter.write(event.getLocation()));
      }
    }

    LOG.info("Exporting done.");
  }

}
