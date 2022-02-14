package gov.noaa.nwfsc.watchAlarms;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * @author Paul McElhany
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class App {
	boolean packFrame = false;
	
	public App(){
		try{
			UIManager.setLookAndFeel(
            UIManager.getCrossPlatformLookAndFeelClassName());
		}
		catch(Exception e){
			System.out.println("Error: " + e);
		}

		WatchAlarmFrame frame = new WatchAlarmFrame();
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	    //Validate frames that have preset sizes
	    //Pack frames that have useful preferred size info, e.g. from their layout
	    if (packFrame) {
	      frame.pack();
	    }
	    else {
	      frame.validate();
	    }
	    //Center the window
	    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
	    Dimension frameSize = frame.getSize();
	    if (frameSize.height > screenSize.height) {
	      frameSize.height = screenSize.height;
	    }
	    if (frameSize.width > screenSize.width) {
	      frameSize.width = screenSize.width;
	    }
	    frame.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
	    frame.setVisible(true);
	}

	public static void main(String[] args) {
		new App();
	}
}
