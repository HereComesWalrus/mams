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
    initiated, firstParticipant, negotiating, waiting, done;
}



public class ParticipantAgent extends Agent {
  private ParticipantGui myGui;
  
  private HashMap<Integer, Double> calendar;
  private double priority = -1.0;
  private ParticipantAgentState state = ParticipantAgentState.initiated;
  private AID[] participantAgents;


  private int hourIndex;

  protected void setup() {
   // catalogue = new Hashtable();
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
    

    
    addBehaviour(new TickerBehaviour(this, 1000)
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
		//catalogue.put(title, new Integer(price));
		System.out.println(getAID().getLocalName() + ": " + title + " put into the catalogue. Price = " + price);
      }
    } );
  }
  

	private class OfferRequestsServer extends Behaviour {
	
	  ACLMessage msg;
	  MessageTemplate mt;
	  private ACLMessage replySchedulerAgent;

	  public void action() {
	    //proposals only template
			    //System.out.println("before switch: "+ state);//update a list of known sellers (DF)
	    switch(state)
	    {   
			case initiated:  
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("book-selling");
				template.addServices(sd);
				try{
					DFAgentDescription[] result = DFService.search(myAgent, template);
					System.out.println(getAID().getLocalName() + ": the following participants have been found");
					participantAgents = new AID[result.length - 1];
					int i=0; int j=0;
					while (i < result.length)
					{

						if (!String.valueOf(result[i].getName()).equals(String.valueOf(getAID())))
						{
							participantAgents[j] = result[i].getName();
							System.out.println("test "+ participantAgents[j].getLocalName());
							j++;
						}
						else
						{
							if (i==0 && j==0)
							{
						   		state = ParticipantAgentState.firstParticipant;
							}
							else
							{
								state = ParticipantAgentState.negotiating;
								mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
							}
						}
						i++;

					}
/*
					for (int i = 0; i < result.length; ++i){
						participantAgents[i] = result[i].getName();
						System.out.println("test "+ participantAgents[i].getLocalName());
					}	
*/		    	
				}
				catch (FIPAException fe){
					System.out.println("catch running !! "+ getAID().getLocalName());

					fe.printStackTrace();
				}
			
			    break;
			    	      
		    case firstParticipant:
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
			    
			    System.out.println("received json: \n"+json);//update a list of known sellers (DF)
				// calculate
			    
			    
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
					
					
					for (i = 0; i < participantAgents.length; ++i) {
						propose.addReceiver(participantAgents[i]);
					}
					JSONObject obj = new JSONObject();
					obj.put("availableHours", availableHours);
					obj.put("asked_hours",Arrays.asList(asked_hours));
					jsonString = obj.toString();
					System.out.println("proposing asked hour in the json file to other participants:"+jsonString);
	      
				    propose.setPerformative(ACLMessage.PROPOSE);
				    propose.setContent(jsonString);
				    propose.setConversationId("participant-negotiation");
				    propose.setReplyWith("propose"+System.currentTimeMillis()); //unique value
				    myAgent.send(propose);
				    
				    mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);

				    //mt = MessageTemplate.and(MessageTemplate.MatchConversationId("participant-negotiation"),
				                            //MatchPerformative(ACLMessage.PROPOSE));
				     
				    System.out.println(getAID().getLocalName() + ": Send propasals. ");
			        System.out.println("highest: "+ priority);
					replySchedulerAgent = msg.createReply();	      
				}else{
					//priority=-1;
					//  participant not available !! 	
				} 			
			      
			    state = ParticipantAgentState.waiting;

			    }			
			    else {
			      block();
			    }
		    	break;
		    //myAgent.send(replySchedulerAgent);
			case negotiating: 
		    		mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);

					msg = myAgent.receive(mt);

				    if (msg != null) {
					  //  if (msg.getPerformative() != ACLMessage.PROPOSE) {System.out.println("wth msg.getPerformative: "+msg.getPerformative());}

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

						System.out.println(getAID().getLocalName()+": got from " +msg.getSender().getLocalName()+json);
						
						// look for priority and do neccassary calc.
						// if it is not available, send 1st participant a message that the xx participant not available 
						// if even one participant not available for entire day, send message to SchedularAgent through first participant that the meeting can not be set.
					
						JSONArray availableHours = (JSONArray)json.get("availableHours");
					    
					    JSONArray list = (JSONArray)json.get("asked_hours");
					    System.out.println("availableHours: "+ availableHours+"\n");
						
						System.out.println("asked_hours: "+ list+"\n");

					 
						
						Integer[] asked_hours = new Integer[list.size()];
						
						
						for(int i = 0; i < list.size(); i++) 
						{
							if (list.get(i)!=null)
								asked_hours[i] = Integer.valueOf(String.valueOf(list.get(i)));
							else{
								asked_hours[i] = null;
								if (asked_hours[i-1] != null)
								{
									priority = calendar.get(Integer.parseInt(String.valueOf(asked_hours[i-1])));	
									System.out.println("priority is set to "+ priority+"\n");

								}
							}
						}
						
						
						ACLMessage reply = msg.createReply();
							reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
							
					for (int i = 0; i < participantAgents.length; ++i) {
						System.out.println(getAID().getLocalName()+": adding receiver-> "+ participantAgents[i].getLocalName());

						reply.addReceiver(participantAgents[i]);
					}
					
						if (priority >= 0.5)
						{
							
					JSONObject obj = new JSONObject();
					obj.put("availableHours", availableHours);
					obj.put("asked_hours",Arrays.asList(asked_hours));
					jsonString = obj.toString();
					System.out.println("accept_proposal asked hour :"+jsonString);
	      
				    reply.setContent(jsonString);
				    reply.setConversationId("participant-accept");
				    
				    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("participant-accept"),
				                            MessageTemplate.MatchInReplyTo(reply.getReplyWith()));
				     
				    System.out.println(getAID().getLocalName() + ": Send accept_propasals. ");
					//myAgent.send(reply);	
					
						}
						else
						{	// that hour is not available for participant, suggesting a new one
						    for(Object key:calendar.keySet()) 
						    {
							    if (priority < calendar.get(Integer.parseInt(String.valueOf(key))))
							    {
									for (int i = 0; i < availableHours.size(); i++)
									  if (!String.valueOf(key).equals(String.valueOf(asked_hours[i])) && String.valueOf(availableHours.get(i)).equals(String.valueOf(key)))
									  {
									  	priority = calendar.get(Integer.parseInt(String.valueOf(key)));
									  	hourIndex = Integer.parseInt(String.valueOf(key));
									  }
								  }
							}
							
							
							int i = 0;
							while(asked_hours[i] != null)
								i++;
							
							asked_hours[i]=hourIndex;
							
					// we should add new info to reply.setContent() also we should re-initiate negotitation phase 							
							JSONObject obj = new JSONObject();
							obj.put("availableHours", availableHours);
							obj.put("asked_hours",Arrays.asList(asked_hours));
							jsonString = obj.toString();
					
							reply.setContent(jsonString);

							reply.setPerformative(ACLMessage.REFUSE);
							reply.setConversationId("participant-refused");
							mt = MessageTemplate.and(MessageTemplate.MatchConversationId("participant-refused"),
				                            MessageTemplate.MatchInReplyTo(reply.getReplyWith()));
				         	
				         	System.out.println(getAID().getLocalName() + ": Send REFUSED.\n"+jsonString);

						}
				        
//*/											
				     	System.out.println("highest: "+ priority);
						reply.setReplyWith("reply"+System.currentTimeMillis()); //unique value
				    	myAgent.send(reply);

						System.out.println(getAID().getLocalName()+": priority-> "+ priority+"\n");
						System.out.println(getAID().getLocalName() + ": Send refused to other participants. ");

				        state = ParticipantAgentState.waiting;
					}
				    else {
					  block();
					}
				 
				break;
				
				
			case waiting:
				msg = myAgent.receive(mt);
				if (msg != null) {
					if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
						System.out.println(msg.getSender().getLocalName()+": sent accepted to "+ getAID().getLocalName());	
						// get the last not null element of asked_hours and only first participant can send it to schedularAgent because other participants did not make a contact with schedularAgent
					
						state = ParticipantAgentState.done;
						System.out.println(getAID().getLocalName()+": is "+state+".");	
	
					}
					else if (msg.getPerformative() == ACLMessage.REFUSE) {
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
						
						System.out.println(msg.getSender().getLocalName()+": sent refused to "+ getAID().getLocalName()+"\ngot json: "+json);	
						
						// we have new asked hour array, we get new suggested hour and send new message to other participants
						// state=particapantAgentState.negotiating
					}
					else
					{
						System.out.println(getAID().getLocalName()+": got this message\n"+msg);	
					}
				}
				break;
	    }
	  }
	  
	   public boolean done() {
	  	
	    if (state == ParticipantAgentState.done)
	    {
		    //System.out.println(getAID().getLocalName()+": is done.");	
		    return true;
	    }
	    else
	    {
		    return false;
	    }
	  }
	}

	
/*
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
*/

}
