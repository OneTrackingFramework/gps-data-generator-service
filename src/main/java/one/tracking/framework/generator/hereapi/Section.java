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
public class Section {

  private String id;
  private String type;
  private List<Action> actions;
  private Residence departure;
  private Residence arrival;
  private String polyline;
}
