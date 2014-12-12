/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package answeringmachine;

import static answeringmachine.Constants.RECORDED_CALLS_PATH;
import configuration.*;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.gstreamer.Bus;
import org.gstreamer.GstObject;
import pipelines.Receiver;
import pipelines.Transmitter;

/**
 * @author Martin
 * @review Baptiste
 */
public final class ProcessCall {

    //-------------------------------------------------------------------------+
    //|************************    VARIABLES     ******************************|
    //+------------------------------------------------------------------------+
    private int port = -1;
    private final String filePath;
    private Receiver receiver;
    private final Config properties = new Config();

    /**
     * Process a call arriving on the answering machine.
     *
     * @param caller is the name of the caller.
     * @param clientIP is the IP of the caller.
     * @param clientPort is the port of the caller.
     */
    public ProcessCall(String caller, String clientIP, int clientPort) {

        //Prepare the file path to save the new voice message.
        filePath = String.format(Constants.RECORDED_CALLS_FILENAME_FORMAT, caller, getDate());

        //Prepare a pipeline to receive the voice message.
        receiver = new Receiver(filePath);
        System.out.println("Receiver " + filePath + " OK.");

        //Read properties to get the path of the greeting message.
        String greeting = "Greeting" + "/" + properties.getProperties("greeting");
        System.out.println("Greeting " + greeting + " OK.");

        //Transmit the Greeting message
        final Transmitter sender = new Transmitter(greeting, clientIP, clientPort);
        sender.getBus().connect(new Bus.EOS() {
            @Override
            //When the Greeting will be finished ...
            public void endOfStream(GstObject source) {
                if (receiver != null) {
                    System.out.println("Biiiip - Start recordnig now... ");
                    receiver.play();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ProcessCall.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        });

        //Start the pipeline.
        sender.play();

        //Console log
        System.out.println("Transmission OK." + sender.getState());

        //Store the port number of the receiver.
        this.port = receiver.getPort();
    }

    /**
     * Get the port number of the receiver.
     *
     * @return the port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Stop recording the voice message.
     *
     * @param recipient is the name of the recipient for the email.
     * @param caller is the name of the caller.
     */
    public void stopRecording(String recipient, String caller) {

        if (receiver != null) {
            if (receiver.isPlaying()) {
                receiver.stop();
                try {
                    Thread.sleep(700);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ProcessCall.class.getName()).log(Level.SEVERE, null, ex);
                }
                receiver = null;
                try {

                    //Read properties to get the Greeting time.
                    double greetingTime = Double.parseDouble(properties.getProperties("greetingTime"));

                    //Trim the message to solve our gost buffer issu.
                    TrimmAudio.TrimmAudio(filePath, greetingTime);

                } catch (NumberFormatException ex) {
                    Logger.getLogger(ProcessCall.class.getName()).log(Level.SEVERE, null, ex);
                }

                //Send an email notification to the user.
                sendEmail(recipient, caller);

                //Move the new voice message to the InBox folder.
                File f = new File(filePath);
                File p = new File(RECORDED_CALLS_PATH);
                if (f.exists() && !f.isDirectory() && p.exists() && p.isDirectory()) {
                    try {
                        Files.move(Paths.get(filePath), Paths.get(RECORDED_CALLS_PATH + File.separator + filePath), REPLACE_EXISTING);
                    } catch (IOException ex) {
                        Logger.getLogger(ProcessCall.class.getName()).log(Level.SEVERE, null, ex);
                    }
                        //f.renameTo(new File(RECORDED_CALLS_PATH + File.separator + filePath));
                        System.out.println("New message in InBox folder OK.");
                }
                
            } else {
                receiver.stop();
                receiver = null;
                try {
                    File file = new File(filePath);
                    if (file.delete()) {
                        System.out.println(file.getName() + " is deleted!");
                    } else {
                        System.out.println("Delete operation is failed.");
                    }
                } catch (Exception e) {
                    Logger.getLogger(ProcessCall.class.getName()).log(Level.SEVERE, null, e);
                }
            }
        }
    }

    /**
     * Send an email to the user.
     *
     * @param recipient is the name of the recipient.
     * @param caller is the name of the caller.
     */
    public void sendEmail(String recipient, String caller) {

        Properties props = new Properties();
        props.setProperty("mail.smtp.host", "mailhost.ludd.ltu.se");
        Session session = Session.getDefaultInstance(props, null);

        try {
            Message msg = new MimeMessage(session);
            String htmlBody = "Hello,\n in your Answering machine is new voice message from: " + caller + ". You can find it in attachement.\n\nHave a nice day :)";
            File f = new File(filePath);
            byte[] attachmentData = Files.readAllBytes(f.toPath());

            Multipart mp = new MimeMultipart();

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/plain");
            mp.addBodyPart(htmlPart);

            MimeBodyPart attachment = new MimeBodyPart();
            attachment.setFileName(f.getName());
            attachment.setContent(attachmentData, "audio/mpeg3");
            mp.addBodyPart(attachment);

            msg.setFrom(new InternetAddress(Constants.SIP_NAME + "@ltu.se", Constants.SIP_NAME));

            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient, ""));
            msg.setSubject("New VoiceMail in AnsweringMachine from: " + caller);
            msg.setContent(mp);
            Transport.send(msg);

        } catch (AddressException e) {
            System.out.println(e);
        } catch (MessagingException e) {
            System.out.println(e);
        } catch (UnsupportedEncodingException ex) {
            System.out.println(ex);
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    /**
     * Get the current Time and Date
     *
     * @return the Date as a string
     */
    public String getDate() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

}
