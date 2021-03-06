package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Commands;
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
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Cancellable;
import scala.concurrent.duration.Duration;

public class Node extends AbstractActor {

	protected int id;           //node id
	protected String remotePath;    //remote path to the groupManager
    protected State state;          //state object of the node

    protected Integer msgSeqnum;    //node messages seq number
    
    protected Cancellable sendTimer;    //schedule message, used to send a multicast message
    private Map<Integer,Cancellable> flushTimeout;  //schedule message for each member, used during all-to-all

    protected Map<String, AtomicBoolean> atomicMap; //hashmap for test commands handling
    
	protected Node(int id, String remotePath) {
		this.id = id;
		this.remotePath = remotePath;
        
        atomicMap = new HashMap<>();
        atomicMap.put(Commands.crash, new AtomicBoolean());             
        atomicMap.put(Commands.crashMessage, new AtomicBoolean());             
        atomicMap.put(Commands.crashMulticast, new AtomicBoolean());             
        atomicMap.put(Commands.crashA2A, new AtomicBoolean());             
        atomicMap.put(Commands.crashViewI, new AtomicBoolean());
        atomicMap.put(Commands.isolate, new AtomicBoolean()); 

        this.init(id);            
	}
    
    protected void init(int id){
        this.state = new State(id);

        this.msgSeqnum = -1;  
        this.sendTimer = null;
        this.flushTimeout = new HashMap<>();    
    }
    
    static public Props props(int id, String remotePath) {
		return Props.create(Node.class, () -> new Node(id, remotePath));
	}

    /**
    *   schedule a message m to be fired in time milliseconds
    */
    protected Cancellable sendSelfAsyncMessage(int time, Serializable m) {
        return getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            m,
            getContext().system().dispatcher(), getSelf()
        );
    }

    
    protected void multicast(ChatMsg m) {
        int sent = generalMulticast(m);
        if (sent!=0)    printMulticastMessage();
    }

    /**
    *   each node can perform a multicast call given a message m
    *   -   if should crash during multicast set onCrash
    *   -   implement shouldCrash interface called from Network
    *   -   if should be isolated do not multicast
    */
    protected int generalMulticast(Serializable m){
        if (atomicMap.get(Commands.crashMulticast).compareAndSet(true, false)){
            onCrash(new Crash());
        }
        if(atomicMap.get(Commands.isolate).get()) return 0;
        
        return Network.delayMulticast(m, this.state, getSelf(), new Network.Action(){
            public Boolean shouldCrash(){
                return atomicMap.get(Commands.crash).get();
            }
        });
    }

    /**
    *   send all unstable messages to all processes in the new view and do an all-to-all
    *   -   if should crash during all-to-all set onCrash
    *   -   implement shouldCrash interface called from Network 
    *   -   if should be isolated do not multicast       
    */
    protected void allToAll(Integer seqnum, Integer id){
        if (atomicMap.get(Commands.crashA2A).compareAndSet(true, false)){
            onCrash(new Crash());
        }
        if(atomicMap.get(Commands.isolate).get()) return;
        
        Network.delayAllToAll(seqnum, id, state, getSelf(), new Network.Action(){
            public Boolean shouldCrash(){
                return atomicMap.get(Commands.crash).get();
            }
        });
    } 
    
    /**
    *   handles new incoming messages:
    *   -   if is crashed ignore message        
    *   -   if should be isolated ignore message
    *   -   if should crash on message receive set onCrash and ignore message   
    *    
    *   -   if a message has a group seqnum grater than the actual, that means that other nodes
    *       have already installed the next view and have sent a multicast 
    *   =>  buffer incoming messages and deliver them once the new view is installed    
    *
    *   -   insert the incoming message in a map, deliver it only if it is not a dupplicate
    */
    protected void onMessage(ChatMsg msg){
        
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            // Logging.out(this.id+" is isolated ");
            return;
        }
        if(atomicMap.get(Commands.crashMessage).compareAndSet(true, false)){
            onCrash(new Crash());
            return;
        }

        if (this.state.shouldDeliver(msg)){
            printDeliverMessage(msg);
        }
    }
    
    /**
    *   insert the flush message in a set, the set reaches the dimension of the group then
    *   -   if is crashed ignore message        
    *   -   if should be isolated ignore message
    *
    *   -   cancel flush timer 
    *   -   if flush SET size is equal to group members number install
    */
    protected void onFlush(Flush msg){
        if(atomicMap.get(Commands.crash).get()) return;
        if(atomicMap.get(Commands.isolate).get()) return;        

        this.state.addFlush(msg);

        //if received cancel it
        Cancellable timer = this.flushTimeout.get(msg.senderID);
        if (timer!=null) timer.cancel();

        if (this.state.shouldInstallView(msg.groupViewSeqnum)){ 
            getSelf().tell(new InstallView(), getSelf());        
        }
    }

    /**
    *   Before installing the deliver the messages in the buffer
    *   - iterate through all Views mapped (eg. consecutive group changes)
    *   - print buffered messages in the View installed
    */
    protected void installView(InstallView msg){
        if(atomicMap.get(Commands.crash).get()) return;

        List<ChatMsg> buffered = this.state.getBufferedMessages();
        printBufferMessages(buffered);
        while(this.state.getNextView()){
            printInstallView();
            
            buffered = this.state.getBufferedMessages();
            printBufferMessages(buffered);
        }
        onViewInstalled();
    }

    /**
    *   Called once all views are installed
    *   - if the node is not alone set SendMessage timer
    */
    protected void onViewInstalled(){        
        cancelTimers();      
        if(atomicMap.get(Commands.crashViewI).compareAndSet(true, false)){
            onCrash(new Crash());
            return;
        }
        if(atomicMap.get(Commands.crash).get()) return; 
        
        Boolean alone = this.state.getGroupViewSize().intValue()==1;
        if (alone==false) this.sendTimer = sendSelfAsyncMessage(Network.Td/2, new SendMessage());
    }

    /**
    *   sends multicast messages if it is not updating the group view
    */
    protected void onSendMessage(SendMessage msg){
        if(atomicMap.get(Commands.crash).get()) return;
        if(atomicMap.get(Commands.isolate).get()) return;
        this.msgSeqnum += 1;
        
        ChatMsg newMsg = new ChatMsg(
            this.msgSeqnum,
            this.id,
            this.state.getGroupViewSeqnum()
        );
        multicast(newMsg);

        
        this.sendTimer = sendSelfAsyncMessage(Network.Td/2, new SendMessage());
    }

    protected void onPing(Ping msg){
        Logging.out("pong");
    }

    protected void onCrash(Crash msg){
        atomicMap.get(Commands.crash).set(true);
        cancelTimers();
        Logging.log(this.state.getGroupViewSeqnum(), this.id+" CRASH");        
        Logging.out(this.id+" crashed");
    }
    protected void onInit(Init msg){
        atomicMap.get(Commands.crash).set(false);   
        Logging.log(this.state.getGroupViewSeqnum(), this.id+" INIT");     
        Logging.out("join request to "+remotePath);
    }
    protected void onCrashMessage(CrashMessage msg){
        atomicMap.get(Commands.crashMessage).set(true);        
        Logging.out("crash during next message receive...");
    }
    protected void onCrashMulticast(CrashMulticast msg){
        atomicMap.get(Commands.crashMulticast).set(true);        
        Logging.out("crash during next multicast send...");
    }
    protected void onCrashA2A(CrashA2A msg){
        atomicMap.get(Commands.crashA2A).set(true);        
        Logging.out("crash during next all-to-all...");
    }
    protected void onCrashViewI(CrashViewI msg){
        atomicMap.get(Commands.crashViewI).set(true);        
        Logging.out("crash during next intall view...");
    }

    protected void onIsolate(Isolate msg){
        atomicMap.get(Commands.isolate).set(true); 
        Logging.log(this.state.getGroupViewSeqnum(), this.id+" ISOLATED");       
        Logging.out("confining node...");
    }
    protected void onAttach(Attach msg){
        atomicMap.get(Commands.isolate).set(false); 
        Logging.log(this.state.getGroupViewSeqnum(), this.id+" ATTACHED");       
        Logging.out("attaching node...");
    }

    protected void setFlushTimeout(){
        List<Integer> memberList = this.state.getMemberList();
        for (Integer member: memberList){
            if (member.intValue() == this.id) continue;

            Cancellable timer = this.flushTimeout.get(member);
            if (timer!=null){
                timer.cancel();
                this.flushTimeout.put(member, null);        
            }
            this.flushTimeout.put(member, sendSelfAsyncMessage(Network.Td, new FlushTimeout(member)));                    
            
        }
    }

    protected void onFlushTimeout(FlushTimeout msg){
        if(atomicMap.get(Commands.crash).get()) return;   
        Logging.out(this.id+" Flush timeout for "+msg.id);
             
    }

    protected void cancelTimers(){
        if (this.sendTimer!=null){
            this.sendTimer.cancel();
            this.sendTimer = null;
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

    /**
    *   create the ReceiveBuilder class with matches that will be inherited
    */
    protected ReceiveBuilder getReceive(){
        return receiveBuilder()
            .match(InstallView.class, this::installView)  

            .match(ChatMsg.class, this::onMessage)
            .match(Flush.class, this::onFlush)
            .match(SendMessage.class, this::onSendMessage)
            .match(FlushTimeout.class, this::onFlushTimeout)
            .match(Ping.class, this::onPing)
            .match(Crash.class, this::onCrash)
            .match(CrashMessage.class, this::onCrashMessage)
            .match(CrashMulticast.class, this::onCrashMulticast)
            .match(CrashA2A.class, this::onCrashA2A)
            .match(CrashViewI.class, this::onCrashViewI)
            .match(Isolate.class, this::onIsolate)
            .match(Attach.class, this::onAttach)
            .match(Init.class, this::onInit);
    }
    @Override
    public Receive createReceive() {return receiveBuilder().build();}

    protected void printInstallView(){
        Logging.log(this.state.getGroupViewSeqnum(),
            this.id+" install view "+this.state.getGroupViewSeqnum()+" "+this.state.commaSeparatedList());
        Logging.out(this.id+" install view "+this.state.getGroupViewSeqnum()+" "+this.state.commaSeparatedList());                

    }
    protected void printMulticastMessage(){
        Logging.log(this.state.getGroupViewSeqnum(),
            this.id+" send multicast "+this.msgSeqnum+" within "+this.state.getGroupViewSeqnum());
    }
    protected void printDeliverMessage(ChatMsg msg){
        Logging.log(this.state.getGroupViewSeqnum(),
            this.id+" deliver multicast "+msg.msgSeqnum+" from "+msg.senderID+" within "+this.state.getGroupViewSeqnum()+" ("+msg.groupViewSeqnum+")");
    }
    protected void printBufferMessages(List<ChatMsg> buffer){
        for(ChatMsg msg :buffer){
            if (this.state.shouldDeliver(msg)){
                printDeliverMessage(msg);
            }  
        }
    }
}