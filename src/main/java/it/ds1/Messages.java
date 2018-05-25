package it.ds1;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import akka.actor.ActorRef;

public class Messages {

  public static class Join implements Serializable {
    int id;

    public Join(int id) {
      this.id = id;
    }
  }

    public static class Test implements Serializable {}

    public static class GroupView implements Serializable {
        Map<Integer, ActorRef> groupView;

        public GroupView(Map<Integer, ActorRef> groupView) {
        this.groupView = Collections.unmodifiableMap(new HashMap<Integer, ActorRef>(groupView));
        }
    }
}
