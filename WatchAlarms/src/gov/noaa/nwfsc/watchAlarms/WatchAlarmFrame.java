package gov.noaa.nwfsc.watchAlarms;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class WatchAlarmFrame extends JFrame implements ActionListener{
	private GridBagConstraints gbc;
	private DirPathPanel dirPathPanel;
	private FilePathPanel contactsPanel;
	private ValuePanel subjectPanel;
	private JButton startButton;
	private WatchDir watchDir;
	private String defaultAlarmFolderPath = "C:\\Users\\MOATS\\Documents\\Alarms";
	private String defaultContactsFilePath = "C:\\Users\\MOATS\\Documents\\AlarmWatch Program\\Chamber Contacts.csv";
	private String defaultSubjectText = "Chamber Alarm";

	
	public WatchAlarmFrame() {
		this.setSize(new Dimension(800, 250));
	    this.setTitle("Watch and Text");
	    this.setLayout(new GridBagLayout());
		gbc = new GridBagConstraints();
		gbc.weightx = 100;
		gbc.weighty = 100;
		
		dirPathPanel = new DirPathPanel();
		dirPathPanel.setText(defaultAlarmFolderPath);
		this.add(dirPathPanel,0,0,1,1);
		
		contactsPanel = new FilePathPanel("Contacts File", true, false);
		contactsPanel.setText(defaultContactsFilePath);
		this.add(contactsPanel,0,1,1,1);
		
		subjectPanel = new ValuePanel("Email Subject Line");
		subjectPanel.setValueText(defaultSubjectText);
		this.add(subjectPanel,0,2,1,1);
		
		startButton = new JButton("Start");
		startButton.addActionListener(this);
		this.add(startButton,0,3,1,1);
	}
	
	private void add(Component c, int x, int y, int w, int h){
		gbc.gridx = x;
		gbc.gridy = y;
		gbc.gridwidth = w;
		gbc.gridheight = h;
		this.add(c, gbc);
	}
	
	public void actionPerformed(ActionEvent e) {		
		Object source = e.getSource();
		if(source == startButton){
			Path dir = Paths.get(dirPathPanel.getPath());
			String[] contactEmails = getContactEmail(contactsPanel.getPath());
			try {
				watchDir = new WatchDir(dir, false, contactEmails, subjectPanel.getText());
				watchDir.processEvents();
			}
			catch (IOException x) {
                System.out.println(x);
            }
		}
	}
	private String[] getContactEmail(String path) {
		BufferedReader inStream;
		String s = "";
		String[] contactEmail = null;
		StringTokenizer t;
		int nRows = 0;
		try{
			//find out how long the data file is
			inStream = new BufferedReader(new FileReader(path));
			s = inStream.readLine();
			nRows = 0;
			while((s = inStream.readLine()) != null){
				nRows++;
			}
			inStream.close();
			
			contactEmail = new String[nRows];
			
			inStream = new BufferedReader(new FileReader(path));
			s = inStream.readLine();
			for(int i = 0; i < contactEmail.length; i++){
				s = inStream.readLine();
				t = new StringTokenizer(s, ",");
				t.nextToken();
				t.nextToken();
				contactEmail[i] = t.nextToken();
			}
			
			inStream.close();
		}
		catch(Exception e){
			System.out.println("Error: " + e);
		}
		return contactEmail;
	}

}