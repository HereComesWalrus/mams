package jadelab2;

import jade.core.AID;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

class SchedulerGui extends JFrame {	
	private SchedulerAgent myAgent;
	
	private boolean schedule = false;
	
	SchedulerGui(SchedulerAgent a) {
		super(a.getLocalName());
		
		myAgent = a;
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(2, 2));
		//p.add(new JLabel("Title:"));
		//titleField = new JTextField(15);
		//p.add(titleField);
		getContentPane().add(p, BorderLayout.CENTER);
		
		JButton addButton = new JButton(" Schedule ");
		addButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				try {
					//String title = titleField.getText().trim();
					schedule = true;
					myAgent.initiateNegotiation(schedule);
					schedule=false;
				}
				catch (Exception e) {
					JOptionPane.showMessageDialog(SchedulerGui.this, "Invalid values. " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
				}
			}
		} );
		p = new JPanel();
		p.add(addButton);
		getContentPane().add(p, BorderLayout.SOUTH);
		
		addWindowListener(new	WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				myAgent.doDelete();
			}
		} );
		
		setResizable(false);
	}
	
	public void display() {
		pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int centerX = (int)screenSize.getWidth() / 2;
		int centerY = (int)screenSize.getHeight() / 2;
		setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
		setVisible(true);
	}	
}
