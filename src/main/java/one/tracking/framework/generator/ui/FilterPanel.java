package one.tracking.framework.generator.ui;

import org.vaadin.viritin.fields.MCheckBox;
import org.vaadin.viritin.fields.MTextField;
import org.vaadin.viritin.layouts.MHorizontalLayout;
import com.vaadin.data.HasValue;
import com.vaadin.data.HasValue.ValueChangeListener;
import com.vaadin.shared.ui.ValueChangeMode;
import com.vaadin.ui.CustomComponent;

@SuppressWarnings({"rawtypes", "unchecked"})
public class FilterPanel extends CustomComponent implements ValueChangeListener {

  private static final long serialVersionUID = 5774507591444615949L;

  @FunctionalInterface
  public interface FilterPanelObserver {

    void onFilterChange();
  }

  private final MCheckBox onlyOnMap = new MCheckBox("Only events in current viewport").withValue(true)
      .withValueChangeListener(this);

  private final MTextField title = new MTextField()
      .withPlaceholder("Filter by userId")
      .withValueChangeMode(ValueChangeMode.LAZY)
      .withValueChangeListener(this);

  private FilterPanelObserver observer = null;

  public FilterPanel() {
    setCompositionRoot(new MHorizontalLayout(this.onlyOnMap, this.title));
  }

  public boolean isOnlyOnMap() {
    return this.onlyOnMap.getValue();
  }

  public String getTitle() {
    return this.title.getValue();
  }

  public void setObserver(final FilterPanelObserver observer) {
    this.observer = observer;
  }

  @Override
  public void valueChange(final HasValue.ValueChangeEvent event) {
    this.observer.onFilterChange();
  }

}
