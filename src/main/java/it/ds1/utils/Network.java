package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.State;
import it.ds1.Logging;
import it.ds1.GroupViewMap;
import it.ds1.MessageMap;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.io.Serializable;

import java.lang.Math;

import akka.actor.ActorRef;
import akka.actor.ActorContext;
import akka.actor.Cancellable;
import scala.concurrent.duration.Duration;

public class Network {
    public static final int Td = 10000;
    private static final int MAXDELAY = Td/100; //max of 100 nodes

    private static Random rnd = new Random();

    public interface Action{
        public Boolean shouldCrash();
    }

    public static void delay(int d) {
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

    public static void delayMulticast(Serializable m, State state, ActorRef self, Action action) {
        List<Integer> memberList = state.getMemberList();
        int[] sent = {0};
        Integer minSend = 1;
        Integer maxSend = Math.max(1, memberList.size()-1); //remove self
        Integer threshold = rnd.nextInt(maxSend)+minSend;
        
        state.getGroupViewInstance().shuffledForEach(new GroupViewMap.Action<ActorRef>(){
            @Override
            public void perform(Integer id, ActorRef nodeRef){            
                if (self.compareTo(nodeRef)!=0){

                    if (sent[0]>=threshold.intValue()){
                        if (action!=null && action.shouldCrash()) return;
                    }
                    delay(rnd.nextInt(MAXDELAY)+1); 
                    nodeRef.tell(m, self);

                    sent[0]=sent[0]+1;
                }
                                           
            }
        });
    }
    public static void delayAllToAll(Integer seqnum, Integer id, State state, ActorRef self, Action action){
        Logging.log(state.getGroupViewSeqnum(), id+" all-to-all");
        state.getMessagesInstance().shuffledForEach(new MessageMap.Action<ChatMsg>(){
            @Override
            public void perform(Integer id, ChatMsg msg){
                delayMulticast(msg, state, self, null);                         
            }
        });      
        delayMulticast(new Flush(seqnum, id), state, self, action);
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
    //                 Logging.log(this.state.getGroupViewSeqnum(),
                        // "delay "+delay[0]);
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