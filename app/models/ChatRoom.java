package models;

import play.mvc.*;
import play.libs.*;
import play.libs.F.*;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import akka.actor.*;
import akka.pattern.Patterns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A chat room is an Actor.
 */
public class ChatRoom extends UntypedActor {
    
    // Default room.
    static ActorRef defaultRoom = Akka.system().actorOf(Props.create(ChatRoom.class));
    
    // Create a Robot, just for fun.
    static {
        new Robot(defaultRoom);
    }
    
    /**
     * Join the default room.
     */
    public static void join(final String username, WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out) throws Exception{
        
        // Send the Join message to the room
    	String result = null;
        result = (String)Await.result(Patterns.ask(defaultRoom,new Join(username, out), 1000), Duration.create(1, "seconds"));
        
        if("OK".equals(result)) {
            
            // For each event received on the socket,
            in.onMessage(new Callback<JsonNode>() {
               public void invoke(JsonNode event) {
            	   
                   // Send a Talk message to the room.
                   defaultRoom.tell(new Talk(event.get("type").asText(), username, event.get("text").asText()), null);
               } 
            });
            
            // When the socket is closed.
            in.onClose(new Callback0() {
               public void invoke() {
                   
                   // Send a Quit message to the room.
                   defaultRoom.tell(new Quit(username), null);
                   
               }
            });
            
        } else {
            
            // Cannot connect, create a Json error.
            ObjectNode error = Json.newObject();
            error.put("error", result);
            
            // Send the error to the socket.
            out.write(error);
            
        }
        
    }
    
    // Members of this room.
    Map<String, WebSocket.Out<JsonNode>> members = new HashMap<String, WebSocket.Out<JsonNode>>();
    
    @Override
    public void onReceive(Object message) throws Exception {
        
        if(message instanceof Join) {
        	
            // Received a Join message
            Join join = (Join)message;

            // Check if this username is free.
            if(members.containsKey(join.username)) {
                getSender().tell("This username is already used", getSelf());
            } else {
//                notifyAll("join", join.username, "has entered the room");
                members.put(join.username, join.channel);
                getSender().tell("OK", getSelf());
            }
            
        } else if(message instanceof Talk)  {
            
            // Received a Talk message
            Talk talk = (Talk)message;
            
			Calendar clndr = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
			sdf.setTimeZone(clndr.getTimeZone());
			String daytime = sdf.format(clndr.getTime());
			
            notifyAll(talk.type, talk.username, talk.text, daytime);
            
        } else if(message instanceof Quit)  {
            
            // Received a Quit message
            Quit quit = (Quit)message;
            
            members.remove(quit.username);
            
			Calendar clndr = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
			sdf.setTimeZone(clndr.getTimeZone());
			String daytime = sdf.format(clndr.getTime());

			notifyAll("quit", quit.username, "has left the room",daytime);
        
        } else {
            unhandled(message);
        }
        
    }
    
    // Send a Json event to all members
    public void notifyAll(String kind, String user, String text, String time) {
        for(WebSocket.Out<JsonNode> channel: members.values()) {
            
            ObjectNode event = Json.newObject();
            event.put("kind", kind);
            event.put("user", user);
            event.put("message", text);
            event.put("time", time);
            
            ArrayNode m = event.putArray("members");
            for(String u: members.keySet()) {
                m.add(u);
            }
            
            channel.write(event);
        }
    }
    
    // -- Messages
    
    public static class Join {
        
        final String username;
        final WebSocket.Out<JsonNode> channel;
        
        public Join(String username, WebSocket.Out<JsonNode> channel) {
            this.username = username;
            this.channel = channel;
        }
        
    }
    
    public static class Talk {
        
        final String type;
        final String username;
        final String text;
        
        public Talk(String type, String username, String text) {
            this.type = type;
            this.username = username;
            this.text = text;
        }
        
    }
    
    public static class Quit {
        
        final String username;
        
        public Quit(String username) {
            this.username = username;
        }
        
    }
    
}
