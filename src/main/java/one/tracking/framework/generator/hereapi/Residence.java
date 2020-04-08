/**
 *
 */
package one.tracking.framework.generator.hereapi;

import java.time.OffsetTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Residence {

  private OffsetTime time;
  private Place place;
}
