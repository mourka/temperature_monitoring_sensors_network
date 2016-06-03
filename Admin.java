

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Admin {

    private static BufferedReader stdIn;
    private static int PortNumber;
    private static Socket socketSink = null;
    private static String SinkIP;
    private static int SinkPort;
    private static String PropertiesFileName;
    private static DataInputStream in = null;
    private static Timekeeper timeKeeper;
    private static FileOutputStream logFile;
    private static PrintStream logPrint;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Must supply properties file name.");
            System.exit(1);
        }         

        //PropertiesFileName = "C:/DistSys/Project/" + args[0];
        PropertiesFileName = "..\\" + args[0];
        // "C:\DistSys\Project" + args[0];
        //System.out.println("Properties file name is " + PropertiesFileName);

        //logFile = new FileOutputStream("C:/DistSys/Project/AdminLog.log");
        logFile = new FileOutputStream("..\\AdminLog.log");
        logPrint = new PrintStream(logFile);

        getProperties();

        // start the threads
        new UserInput().start();
        new AdminRightListener().start();


    } // end of main  

    // A thread that reads the user's input and sends
    // it to the sink
    private static class UserInput extends Thread {

        private String userInput = null;
        DataOutputStream out;
        String msg;
        int value;
        String variableIinput;
        DataOutputStream dataos;

        private UserInput() {
        }

        @Override
        public void run() {
            try {
                while (true) {
                    //System.out.println("Start UserInput");

                    //System.out.println("Type Message or (\"quit\" to quit)");
                    stdIn = new BufferedReader(new InputStreamReader(System.in));
                    userInput = stdIn.readLine();

                    try {


                        socketSink = new Socket(SinkIP, SinkPort);     

                        out = new DataOutputStream(socketSink.getOutputStream());
                        in = new DataInputStream(socketSink.getInputStream());
                        // buffer to read the user input


                        switch (userInput) {
                            case "avg":
                                timeKeeper = new Timekeeper(this, 10);
                                // write to sink
                                out.writeUTF("AVG");
                                out.flush();
                                printLogLine("SND", "AVG");
                                // read from sink
                                msg = in.readUTF();
                                value = in.readInt();
                                
                                if (msg.equals("AVG")) {
                                    timeKeeper.stopTimer();
                                    printLogLine("RCV", "AVG", Integer.toString(value));
                                }
                                // else timer goes off                            
                                break;
                            case "min":
                                timeKeeper = new Timekeeper(this, 10);
                                // write to sink
                                out.writeUTF("MIN");
                                out.flush();
                                printLogLine("SND", "MIN");
                                // read from sink
                                msg = in.readUTF();
                                value = in.readInt();
                                if (msg.equals("MIN")) {
                                    timeKeeper.stopTimer();
                                    printLogLine("RCV", "MIN", Integer.toString(value));
                                }
                                // else timer goes off                            
                                break;
                            case "max":
                                timeKeeper = new Timekeeper(this, 10);
                                // write to sink
                                out.writeUTF("MAX");
                                out.flush();
                                printLogLine("SND", "MAX");
                                // read from sink
                                msg = in.readUTF();
                                value = in.readInt();
                                if (msg.equals("MAX")) {
                                    timeKeeper.stopTimer();
                                    printLogLine("RCV", "MAX", Integer.toString(value));
                                }
                                // else timer goes off                            
                                break;
                            case "conf per": // configure period
                                System.err.print("enter period in seconds: ");
                                variableIinput = stdIn.readLine();
                                printLogLine("SND", "PRD", variableIinput);
                                timeKeeper = new Timekeeper(this, 10);
                                // write to sink
                                out.writeUTF("PRD");
                                out.writeInt(Integer.parseInt(variableIinput));
                                out.flush();
                                // read from sink
                                msg = in.readUTF();
                                if (msg.equals("ACK")) {
                                    timeKeeper.stopTimer();
                                    printLogLine("RCV", "ACK", "");
                                }
                                // else timer goes off                            
                                break;
                            case "conf th": // configure threshold
                                System.err.print("enter threshold in degrees: ");
                                variableIinput = stdIn.readLine();
                                printLogLine("SND", "THR", variableIinput);
                                timeKeeper = new Timekeeper(this, 10);
                                // write to sink
                                out.writeUTF("THR");
                                out.writeInt(Integer.parseInt(variableIinput));
                                out.flush();
                                msg = in.readUTF();
                                if (msg.equals("ACK")) {
                                    timeKeeper.stopTimer();
                                    printLogLine("RCV", "ACK", "");
                                }
                                // else timer goes off                            
                                break;
                            case "quit":
                                System.err.println("user stops the program.");
                                System.exit(1);
                                break;
                            default:
                                System.err.println("unknown input.");
                                break;
                        } // end of switch
                    } // end of try
                    finally {
                        in.close();
                        out.close();
                        socketSink.close();
                    }

                } // end of true
            } catch (IOException ex) {
                Logger.getLogger(Admin.class.getName()).log(Level.SEVERE, null, ex);
                System.err.println("error:" + ex.toString());
            }
        } // end of run

        public void startAgain() {
            this.run();
        }
    }// end of class UserInput

    // A thread that listens to the right channel
    static class AdminRightListener extends Thread {

        DataInputStream inRightPort;
        DataOutputStream outRightPort;
        String dataType;
        int originalSenderId;
        int measurement;
        Socket RightClientSocket;
        DataOutputStream out;

        // constructor
        public AdminRightListener() {
        }
        // the part that listens for data and alarm messages and handles them

        @Override
        public void run() {
            //System.out.println("\nEntered the SinkRightListener");
            try {
                ServerSocket listenRightSocket = new ServerSocket(PortNumber);

                while (true) {

                    RightClientSocket = listenRightSocket.accept();

                    outRightPort = new DataOutputStream(RightClientSocket.getOutputStream());
                    inRightPort = new DataInputStream(RightClientSocket.getInputStream());
                 
                    dataType = inRightPort.readUTF();
                    originalSenderId = inRightPort.readInt();
                    measurement = inRightPort.readInt();
                    
                    printLogLine("RCV", dataType,
                            Integer.toString(originalSenderId) + ":"
                            + Integer.toString(measurement));
                    
                    if ( dataType.equals("ALM")){
                    	
                    	socketSink = new Socket(SinkIP, SinkPort);     

                        out = new DataOutputStream(socketSink.getOutputStream());
                    	
                        out.writeUTF("ACK");
                        out.flush(); 
                        
                        out.close();
                        socketSink.close();
                    }
                    
                } // end of try                
            } catch (IOException ex) {
                System.err.println(ex);
            }
        }// end of run
    } // end of class SinkRightListener

    // a class to keep the TIMER
    // http://www.java2s.com/Code/Java/Threads/ThreadReminder.htm
    private static class Timekeeper {

        private static Timer timer;
        private static UserInput userInput;

        public Timekeeper(UserInput ui, int seconds) {
            userInput = ui;
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
                System.err.format("\nTIMER HAS TIMED OUT");
                timer.cancel(); //Terminate the timer thread
                userInput.startAgain();
            }
        }
    } // end of Timekeeper

    private static void printLogLine(String type, String packet) {

        SimpleDateFormat format = new SimpleDateFormat("HHmmssSSSS");

        Calendar calendar = Calendar.getInstance();
        Logline log = new Logline();
        java.util.Date now = calendar.getTime();
        String currentTime = format.format(now);
        log.timestamp = currentTime;
        log.msg_type = type;
        log.pck_type = packet;
        System.out.println(log.timestamp + "\t" + log.msg_type + "\t" + log.pck_type);
       // logPrint.println(log.timestamp + "\t" + log.msg_type + "\t" + log.pck_type);      //one more arg or 1 more method to print the standarerror
    }                                                                                          //boolean arg

    private static void printLogLine(String type, String packet, String value) {
        SimpleDateFormat format = new SimpleDateFormat("HHmmssSSSS");
        Calendar calendar = Calendar.getInstance();
        Logline log = new Logline();
        java.util.Date now = calendar.getTime();
        String currentTime = format.format(now);
        log.timestamp = currentTime;
        log.msg_type = type;
        log.pck_type = packet;
        log.value = value;
        System.out.println(log.timestamp + "\t" + log.msg_type + "\t" + log.pck_type + "\t" + log.value);
        //logPrint.println(log.timestamp + "\t" + log.msg_type + "\t" + log.pck_type + "\t" + log.value);
    }

    // struct for log line
    private static class Logline {

        String timestamp;
        String msg_type;
        String pck_type;
        String value;
    }

    private static void getProperties() {
        // Read properties file.
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(PropertiesFileName));

        } catch (IOException e) {
            System.err.println("Error loading properties file" + e.toString());
            System.exit(1);
        }

        // get the properties from the properties file
        PortNumber = Integer.parseInt(properties.getProperty("PORT"));
        //System.out.println("Port number is " + PortNumber);
        SinkIP = properties.getProperty("SINK_IP");
        //System.out.println("Sink number is " + SinkIP);
        SinkPort = Integer.parseInt(properties.getProperty("SINK_PORT"));
        //System.out.println("Sink number is " + SinkPort);
    }
} // end of class Admin
