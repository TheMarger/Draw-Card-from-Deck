
import javax.swing.*;
import java.awt.*;

public class Main {
	public static void main(String[] args) {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
		    e.printStackTrace();
		});

		SwingUtilities.invokeLater(() -> {
			

		    JFrame window = new JFrame("Card Drawer");
		    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		    window.setResizable(false);

		    gamePanel gp = new gamePanel();
		    window.add(gp);

		    window.pack();
		    window.setLocationRelativeTo(null);
		    window.setVisible(true);

		    gp.setupGame();
		});
		
	}
}
