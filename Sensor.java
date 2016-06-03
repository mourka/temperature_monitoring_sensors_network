

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Sensor {

	    private static String PropertiesFileName = null;
	    private static int id;
	    private static int period;
	    private static int threshold;
	    private static int LeftPort;
	    private static int RightPort;
	    private static Node rightNodes[];
	    private static int numberOfRightNodes;
	    private static Node leftNodes[];
	    private static int numberOfLeftNodes;

	    public static void main(String[] args) throws IOException {
	        if (args.length < 1) {
	            System.err.println("Must supply properties file name.");
	            System.exit(1);
	        }

	        //PropertiesFileName = "C:/DistSys/Project/" + args[0];
	        PropertiesFileName = "..\\" + args[0];
	        // "C:\DistSys\Project" + args[0];
	        //System.out.println("Properties file name is " + PropertiesFileName);

	        getProperties();

	        //        THREADS
	        // listen to the left channel
	        new TransceiverLeftListener().start();
	        // take periodical measurements
	        new TemperatureSensor().start();
	        new TransieverRightListener().start();

	    } // end of main

	    // A forever running thread that listens to the left channel
	    static class TransceiverLeftListener extends Thread {

	        DataInputStream inLeftPort;
	        Socket LeftClientSocket;
	        String input;
	        String remote;
	        int value;
	        String delimiter = "[ ]";
	        String[] sinkDataPacketParts;
	        String SinkDataPacket;
	        ServerSocket ss;
	 
	        public TransceiverLeftListener() {
	        }
	        // the part that receives the configuration commands, applies them to itself and then forwards them to all right nodes
	        @Override
	        public void run() {
	            try {
	                ServerSocket listenLeftSocket = new ServerSocket(LeftPort);
	                //System.out.println("listening for CONF on right" + LeftPort);
	                while (true) {
	                    try {

	                        Socket LeftClientSocket = listenLeftSocket.accept();

	                        //System.out.println("\nstart listen to sink thread");
	                        inLeftPort = new DataInputStream(LeftClientSocket.getInputStream());

	                        input = inLeftPort.readUTF();
	                        remote = inLeftPort.readUTF();        

	                        switch (input) {
	                            case "PRD":
	                                value = inLeftPort.readInt();     
	                                printLogLine(Integer.toString(id), remote, "RCV", "PRD", Integer.toString(value));
	                                period = value;
	                                printLogLine(Integer.toString(id), Integer.toString(id), "SET", "PRD", Integer.toString(value));
	                                break;
	                            case "THR":
	                                value = inLeftPort.readInt();
	                                printLogLine(Integer.toString(id), remote, "RCV", "THR", Integer.toString(value));
	                                threshold = value;
	                                printLogLine(Integer.toString(id), Integer.toString(id), "SET", "THR", Integer.toString(value));
	                                break;
	                            default:
	                                System.err.println("unknown input.");
	                                System.exit(1);
	                                break;
	                        }

	                    } catch (IOException ex) {
	                        System.err.println( ex.getMessage());
	                    }

	                    // forward the configuration commands to all the right nodes
	                    for (int i = 0; i < numberOfRightNodes; i++) {
	                        //System.out.println("forwarding CONF right" + rightNodes[i].ip + " " + rightNodes[i].port);
	                        Socket rightSocket = new Socket(rightNodes[i].ip, rightNodes[i].port);
	                        DataOutputStream rightOut = new DataOutputStream(rightSocket.getOutputStream());
	                        rightOut.writeUTF(input);
	                        rightOut.writeUTF(remote); 
	                        rightOut.writeInt(value);
	                        rightOut.flush();
	                        rightOut.close();
	                        rightSocket.close();
	                    }
	                }
	            } // end of run
	            catch (IOException ex) {
	                System.err.println("run:" + ex.getMessage());
	            }
	        } // end of run
	    }// end of class TransceiverLeftListener

	    // A thread that listens to the right channel
	    static class TransieverRightListener extends Thread {

	        DataInputStream inRightPort;
	        DataOutputStream outRightPort;
	        Socket RightClientSocket;
	        String sinkDataPacket;
	        String messagetype;
	        int senderId;
	        boolean retransmit;
	        String dataType;
	        int originalSenderId;
	        int measurement;

	        // constructor
	        public TransieverRightListener() {
	        }
	        // the part that listens for data and alarm from the right nodes messages and handles them

	        @Override
	        public void run() {
	            //System.out.println("\nEntered the NewTransieverRightListener");
	            try {
	                ServerSocket listenRightSocket = new ServerSocket(RightPort);

	                while (true) {

	                    Socket RightClientSocket = listenRightSocket.accept();

	                    inRightPort = new DataInputStream(RightClientSocket.getInputStream());
	                    outRightPort = new DataOutputStream(RightClientSocket.getOutputStream());

	                    messagetype = inRightPort.readUTF();

	                    temperatureReading reading = new temperatureReading();

	                    if ( messagetype.equals("DATAPACKET") ) {
	                        reading.fromId = inRightPort.readInt();
	                        reading.retransmit = inRightPort.readBoolean();
	                        reading.type = inRightPort.readUTF();
	                        reading.originalId = inRightPort.readInt();
	                        reading.measurement = inRightPort.readInt();

	                        outRightPort.writeUTF("ACK");
	                        outRightPort.flush();

	                        reading.toIP = new String[leftNodes.length];
	                        reading.toPort = new int[leftNodes.length];

	                        for (int i = 0; i < leftNodes.length; i++) {
	                            reading.toIP[i] = leftNodes[i].ip;
	                            reading.toPort[i] = leftNodes[i].port;
	                        }

	                         new Forwarder(reading ).start();

	                    } else {
	                        System.err.println("not DATAPACKET ");
	                    }
	                } // end of try
	            } catch (IOException ex) {
	                System.err.println(ex);
	            }
	        }// end of run
	    } // end of class TransieverRightListener

	    // a thread forwards a data reading or alarm, follows up on acknowledgments
	    // and retransmits if necessary
	    static class Forwarder extends Thread {

	        static Socket leftSocket;
	        static DataOutputStream output;
	        static DataInputStream input;
	        static temperatureReading reading;
	        static String IPnumbers[];
	        static int portNumbers[];
	        static Timekeeper timer;
	        static boolean timeout;
	        static boolean receivedAck;

	        // constructor
	        public Forwarder(temperatureReading tr) {
	            reading = tr;
	            IPnumbers = tr.toIP;
	            portNumbers = tr.toPort;
	            receivedAck = false;
	            timeout = true;
	        }

	        @Override
	        public void run() {

	            int arrayLength = IPnumbers.length;
	            int i = 0; // current node
	            while ( !receivedAck & i <= arrayLength ) {
	                //System.out.println("\nin the nodes loop, nr " + i);
	                if (timeout) {
	                    transmit(i);
	                    i++;
	                    timeout = false;
	                } // end of if
	            } // end of while

	            //repeat once more for alarm messages that haven't received an acknowledgment
	            if (reading.type.equals("ALM") & reading.retransmit & !receivedAck) {
	                reading.retransmit = false;
	                new Forwarder(reading).start();
	            }
	        } // end of run

	        // writes the measurement-packet to the left node with the nodeNumber and waits for an ACK
	        private void transmit(int nodeNumber){
	            try {
	                leftSocket = new Socket(IPnumbers[nodeNumber], portNumbers[nodeNumber]);
	                output = new DataOutputStream(leftSocket.getOutputStream());
	                input = new DataInputStream(leftSocket.getInputStream());
	                timer = new Timekeeper(this,5);

	                output.writeUTF("DATAPACKET");              // type of transmission
	                output.writeInt(id);                        // this node's id
	                output.writeBoolean(reading.retransmit);    // retransmit or not
	                output.writeUTF(reading.type);              // is it alarm or data
	                output.writeInt(reading.originalId);        // the originating node's id
	                output.writeInt(reading.measurement);       // the measurement itself
	                output.flush();

	                printLogLine(Integer.toString(id),
	                        leftNodes[nodeNumber].id  ==  0 ? "SINK" : Integer.toString(leftNodes[nodeNumber].id), 
	                        "SND",
	                        reading.type,
	                        Integer.toString(reading.originalId) + ":" + Integer.toString(reading.measurement));
	               
	                String answer;
	                answer = input.readUTF();
	                if (answer.equals("ACK")) {
	                    receivedAck = true;
	                    if(reading.type=="DAT"){
	                    	printLogLine(Integer.toString(id),"SINK","RCV","ACK","");
	                    }
	                    timer.stopTimer();
	                }

	            } catch (IOException ex) {
	                System.err.println(ex);
	            } finally {
	                try {
	                    output.close();
	                    leftSocket.close();
	                } catch (IOException ex) {
	                    Logger.getLogger(Sensor.class.getName()).log(Level.SEVERE, null, ex);
	                }
	             } // end of try

	        } // end of transmit

	        public void setTimeout(){
	            timeout = true;
	            System.err.println("Timeout fired"); 
	        }
	    } // end of class Forwarder

	    // A forever running thread that periodically takes measurements
	    static class TemperatureSensor extends Thread {

	        static Socket leftSocket;
	        static DataOutputStream output;

	        // constructor
	        public TemperatureSensor() {
	        }

	        @Override
	        public void run() {

	            while (true) {

	                try {
	                    Thread.sleep(period * 1000);
	                    //System.out.format("\nTIME FOR A NEW MEASUREMENT in " + period);
	                    temperatureReading reading = new temperatureReading();
	                    reading.toIP = new String[leftNodes.length];
	                    reading.toPort = new int[leftNodes.length];

	                    for (int i = 0; i < leftNodes.length; i++) {
	                        reading.toIP[i] = leftNodes[i].ip;
	                        reading.toPort[i] = leftNodes[i].port;
	                    }

	                    reading.fromId = id;
	                    reading.measurement = getMeasurement();
	                    if (reading.measurement >= threshold) {
	                        reading.type = "ALM";
	                        reading.retransmit = true;
	                    } else {
	                        reading.type = "DAT";
	                        reading.retransmit = false;
	                    }
	                    reading.originalId = id;

	                    printLogLine(Integer.toString(id),
	                            Integer.toString(id), "GEN", reading.type,
	                            Integer.toString(id) + ":" + Integer.toString(reading.measurement));

	                    //System.out.format("\nThe new temperature " + reading.measurement);

	                    new Forwarder(reading ).start();

	                } catch (InterruptedException ex) {
	                    System.err.println(ex);
	                }
	            } // end of run
	        } // end of run
	    } // end of class TemperatureSensor


	    // a structure to hold information about a temperature reading and information for forwarding
	    private static class temperatureReading {
	        // information for forwarding purpose
	        String toIP[];        // the IP the packet is forwarded next
	        int toPort[];         // the port that will be used
	        int fromId;          // the id of the node the packet is coming from
	        boolean retransmit; // value for the ALM packet to guard that it is retransmitted only once and then drop
	        // strictly about the reading
	        String type;        // the type can be DAT or ALM
	        int originalId;             // the id of the originating node
	        int measurement;    // the heat measurement itself
	    }

	    // a structure to hold information about a node
	    private static class Node {

	        int id;
	        String ip;
	        int port;
	        int lastTemperature;
	    }

	    private static void getProperties() {

	        //System.out.println("Properties file name is " + PropertiesFileName);

	        String RightChannelID[] = new String[]{};
	        String RightChannelPort[] = new String[]{};
	        String RightChannelIP[] = new String[]{};

	        String LeftChannelID[] = new String[]{};
	        String LeftChannelPort[] = new String[]{};
	        String LeftChannelIP[] = new String[]{};

	        // Read properties file.
	        Properties properties = new Properties();
	        try {
	            properties.load(new FileInputStream(PropertiesFileName));

	        } catch (IOException e) {
	            System.err.println("Error loading properties file" + e.toString());
	            System.exit(1);
	        }

	        // get the properties from the properties file
	        id = Integer.parseInt(properties.getProperty("ID"));
	        //System.out.println("The id number is " + id);
	        period = Integer.parseInt(properties.getProperty("PERIOD"));
	        //System.out.println("The period is " + period);
	        threshold = Integer.parseInt(properties.getProperty("THRESHOLD"));
	        //System.out.println("The threshold is " + threshold);

	        // get left and right port numbers
	        LeftPort = Integer.parseInt(properties.getProperty("LEFT_PORT"));
	        //System.out.println("Left port number is " + LeftPort);
	        RightPort = Integer.parseInt(properties.getProperty("RIGHT_PORT"));
	        //System.out.println("Right port number is " + RightPort);


	        // allocate temporary arrays for the split strings
	        if (!properties.getProperty("RIGHT_CHANNEL_ID").equals("")) {
	            RightChannelID = properties.getProperty("RIGHT_CHANNEL_ID").split(";");
	            RightChannelIP = properties.getProperty("RIGHT_CHANNEL_IP").split(";");
	            RightChannelPort = properties.getProperty("RIGHT_CHANNEL_PORT").split(";");
	        }


	        // find the right channels
	        numberOfRightNodes = RightChannelID.length;
	        rightNodes = new Node[numberOfRightNodes];
	        //System.out.println("number of right channels " + RightChannelID.length);

	        //System.out.print("\nFill array of right nodes ");
	        for (int i = 0; i < numberOfRightNodes; i++) {
	            rightNodes[i] = new Node();
	            if (!RightChannelID[i].equals("")) {                         
	                rightNodes[i].id = Integer.parseInt(RightChannelID[i]);
	            }
	            rightNodes[i].ip = RightChannelIP[i];
	            if (!RightChannelID[i].equals("")) {
	                rightNodes[i].port = Integer.parseInt(RightChannelPort[i]);
	            }
	            rightNodes[i].lastTemperature = 0;                                
	        }

	        // allocate temporary arrays for the split strings
	        LeftChannelID = properties.getProperty("LEFT_CHANNEL_ID").split(";");
	        LeftChannelIP = properties.getProperty("LEFT_CHANNEL_IP").split(";");
	        LeftChannelPort = properties.getProperty("LEFT_CHANNEL_PORT").split(";");
	        //System.out.println("\n number of left channels " + LeftChannelID.length);

	        // find the right channels
	        numberOfLeftNodes = LeftChannelID.length;
	        leftNodes = new Node[numberOfLeftNodes];

	        //System.out.print("\nFill array of left nodes ");
	        for (int i = 0; i < numberOfLeftNodes; i++) {
	            leftNodes[i] = new Node();
	            if (!LeftChannelID[i].equals("")) {
	                leftNodes[i].id = Integer.parseInt(LeftChannelID[i]);
	            }
	            leftNodes[i].ip = LeftChannelIP[i];
	            leftNodes[i].port = Integer.parseInt(LeftChannelPort[i]);
	            leftNodes[i].lastTemperature = 0;

	            //System.out.print("\nafter properties: " + leftNodes[0].ip );
	        }
	    } // end of getProperties

	    // structure for log line
	    private static class Logline {

	        String timestamp;
	        String id;          // the id of the current node
	        String remote;      // the node the message will be sent to
	        String msg_type;    // i.e. SND, RCV
	        String pck_type;    // i.e. ALM, DAT,THR
	        String value;       // heat measurement, only used for DAT and ALM types
	    }

	    private static void printLogLine(String sender, String remote, String type, String packet, String value) {
	        SimpleDateFormat format = new SimpleDateFormat("HHmmssSSSS");
	        Calendar calendar = Calendar.getInstance();
	        Logline log = new Logline();
	        java.util.Date now = calendar.getTime();
	        String currentTime = format.format(now);
	        log.id = sender;
	        log.remote = remote;
	        log.timestamp = currentTime;
	        log.msg_type = type;
	        log.pck_type = packet;
	        log.value = value;
	        System.out.println(log.timestamp + "\t" + log.id + "\t" + log.remote + "\t" + log.msg_type + "\t" + log.pck_type + "\t" + log.value + "\n");
	    }

	    // finds a random temperature with a mean of 25 and standard deviation of 5
	    // http://javamex.com/tutorials/random_numbers/gaussian_distribution_2.shtml
	    private static int getMeasurement() {
	        Random r = new Random();
	        double val = r.nextGaussian() * 5 + 25;
	        int randomTemperature = (int) Math.round(val);
	        return randomTemperature;
	    }

	    // a class to keep the TIMER
	    // http://www.java2s.com/Code/Java/Threads/ThreadReminder.htm
	    private static class Timekeeper {

	        private static Timer timer;
	        private static Forwarder forwarder;

	        public Timekeeper(Forwarder fw, int seconds) {
	            forwarder = fw;
	            timer = new Timer();
	            timer.schedule(new RemindTask(), seconds * 1000, seconds * 10000);
	            //System.out.println("timer started: " + seconds );
	        }

	        public void stopTimer() {
	            timer.cancel();
	            //System.out.println("timer has been stopped ");
	        }

	        class RemindTask extends TimerTask {

	            public void run() {
	                //System.out.format("TIMER HAS TIMED OUT");
	                timer.cancel(); //Terminate the timer thread
	                forwarder.setTimeout();
	            }
	        }
	    } // end of Timekeeper
	
}
