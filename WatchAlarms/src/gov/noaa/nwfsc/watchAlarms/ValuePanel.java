package gov.noaa.nwfsc.watchAlarms;

import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class ValuePanel extends JPanel{
	public JTextField valueTextField = new JTextField();
	private Dimension valueTextBoxDim = new Dimension(200, 21);
	
	public ValuePanel(String label) {
		this.setLayout(new FlowLayout(FlowLayout.LEFT));
		this.add( new JLabel(label + " "));
		this.valueTextField.setPreferredSize(valueTextBoxDim);
		this.add(valueTextField);
	}
	
	public double getDouble(){
		return Double.parseDouble(valueTextField.getText());
	}
	
	public int getInt() {
		return Integer.parseInt(valueTextField.getText());
	}
	
	public void setValueText(String s){
		valueTextField.setText(s);
	}
	
	public String getText(){
		return valueTextField.getText();
	}
}
