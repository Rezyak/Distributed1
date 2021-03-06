package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;

import java.io.Serializable;

import java.util.Queue;
import java.util.LinkedList;
import javafx.util.Pair;
import java.util.concurrent.atomic.AtomicBoolean;

import akka.actor.Props;
import akka.actor.ActorRef;
import akka.actor.Cancellable;

public class GroupMember extends Node{

    private Cancellable messageTimeout;
    
    private GroupMember(int id, String remotePath) {
        super(id, remotePath);   
    }

    @Override 
    protected void init(int id){
        atomicMap.put(Commands.crashJoinID, new AtomicBoolean());
        atomicMap.put(Commands.crashGChange, new AtomicBoolean());
        
        super.init(id);
        this.messageTimeout = null; 
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupMember.class, () -> new GroupMember(id, remotePath));
	}

    /**
    *   Join request to the manager
    *   - if not isolated, set timeout for messages received from manager
    */
    public void preStart() {
		if (this.remotePath != null) {      
            if(atomicMap.get(Commands.isolate).get()==false){
    			getContext().actorSelection(remotePath).tell(new Join(), getSelf());
                checkMessageTimeout();        
            }
		}
	}
    
    private void onJoinID(JoinID message) {
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            // Logging.out(this.id+" is isolated ");
            return;
        }   
        
        if(atomicMap.get(Commands.crashJoinID).compareAndSet(true, false)){
            onCrash(new Crash());
            return;
        }       
        Logging.out(this.id+" received id "+message.id);                
        
        this.id = message.id;
        this.state.setID(this.id);
        checkMessageTimeout();
	}

    /**
    *   On receiving a Group View change request from the manager
    *   - change the view
    *   - do an all-to-all
    */
    private void onGroupView(GroupView message) {  
        // Logging.out(this.id+" received group change ");                                                     
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            // Logging.out(this.id+" is isolated ");
            return;
        }  
        
        if(atomicMap.get(Commands.crashGChange).compareAndSet(true, false)){
            onCrash(new Crash());
            return;
        }       
        
        this.state.groupViewChange(message);
        cancelTimers();         
        setFlushTimeout();
        allToAll(message.groupViewSeqnum-1, this.id);       
	}
    

    /**
    *   After handling the message reception
    *   - set a timeout for manager crash detection
    */
    @Override
    protected void onMessage(ChatMsg msg){
        super.onMessage(msg);
        // Logging.out(this.id+" received message from "+msg.senderID);                                                     
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            // Logging.out(this.id+" is isolated ");
            return;
        } 
             
        if (msg.senderID.intValue()==0){
            checkMessageTimeout();
        }
    }

    private void onCrashJoinID(CrashJoinID msg){
        atomicMap.get(Commands.crashJoinID).set(true);
        Logging.out("prepare to crash afrer received id from manager...");
    }
    private void onCrashGChange(CrashGChange msg){
        atomicMap.get(Commands.crashGChange).set(true);
        Logging.out("prepare to crash afrer group change request...");
    }

    private void checkMessageTimeout(){
        if (this.messageTimeout!=null) this.messageTimeout.cancel();
        this.messageTimeout = sendSelfAsyncMessage(Network.Td, new MessageTimeout(0));
    }

    @Override
    protected void onFlushTimeout(FlushTimeout msg){
        super.onFlushTimeout(msg);

        if(atomicMap.get(Commands.crash).get()) return;        
        if (this.remotePath != null) {      
            if(atomicMap.get(Commands.isolate).get()==false){
                //TODO decided to not handle crash detection in member node
                // to much overhead
    			// getContext().actorSelection(remotePath).tell(new FlushTimeout(msg.id), getSelf());
            }      
		}
    }

    @Override
    protected void onAttach(Attach msg){
        super.onAttach(msg);
        Logging.out("waiting for manager message...");
        checkMessageTimeout();
    }

    protected void onMessageTimeout(MessageTimeout msg){
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            Logging.out(this.id+" is isolated ");
            return;
        } 

        Logging.out(this.id+" onTimeout");                                                 
        
        //stop timers and clear state
        cancelTimers();
        init(-1);
        //Rejoin
        Logging.out(this.id+" rejoin request");
        preStart();
    }

    @Override    
    protected void onInit(Init msg){
        cancelTimers();
        
        super.onInit(msg);
        init(-1);        
        preStart();
    }
    @Override
    protected void cancelTimers(){
        super.cancelTimers();
        if (this.messageTimeout!=null){
            this.messageTimeout.cancel();
            this.messageTimeout = null;
        }
    }

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(GroupView.class, this::onGroupView)        
            .match(MessageTimeout.class, this::onMessageTimeout)            
            .match(JoinID.class, this::onJoinID)   
                   
            .match(CrashJoinID.class, this::onCrashJoinID)            
            .match(CrashGChange.class, this::onCrashGChange)         
            .build();
	}
}