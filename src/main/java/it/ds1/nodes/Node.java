package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.State;
import it.ds1.Network;

import java.io.Serializable;
import java.lang.StringBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import akka.actor.ActorRef;
import akka.actor.Props;

public class Node extends AbstractActor {

    protected State state = null;
	protected int id;
    
	protected String remotePath = null;

	protected Node(int id, String remotePath, State state) {
		this.id = id;
		this.remotePath = remotePath;
        this.state = state;            
	}
    
    static public Props props(int id, String remotePath, State state) {
		return Props.create(Node.class, () -> new Node(id, remotePath, state));
	}

    protected void multicast(Serializable m) {
        Network.delayMulticast(m, this.state, getSelf());
        // Network.multicast(m, this.state, getSelf());
    }

    protected void allToAll(){
        Network.delayAllToAll(this.state, getSelf());
    } 
    
    protected void onMessage(ChatMsg msg){

        if (this.state.insertNewMessage(msg, msg.senderID)){
            Logging.log("message from "+msg.senderID);
            Logging.log("**************");
            Logging.log("msg seqnum: "+msg.msgSeqnum);
            Logging.log("msg sender id: "+msg.senderID);
            Logging.log("msg V"+msg.groupViewSeqnum);
            Logging.log("**************");
        }        
    }
    protected void onFlush(Flush msg){
        Logging.log("Flush from "+msg.senderID);
        Logging.log("**************");
        Logging.log("groupView seqnum: "+msg.groupViewSeqnum);
        Logging.log("**************");
    }

    protected ReceiveBuilder getReceive(){
        return receiveBuilder()
            .match(ChatMsg.class, this::onMessage)
            .match(Flush.class, this::onFlush);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}