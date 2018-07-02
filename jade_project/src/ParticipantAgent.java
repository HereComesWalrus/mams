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

import java.util.Arrays;

enum ParticipantAgentState
{
    initiated, firstParticipant, negotiating, waiting, informing, done;
}



public class ParticipantAgent extends Agent {
  private ParticipantGui myGui;
  
  private HashMap<Integer, Double> calendar;
  private double priority = -1.0;
  private ParticipantAgentState state = ParticipantAgentState.initiated;
  private AID[] participantAgents;

  private int hourIndex;
  private int acceptCount = 0;
  
  private ACLMessage replySchedulerAgent=null;


  protected void setup() {
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
			myAgent.addBehaviour(new OfferRequestsServer());
			  
		  }
	  });

  }

  protected void takeDown() {
    try {
      DFService.deregister(this);
    }
    catch (FIPAException fe) {
      fe.printStackTrace();
    }
  	myGui.dispose();
    System.out.println("Paricipant agent " + getAID().getName() + " terminated.");
  }


	private class OfferRequestsServer extends Behaviour {
	
	  ACLMessage msg;
	  
	  private boolean contains(Integer[] arr, Integer item) {
      return Arrays.stream(arr).anyMatch(item::equals);
	  }
	  
	  public void action() {
	  
		msg = myAgent.receive();
		if (msg != null || state == ParticipantAgentState.initiated )
		{
			 
			if (state == ParticipantAgentState.initiated )
			{  
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
							System.out.println(participantAgents[j].getLocalName());
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
							}
						}
						i++;

					}

				}
				catch (FIPAException fe){
					System.out.println("catch running !! "+ getAID().getLocalName());

					fe.printStackTrace();
				}
			
			}
		    else if (state == ParticipantAgentState.firstParticipant && msg.getPerformative() == ACLMessage.CFP )
			{
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
			    
			    System.out.println("received json from SchedularAgent: \n"+json);
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
				
				if (priority >=0.5){ // we need it for the first time
					
					System.out.println(getAID().getLocalName()+" will propose hour: "+asked_hours[i]);

					ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
					
					
					for (i = 0; i < participantAgents.length; ++i) {
						propose.addReceiver(participantAgents[i]);
						System.out.println(getAID().getLocalName()+": first participant adding receiver as "+participantAgents[i].getLocalName() );
					}
					
					JSONObject obj = new JSONObject();
					obj.put("availableHours", availableHours);
					obj.put("asked_hours",Arrays.asList(asked_hours));
					jsonString = obj.toString();
					System.out.println(getAID().getLocalName()+": first participant proposes other participants:"+jsonString);
	      
				    propose.setPerformative(ACLMessage.PROPOSE);
				    propose.setContent(jsonString);
				    propose.setConversationId("participant-negotiation");
				    propose.setReplyWith("propose"+System.currentTimeMillis()); //unique value
				    myAgent.send(propose);
				    
					replySchedulerAgent = msg.createReply();	      
				}else{
					//priority=-1;
					//  participant not available !! 	
					System.out.println(getAID().getLocalName()+": firstParticipant priority is less than 0.5 => "+ priority);

				} 			
			      
			    state = ParticipantAgentState.negotiating;

			}			
			else if (msg.getPerformative() == ACLMessage.PROPOSE && state == ParticipantAgentState.negotiating) 
			{
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

				System.out.println(getAID().getLocalName()+": proposed by " +msg.getSender().getLocalName()+json);
						
				JSONArray availableHours = (JSONArray)json.get("availableHours");
					    
			    JSONArray list = (JSONArray)json.get("asked_hours");
					   						
				Integer[] asked_hours = new Integer[list.size()];
						
				double new_priority=-2;

				for(int i = 0; i < list.size(); i++) 
				{
					if (list.get(i)!=null)
						asked_hours[i] = Integer.valueOf(String.valueOf(list.get(i)));
					else
					{
						asked_hours[i] = null;
						if (asked_hours[i-1] != null)
						{
							new_priority = calendar.get(Integer.parseInt(String.valueOf(asked_hours[i-1])));
						}
					}
				}
						
						
				ACLMessage reply = msg.createReply();
				reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
			
				if(
					(priority == -1 && new_priority >= 0.5) ||
					(priority != -1 && new_priority > priority ) ||
					( (priority != -1 && new_priority != -2) &&
					 (new_priority <= priority) && 
					(0.0 <= priority-new_priority && priority-new_priority <= 0.5) )
				)
				{
							
				if(  (priority == -1) || (new_priority > priority) )
					{priority = new_priority;}
						
				JSONObject obj = new JSONObject();
				obj.put("availableHours", availableHours);
				obj.put("asked_hours",Arrays.asList(asked_hours));
				jsonString = obj.toString();
	      
				reply.setContent(jsonString);
				reply.setConversationId("participant-accept");
				    				     
				System.out.println(getAID().getLocalName() + ": Send accept_propasals. ");					
				}
				else
				{	// that hour is not available for participant, suggesting a new one
					
					reply = new ACLMessage(ACLMessage.PROPOSE);

					System.out.println(getAID().getLocalName()+": refused. sending new propose.");
					for (int i = 0; i < participantAgents.length; ++i)
					{
						reply.addReceiver(participantAgents[i]);
						System.out.println(getAID().getLocalName()+": adding receiver to new propose-> "+ participantAgents[i].getLocalName());
					}

					
					state = ParticipantAgentState.negotiating;
						
					double highest_priority = -1;
					int highest_hour = -1;
					
					for (int i=0; i< calendar.size();i++)
					{
						int hour = Integer.parseInt(String.valueOf(calendar.keySet().toArray()[i]));
						if (contains(asked_hours, hour) == false )
						{
							double value = calendar.get(hour);
							if (highest_priority < value)
							{
								highest_priority = value;
								highest_hour = hour;
							}
						}
					}
						
					System.out.println("\n\n"+getAID().getLocalName()+": highest_hour: "+ highest_hour+" highest_priority: "+ highest_priority+"\n");
						
					if (highest_priority != -1 && priority < highest_priority && highest_priority >= 0.5)
					{
						priority = highest_priority;
						System.out.println(getAID().getLocalName()+"priority set to : "+priority+ " from hour: "+highest_hour);
						int i = 0;
					   	while (i < asked_hours.length)
						{
							if (asked_hours[i] == null)
							{
								asked_hours[i] = highest_hour; 
								System.out.println(getAID().getLocalName()+"new suggested hour: "+asked_hours[i] +" asked_hours:"+Arrays.asList(asked_hours));
								break;
							}
							else {i++;}	 
						}
					}
					else if (highest_priority != -1 && priority >= highest_priority && priority - highest_priority<= 0.5)
					{
						int i = 0;
						while (i < asked_hours.length)
						{
							if (asked_hours[i] == null)
							{
								asked_hours[i] = highest_hour; 
								System.out.println(getAID().getLocalName()+": difference accaptable :"+ String.valueOf(priority - highest_priority) + " new suggested hour: "+highest_hour+" asked_hours:"+Arrays.asList(asked_hours));
								break;
							}
							else {i++;}	 
						}
					}
					else if (highest_priority != -1 && priority - highest_priority> 0.5)
					{
						System.out.println(getAID().getLocalName()+": difference huge :"+ String.valueOf(priority - highest_priority) + "  hour: "+highest_hour +" priority:"+priority+" value:"+highest_priority );

					}
					
					acceptCount=0;	
					JSONObject obj = new JSONObject();
					obj.put("availableHours", availableHours);
					obj.put("asked_hours",Arrays.asList(asked_hours));
					jsonString = obj.toString();
					
					reply.setContent(jsonString);
							
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setConversationId("participant-negotiation");
					reply.setReplyWith("propose"+System.currentTimeMillis()); //unique value
					
				         	
					System.out.println(getAID().getLocalName()+" acceptCount is reset.");

					System.out.println(getAID().getLocalName() + ": Send new PROPOSE to other participants.\n"+jsonString);
						 	
					state = ParticipantAgentState.negotiating;
				}
							

				reply.setReplyWith("reply"+System.currentTimeMillis()); //unique value
		    	myAgent.send(reply);
			}	  
			else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL)
			{
				System.out.println("\nn"+msg.getSender().getLocalName()+": accepted proposal by "+getAID().getLocalName() +"got json: "+msg.getContent()+"\n");	
						// get the last not null element of asked_hours and only first participant can send it to schedularAgent because other participants did not make a contact with schedularAgent
					
						//state = ParticipantAgentState.done;
						//System.out.println(getAID().getLocalName()+": is "+state+".");	
				acceptCount++;
				System.out.println(getAID().getLocalName()+" acceptCount is increased to "+acceptCount);

				if (acceptCount == participantAgents.length)
				{
					state = ParticipantAgentState.informing;
					
					if (replySchedulerAgent == null)
					{
						System.out.println(getAID().getLocalName()+" will inform first participant that all participants decided to set a meeting.");
						ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
										
						inform.setContent(msg.getContent());
						inform.setConversationId("participant-inform");
						inform.setReplyWith("inform"+System.currentTimeMillis()); //unique value
						
						for(int i=0; i<participantAgents.length;i++)
							inform.addReceiver(participantAgents[i]);
						
						myAgent.send(inform);
					}
					else{
						System.out.println(getAID().getLocalName()+ ": is first participant, it will respond back to SchedularAgent." );
						replySchedulerAgent.setContent(msg.getContent());
						myAgent.send(replySchedulerAgent);
						state = ParticipantAgentState.done;	
						//myAgent.doDelete();
					}		
				}
				if (acceptCount > participantAgents.length)
				{
					acceptCount = 1;
					System.out.println(getAID().getLocalName()+" acceptCount set to 1.");
				}


			}
			else if (msg.getPerformative() == ACLMessage.INFORM)
			{
				if (replySchedulerAgent != null)
				{
					System.out.println(msg.getSender().getLocalName()+ " informed: "+getAID().getLocalName()+" and it will respond back to SchedularAgent." );	
					replySchedulerAgent.setContent(msg.getContent());
					myAgent.send(replySchedulerAgent);	
					//myAgent.doDelete();
					state = ParticipantAgentState.done;	
				}
	
			}
			else
			{
				System.out.print("\n\n\n\n"+getAID().getLocalName()+": got this message from "+ msg.getSender().getLocalName()+"\n"+msg+"\n\n\n\n");
			}		
	 
	    }
		else 
		{
			block();
		}	
		
	  }
	 
	  
	   public boolean done() {
	  	
	    return(state == ParticipantAgentState.done);
	  }
	}

}
