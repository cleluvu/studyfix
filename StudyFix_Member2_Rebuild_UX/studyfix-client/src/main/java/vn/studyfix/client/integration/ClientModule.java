package vn.studyfix.client.integration;

import javax.swing.JComponent;

/** One module contributes a tab to the Swing application; the caller owns lifecycle. */
public interface ClientModule {
    String tabTitle();
    JComponent view();
}
