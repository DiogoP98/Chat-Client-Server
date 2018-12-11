import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;


public class ChatServer {
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    static final private String ANS_PATTERN_MESSAGE = "MESSAGE [name] [message]\n";
    static final private String ANS_PATTERN_NEWNICK = "NEWNICK [old] [new]\n";
    static final private String ANS_PATTERN_JOINED  = "JOINED [name]\n";
    static final private String ANS_PATTERN_LEFT    = "LEFT [name]\n";
    static final private String ANS_PATTERN_PRIVATE = "PRIVATE [name] [message]\n";

    static private HashSet<String> userNameSet = null;
    static private Selector selector = null;

    static public void main(String args[]) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt(args[0]);

        userNameSet = new HashSet<String>();

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // The selecter allows us to analize one or more connection and determine
            // which are ready to read/write
            selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            while(true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                // If we don't have any activity, loop around and wait again
                if(selector.select() == 0)
                    continue;

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while(it.hasNext()) {
                    // Get a key representing one of bits of I/O activity
                    SelectionKey key = it.next();

                    // What kind of activity is it?
                    if((key.readyOps() & SelectionKey.OP_ACCEPT) ==
                        SelectionKey.OP_ACCEPT) {

                        Socket s = ss.accept();
                        System.out.println("Got connection from " + s);

                        // It's an incoming connection.  Register this socket with
                        // the Selector so we can listen for input on it
                        SocketChannel sc = s.getChannel();
                        
                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        sc.configureBlocking(false);

                        // Register it with the selector, for reading
                        sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                    }
                    else if((key.readyOps() & SelectionKey.OP_READ) ==
                        SelectionKey.OP_READ) {

                        SocketChannel sc = null;

                        if(key.attachment() == null)
                            key.attach(new User());

                        User user = (User)key.attachment();

                        try {
                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel)key.channel();

                            System.out.println("Received a message");

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if(!processInput(user, sc, selector)) {
                                userNameSet.remove(user.getNick());

                                key.cancel();

                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    System.out.println("Closing connection to " + s);
                                    s.close();
                                } catch( IOException ie) {
                                    System.err.println("Error closing socket " + s + ": " + ie);
                                }
                            }

                        } catch(IOException ie) {
                            // On exception, remove this channel from the selector
                            userNameSet.remove(user.getNick());

                            key.cancel();

                            try {
                                sc.close();
                            } catch(IOException ie2) {
                                System.out.println(ie2);
                            }

                            System.out.println("Closed " + sc);
                        }
                    }
                }
                // key processed, remove from ready state queue
                it.remove();
            }
        } catch(IOException ie) {
            System.err.println(ie);
        }
    }


    // Just read the message from the socket and send it to stdout
    static private boolean processInput(User user, SocketChannel sc, Selector selector) throws IOException {
        // Read the message to the buffer
        buffer.clear();
        sc.read(buffer);
        buffer.flip();

        // If no data, close the connection
        if(buffer.limit()==0) {
            if(user.getState() == User.State.INSIDE)
                sendRoomMessage(user.getNick(), user.getRoom(), ANS_PATTERN_LEFT.replace("[name]", user.getNick()), false);
            
            return false;
        }

        // Decode and print the message to stdout
        String message = "";
        message += decoder.decode(buffer).toString();

        user.cacheMessage(message);
        if(!message.endsWith("\n")){
            System.out.println("Caching message: " + message);
            return true;
        }

        String fullMessage = user.getFullMessage();
        String[] lines = fullMessage.split("\n");
        System.out.println("Processing message: " + fullMessage);

        for(int i=0; i!=lines.length; i++) {
            message = lines[i];
            String[] messageTokens = message.split(" ");

            switch(user.getState()) {
                case INIT:
                    if(messageTokens[0].equals("/nick") && messageTokens.length == 2) {
                        if(userNameSet.contains(messageTokens[1]))
                            sendMessage(Common.ANS_ERROR, sc);
                        else {
                            userNameSet.add(messageTokens[1]);
                            user.setNick(messageTokens[1]);
                            user.setState(User.State.OUTSIDE);
                            sendMessage(Common.ANS_OK, sc);
                        }
                    }
                    else if(messageTokens[0].equals("/bye")) {
                        sendMessage(Common.ANS_BYE, sc);
                        return false;
                    }
                    else
                        sendMessage(Common.ANS_ERROR, sc);

                    break;
                case OUTSIDE:
                    if(messageTokens[0].equals("/nick") && messageTokens.length == 2) {
                        if(userNameSet.contains(messageTokens[1]))
                            sendMessage(Common.ANS_ERROR, sc);
                        else {
                            userNameSet.add(messageTokens[1]);
                            userNameSet.remove(user.getNick());
                            user.setNick(messageTokens[1]);
                            sendMessage(Common.ANS_OK, sc);
                        }
                    }
                    else if(messageTokens[0].equals("/join") && messageTokens.length == 2) {
                        message = ANS_PATTERN_JOINED.replace("[name]", user.getNick());
                        sendRoomMessage(user.getNick(), messageTokens[1], message, false);
                        user.setRoom(messageTokens[1]);
                        user.setState(User.State.INSIDE);
                        sendMessage(Common.ANS_OK, sc);
                    }
                    else if(messageTokens[0].equals("/priv") && messageTokens.length > 2) {
                        String receiver = messageTokens[1];

                        if(!userNameSet.contains(receiver))
                            sendMessage(Common.ANS_ERROR, sc); // if receiver doesn't exist, send error to sender
                        else {
                            message = message.substring(message.indexOf(messageTokens[2]));
                            message = ANS_PATTERN_PRIVATE.replace("[name]", user.getNick()).replace("[message]", message);
                            sendPrivateMessage(message, receiver);
                            sendMessage(Common.ANS_OK, sc);
                        }
                    }
                    else if(messageTokens[0].equals("/bye")) {
                        sendMessage(Common.ANS_BYE, sc);
                        userNameSet.remove(user.getNick());
                        return false;
                    }
                    else
                        sendMessage(Common.ANS_ERROR, sc);
                    
                    break;
                case INSIDE:
                    if(messageTokens[0].equals("/nick") && messageTokens.length == 2) {
                        if(userNameSet.contains(messageTokens[1]))
                            sendMessage(Common.ANS_ERROR, sc);
                        else {
                            message = ANS_PATTERN_NEWNICK.replace("[old]",user.getNick()).replace("[new]",messageTokens[1]);
                            sendRoomMessage(user.getNick(), user.getRoom(), message, false);
                            sendMessage(Common.ANS_OK, sc);
                            userNameSet.remove(user.getNick());
                            userNameSet.add(messageTokens[1]);
                            user.setNick(messageTokens[1]);
                        }
                    }
                    else if(messageTokens[0].equals("/join") && messageTokens.length == 2) {
                        message = ANS_PATTERN_LEFT.replace("[name]", user.getNick());
                        sendRoomMessage(user.getNick(), user.getRoom(), message, false);
                        user.setRoom(messageTokens[1]);
                        message = ANS_PATTERN_JOINED.replace("[name]", user.getNick());
                        sendRoomMessage(user.getNick(), user.getRoom(), message, false);
                        sendMessage(Common.ANS_OK, sc);
                    }
                    else if(messageTokens[0].equals("/priv") && messageTokens.length > 2) {
                        String receiver = messageTokens[1];

                        if(!userNameSet.contains(receiver))
                            sendMessage(Common.ANS_ERROR, sc); // receiver doesn't exist, throw error to sender
                        else {
                            message = message.substring(message.indexOf(messageTokens[2]));
                            message = ANS_PATTERN_PRIVATE.replace("[name]", user.getNick()).replace("[message]", message);
                            sendPrivateMessage(message, receiver);
                            sendMessage(Common.ANS_OK, sc);
                        }
                    }
                    else if(messageTokens[0].equals("/leave")) {
                        message = ANS_PATTERN_LEFT.replace("[name]", user.getNick());
                        sendRoomMessage(user.getNick(), user.getRoom(), message, false);
                        user.setState(User.State.OUTSIDE);
                        sendMessage(Common.ANS_OK, sc);
                    }
                    else if(messageTokens[0].equals("/bye")) {
                        message = ANS_PATTERN_LEFT.replace("[name]", user.getNick());
                        sendRoomMessage(user.getNick(), user.getRoom(), message, false);
                        sendMessage(Common.ANS_BYE, sc);
                        userNameSet.remove(user.getNick());
                        return false;
                    }
                    else if(!messageTokens[0].matches("/[^/]*")) {
                        if(message.startsWith("//"))
                            message = message.substring(1); // escape first '/''

                        message = ANS_PATTERN_MESSAGE.replace("[name]", user.getNick()).replace("[message]", message);
                        sendRoomMessage(user.getNick(), user.getRoom(), message, true);
                    }
                    else
                        sendMessage(Common.ANS_ERROR, sc);

                    break;
                default:
                    sendMessage(Common.ANS_ERROR, sc);
            }
        }

        return true;
    }

    static private void sendMessage(String message, SocketChannel sc) throws IOException {
        buffer.clear();
        buffer.put(message.getBytes("UTF-8"));
        buffer.flip();

        System.out.println("Sending: " + message);

        while(buffer.hasRemaining())
            sc.write(buffer);
    }

    static private void sendRoomMessage(String username, String room, String message, boolean sendMessageToSender) throws IOException {
        Iterator<SelectionKey> it = selector.keys().iterator();

        while(it.hasNext()) {
            SelectionKey key = it.next();

            User user = (User)key.attachment();
            if(user == null                                 ||
                user.getState() != User.State.INSIDE        ||
                !user.getRoom().equals(room)                ||
                (user.getNick().equals(username) && !sendMessageToSender)) // sendMessageToSender is false if the message is a command
                continue;

            SocketChannel sc = (SocketChannel)key.channel();
            sendMessage(message, sc);
        }
    }

    static private void sendPrivateMessage(String message, String to) throws IOException {
        Iterator<SelectionKey> it = selector.keys().iterator();

        while(it.hasNext()) {
            SelectionKey key = it.next();

            User user = (User)key.attachment();
            if(user == null || !user.getNick().equals(to))
                continue;

            SocketChannel sc = (SocketChannel)key.channel();
            sendMessage(message, sc);
            break;
        }
    }
}
