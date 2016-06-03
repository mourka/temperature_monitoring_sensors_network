
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;



public class Sink {

	private static String PropertiesFileName = null;
    private static int LeftPort;
    private static int RightPort;
    private static String AdminIP;
    private static int AdminPort;
    private static Node nodes[];
    private static int numberOfRightNodes;
    private static Timekeeper timeKeeper;
    private static SortedMap<Integer,Integer> readingCollection = new TreeMap<Integer,Integer>();
    

    public static void main(String[] args) throws IOException {
        // Check if a different server ip is present
        if (args.length < 1) {
            System.err.println("Must supply properties file name.");
            System.exit(1);
        }

        //PropertiesFileName = "C:/DistSys/Project/" + args[0];
        PropertiesFileName = "..\\" + args[0];
        // "C:\DistSys\Project" + args[0];
        //System.out.println("Properties file name is " + PropertiesFileName);

        getProperties();

        new ListenToAdmin().start();
        new SinkRightListener().start();
        

    } // end of main          

    // A forever running thread that listens to the right channel
    static class SinkRightListener extends Thread {

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
        int counter=0;
        static boolean timeout = false;
        Collection c; 
        Iterator itr;

        // constructor
        public SinkRightListener() {
        }
        // the part that listens for data and alarm messages and handles them

        @Override
        public void run() {
            //System.out.println("\nEntered the SinkRightListener");
            try {
                ServerSocket listenRightSocket = new ServerSocket(RightPort);

                while (true) {

                    Socket RightClientSocket = listenRightSocket.accept();

                    inRightPort = new DataInputStream(RightClientSocket.getInputStream());
                    outRightPort = new DataOutputStream(RightClientSocket.getOutputStream());

                    messagetype = inRightPort.readUTF();
                    

                    if (messagetype.equals("DATAPACKET")) {
                        senderId = inRightPort.readInt();
                        retransmit = inRightPort.readBoolean();
                        dataType = inRightPort.readUTF();
                        originalSenderId = inRightPort.readInt();
                        measurement = inRightPort.readInt();
                        
                        readingCollection.put(originalSenderId, measurement);

                        outRightPort.writeUTF("ACK");
                        outRightPort.flush();

                        printLogLine(Integer.toString(senderId),
                                "RCV", dataType,
                                Integer.toString(originalSenderId) + ":"
                                + Integer.toString(measurement));
                        
                        if(dataType.equals("DAT")){
                        	printLogLine(Integer.toString(senderId),
                                    "SND", "ACK","");
                        }
                        

                        
                        //Printing the data entries every 6 updates
                        counter++;
                        c = readingCollection.values();
                        if(counter==6){
                        	
                        	itr = c.iterator();
                        	while(itr.hasNext()){
                        		System.err.println(itr.next());
                        	}
                        	counter=0;
                        } 
                        
                        
                        // forward the alarm message to the admin application
                        if (dataType.equals("ALM")) {
                            Socket adminsocket = new Socket(AdminIP, AdminPort);
                            DataOutputStream adminOut = new DataOutputStream(adminsocket.getOutputStream());
                            adminOut.writeUTF(dataType);
                            adminOut.writeInt(originalSenderId);
                            adminOut.writeInt(measurement);
                            adminOut.flush();    
                            timeout=false;
                            timeKeeper = new Timekeeper(this, 10);
                            adminOut.close();
                            adminsocket.close();
                        }
                    } else {
                        System.err.println("not DATAPACKET ");
                    }
                } // end of try                
            } catch (IOException ex) {
                System.err.println(ex);
            }
        }// end of run
        
        public void setTimeout(){
            timeout = true;
            System.err.println("Timeout fired\n");
        }
        
    } // end of class SinkRightListener

    
    // A running thread that listens to the admin's channel
    static class ListenToAdmin extends Thread {

        DataInputStream inAdmin;
        DataOutputStream outAdmin;
        DataOutputStream toRightStream;
        Socket adminSocket;
        String listenInput;         
        String ackmsg;
        String[] adminDataPacketParts;
        Socket rightSocket;
        String SinkDataPacket;

        // constructor
        public ListenToAdmin() {
        }

        @Override
        public void run() {
            try {
                ServerSocket listenLeftSocket = new ServerSocket(LeftPort);
                while (true) {
                    adminSocket = listenLeftSocket.accept();

                    inAdmin = new DataInputStream(adminSocket.getInputStream());
                    outAdmin = new DataOutputStream(adminSocket.getOutputStream());

                    listenInput = inAdmin.readUTF();
                    
                    
                    switch (listenInput) {
                        case "AVG":
                            printLogLine("ADMIN", "RCV", "AVG", "");
                            outAdmin.writeUTF("AVG");
                            outAdmin.writeInt(getAverageTemperature());
                            outAdmin.flush();
                            break;
                        case "MAX":
                            printLogLine("ADMIN", "RCV", "MAX", "");
                            outAdmin.writeUTF("MAX");
                            outAdmin.writeInt(getMaximumTemperature());
                            outAdmin.flush();
                            break;
                        case "MIN":
                            printLogLine("ADMIN", "RCV", "MIN", "");
                            outAdmin.writeUTF("MIN");
                            outAdmin.writeInt(getMinimumTemperature());
                            outAdmin.flush();
                            break;
                        case "PRD":
                            int newPeriod;
                            newPeriod = inAdmin.readInt();
                            printLogLine("ADMIN", "RCV", "PRD", Integer.toString(newPeriod));
                            outAdmin.writeUTF("ACK");
                            outAdmin.flush();
                            // writing to the right channels
                            for (int i = 0; i < nodes.length; i++) {
                                rightSocket = new Socket(nodes[i].ip, nodes[i].port);
                                toRightStream = new DataOutputStream((rightSocket.getOutputStream()));
                                toRightStream.writeUTF("PRD");
                                toRightStream.writeUTF("SINK"); // write remote id
                                toRightStream.writeInt(newPeriod);
                                toRightStream.flush();
                            }
                            break;
                        case "THR":
                            int newTreshold;
                            newTreshold = inAdmin.readInt();
                            printLogLine("ADMIN", "RCV", "THR", Integer.toString(newTreshold));
                            outAdmin.writeUTF("ACK");
                            outAdmin.flush();
                            // writing to the right channels
                            for (int i = 0; i < nodes.length; i++) {
                                rightSocket = new Socket(nodes[0].ip, nodes[0].port);
                                toRightStream = new DataOutputStream((rightSocket.getOutputStream()));
                                toRightStream.writeUTF("THR");
                                toRightStream.writeUTF("SINK"); // write remote id
                                toRightStream.writeInt(newTreshold);
                                toRightStream.flush();
                            }
                            break;
                        case "ACK":
                        	printLogLine("ADMIN", "RCV", "ALM", "");
                        	timeKeeper.stopTimer();
                        	break;
                        default:
                            System.err.println("unknown input.");
                            break;
                    }
                }
            } // end of run
            catch (IOException ex) {
                System.err.println("Listen to Admin exception:" + ex.getMessage());
                System.err.println("Listen to Admin exception:" + ex.toString());
            }
        } // end of run
        
    }// end of class ListenToAdmin

    
    private static class Node {
        int id;
        String ip;
        int port;
    }

    private static void getProperties() {

        //System.out.println("Properties file name is " + PropertiesFileName);
        String RightChannelID[] = new String[]{};
        String RightChannelPort[] = new String[]{};
        String RightChannelIP[];

        // Read properties file.
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(PropertiesFileName));

        } catch (IOException e) {
            System.err.println("Error loading properties file" + e.toString());
            System.exit(1);
        }

        // get the properties from the properties file
        LeftPort = Integer.parseInt(properties.getProperty("LEFT_PORT"));
        //System.out.println("Left port number is " + LeftPort);
        RightPort = Integer.parseInt(properties.getProperty("RIGHT_PORT"));
        //System.out.println("Right port number is " + RightPort);
        AdminIP = properties.getProperty("ADMIN_IP");
        //System.out.println("Admin IP number is " + AdminIP);
        AdminPort = Integer.parseInt(properties.getProperty("ADMIN_PORT"));
        //System.out.println("Admin portnumber is " + AdminPort);

        // allocate temporary arrays for the split strings
        RightChannelID = properties.getProperty("RIGHT_CHANNEL_ID").split(";");
        RightChannelIP = properties.getProperty("RIGHT_CHANNEL_IP").split(";");
        RightChannelPort = properties.getProperty("RIGHT_CHANNEL_PORT").split(";");

        numberOfRightNodes = RightChannelID.length;
        nodes = new Node[numberOfRightNodes];

        //System.out.print("Fill array of nodes ");
        for (int i = 0; i < numberOfRightNodes; i++) {
            nodes[i] = new Node();
            nodes[i].id = Integer.parseInt(RightChannelID[i]);
            nodes[i].ip = RightChannelIP[i];
            nodes[i].port = Integer.parseInt(RightChannelPort[i]);
        }

    } // end of getProperties

    // structure for log line
    private static class Logline {

        String timestamp;
        String sender;
        String msg_type;
        String pck_type;
        String value;
    }

    private static void printLogLine(String sender, String type, String packet, String value) {
        SimpleDateFormat format = new SimpleDateFormat("HHmmssSSSS");
        Calendar calendar = Calendar.getInstance();
        Logline log = new Logline();
        java.util.Date now = calendar.getTime();
        String currentTime = format.format(now);
        log.sender = sender;
        log.timestamp = currentTime;
        log.msg_type = type;
        log.pck_type = packet;
        log.value = value;
        System.out.println(log.timestamp + "\t" + log.sender + "\t" + log.msg_type + "\t" + log.pck_type + "\t" + log.value + "\n");
    }

    private static int getMinimumTemperature() {
        int id;
        int reading;
        int minTemp = 9999;     
        
        //System.out.println("All key value is:\n" + readingCollection);
        Iterator iterator = readingCollection.keySet().iterator();
        while (iterator.hasNext()) {
            id = (Integer) iterator.next();
            //System.out.println("key : " + id + " value :" + readingCollection.get(id));
            reading = (Integer) readingCollection.get(id);
            if (reading < minTemp){
                minTemp = reading;
            }
        }
        return minTemp;
    }

    private static int getMaximumTemperature() {
        int id;
        int reading;
        int maxTemp = -9999;
        
        //System.out.println("All key value is:\n" + readingCollection);
        Iterator iterator = readingCollection.keySet().iterator();
        while (iterator.hasNext()) {
            id = (Integer) iterator.next();
            //System.out.println("key : " + id + " value :" + readingCollection.get(id));
            reading = (Integer) readingCollection.get(id);
            if (reading > maxTemp) {
                maxTemp = reading;
            }
        }
        return maxTemp;
    }

    private static int getAverageTemperature() {
        int id;
        int reading;
        int totalTemp = 0;
        
        //System.out.println("All key value is:\n" + readingCollection);
        Iterator iterator = readingCollection.keySet().iterator();
        while (iterator.hasNext()) {
            id = (Integer) iterator.next();
            //System.out.println("key : " + id + " value :" + readingCollection.get(id));
            reading = (Integer) readingCollection.get(id);
            totalTemp += reading;
        }
        return totalTemp / readingCollection.size();
    }
    
   

 // a class to keep the TIMER
 // http://www.java2s.com/Code/Java/Threads/ThreadReminder.htm
 private static class Timekeeper {

     private static Timer timer;
     private static SinkRightListener sinkrightlistener;

     public Timekeeper(SinkRightListener srl, int seconds) {
    	 sinkrightlistener = srl;
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
             System.err.format("TIMER HAS TIMED OUT\n");
             timer.cancel(); //Terminate the timer thread
             sinkrightlistener.setTimeout();
         }
     }
 } // end of Timekeeper
 
} // end of class Sink

