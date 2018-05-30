package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;

import java.util.Queue;
import java.util.LinkedList;
import javafx.util.Pair;

import akka.actor.Props;
import akka.actor.ActorRef;

public class GroupMember extends Node{
    private Queue<Pair<ActorRef, GroupView>> groupViewQueue;

    private GroupMember(int id, String remotePath) {
        super(id, remotePath);   
        this.groupViewQueue = new LinkedList<>();                
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupMember.class, () -> new GroupMember(id, remotePath));
	}

    public void preStart() {
		if (this.remotePath != null) { 
			getContext().actorSelection(remotePath).tell(new Join(this.id), getSelf());
		}
	}
    
    private void onGroupView(GroupView message) {
        if (onGroupViewUpdate==true){
            Logging.log("onGroup change request enqueued");
            this.groupViewQueue.add(new Pair(getSender(), message));
            return;
        } 
        Logging.log("request update group view");
        this.onGroupViewUpdate = true;               

        this.state.putAllMembers(message);
        // this.state.printState();
        allToAll(message.groupViewSeqnum, this.id);       
	}
    @Override
    protected void onViewInstalled(){
        super.onViewInstalled();
        if (this.groupViewQueue.size()>0){
            Pair<ActorRef, GroupView> item = this.groupViewQueue.remove();
            Logging.log("dequeue");
            getSelf().tell(item.getValue(), item.getKey());                    
        }
    }

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(GroupView.class, this::onGroupView)        
            .build();
	}
}