package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;

import java.util.Queue;
import java.util.LinkedList;
import javafx.util.Pair;

import akka.actor.Props;
import akka.actor.ActorRef;
import akka.actor.Cancellable;

public class GroupMember extends Node{
    private Queue<Pair<ActorRef, GroupView>> groupViewQueue;

    private Cancellable messageTimeout;
    private Cancellable flushTimeout;

    private GroupMember(int id, String remotePath) {
        super(id, remotePath);   
    }

    @Override 
    protected void init(int id){
        super.init(id);

        this.groupViewQueue = new LinkedList<>();
        this.messageTimeout = null;
        this.flushTimeout = null;
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
        setFlushTimeout();             
        
        // this.state.printState();
        allToAll(message.groupViewSeqnum, this.id);       
	}

    @Override
    protected void onFlushTimeout(FlushTimeout msg){
        Logging.log("Flush timeout for "+msg.id);
    }
    
    @Override
    protected void onViewInstalled(){
        super.onViewInstalled();
        this.messageTimeout = sendSelfAsyncMessage(Network.Td, new MessageTimeout(0));        
        
        if (this.groupViewQueue.size()>0){
            Pair<ActorRef, GroupView> item = this.groupViewQueue.remove();
            Logging.log("dequeue");
            getSelf().tell(item.getValue(), item.getKey());                    
        }
    }

    @Override
    protected void onMessage(ChatMsg msg){
        if (msg.senderID.compareTo(0)==0){
            if (this.messageTimeout!=null) this.messageTimeout.cancel();
            this.messageTimeout = sendSelfAsyncMessage(Network.Td, new MessageTimeout(0));
        }
        super.onMessage(msg);
    }

    protected void onMessageTimeout(MessageTimeout msg){
        Logging.log("onTimeout");
        if (onGroupViewUpdate)  return;
        //stop timers and clear state
        cancelTimers();
        init(this.id);
        //Rejoin
        Logging.log("rejoin");
        preStart();
    }    

    @Override
    protected void cancelTimers(){
        super.cancelTimers();
        if (this.messageTimeout!=null){
            this.messageTimeout.cancel();
            this.messageTimeout = null;
        }
        if (this.flushTimeout!=null){
            this.flushTimeout.cancel();
            this.flushTimeout = null;
        }
    }

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(GroupView.class, this::onGroupView)        
            .match(MessageTimeout.class, this::onMessageTimeout)            
            .build();
	}
}