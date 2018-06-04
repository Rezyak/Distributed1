package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.GroupManager;
import it.ds1.GroupMember;
import it.ds1.State;
import it.ds1.Logging;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Scanner;

import java.lang.Runnable;
import java.lang.Thread;

import java.io.IOException;
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
                // try {
                //     mIP = InetAddress.getLocalHost().getHostAddress();
                //     debugIP = mIP;
                // } catch (UnknownHostException e) {
                //     Logging.stderr(e.getMessage());
                //     Logging.stderr("using localhost");
                // }
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

        // Thread reader = new Thread(new CommandReader());
        // reader.start();
        // asystem.terminate();

        Logging.out(debugIP+":"+debugPort);
	}
}

// class CommandReader implements Runnable{  
//     public void run(){     
//         String command = "";
//         while(command.equals("q")==false){
//             Scanner scanner = new Scanner(System.in);
//             command = scanner.next();
//             Logging.out("command => "+command); 
//         }  
//     }  
// }
