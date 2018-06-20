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
    waiting, initiate, negotiating, BLUE;
}

public class SchedulerAgent extends Agent {
  private SchedulerGui myGui;
  private String targetBookTitle;
  private SchedulingState schedule;
  
  //list of found sellers
  private AID[] participantAgents;
  
  protected void setup() {
	  System.out.println("||SchedulerAgent||-> " + getAID().getLocalName() + " is ready to contact the participant agents.\n");
	  myGui = new SchedulerGui(this);
	  myGui.display();
		//time interval for buyer for sending subsequent CFP
		//as a CLI argument
		//int interval = 20000;
	  Object[] args = getArguments();
	  //if (args != null && args.length > 0) interval = Integer.parseInt(args[0].toString());
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
		System.out.println("Scheduling agent " + getAID().getLocalName() + " terminated.");
	}
  
	private class RequestPerformer extends Behaviour {
	  private AID bestSeller;
	  private int bestPrice;
	  private int repliesCnt = 0;
	  private MessageTemplate mt;
	  private int step = 0;
	  private Integer availableHours[] = { 8, 9, 10, 11, 12};
	  private Integer[] asked_hours = new Integer[availableHours.length];
	  
	  public void action() {
	  	if (schedule == null)
	    	return;
	    switch (schedule) 
	    {
	    	case initiate:
	    	//contact first participant agent:
	    	initiate();
	    	break;

	    	case negotiating:
	    	negotiate();	
	    	break;
	    }        
	  }
	
	  public boolean done() {
	  	if (step == 2 && bestSeller == null) {
	  		System.out.println(getAID().getLocalName() + ": " + targetBookTitle + " is not on sale.");
	  	}
	    //process terminates here if purchase has failed (title not on sale) or book was successfully bought 
	    return ((step == 2 && bestSeller == null) || step == 4);
	  }

	  public void initiate(){
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
		/*
	      for (int i = 0; i < participantAgents.length; ++i) {
	        cfp.addReceiver(participantAgents[i]);
	      }
		*/ 		
		
		System.out.println("CFP to " + participantAgents[0].getLocalName() + ":");

	    cfp.addReceiver(participantAgents[0]);

	    JSONObject obj = new JSONObject();
	    //JSONArray arr = new JSONArray();
		//arr.put(new Integer[] { 8, 9, 10});
		//obj.put("availableHours", new JSONArray(new Integer[] { 8, 9, 10} ));
		  
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
			
			
			System.out.println("\n\nMeeting hour is set to :: "+meeting_hour);
			
	    	
	    	//myAgent.doDelete();
	    }
	    else{
	    	block(10000);
	    }
	  }
	}
}

//case 1:
	      //collect proposals
/*
	      ACLMessage reply = myAgent.receive(mt);
	      if (reply != null) {
	        if (reply.getPerformative() == ACLMessage.PROPOSE) {
	          //proposal received
	          int price = Integer.parseInt(reply.getContent());
	          if (bestSeller == null || price < bestPrice) {
	            //the best proposal as for now
	            bestPrice = price;
	            bestSeller = reply.getSender();
	          }
	        }
	        repliesCnt++;
	        if (repliesCnt >= participantAgents.length) {
	          //all proposals have been received
	          step = 2; 
	        }
	      }
	      else {
	        block();
	      }

	      break;
*/
/*	    case 2:
	      //best proposal consumption - purchase

	      ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
          order.addReceiver(bestSeller);
	      order.setContent(targetBookTitle);
	      order.setConversationId("book-trade");
	      order.setReplyWith("order"+System.currentTimeMillis());
	      myAgent.send(order);
	      mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
	                               MessageTemplate.MatchInReplyTo(order.getReplyWith()));
	      step = 3;
	      break;
	      
/*	    case 3:      
	      //seller confirms the transaction
/*
	      reply = myAgent.receive(mt);
	      if (reply != null) {
	        if (reply.getPerformative() == ACLMessage.INFORM) {
	          //purchase succeeded
	          System.out.println(getAID().getLocalName() + ": " + targetBookTitle + " purchased for " + bestPrice + " from " + reply.getSender().getLocalName());
		  System.out.println(getAID().getLocalName() + ": waiting for the next purchase order.");
		  targetBookTitle = "";
	          //myAgent.doDelete();
	        }
	        else {
	          System.out.println(getAID().getLocalName() + ": purchase has failed. " + targetBookTitle + " was sold in the meantime.");
	        }
	        step = 4;	//this state ends the purchase process
	      }
	      else {
	        block();
	      }

	      break;
	    */