/**
 *
 */
package one.tracking.framework.generator.web;

import java.util.List;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import one.tracking.framework.generator.entity.SpatialEvent;
import one.tracking.framework.generator.repo.SpatialEventRepository;

/**
 * @author Marko Voß
 *
 */
@RestController
@RequestMapping
public class WebController {

  @Autowired
  private SpatialEventRepository repo;

  @RequestMapping("/find")
  public List<Coordinate> findCloseEvents() {
    return this.repo.findAllWithin(5d).stream().map(SpatialEvent::getLocation).map(Point::getCoordinate)
        .collect(Collectors.toList());
  }

  @RequestMapping("/dist")
  public List<Double> findDistances() {
    return this.repo.findAllWithin();
  }
}
