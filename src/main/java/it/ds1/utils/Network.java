package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.State;
import it.ds1.Logging;
import it.ds1.GroupViewMap;
import it.ds1.MessageMap;

import java.util.Random;
import java.io.Serializable;
import akka.actor.ActorRef;

public class Network {
    private static final int Td = 2000;
    private static final int MAXDELAY = 2000;
    private static Random rnd = new Random();

    private static void delay(int d) {
      try {Thread.sleep(d);} catch (Exception e) {}
    }

    /** Theory
        - group membership might change while multicasts are being issued
        - VS, each process maintains a "view" of the group
            ** all correct processes will see the same sequence of group views
        - multicasts do not cross epoch boundaries
            ** If there are multicasts for messages m1 and m2, and a view change occurs
            ** to satisfy view synchrony, these multicasts must complete before the new view is installed
        - A message multicast in an epoch E defined by view V should either
            ** be delivered within E by all operational participants
            ** be delivered by none of operational participants (only allowed if the sender crashes)
    */

    /** Implementation
        - Multicast is implemented as a sequence of unicasts to all group members
        - The only case in which a multicast can fail is when the initiator crashes in the middle of it
            ** every process p
            ** Delivers immediately the message m it received
            ** keeps a copy of m until it is sure that every correct process in the current view has it
            ** once this happens, m is said to be stable, and the copy can be dropped
            ** sends the copy to processes that might need it
        - Based on assumptions, the initiator learns that its multicast m is stable after it has sent the individual (unicast) messages to all the group members
        - The initiator announces to the group that m is now stable, Piggybacking this information on the following multicasts

        -View change
            ** Upon receiving a view change with view V1
            ** pause sending new multicasts until the new view is installed but keep delivering incoming messages
            ** do an “all-to-all” echo: send all unstable messages to all processes in the new view V1, followed by a FLUSH message
            ** wait until the FLUSH message arrives from every other process in V1
            ** install the view V1
        - Having received FLUSH from all processes in V1, we know that we have received all the unstable messages sent in V0 from all currently operational processes (because of FIFO), therefore
            ** we deliver them in the old view V0
            ** we install the new view V1
        - If someone else crashes before sending FLUSH: repeat the all-to-all phase with the next view V2
    */
    public static void multicast(Serializable m, State state, ActorRef self) {
        state.getGroupViewInstance().forEach(new GroupViewMap.Action<ActorRef>(){
            @Override
            public void perform(Integer id, ActorRef nodeRef){               
                if (self.compareTo(nodeRef)!=0){
                    nodeRef.tell(m, self);
                }
            }
        });
    }

    public static void delayMulticast(Serializable m, State state, ActorRef self) {
        state.getGroupViewInstance().shuffledForEach(new GroupViewMap.Action<ActorRef>(){
            @Override
            public void perform(Integer id, ActorRef nodeRef){            
                if (self.compareTo(nodeRef)!=0){
                    delay(rnd.nextInt(MAXDELAY)+1); 
                    nodeRef.tell(m, self);
                }
                                           
            }
        });
    }

    public static void delayAllToAll(State state, ActorRef self){
        Integer gSeqnum = state.getGroupViewSeqnum();
        Integer nodeID = state.getID();
        Logging.log(nodeID+" all-to-all with seqnum "+gSeqnum);
        state.getOldMessagesInstance().shuffledForEach(new MessageMap.Action<ChatMsg>(){
            @Override
            public void perform(Integer id, ChatMsg msg){
                Logging.log("sending message of id="+id+" and seqn="+msg.msgSeqnum);            
                delayMulticast(msg, state, self);                         
            }
        });

        Logging.log(nodeID+" sending Flush V"+gSeqnum);        
        delayMulticast(new Flush(gSeqnum, nodeID), state, self);
    }
}