package it.ds1;
import static it.ds1.Messages.*;

import java.util.HashMap;
import java.util.Map;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

public class Node extends AbstractActor {

	// The table of all nodes in the system id->ref
	private Map<Integer, ActorRef> nodes = new HashMap<>();
	private String remotePath = null;
	private int id;

	/* -- Actor constructor --------------------------------------------------- */
	private Node(int id, String remotePath) {
		this.id = id;
		this.remotePath = remotePath;
	}

	static public Props props(int id, String remotePath) {
		return Props.create(Node.class, () -> new Node(id, remotePath));
	}

	public void preStart() {
		if (this.remotePath != null) {
			getContext().actorSelection(remotePath).tell(new RequestNodelist(), getSelf());
		}
		nodes.put(this.id, getSelf());
	}

	private void onRequestNodelist(RequestNodelist message) {
		getSender().tell(new Nodelist(nodes), getSelf());
	}

	private void onNodelist(Nodelist message) {
		nodes.putAll(message.nodes);
		for (ActorRef n : nodes.values()) {
			n.tell(new Join(this.id), getSelf());
		}
	}

	private void onJoin(Join message) {
		int id = message.id;
		System.out.println("Node " + id + " joined");
		nodes.put(id, getSender());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(RequestNodelist.class, this::onRequestNodelist)
				.match(Nodelist.class, this::onNodelist).match(Join.class, this::onJoin).build();
	}
}