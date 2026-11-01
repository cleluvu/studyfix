package vn.studyfix.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.awt.Font;

final class UiStyle {
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    private UiStyle() { }
    static void pad(JComponent component) { component.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8)); }
    static JButton button(String text) { JButton b = new JButton(text); b.setFocusable(false); return b; }
    static JTextArea monoArea() { JTextArea area = new JTextArea(); area.setFont(MONO); area.setTabSize(4); return area; }
}
