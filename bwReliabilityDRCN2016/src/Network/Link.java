package Network;
import java.util.ArrayList;

/**
 * This class defines the physical links in the tree network
 * 
 * @author Hyame
 *
 */
public class Link {
	
	//id of the link
	public int id;
	
	//id of the link set in a continuous way not based on the level. It is used in the models
	public int continuousId;
	
	//source node of the link
	public Node sourceNode;
	
	//destination node of the link
	public Node destinationNode;
	
	//capacity of the link
	public int capacity;
	
	//residual bandwidth of the link
	public int bandwidth;
	
	//link type
	public String type;
	
	//array holding requests id and the bandwidth reserved for this request on that link
	public ArrayList <int[]> bandwidthForRequests;
	
	//defining link type as constant
	public static final String MACHINE_TO_TOR_TYPE = "PhysicalMachineToTORLink";
	public static final String TOR_TO_AGGREGATE_TYPE = "TORToAggregateLink";
	public static final String AGGREATE_TO_CORE_TYPE = "AggregateToCoreLink";
	
	public enum BandwidthType {PRIMARY, BACKUP}; 
		
	/**
	 * General constructor
	 */
	public Link(){
		
	}
	
	
	/**
	 * Constructor of the link object 
	 * 
	 * @param id
	 * @param sourceNode
	 * @param destinationNode
	 * @param capacity
	 */
	public Link(int id, int continuousId, Node sourceNode, Node destinationNode, int capacity, String type)
	{		
		this.id = id;
		this.continuousId = continuousId;
		this.sourceNode = sourceNode;
		this.destinationNode = destinationNode;
		this.capacity = capacity;
		this.bandwidth = this.capacity;
		this.type = type;
		this.bandwidthForRequests = new ArrayList<int[]>();
	}
	
	
	/**
	 * This function returns the link level based on its type
	 * 
	 * @return level link level
	 */
	
	public int getLinkLevel()
	{
		int level = 0;
		
		switch (this.type) 
		{
			case Link.MACHINE_TO_TOR_TYPE:
				level = 1;
			
			case Link.TOR_TO_AGGREGATE_TYPE:
				//number of links between the aggregate switches and the TOR is equal to the number of TOR switches
				level = 2;
			
			case Link.AGGREATE_TO_CORE_TYPE:
				//number of links between the core switches and the aggregate switches is equal to the number of aggregate switches
				level = 3;
		}
		
		return level;
	}
	
	
	/**
	 * This function allows to reserve bandwidth 
	 * 
	 * @param bandwidthToReserve
	 * @param requestId
	 * @param bandwidthType specifies if the reserved bandwidth is {primary, backup}
	 * @return boolean 
	 */
	public boolean reserveBandwidth (int bandwidthToReserve, int requestId, BandwidthType bandwidthType)
	{
		int [] requestReservation = new int[3];
		boolean allocationExists = false;
		
		//check if there exist sufficient bandwidth to reserve
		if (this.bandwidth < bandwidthToReserve)
		{
			return false;
		}
		
		this.bandwidth -= bandwidthToReserve;
		
		//check if we already reserved  primary or backup bandwidth for the request
		for(int i = 0; i<this.bandwidthForRequests.size(); i++)
		{
			requestReservation = this.bandwidthForRequests.get(i);
			if (requestReservation[0] == requestId)
			{
				allocationExists = true;
				if (bandwidthType == BandwidthType.PRIMARY)
				{
					requestReservation[1] = bandwidthToReserve;
					
				}
				else
				{
					requestReservation[2] = bandwidthToReserve;
					
				}
				
				this.bandwidthForRequests.set(i, requestReservation);
				break;
			}
			
		}
		requestReservation = null;
		requestReservation = new int[3];
		//if this is the first time we reserve bandwidth for the request we need to add it to the array
		if (!allocationExists)
		{
			//set the bandwidth reserved on this link for the specified request
			requestReservation[0] = requestId;
			if (bandwidthType == BandwidthType.PRIMARY)
			{
				requestReservation[1] = bandwidthToReserve;
				
			}
			else
			{
				requestReservation[2] = bandwidthToReserve;
				
			}
			this.bandwidthForRequests.add (requestReservation);
		}
		requestReservation = null;
		return true;
	}
	
	
	/**
	 * This function releases the bandwidth reserved for the specified request
	 *
	 * @param requestId
	 * @param bandwidthType bandwidth type to release. Set to null if we want to release 
	 * all the bandwidth of the request
	 * 
	 * @return 
	 */
	public void releaseBandwidth ( int requestId, BandwidthType bandwidthType)
	{
		int[] requestBandwidth;
		
		for(int i = 0; i<this.bandwidthForRequests.size(); i++)
		{
			requestBandwidth = new int[3];
			requestBandwidth = this.bandwidthForRequests.get(i);
			
			if (requestBandwidth[0] != requestId)
			{
				continue;
			}
			
			if (bandwidthType == BandwidthType.PRIMARY)
			{
				this.bandwidth += requestBandwidth[1];
				
				requestBandwidth[1] = 0;
			}
			else if (bandwidthType == BandwidthType.BACKUP)
			{
				this.bandwidth += requestBandwidth[2];
				
				requestBandwidth[2] = 0;
			}				
			else if (bandwidthType == null)
			{	
				//release primary and backup bandwidth
				this.bandwidth += requestBandwidth[1]+requestBandwidth[2];
				this.bandwidthForRequests.remove(i);
			}
			
			//free memory
			requestBandwidth = null;
		}
		

		
	}
	
	/**
	 * This function returns a string of link information
	 * 
	 * @return String linkInformation
	 */
	public String toString()
	{
		String linkInformation = "";
		
		linkInformation +=" Link"+this.continuousId+" : capacity = "+this.capacity+" ; residualBanadiwdth = "+this.bandwidth+" \n";
		
		return linkInformation;
	}
	
}
