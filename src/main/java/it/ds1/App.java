package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.GroupManager;
import it.ds1.GroupMember;
import it.ds1.State;
import it.ds1.Logging;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;

public class App {
	static private String remotePath = null;

	public static void main(String[] args) {

        //TODO In addition to that, in the case of the networked implementation, 
        // the user should be able to add a new group member by running a new instance of the program and specifying the IP address
        // and the port of the group manager.
		Config config = ConfigFactory.load();
		int mID = config.getInt("nodeapp.id");

        Props mNode = null;
        String actorSystemName = "DistributedChat_"+mID;
        String actorName = "node_"+mID;

        if (mID==0){    
			mNode = GroupManager.props(
                mID, 
                remotePath
            );
        }else{
            if (config.hasPath("nodeapp.remote_ip")) {
                String remote_ip = config.getString("nodeapp.remote_ip");
                int remote_port = config.getInt("nodeapp.remote_port");

                remotePath = "akka.tcp://DistributedChat_0@" + remote_ip + ":" + remote_port + "/user/node_0";
            }else{
                Logging.err("no romete address found in config file");
            }
            mNode = GroupMember.props(
                mID,
                remotePath
            );            
        }

		final ActorSystem asystem = ActorSystem.create(actorSystemName, config);
		final ActorRef receiver = asystem.actorOf(mNode, actorName);

		try {
			System.in.read();
		} catch (IOException e) {
		} finally {
			asystem.terminate();
		}
	}
}
