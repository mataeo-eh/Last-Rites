package com.lastrites;

import com.runeliteplugins.reporting.BugReportController;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.*;

/** Checks real sidebar controls, including the difference between saved preference and visibility. */
class SidebarControlsTest
{
    @Test
    void sampleChangesVisibilityWithoutCheckingAutomaticPreferenceAndSettingsAreEditable() throws Exception
    {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean enabled = new AtomicBoolean(true);
            AtomicBoolean full = new AtomicBoolean(true);
            AtomicInteger seconds = new AtomicInteger(7);
            LastRitesConfig config = mock(LastRitesConfig.class);
            when(config.popupEnabled()).thenAnswer(call -> enabled.get());
            when(config.lookupTotals()).thenAnswer(call -> full.get());
            when(config.duration()).thenAnswer(call -> seconds.get());
            when(config.excludeRegex()).thenReturn("");
            ConfigManager manager = mock(ConfigManager.class);
            doAnswer(call -> {
                switch ((String) call.getArgument(1))
                {
                    case "popupEnabled": enabled.set(call.getArgument(2)); break;
                    case "lookupTotals": full.set(call.getArgument(2)); break;
                    case "duration": seconds.set(call.getArgument(2)); break;
                    default: fail("Unexpected configuration key");
                }
                return null;
            }).when(manager).setConfiguration(eq(LastRitesConfig.GROUP), anyString(), any(Object.class));
            NoticeQueue state = new NoticeQueue();
            LastRitesPanel panel = new LastRitesPanel(state, config, manager,
                mock(BugReportController.class), () -> {});
            JDialog window = new JDialog();
            try
            {
                window.setContentPane(panel.getWrappedPanel());
                window.setSize(260, 750);
                window.setVisible(true);
                JCheckBox automatic = find(panel, "popupAutomatic", JCheckBox.class);
                automatic.doClick();
                assertFalse(enabled.get());
                find(panel, "popupSample", JButton.class).doClick();
                assertTrue(state.isVisible());
                assertFalse(automatic.isSelected());
                // Run the known Swing timer action, avoiding wall-clock sleeps in UI tests.
                java.lang.reflect.Field timerField = LastRitesPanel.class.getDeclaredField("timer");
                timerField.setAccessible(true);
                Timer timer = (Timer) timerField.get(panel);
                for (java.awt.event.ActionListener listener : timer.getActionListeners()) { listener.actionPerformed(null); }
                assertEquals("Pop-up: visible", find(panel, "popupVisibility", JLabel.class).getText());
                assertEquals("Hide pop-up", find(panel, "popupToggle", JButton.class).getText());
                JSpinner duration = find(panel, "popupDuration", JSpinner.class);
                assertEquals(7, duration.getValue());
                duration.setValue(0);
                assertEquals(0, seconds.get());
                find(panel, "popupFullTotals", JCheckBox.class).doClick();
                assertFalse(full.get());
                window.validate();
                BufferedImage image = new BufferedImage(260, 750, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                try { window.getContentPane().printAll(g); }
                finally { g.dispose(); }
                Path output = Path.of("build/previews/sidebar.png");
                Files.createDirectories(output.getParent());
                ImageIO.write(image, "png", output.toFile());
            }
            catch (Exception ex) { throw new AssertionError(ex); }
            finally { panel.stop(); window.dispose(); }
        });
    }

    private static <T extends Component> T find(Container container, String name, Class<T> type)
    {
        for (Component component : container.getComponents())
        {
            if (name.equals(component.getName())) { return type.cast(component); }
            if (component instanceof Container)
            {
                T nested = find((Container) component, name, type);
                if (nested != null) { return nested; }
            }
        }
        return null;
    }
}
