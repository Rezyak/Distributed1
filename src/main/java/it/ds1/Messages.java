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

  public static class RequestNodelist implements Serializable {}

  public static class Nodelist implements Serializable {
    Map<Integer, ActorRef> nodes;

    public Nodelist(Map<Integer, ActorRef> nodes) {
      this.nodes = Collections.unmodifiableMap(new HashMap<Integer, ActorRef>(nodes));
    }
  }
}
