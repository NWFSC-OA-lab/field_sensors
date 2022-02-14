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

public class DirPathPanel extends JPanel implements ActionListener{
	private JTextField valueTextField = new JTextField();
	private Dimension valueTextBoxDim = new Dimension(400, 21);
	private JButton setDirButton;
	private JFileChooser chooser;
	private String label = "Watch Directory";
	
	public DirPathPanel(){

		this.setLayout(new FlowLayout(FlowLayout.LEFT));
		this.add( new JLabel(label + " "));
		this.valueTextField.setPreferredSize(valueTextBoxDim);
		this.add(valueTextField);
		setDirButton = new JButton("Set Directory...");
		setDirButton.addActionListener(this);
		this.add(setDirButton);
		chooser = new JFileChooser();
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	}
	
	public void actionPerformed(ActionEvent e){
		chooser.showOpenDialog(this);	
		valueTextField.setText(chooser.getSelectedFile().getPath());
	}
	public String getPath(){
		return valueTextField.getText();
	}
	
	public void setCurrentDirectory(String path){
		chooser.setCurrentDirectory(new File(path));
	}
	public void setText(String t) {
		valueTextField.setText(t);
	}
}

