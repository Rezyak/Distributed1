package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.State;
import it.ds1.Logging;
import it.ds1.GroupViewMap;
import it.ds1.MessageMap;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.io.Serializable;

import akka.actor.ActorRef;
import akka.actor.ActorContext;
import akka.actor.Cancellable;
import scala.concurrent.duration.Duration;

public class Network {
    public static final int Td = 10000;
    private static final int MAXDELAY = Td/100; //max of 100 nodes

    private static Random rnd = new Random();

    private static void delay(int d) {
      try {Thread.sleep(d);} catch (Exception e) {e.printStackTrace();}
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

    /**
    * it brokes the fifoness of multicast through unicast
    */
    // private static void delayUnicast(int time, Serializable m, ActorRef dst, ActorRef src, ActorContext context) {
    //     context.system().scheduler().scheduleOnce(
    //         Duration.create(time, TimeUnit.MILLISECONDS),  
    //         dst,
    //         m,
    //         context.system().dispatcher(), 
    //         src
    //     );
    // }
    // public static void delayMulticast(Serializable m, State state, ActorRef self, ActorContext context) {
    //     int[] delay = new int[1];
    //     state.getGroupViewInstance().shuffledForEach(new GroupViewMap.Action<ActorRef>(){
    //         @Override
    //         public void perform(Integer id, ActorRef nodeRef){            
    //             if (self.compareTo(nodeRef)!=0){
    //                 // delay(rnd.nextInt(MAXDELAY)+1); 
    //                 // nodeRef.tell(m, self);
    //                 delay[0] += rnd.nextInt(MAXDELAY)+1; 
    //                 Logging.log("delay "+delay[0]);
    //                 delayUnicast(delay[0], m, nodeRef, self, context);
    //             }
                                           
    //         }
    //     });
    // }
    // public static void delayAllToAll(Integer seqnum, Integer id, State state, ActorRef self, ActorContext context){

    //     state.getMessagesInstance().shuffledForEach(new MessageMap.Action<ChatMsg>(){
    //         @Override
    //         public void perform(Integer id, ChatMsg msg){
    //             delayMulticast(msg, state, self, context);                         
    //         }
    //     });      
    //     delayMulticast(new Flush(seqnum, id), state, self, context);
    // }
}