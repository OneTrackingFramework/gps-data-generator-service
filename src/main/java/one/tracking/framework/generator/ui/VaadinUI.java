package one.tracking.framework.generator.ui;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.vaadin.addon.leaflet.AbstractLeafletLayer;
import org.vaadin.addon.leaflet.LMap;
import org.vaadin.addon.leaflet.LOpenStreetMapLayer;
import org.vaadin.addon.leaflet.LTileLayer;
import org.vaadin.addon.leaflet.shared.Bounds;
import org.vaadin.addon.leaflet.shared.Point;
import org.vaadin.addon.leaflet.util.JTSUtil;
import org.vaadin.viritin.grid.MGrid;
import org.vaadin.viritin.label.MLabel;
import org.vaadin.viritin.layouts.MHorizontalLayout;
import org.vaadin.viritin.layouts.MVerticalLayout;
import com.vaadin.annotations.StyleSheet;
import com.vaadin.annotations.Theme;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;
import one.tracking.framework.generator.entity.SpatialEvent;
import one.tracking.framework.generator.repo.SpatialEventRepository;
import one.tracking.framework.generator.ui.FilterPanel.FilterPanelObserver;

/**
 * @author mstahv
 */
@Theme("valo")
@StyleSheet("vaadin://style.css")
@SpringUI
public class VaadinUI extends UI implements Window.CloseListener, FilterPanelObserver {

  private static final long serialVersionUID = 3326102015975811662L;

  private final SpatialEventRepository repo;

  private final Envelope envelope;

  public VaadinUI(final SpatialEventRepository repo, final Envelope envelope) {
    this.repo = repo;
    this.envelope = envelope;
  }

  private MGrid<SpatialEvent> table;
  private final LMap map = new LMap();
  private final LTileLayer osmTiles = new LOpenStreetMapLayer();

  private final FilterPanel filterPanel = new FilterPanel();

  @Override
  protected void init(final VaadinRequest request) {

    this.map.zoomToExtent(new Bounds(
        new Point(this.envelope.getMinX(), this.envelope.getMinY()),
        new Point(this.envelope.getMaxX(), this.envelope.getMaxY())));

    this.filterPanel.setObserver(this);
    this.table = new MGrid<>(SpatialEvent.class);
    this.table.withProperties("userId");
    this.table.setWidth("100%");
    this.table.setHeight("200px");
    this.table.addStyleName("mytable");

    this.table.addComponentColumn(spatialEvent -> {
      final OffsetDateTime ts = OffsetDateTime.ofInstant(spatialEvent.getTimestampCreate(),
          ZoneOffset.ofTotalSeconds(spatialEvent.getTimestampOffset()));
      return new MHorizontalLayout(new MLabel(ts.toString()));
    }).setCaption("Timestamp");

    this.table.addComponentColumn(spatialEvent -> {
      return new MHorizontalLayout(
          new MLabel(
              spatialEvent.getLocation().getCoordinate().y + " " + spatialEvent.getLocation().getCoordinate().x));
    }).setCaption("Location");

    // this.table.addComponentColumn(spatialEvent -> {
    // return new MHorizontalLayout(
    // new MButton(VaadinIcons.EDIT, e -> {
    // editInPopup(spatialEvent);
    // }).withStyleName(ValoTheme.BUTTON_BORDERLESS),
    // new MButton(VaadinIcons.TRASH, e -> {
    // this.repo.delete(spatialEvent);
    // loadEvents(this.filterPanel.isOnlyOnMap(), this.filterPanel.getTitle());
    // }).withStyleName(ValoTheme.BUTTON_BORDERLESS));
    // }).setCaption("Actions");

    loadEvents(this.filterPanel.isOnlyOnMap(), this.filterPanel.getTitle());

    this.osmTiles.setAttributionString("© OpenStreetMap Contributors");

    final MVerticalLayout foo = new MVerticalLayout(this.filterPanel, this.table)
        .alignAll(Alignment.MIDDLE_LEFT);

    final MVerticalLayout mainLayout = new MVerticalLayout(foo).alignAll(Alignment.MIDDLE_LEFT);

    // if (Page.getCurrent().getBrowserWindowWidth() > 800) {
    // mainLayout.expand(new MHorizontalLayout().expand(this.map).add(this.table).withFullHeight());
    // } else {
    // in mobile devices, layout out vertically
    mainLayout.expand(this.map);
    // }

    setContent(mainLayout);

    this.map.addMoveEndListener(event -> {
      if (this.filterPanel.isOnlyOnMap()) {
        onFilterChange();
      }
    });

  }

  private void loadEvents(final boolean onlyInViewport, final String userIdContains) {

    List<SpatialEvent> events;
    if (this.map.getBounds() != null) {
      final Polygon polygon = toPolygon(this.map.getBounds());
      events = this.repo.findAllWithin(polygon, "%" + userIdContains + "%");
    } else {
      events = this.repo.findAll();
    }

    /* Populate table... */
    this.table.setRows(events);

    /* ... and map */
    this.map.removeAllComponents();
    this.map.addBaseLayer(this.osmTiles, "OSM");
    for (final SpatialEvent spatialEvent : events) {
      addEventVector(spatialEvent.getLocation(), spatialEvent);
      // addEventVector(spatialEvent.getRoute(), spatialEvent);
    }
    if (!this.filterPanel.isOnlyOnMap()) {
      this.map.zoomToContent();
    }
  }

  private Polygon toPolygon(final Bounds bounds) {
    final GeometryFactory factory = new GeometryFactory();
    final double north = bounds.getNorthEastLat();
    final double south = bounds.getSouthWestLat();
    final double west = bounds.getSouthWestLon();
    final double east = bounds.getNorthEastLon();
    final Coordinate[] coords = new Coordinate[] {new Coordinate(east, north), new Coordinate(east, south),
        new Coordinate(west, south), new Coordinate(west, north), new Coordinate(east, north)};
    // GeoDb does not support LinerRing intersection, but polygon ?!
    final LinearRing lr = factory.createLinearRing(coords);
    final Polygon polygon = factory.createPolygon(lr, null);
    polygon.setSRID(4326);
    return polygon;
  }

  private void addEventVector(final Geometry g, final SpatialEvent spatialEvent) {
    if (g != null) {
      /*
       * JTSUtil will make LMarker for point event, LPolyline for events with route
       */
      final AbstractLeafletLayer layer = (AbstractLeafletLayer) JTSUtil.toLayer(g);

      /* Add click listener to open event editor */
      // layer.addClickListener(e -> {
      // this.editor.setEntity(spatialEvent);
      // this.editor.focusFirst();
      // this.editor.openInModalPopup();
      // });
      this.map.addLayer(layer);
    }
  }

  @Override
  public void addWindow(final Window window) throws IllegalArgumentException,
      NullPointerException {
    super.addWindow(window);
    window.addCloseListener(this);
  }

  @Override
  public void windowClose(final Window.CloseEvent e) {
    // refresh table after edit
    loadEvents(this.filterPanel.isOnlyOnMap(), this.filterPanel.getTitle());
  }

  @Override
  public void onFilterChange() {
    loadEvents(this.filterPanel.isOnlyOnMap(), this.filterPanel.getTitle());
  }

}
