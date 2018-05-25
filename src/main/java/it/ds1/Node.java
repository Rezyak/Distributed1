package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.State;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import akka.actor.ActorRef;
import akka.actor.Props;

public class Node extends AbstractActor {

    protected State state = null;
	protected int id;
    
	protected String remotePath = null;

	protected Node(int id, String remotePath) {
		this.id = id;
		this.remotePath = remotePath;
        this.state = new State();
	}

    protected void multicast(Serializable m) {
        state.forEach(new State.Action(){
            @Override
            public void perform(Integer id, ActorRef nodeRef){
                nodeRef.tell(m, getSelf());
            }
        });
    }

    protected void printGroupView(){
        this.state.forEach(new State.Action(){
            @Override
            public void perform(Integer id, ActorRef nodeRef){
                System.out.println(id+" "+nodeRef);                
            }
        });
    }
    protected ReceiveBuilder getReceive(){
        return receiveBuilder();   
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}