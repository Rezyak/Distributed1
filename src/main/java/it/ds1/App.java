package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Node;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;

public class App {
	static private String remotePath = null; // Akka path of the bootstrapping peer

	public static void main(String[] args) {
		System.out.println(">>> Press ENTER to exit <<<");

		Config config = ConfigFactory.load();
		int myId = config.getInt("nodeapp.id");

		if (config.hasPath("nodeapp.remote_ip")) {
			String remote_ip = config.getString("nodeapp.remote_ip");
			int remote_port = config.getInt("nodeapp.remote_port");
			// Starting with a bootstrapping node
			// The Akka path to the bootstrapping peer
			remotePath = "akka.tcp://DistributedChat@" + remote_ip + ":" + remote_port + "/user/node";
			System.out.println("Starting node " + myId + "; bootstrapping node: " + remote_ip + ":" + remote_port);
		} else {
			System.out.println("Starting bootstrapping node " + myId);
		}

		final ActorSystem asystem = ActorSystem.create("DistributedChat", config);
		// Create a single node actor locally
		final ActorRef receiver = asystem.actorOf(
			Node.props(myId, remotePath),
			"node"      // actor name
		);
		try {
			System.in.read();
		} catch (IOException e) {
		} finally {
			asystem.terminate();
		}
	}
}
