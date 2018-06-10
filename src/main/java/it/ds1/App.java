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
import java.util.Random;

import java.lang.Runnable;
import java.lang.Thread;
import java.lang.Math;

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

    private static String remoteActorSystemName = "DistributedChat_"+remoteID;
    private static String remoteActorName = "node_"+remoteID;
    
    private static ActorSystem asystem = null;
    private static ActorRef receiver = null;

	public static void main(String[] args) {
        
        // if project.hasProperty('manager') from gradle then start manager node
        Boolean manager = (args.length>0)? true: false;
        Config config = getConfiguration();
        
        Props mNode = null;        
        String uuid = UUID.randomUUID().toString();
        String actorSystemName = "DistributedChat_"+uuid;
        String actorName = "node_"+uuid;

        if (manager){
            Logging.out("MANAGER");
            actorSystemName = remoteActorSystemName;
            actorName = remoteActorName;

            mNode = GroupManager.props(
                remoteID, 
                remotePath
            );
        } 
        else{
            Logging.out("MEMBER");
            config = createMemberConfig(config);
            mNode = GroupMember.props(
                -1,
                remotePath
            );    
        }                    

		asystem = ActorSystem.create(actorSystemName, config);
		receiver = asystem.actorOf(mNode, actorName);

        String debugIP = config.getString("akka.remote.netty.tcp.hostname");
        Integer debugPort = config.getInt("akka.remote.netty.tcp.port");

        Logging.out(debugIP+":"+debugPort);  
        Logging.out("-> help to list commands");              
        Logging.out("-> q to exit");

        Thread reader = new CommandReader(asystem, receiver, manager);        
        reader.start();
	}

    /**
    *   gets configuration from resources/application.conf
    */
    private static Config getConfiguration(){
        return ConfigFactory.load();
    }
    
    /**
    *   adds a local actor to the ActorSystem
    */
    public static void createLocalMember(){
        Config config = createMemberConfig(getConfiguration());      
        String uuid = UUID.randomUUID().toString();
        String actorName = "node_"+uuid;
        
        Props mNode = GroupMember.props(
            -1,
            remotePath
        );
        asystem.actorOf(mNode, actorName);        
    }

    /**
    *   overrides 
    *   - akka.remote.netty.tcp.hostname with current host address
    *   - akka.remote.netty.tcp.port with 0 (akka will provide a free port in the system)
    */
    public static Config createMemberConfig (Config config){
        if (config.hasPath("manager.remote_ip")==false){
            Logging.stderr("no romete address found in config file");            
            return null;
        }

        String remote_ip = config.getString("manager.remote_ip");
        int remote_port = config.getInt("manager.remote_port");

        remotePath = "akka.tcp://"+remoteActorSystemName+"@" + remote_ip + ":" + remote_port+ "/user/"+remoteActorName;

        String mIP = "127.0.0.1";
        try {
            mIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            Logging.stderr(e.getMessage());
            Logging.stderr("using localhost");
        }
        Config mHostname = ConfigFactory.parseString("akka.remote.netty.tcp.hostname="+mIP);
        Config mPort = ConfigFactory.parseString("akka.remote.netty.tcp.port=0");

        Config combinedHostname = mHostname.withFallback(config);
        Config combinedPort = mPort.withFallback(combinedHostname);

        return ConfigFactory.load(combinedPort);
    }

}

class CommandReader extends Thread{

    private interface Command {
        void call();
    }
    private Map<String, Command> commands;

    /**
    *   initializes a hashmap with (string command -> interface call)
    */
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

            commands.put(Commands.joinOnJoin, new Command(){
                public void call() { 
                    receiver.tell(new JoinOnJoin(), null);
                }
            });
            commands.put(Commands.joinOnMulticast, new Command(){
                public void call() { 
                    receiver.tell(new JoinOnMulticast(), null);
                }
            });
            commands.put(Commands.joinOnMessage, new Command(){
                public void call() { 
                    receiver.tell(new JoinOnMessage(), null);
                }
            });
            commands.put(Commands.joinOnViewI, new Command(){
                public void call() { 
                    receiver.tell(new JoinOnViewI(), null);
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

        commands.put(Commands.isolate, new Command(){
            public void call() { 
                receiver.tell(new Isolate(), null);                
            }
        });
        commands.put(Commands.attach, new Command(){
            public void call() { 
                receiver.tell(new Attach(), null);                
            }
        });
        
        commands.put(Commands.help, new Command(){
            public void call() { 
                for ( String key : commands.keySet() ) {
                    Logging.out("- "+key);
                }
            }
        });
        commands.put(Commands.clear, new Command(){
            public void call() { 
                int lines = 42;
                for (int l=0; l<lines; l++ ) {
                    Logging.out("");
                }
            }
        });

        commands.put(Commands.TEST, new Command(){
            public void call() { 
                Logging.out("****Starting TEST****");
                Random rnd = new Random();
                
                for (Integer i=0; i<2; i++){
                    for(Integer j=0; j<=5; j++){
                        Boolean createNode = Math.random() <0.85;
                        Boolean crashNode = Math.random() <0.6;
                                                
                        if (createNode){
                            Logging.out("creating node...");
                            App.createLocalMember();                                                    
                        } 
                        if (crashNode){
                            Logging.out("killing a node...");                            
                            receiver.tell(new CrashRandom(), null);
                        }
                        Network.delay(rnd.nextInt(100));                        
                    }                
                }
                Logging.out("====> TEST DONE run <node evaluation.js>");                
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
                Logging.out("command "+command+" not found");
            }
        }  
        this.commands.get("shutdown").call();
    }  

}
