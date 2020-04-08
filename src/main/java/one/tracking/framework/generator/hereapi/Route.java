/**
 *
 */
package one.tracking.framework.generator.hereapi;

import java.util.List;
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
public class Route {

  private String id;

  private List<Section> sections;
}
