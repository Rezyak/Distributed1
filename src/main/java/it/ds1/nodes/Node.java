package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.State;
import it.ds1.Network;

import java.io.Serializable;
import java.lang.StringBuilder;

import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Cancellable;
import scala.concurrent.duration.Duration;

public class Node extends AbstractActor {

	protected Integer id;           //node id
	protected String remotePath;    //remote path to the groupManager
    protected State state;          //state object of the node

    protected Integer msgSeqnum;
    protected Boolean onGroupViewUpdate;

    
    protected Cancellable sendTimeout;
    private Map<Integer,Cancellable> flushTimeout;

	protected Node(int id, String remotePath) {
		this.id = id;
		this.remotePath = remotePath;
        this.init(id);      
	}
    
    protected void init(int id){
        this.state = new State(id);

        this.msgSeqnum = 0;
        this.onGroupViewUpdate = false;  

        this.sendTimeout = null;
        this.flushTimeout = new HashMap<>();          
    }
    
    static public Props props(int id, String remotePath) {
		return Props.create(Node.class, () -> new Node(id, remotePath));
	}

    protected Cancellable sendSelfAsyncMessage(int time, Serializable m) {
        return getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            m,
            getContext().system().dispatcher(), getSelf()
        );
    }

    /**
    *   each node can perform a multicast call given a message m
    */
    protected void multicast(Serializable m) {
        Network.delayMulticast(m, this.state, getSelf());
        // Network.multicast(m, this.state, getSelf());
    }

    /**
    *   send all unstable messages to all processes in the new view
    */
    protected void allToAll(Integer seqnum, Integer id){
        Network.delayAllToAll(seqnum, id, state, getSelf());
    } 
    
    /**
    *   handles new incoming messages:
    *   -   if a message has a group seqnum grater than the actual, that means that other nodes
    *       have already installed the next view and have sent a multicast 
    *   =>  buffer incoming messages and deliver them once the new view is installed    
    *
    *   -   insert the incoming message in a map, deliver it only if it is not a dupplicate
    */
    protected void onMessage(ChatMsg msg){
        if (this.state.getGroupViewSeqnum()==null) return;
        if (msg.senderID.compareTo(this.id)==0) return;
        if (this.state.isMember(msg.senderID)==false) return;

        if (msg.groupViewSeqnum>this.state.getGroupViewSeqnum()){
            this.state.addBuffer(msg);
            return;
        }
        if (this.state.insertNewMessage(msg, msg.senderID)){
            printDeliverMessage(msg);
        }        
    }
    
    /**
    *   insert the flush message in a set, the set reaches the dimension of the group then
    *   -   install the new view
    *   -   print the buffered messages of this view
    *   -   start multicast
    */
    protected void onFlush(Flush msg){
        Boolean selfMessage = msg.senderID.compareTo(this.id)==0; 
        Boolean inGroup = this.state.isMember(msg.senderID);
        if (selfMessage) return;
        if (inGroup==false) return;

        //if received cancel it
        Cancellable timer = this.flushTimeout.get(msg.senderID);
        if (timer!=null) timer.cancel();

        Logging.log(this.id+" flush "+msg.groupViewSeqnum+" from "+msg.senderID+" within "+this.state.getGroupViewSeqnum());
        this.state.insertFlush(msg);
        Integer flushSize = this.state.getFlushSize();
        Integer groupSize = this.state.getGroupViewSize()-1;
        if (flushSize==groupSize){ 
            this.state.setGroupViewSeqnum(msg.groupViewSeqnum+1);                       
            onViewInstalled();     
        }
    }

    protected void onViewInstalled(){
        cancelTimers();        
        printInstallView();
        printBufferMessages();

        this.onGroupViewUpdate = false;            
        this.state.clearBuffer();
        this.state.clearFlush();  

        getSelf().tell(new SendMessage(), getSelf());
    }

    /**
    *   sends multicast messages if it is not updating the group view
    */
    protected void onSendMessage(SendMessage msg){
        if (onGroupViewUpdate)  return;
        
        printMulticastMessage();
        ChatMsg newMsg = new ChatMsg(
            this.msgSeqnum,
            this.id,
            this.state.getGroupViewSeqnum()
        );
        multicast(newMsg);
        this.msgSeqnum += 1;
        
        this.sendTimeout = sendSelfAsyncMessage(Network.Td/2, new SendMessage());
    }

    /**
    *   create the ReceiveBuilder class with matches that will be inherited
    */
    protected ReceiveBuilder getReceive(){
        return receiveBuilder()
            .match(ChatMsg.class, this::onMessage)
            .match(Flush.class, this::onFlush)
            .match(SendMessage.class, this::onSendMessage)
            .match(FlushTimeout.class, this::onFlushTimeout);
    }

    protected void setFlushTimeout(){
        List<Integer> memberList = this.state.getMemberList();
        for (Integer member: memberList){
            if (member.compareTo(this.id)==0) continue;

            Cancellable timer = this.flushTimeout.get(member);
            if (timer!=null){
                timer.cancel();
                this.flushTimeout.put(member, null);        
            }
            this.flushTimeout.put(member, sendSelfAsyncMessage(Network.Td, new FlushTimeout(member)));                    
            
        }
    }

    protected void onFlushTimeout(FlushTimeout msg){}

    protected void cancelTimers(){
        if (this.sendTimeout!=null){
            this.sendTimeout.cancel();
            this.sendTimeout = null;
        }
        List<Integer> memberList = this.state.getMemberList();
        for (Integer member: memberList){
            Cancellable timer = this.flushTimeout.get(member);
            if (timer!=null){
                timer.cancel();
                this.flushTimeout.put(member, null);        
            }
        }
    }

    @Override
    public Receive createReceive() {return receiveBuilder().build();}

    protected void printInstallView(){
        Logging.log(this.id+" install view "+this.state.getGroupViewSeqnum()+" "+this.state.commaSeparatedList());        
    }
    protected void printMulticastMessage(){
        Logging.log(this.id+" send multicast "+this.msgSeqnum+" within "+this.state.getGroupViewSeqnum());
    }
    protected void printDeliverMessage(ChatMsg msg){
        Logging.log(this.id+" deliver multicast "+msg.msgSeqnum+" from "+msg.senderID+" within "+this.state.getGroupViewSeqnum());
    }
    protected void printBufferMessages(){
        List<ChatMsg> buffer = this.state.getBufferMessages();
        for(ChatMsg msg :buffer){
            if (this.state.insertNewMessage(msg, msg.senderID)){
                printDeliverMessage(msg);
            }        
        }
    }
}