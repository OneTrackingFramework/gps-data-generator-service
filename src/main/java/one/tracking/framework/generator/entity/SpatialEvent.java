package one.tracking.framework.generator.entity;

import java.io.Serializable;
import java.time.Instant;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Version;
import org.locationtech.jts.geom.Point;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class SpatialEvent implements Serializable, Cloneable {

  private static final long serialVersionUID = 4068404547785866893L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Version
  private int version;

  private String userId;

  @Basic
  private Instant timestampCreate;

  private int timestampOffset;

  // @Column(columnDefinition = "POINT") // this type is known by MySQL
  @Column(columnDefinition = "geometry")
  private Point location;

  // @Column(columnDefinition = "POLYGON") // this type is known by MySQL
  // @Column(columnDefinition = "geometry")
  // private LineString route;

}
