package one.tracking.framework.generator.repo;

import java.util.List;
import org.locationtech.jts.geom.Geometry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import one.tracking.framework.generator.entity.SpatialEvent;

/**
 * @author mstahv
 */
public interface SpatialEventRepository extends JpaRepository<SpatialEvent, Long> {

  /**
   * Example method of a GIS query. This uses Hibernate spatial extensions, so it does not work with
   * other JPA implementations.
   *
   * @param bounds the geometry
   * @param titleFilter the filter string
   * @return SpatialEvents inside given geometry and with given filter for the title
   */
  @Query(value = "SELECT se FROM SpatialEvent se WHERE within(se.location, :bounds) = true AND se.userId LIKE :filter")
  List<SpatialEvent> findAllWithin(@Param("bounds") Geometry bounds, @Param("filter") String filter);

  @Query(value = "SELECT b FROM SpatialEvent a, SpatialEvent b WHERE dwithin(a.location, b.location, :distance) = true")
  List<SpatialEvent> findAllWithin(@Param("distance") Double distance);

  @Query(value = "SELECT DISTANCE(a.location, b.location) FROM SpatialEvent a, SpatialEvent b")
  List<Double> findAllWithin();

}
