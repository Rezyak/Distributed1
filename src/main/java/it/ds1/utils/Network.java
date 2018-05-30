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
    public static final int Td = 2000;

    private static final int MAXDELAY = 2000;
    private static Random rnd = new Random();

    public static void delay(int d) {
      try {Thread.sleep(d);} catch (Exception e) {}
    }

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

    public static void delayAllToAll(Integer seqnum, Integer id, State state, ActorRef self){

        state.getMessagesInstance().shuffledForEach(new MessageMap.Action<ChatMsg>(){
            @Override
            public void perform(Integer id, ChatMsg msg){
                delayMulticast(msg, state, self);                         
            }
        });      
        delayMulticast(new Flush(seqnum, id), state, self);
    }
}