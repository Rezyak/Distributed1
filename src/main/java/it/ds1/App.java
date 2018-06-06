package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Commands;
import it.ds1.GroupManager;
import it.ds1.GroupMember;
import it.ds1.State;
import it.ds1.Logging;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;

import java.lang.Runnable;
import java.lang.Thread;

import java.io.IOException;
import java.lang.NullPointerException;
import java.net.UnknownHostException;
import java.net.InetAddress;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;

public class App {
	private static String remotePath = null;
    private static final Integer remoteID = 0;

    private static String debugIP="127.0.0.1";
    private static Integer debugPort=0;

	public static void main(String[] args) {
        
        Boolean manager = false;
        if (args.length>0)  manager = true;
        
        if (manager) Logging.out("MANAGER");
        else Logging.out("MEMBER");
        
		Config config = ConfigFactory.load();

        Props mNode = null;
        String uuid = UUID.randomUUID().toString();

        String remoteActorSystemName = "DistributedChat_"+remoteID;
        String remoteActorName = "node_"+remoteID;
        String actorSystemName = "DistributedChat_"+uuid;
        String actorName = "node_"+uuid;

        if (manager){  
            actorSystemName = remoteActorSystemName;
            actorName = remoteActorName;

			mNode = GroupManager.props(
                remoteID, 
                remotePath
            );

            debugIP = config.getString("nodeapp.remote_ip");
            debugPort = config.getInt("nodeapp.remote_port");
        }else{
            if (config.hasPath("nodeapp.remote_ip")) {
                String remote_ip = config.getString("nodeapp.remote_ip");
                int remote_port = config.getInt("nodeapp.remote_port");

                remotePath = "akka.tcp://"+remoteActorSystemName+"@" + remote_ip + ":" + remote_port+ "/user/"+remoteActorName;

                String mIP = "127.0.0.1";
                try {
                    mIP = InetAddress.getLocalHost().getHostAddress();
                    debugIP = mIP;
                } catch (UnknownHostException e) {
                    Logging.stderr(e.getMessage());
                    Logging.stderr("using localhost");
                }
                Config mHostname = ConfigFactory.parseString("akka.remote.netty.tcp.hostname="+mIP);
                Config mPort = ConfigFactory.parseString("akka.remote.netty.tcp.port=0");

                Config combinedHostname = mHostname.withFallback(config);
                Config combinedPort = mPort.withFallback(combinedHostname);
                config = ConfigFactory.load(combinedPort);

            }else{
                Logging.stderr("no romete address found in config file");
            }
            mNode = GroupMember.props(
                -1,
                remotePath
            );            
        }

		final ActorSystem asystem = ActorSystem.create(actorSystemName, config);
		final ActorRef receiver = asystem.actorOf(mNode, actorName);

        Logging.out(debugIP+":"+debugPort);  
        Logging.out("-> help to list commands");              
        Logging.out("-> q to exit");

        Thread reader = new CommandReader(asystem, receiver, manager);        
        reader.start();
	}
}

class CommandReader extends Thread{
    private Map<String, Command> commands;

    public CommandReader(ActorSystem asystem, ActorRef receiver, Boolean manager){
        commands = new HashMap<String, Command>();

        if (manager){
            commands.put(Commands.McrashJoin, new Command(){
                public void call() { 
                    receiver.tell(new McrashJoin(), null);
                }
            });
            commands.put(Commands.McrashMessage, new Command(){
                public void call() { 
                    receiver.tell(new McrashMessage(), null);
                }
            });
            commands.put(Commands.McrashViewI, new Command(){
                public void call() { 
                    receiver.tell(new McrashViewI(), null);
                }   
            });
        }
        else{
            commands.put(Commands.crashPrestart, new Command(){
                public void call() { 
                    receiver.tell(new CrashPrestart(), null);
                }
            });
            commands.put(Commands.crashJoinID, new Command(){
                public void call() { 
                    receiver.tell(new CrashJoinID(), null);
                }
            });
            commands.put(Commands.crashGChange, new Command(){
                public void call() { 
                    receiver.tell(new CrashGChange(), null);
                }
            });
        }
        
        commands.put(Commands.shutdown, new Command(){
            public void call() { 
                Logging.out("shutting down ...");                
                asystem.terminate();
            }
        });
        commands.put(Commands.crash, new Command(){
            public void call() { 
                Logging.out("crashing ...");
                receiver.tell(new Crash(), null); 
            }
        }); 
        commands.put(Commands.init, new Command(){
            public void call() { 
                Logging.out("init...");
                receiver.tell(new Init(), null);
            }
        });
        commands.put(Commands.ping, new Command(){
            public void call() { 
                Logging.out("ping...");
                receiver.tell(new Ping(), null);
            }
        });
        commands.put(Commands.crashMessage, new Command(){
            public void call() { 
                receiver.tell(new CrashMessage(), null);
            }
        });
        commands.put(Commands.crashMulticast, new Command(){
            public void call() { 
                receiver.tell(new CrashMulticast(), null);
            }
        });
        commands.put(Commands.crashA2A, new Command(){
            public void call() { 
                receiver.tell(new CrashA2A(), null);
            }
        });
        commands.put(Commands.crashViewI, new Command(){
            public void call() { 
                receiver.tell(new CrashViewI(), null);
            }
        });
        
        commands.put(Commands.help, new Command(){
            public void call() { 
                for ( String key : commands.keySet() ) {
                    Logging.out("- "+key);
                }
            }
        });
    }
    

    public void run(){     
        String command = "";
        while(true){            
            Scanner scanner = new Scanner(System.in);
            command = scanner.next();
            if (command.equals("q") || command.equals("shutdown")) break;

            try{
                this.commands.get(command).call();
            }catch(NullPointerException e){
                Logging.out("command not found");
            }
        }  
        this.commands.get("shutdown").call();
    }  
    public interface Command {
        void call();
    }
}
