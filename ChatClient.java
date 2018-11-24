import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variables related to the GUI
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // Variables related to the network
    private Socket clientSocket;
    private DataOutputStream outToServer;
    private BufferedReader inFromServer;
    // End of variables


    // Add message to the chatbox
    public void printMessage(String message) {
        message = message.replace("\n", "");
        String[] tokens = message.split(" ");
        
        if(tokens[0].equals(Common.ANS_MESSAGE)) {
            message = message.replaceFirst(Common.ANS_MESSAGE, "").replaceFirst(tokens[1], "");
            message = tokens[1] + ": " + message.substring(message.indexOf(tokens[2]));
        }
        else if(tokens[0].equals(Common.ANS_NEWNICK))
            message = tokens[1] + " mudou de nome para " + tokens[2];
        else if(tokens[0].equals(Common.ANS_JOINED))
            message = tokens[1] + " juntou-se à sala";
        else if(tokens[0].equals(Common.ANS_LEFT))
            message = tokens[1] + " saiu da sala";
        else if(tokens[0].equals(Common.ANS_PRIVATE)) {
            message = message.replaceFirst(Common.ANS_PRIVATE, "").replaceFirst(tokens[1], "");
            message = "MP: " + tokens[1] + ": " + message.substring(message.indexOf(tokens[2]));
        }

        System.out.println("Printing in chat box: " + message + "\n");
        chatArea.append(message + "\n");
    }

    private class messageListeningSocket implements Runnable {   
        public messageListeningSocket() {};

        public void run() {
            try {
                boolean connected = true;
                
                while(connected) {
                    String message = inFromServer.readLine() + "\n"; // readLine() removes end of line
                    System.out.print("Received: " + message);

                    if(message.equals("null\n")){
                        System.out.println("Lost connection to server");
                        break;
                    }
                    
                    if(message.equals(Common.ANS_BYE))
                        connected = false;
                    
                    printMessage(message);
                }

                System.out.println("Exiting");
                clientSocket.close();
                System.exit(0);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }


    // Constructor
    public ChatClient(String server, int port) throws IOException {
        // Initiate the GUI
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });

        // Initiate the network communication
        clientSocket = new Socket(server, port);
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }


    // Method invoked when the user sends a message from the text box
    public void newMessage(String message) throws IOException {
        message = message.trim();
        outToServer.write((message + "\n").getBytes("UTF-8"));
        System.out.println("Sent message: " + message);
    }


    // Main method of the object
    public void run() throws IOException {
        new Thread(new messageListeningSocket()).start();
    }


    // Instantiates the ChatClient object and starts it invoking the run() method
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
