package gov.noaa.nwfsc.watchAlarms;


import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * @author paul mcelhany
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FilePathPanel extends JPanel implements ActionListener{
	private JTextField valueTextField = new JTextField();
	private Dimension valueTextBoxDim = new Dimension(400, 21);
	private JButton setFileButton;
	private JFileChooser fChooser;
	private boolean isOpenChooser;
	private boolean isMultiSelection;
	
	public FilePathPanel(String label, boolean isOpen, boolean isMultipleSelection){
		isOpenChooser = isOpen;
		isMultiSelection = isMultipleSelection;
		this.setLayout(new FlowLayout(FlowLayout.LEFT));
		this.add( new JLabel(label + " "));
		this.valueTextField.setPreferredSize(valueTextBoxDim);
		this.add(valueTextField);
		setFileButton = new JButton("Set File...");
		setFileButton.addActionListener(this);
		this.add(setFileButton);
		fChooser = new JFileChooser();
	}
	
	public void actionPerformed(ActionEvent e){
		if(isOpenChooser){
			fChooser.setMultiSelectionEnabled(isMultiSelection);
			fChooser.showOpenDialog(this);
			if(isMultiSelection){
				String[] paths = this.getNames();
				String s = "";
				for(int i = 0; i < paths.length; i++){
					s += paths[i] + ", ";
				}
				s = s.substring(0, s.length()-2);
				valueTextField.setText(s);
			}
			else{
				valueTextField.setText(fChooser.getSelectedFile().getPath());
			}
			
		}
		else{		
			fChooser.showSaveDialog(this);
			valueTextField.setText(fChooser.getSelectedFile().getPath());
		}
		
	}
	public String getPath(){
		return valueTextField.getText();
	}
	public String[] getPaths(){
		File[] files = fChooser.getSelectedFiles();
		String[] s = new String[files.length];
		for(int i = 0; i < files.length; i++){
			s[i] = files[i].getPath();
		}
		return s;
	}
	public String[] getNames(){
		File[] files = fChooser.getSelectedFiles();
		String[] s = new String[files.length];
		for(int i = 0; i < files.length; i++){
			s[i] = files[i].getName();
		}
		return s;
	}

	
	public void setCurrentDirectory(String path){
		fChooser.setCurrentDirectory(new File(path));
	}
	 
	public void setText(String t) {
		valueTextField.setText(t);
	}	
}