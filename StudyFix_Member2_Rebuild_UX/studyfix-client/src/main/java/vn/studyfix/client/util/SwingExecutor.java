package vn.studyfix.client.util;

import javax.swing.SwingUtilities;

public final class SwingExecutor {
    private SwingExecutor() { }
    public static void run(Runnable task) { if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task); }
}
