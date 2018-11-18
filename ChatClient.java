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
    public void printMessage(final String message) {
        chatArea.append(message);
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
        System.out.println("Boas");
        outToServer.writeBytes(message + '\n');
    }


    // Main method of the object
    public void run() throws IOException {

    }


    // Instantiates the ChatClient object and starts it invoking the run() method
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
