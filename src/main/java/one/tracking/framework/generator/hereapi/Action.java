/**
 *
 */
package one.tracking.framework.generator.hereapi;

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
public class Action {

  private String action;
  private Integer duration;
  private String instruction;
  private Integer offset;
}
