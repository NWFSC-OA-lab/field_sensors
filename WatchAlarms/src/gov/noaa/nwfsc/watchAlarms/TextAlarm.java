package gov.noaa.nwfsc.watchAlarms;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;




public class TextAlarm {
	String[] contactEmail;
	String subjectLine;
	
	public TextAlarm(String[] contacts, String subject) {
		contactEmail = contacts;
		subjectLine = subject;
	}
	
	private String getFileText(String filePath) {
		System.out.println(filePath);
		
		BufferedReader inStream;
		String s = "";
		try{
			TimeUnit.SECONDS.sleep(5);
			inStream = new BufferedReader(new FileReader(filePath));
			s = inStream.readLine();
			inStream.close();
		}
		catch(Exception e){
			System.out.println("Error: " + e);
		}
		System.out.println(s);
		return s;	
	}
	
	public void sendText(String filePath){	
		  String textMessage = this.getFileText(filePath);
		  //String textMessage = filePath;
	      String from = "paul.mcelhany@noaa.gov";
	      String pass = "zclicgfjdxzjsprf"; 
	      String[] to = contactEmail;
	      String subject = subjectLine;
	      
	      this.sendFromGMail(from, pass, to, subject, textMessage);
	}
	
	private void sendFromGMail(String from, String pass, String[] to, String subject, String body) {
        Properties props = System.getProperties();
        String host = "smtp.gmail.com";
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.user", from);
        props.put("mail.smtp.password", pass);
        props.put("mail.smtp.port", "25");
        props.put("mail.smtp.auth", "true");

        Session session = Session.getDefaultInstance(props);
        MimeMessage message = new MimeMessage(session);

        try {
            message.setFrom(new InternetAddress(from));
            InternetAddress[] toAddress = new InternetAddress[to.length];

            // To get the array of addresses
            for( int i = 0; i < to.length; i++ ) {
                toAddress[i] = new InternetAddress(to[i]);
            }

            for( int i = 0; i < toAddress.length; i++) {
                message.addRecipient(Message.RecipientType.TO, toAddress[i]);
            }

            message.setSubject(subject);
            message.setText(body);
            Transport transport = session.getTransport("smtps");
            transport.connect(host, from, pass);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();
        }
        catch (AddressException ae) {
            ae.printStackTrace();
        }
        catch (MessagingException me) {
            me.printStackTrace();
        }	    
	}

}
