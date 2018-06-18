package jadelab2;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.AID;

import java.util.*;
import org.json.simple.JSONObject;
import org.json.simple.*;
import org.json.simple.parser.*;



enum ParticipantAgentState
{
    initiated, negotiating, waiting, done;
}



public class ParticipantAgent extends Agent {
  private Hashtable catalogue;
  private ParticipantGui myGui;
  
  private AID[] participantAgents;
  private HashMap<Integer, Double> calendar;
  private double priority = -1.0;
  private ParticipantAgentState state = ParticipantAgentState.initiated;


  private ACLMessage replySchedulerAgent;
  private int hourIndex;

  protected void setup() {
    catalogue = new Hashtable();
    myGui = new ParticipantGui(this);
    myGui.display();
    
	Object[] args = getArguments();
	int hour = 0;
	double priority = 0.0;
	calendar = new HashMap<Integer, Double>();
	

	for(int i = 0; i < args.length; i = i+2){
			if (args != null && args.length > 0) hour = Integer.parseInt(args[i].toString());
			if (args != null && args.length > 0) priority = Double.valueOf(args[i+1].toString());
			calendar.put(hour, priority);
		}
		System.out.println(getAID().getLocalName() + " is initiated.");
		for(Object key:calendar.keySet()) {
		   System.out.println("hour: "+key+ "\t-\t"+ calendar.get(key));
		 }	
	System.out.println("\n");

    //book selling service registration at DF
    DFAgentDescription dfd = new DFAgentDescription();
    dfd.setName(getAID());
    ServiceDescription sd = new ServiceDescription();
    sd.setType("book-selling");
    sd.setName("JADE-book-trading");
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    }
    catch (FIPAException fe) {
      fe.printStackTrace();
    }
    
    addBehaviour(new TickerBehaviour(this, 5000)
	  {
		  protected void onTick()
		  {
			  //search only if the purchase task was ordered
				  myAgent.addBehaviour(new OfferRequestsServer());
			  
		  }
	  });
    //addBehaviour(new OfferRequestsServer());

  }

  protected void takeDown() {
    //book selling service deregistration at DF
    try {
      DFService.deregister(this);
    }
    catch (FIPAException fe) {
      fe.printStackTrace();
    }
  	myGui.dispose();
    System.out.println("Paricipant agent " + getAID().getName() + " terminated.");
  }

  //invoked from GUI, when a new book is added to the catalogue
  public void updateCatalogue(final String title, final int price) {
    addBehaviour(new OneShotBehaviour() {
      public void action() {
		catalogue.put(title, new Integer(price));
		System.out.println(getAID().getLocalName() + ": " + title + " put into the catalogue. Price = " + price);
      }
    } );
  }
  
	private class OfferRequestsServer extends Behaviour {
		
	  ACLMessage msg;
	  MessageTemplate mt;

	  public void action() {
	    //proposals only template
			    //System.out.println("before switch: "+ state);//update a list of known sellers (DF)
	    switch(state)
	    {
		    case waiting:
		    	
		    	
		    	
		    break;
		    
			case initiated:  
				mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
				msg = myAgent.receive(mt);
			    if (msg != null) {
			      String jsonString = msg.getContent();
			      JSONParser parser = new JSONParser();		
				//convert from JSON string to JSONObject
				JSONObject json = null;
				try
				{
					json = (JSONObject) parser.parse(jsonString);
				} 
				catch (ParseException e)
				{
					e.printStackTrace();
				}
			    
			    System.out.println("gelen json: \n"+json);//update a list of known sellers (DF)
				// calculate
			    
			    
			    DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("book-selling");
				template.addServices(sd);
				try{
					DFAgentDescription[] result = DFService.search(myAgent, template);
					System.out.println(getAID().getLocalName() + ": the following participants have been found");
					participantAgents = new AID[result.length];
					for (int i = 1; i < result.length; ++i){
						participantAgents[i] = result[i].getName();
						System.out.println("test "+ participantAgents[i].getLocalName());
					}			    	
				}
				catch (FIPAException fe){
					fe.printStackTrace();
				}
			    
			    
			    JSONArray availableHours = (JSONArray)json.get("availableHours");
			    
			    JSONArray list = (JSONArray)json.get("asked_hours");
				Integer[] asked_hours = new Integer[list.size()];
				for(int i = 0; i < list.size(); i++) 
				{
					if (list.get(i)!=null)
						asked_hours[i] = Integer.valueOf(String.valueOf(list.get(i)));
					else
						asked_hours[i] = null;
				}
				//for (int i=0; i<asked_hours.length;i++)
				//	System.out.print("asked_hours[i]: "+ asked_hours[i]);

				//	System.out.print("\n");
					
			    for(Object key:calendar.keySet()) 
			    {
				    if (priority < calendar.get(key))
				    {
						for (int i = 0; i < availableHours.size(); i++)
						  if (!String.valueOf(key).equals(String.valueOf(asked_hours[i])) && String.valueOf(availableHours.get(i)).equals(String.valueOf(key)))
						  {
						  	priority = calendar.get(key);
						  	hourIndex = Integer.parseInt(String.valueOf(key));
						  }
					  }
				}
				
				
				int i = 0;
				while(asked_hours[i] != null)
					i++;
				
				asked_hours[i]=hourIndex;
				System.out.println("asked["+i+"]: "+asked_hours[i]);
				//asked_hours.put(i, Integer.parseInt(String.valueOf(availableHours.get(hourIndex))));
				
				
				if (priority >=0.5){ // we need it for the first time
					 ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
					for (i = 1; i < participantAgents.length; ++i) {
						propose.addReceiver(participantAgents[i]);
					}
					JSONObject obj = new JSONObject();
					obj.put("availableHours", availableHours);
					obj.put("asked_hours",Arrays.asList(asked_hours));
					jsonString = obj.toString();
					System.out.println("proposing asked hour in the json file to other participants:"+jsonString);
	      
				    propose.setContent(jsonString);
				    propose.setConversationId("participant-negotiation");
				    propose.setReplyWith("propose"+System.currentTimeMillis()); //unique value
				    myAgent.send(propose);
				    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("participant-negotiation"),
				                            MessageTemplate.MatchInReplyTo(propose.getReplyWith()));
				     System.out.println(getAID().getLocalName() + ": Send propasals. ");
			         System.out.println("highest: "+ priority);
			      replySchedulerAgent = msg.createReply();	      
				}else{
					//  participant not available !! 	
				} 			
			      
			    state = ParticipantAgentState.negotiating;

			    }			
			    else {
				   // all participants will wait to negotiate except the participant that SchedularAgent contacted
				   state = ParticipantAgentState.negotiating;
			      block();
			    }
			    
			    break;	      
		    
		    //myAgent.send(replySchedulerAgent);
			case negotiating: 
					
					msg = myAgent.receive(mt);
					//System.out.println("MSG: \n"+msg);//update a list of known sellers (DF)

				    if (msg != null) {
						String jsonString = msg.getContent();
						JSONParser parser = new JSONParser();	
						JSONObject json = null;
						try
						{
							json = (JSONObject) parser.parse(jsonString);
						} 
						catch (ParseException e)
						{
							e.printStackTrace();
						}

						System.out.println("Proposed json to "+getAID().getLocalName()+": \n"+json);//update a list of known sellers (DF)
						
						// Alexis's PART 
						JSONArray list = (JSONArray)json.get("asked_hours");
						
						int i = 0;
						while (list.get(i) == null) {
							i++;
						}
						
						long currentAskedHour = (long) list.get(i);
						if (calendar.get(currentAskedHour) > 0.5) {
							System.out.println("jsuis chaud");
						} else {
							System.out.println("nope");
						}	
						// There IS A NULL EXPECTION ERROR HERE SO ITS NORMAL
						// --------
						
						
						// look for priority and do neccassary calc.
						// if it is not available, send 1st participant a message that the xx participant not available 
						// if even one participant not available for entire day, send message to SchedularAgent through first participant that the meeting can not be set.
							
				        ACLMessage reply = msg.createReply();
				     
				      //myAgent.send(reply);
				    }
				    else {
					  block();
					}
				 
				break;
	    }
	  }
	  
	   public boolean done() {
	  	
	    //process terminates here if purchase has failed (title not on sale) or book was successfully bought 
	    return (state == ParticipantAgentState.done);
	  }
	}

	
	private class ProposeServer extends CyclicBehaviour {
	  public void action() {
	    //purchase order as proposal acceptance only template
		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
		ACLMessage msg = myAgent.receive(mt);
	    if (msg != null) {
	      String title = msg.getContent();
	      ACLMessage reply = msg.createReply();
	      Integer price = (Integer) catalogue.remove(title);
	      if (price != null) {
	        reply.setPerformative(ACLMessage.INFORM);
	        System.out.println(getAID().getLocalName() + ": " + title + " sold to " + msg.getSender().getLocalName());
	      }
	      else {
	        //title not found in the catalogue, sold to another agent in the meantime (after proposal submission)
	        reply.setPerformative(ACLMessage.FAILURE);
	        reply.setContent("not-available");
	      }
	      myAgent.send(reply);
	    }
	    else {
		    System.out.println(getAID().getLocalName()+":" +state);
				   state = ParticipantAgentState.initiated;
		  block();
		}
	  }
	}

}
