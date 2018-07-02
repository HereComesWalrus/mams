package jadelab2;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import java.util.Arrays;

import org.json.simple.JSONObject;
import org.json.simple.*;
import org.json.simple.parser.*;


enum SchedulingState
{
    waiting, initiate, negotiating, done;
}

public class SchedulerAgent extends Agent {
  private SchedulerGui myGui;
  private String targetBookTitle;
  private SchedulingState schedule = SchedulingState.waiting;
  
  //list of found sellers
  private AID[] participantAgents;
  
  protected void setup() {
	  System.out.println("||SchedulerAgent||-> " + getAID().getLocalName() + " is ready to contact the participant agents.\n");
	  myGui = new SchedulerGui(this);
	  myGui.display();
		
	  Object[] args = getArguments();

	  addBehaviour(new TickerBehaviour(this, 1000)
	  {
		  protected void onTick()
		  {
			myAgent.addBehaviour(new RequestPerformer());
		  }
	  });
	}

	//invoked from GUI, when schedule button is pressed
	public void initiateNegotiation(final boolean schedule_is_pressed)
	{
		addBehaviour(new OneShotBehaviour()
		{
			public void action()
			{
				if (schedule_is_pressed){
					schedule = SchedulingState.initiate;
					System.out.println("\n\n"+getAID().getLocalName() + ": Schedule button is pressed, notifying first participant to initiate negotiation. ");
					}
			}
		});
	}

    protected void takeDown() {
		myGui.dispose();
		System.out.println("Scheduling agent: " + getAID().getLocalName() + " is terminated.");
	}
  
	private class RequestPerformer extends Behaviour {
	  
	  private Integer availableHours[] = { 8, 9, 10, 11, 12};
	  private Integer[] asked_hours = new Integer[availableHours.length];
	  
	  public void action() {
	  	
	    switch (schedule) 
	    {
	    	case initiate:
	    	//contact first participant agent:
	    	initiate();
	    	break;

	    	case negotiating:
	    	negotiate();	
	    	break;
	    	
	    	case waiting:
	    	block();
	    	break;
	    }        
	  }
	
	  public boolean done() {
	  	
	    return (schedule == SchedulingState.done);
	  }

	  public void initiate(){
		
		MessageTemplate mt;
	  	
	  	System.out.println(getAID().getLocalName() + ": notifying first participant.");
	  	//update the list of participant agents
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-selling");
		template.addServices(sd);
		try
		{
			DFAgentDescription[] result = DFService.search(myAgent, template);
			System.out.println(getAID().getLocalName() + ": the following participants have been found");
			participantAgents = new AID[result.length];
			for (int i = 0; i < result.length; ++i)
			{
				participantAgents[i] = result[i].getName();
				System.out.println(participantAgents[i].getLocalName());
			}
		}
		catch (FIPAException fe)
		{
			fe.printStackTrace();
		}

		myAgent.addBehaviour(new RequestPerformer());
			 
	      
	    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);	
		
		System.out.println("CFP to " + participantAgents[0].getLocalName() + ":");

	    cfp.addReceiver(participantAgents[0]);

	    JSONObject obj = new JSONObject();
	   
		obj.put("availableHours", Arrays.asList(availableHours));
		obj.put("asked_hours", Arrays.asList(asked_hours));
	      
	    String jsonText = obj.toString();
	    System.out.println(jsonText);
	      
	    cfp.setContent(jsonText);
	    cfp.setConversationId("schedule-start");
	    cfp.setReplyWith("cfp"+System.currentTimeMillis()); //unique value
	    myAgent.send(cfp);
	    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("schedule-start"),
	                               MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
	      
	    schedule = SchedulingState.negotiating;
	  }

	  public void negotiate(){
	  	ACLMessage reply = myAgent.receive();
	    if (reply != null && reply.getPerformative() == ACLMessage.CFP) {
	    	//schedulling informed
	    	//Retrieve schedulled hour and remove it from available hours, and then print the array
	    	System.out.println("\n\nGERONIMOOO !!\nSchedulerAgent got: "+reply.getContent());
	    	
	    	
	    	String jsonString = reply.getContent();
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

						    
			JSONArray asked_hours = (JSONArray)json.get("asked_hours");
					   						
			int meeting_hour=-1;
						
			for(int i = 0; i < asked_hours.size(); i++) 
			{
				if (asked_hours.get(i)== null)
				{
					if ( i!=0 && asked_hours.get((i-1)) != null)
					{
						meeting_hour = Integer.parseInt(String.valueOf(asked_hours.get(i-1)));
					}
				}
				else if (i == asked_hours.size()-1)
				
					meeting_hour = Integer.parseInt(String.valueOf(asked_hours.get(i-1)));
			}
			
			
			for(int i = 0; i < availableHours.length; i++) 
			{
				if (meeting_hour==availableHours[i])
					availableHours[i] = null;
			}
			
			
			System.out.println("\n\nMeeting hour is set to :: "+meeting_hour+" o'clock.");
			
	    	schedule = SchedulingState.done;
	    	//myAgent.doDelete();
	    }
	    else{
	    	block(10000);
	    }
	  }
	}
}
