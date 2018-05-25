package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Node;

import akka.actor.Props;

public class GroupManager extends Node{

    private GroupManager(int id, String remotePath) {
        super(id, remotePath);
        this.state.putMember(id, getSelf());        
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
	}

	private void onJoin(Join message) {
		int id = message.id;
		System.out.println("Node " + id + " joined");
        this.state.putMember(id, getSender());
		System.out.println(this.id+" sending multicast with:");
        printGroupView();   
        multicast(new GroupView(this.state.getGroupView()));
	}

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(Join.class, this::onJoin)
            .build();
	}
}