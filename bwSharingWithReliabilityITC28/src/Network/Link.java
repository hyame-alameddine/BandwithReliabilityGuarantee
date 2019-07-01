package Network;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;

import java.util.ArrayList;

import HelperClasses.Search;
import MainFunctionalities.*;
import Models.ShareBwBetweenTenantsModel;

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
	
	//array of backup bandwidth sharing sets (requests sharing backup bandwidth)
	public ArrayList<SharingSet> sharingSets;
	
	//defining link type as constant
	public static final String MACHINE_TO_TOR_TYPE = "PhysicalMachineToTORLink";
	public static final String TOR_TO_AGGREGATE_TYPE = "TORToAggregateLink";
	public static final String AGGREATE_TO_CORE_TYPE = "AggregateToCoreLink";
	
	public enum BandwidthType {PRIMARY, BACKUP,SHAREDBACKUP}; 
		
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
		this.sharingSets = new ArrayList<SharingSet>();
	}
	
	
	/**
	 * This function clones all the elements of a link
	 */
	public Link clone(FatTreeNetwork clonedNetwork)
	{
		Link l;
		SharingSet s;
		ArrayList<int[]> newBandwidthForRequests = new ArrayList<int[]>();
		ArrayList<SharingSet> newSharingSets = new ArrayList<SharingSet>();
		int [] requestReservation;
		int [] bwForReq;
		Search search = new Search();
		
		Node newSourceNode = search.getClonedNode(clonedNetwork, this.sourceNode);
		Node newDestinationNode = search.getClonedNode(clonedNetwork, this.destinationNode); 
		l= new Link(this.id, this.continuousId, newSourceNode, newDestinationNode, this.capacity, this.type);
		
		for (int i=0; i<this.bandwidthForRequests.size(); i++)
		{
			bwForReq = this.bandwidthForRequests.get(i);
			requestReservation = new int[bwForReq.length];
			
			for (int j=0; j<bwForReq.length; j++)
			{
				requestReservation[j] = bwForReq[j];
			}
			newBandwidthForRequests.add(requestReservation);
		}
		
		for (int i=0; i<this.sharingSets.size(); i++)
		{
			s = this.sharingSets.get(i).clone(l);
			newSharingSets.add(s);
		}		
		
		l.bandwidth = this.bandwidth;
		l.bandwidthForRequests = newBandwidthForRequests;
		l.sharingSets = newSharingSets;
		
		return l;
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
				break;
			case Link.TOR_TO_AGGREGATE_TYPE:
				//number of links between the aggregate switches and the TOR is equal to the number of TOR switches
				level = 2;
				break;
			case Link.AGGREATE_TO_CORE_TYPE:
				//number of links between the core switches and the aggregate switches is equal to the number of aggregate switches
				level = 3;
				break;
		}
		
		return level;
	}
	
	
	/**
	 * This function allows to reserve bandwidth 
	 * if the bandwidth type is sharedBackup, then no bandwidth reservation is done on the link.
	 * The shared bandwidth specifies the backup bandwidth that the requests shares with other tenants
	 * If the shared bandwidth is set than this is the needed backup bandwidth when no bandwidth reuse exists
	 * 
	 * @param bandwidthToReserve
	 * @param requestId
	 * @param bandwidthType specifies if the reserved bandwidth is {primary, backup, sharedBackup}
	 * @return boolean 
	 */
	public boolean reserveBandwidth (int bandwidthToReserve, int requestId, BandwidthType bandwidthType)
	{
		int [] requestReservation = new int[4];
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
				else if (bandwidthType == BandwidthType.BACKUP)
				{	
					//if the bandwidth to reserve was 0, set the backup bandwidth = to the shared bandwidth 
					if (bandwidthToReserve == 0)
					{
						requestReservation[2] =requestReservation[3];
						
						//reserve the backup bandwidth again
						this.bandwidth -= requestReservation[2];
					}
					else
					{
						requestReservation[2] =bandwidthToReserve;
					}
				}
				else if (bandwidthType == BandwidthType.SHAREDBACKUP)
				{
					//in this case the bandwidth to reserve =0, and the shared bandwidth should be = backup bandwidth(no reuse)
					requestReservation[3] = bandwidthToReserve == 0 ? requestReservation[2]: bandwidthToReserve;
				
				}
				
				this.bandwidthForRequests.set(i, requestReservation);
				break;
			}
			
		}
		
		requestReservation = null;
		requestReservation = new int[4];
		
		//if this is the first time we reserve bandwidth for the request we need to add it to the array
		if (!allocationExists)
		{
			//set the bandwidth reserved on this link for the specified request
			requestReservation[0] = requestId;
			if (bandwidthType == BandwidthType.PRIMARY)
			{
				requestReservation[1] = bandwidthToReserve;
				
			}
			else if (bandwidthType == BandwidthType.BACKUP)
			{
				requestReservation[2] = bandwidthToReserve;
				
			}
			else if (bandwidthType == BandwidthType.SHAREDBACKUP)
			{	
				//this case is unlikely to happen because sharing bandwidth is included after reserving main backup bandwidth
				requestReservation[3] = bandwidthToReserve;
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
			requestBandwidth = new int[4];
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
			else if (bandwidthType == BandwidthType.SHAREDBACKUP)
			{
				//this bandwidth is not actually reserved on the link to add to the link bandwidth
				requestBandwidth[3] = 0;
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
		
		linkInformation +=" \n--------Link"+this.continuousId+" : capacity = "+this.capacity+" ; residualBanadiwdth = "+this.bandwidth+"------ \n";
		linkInformation +="SharingSets---\n";
		
		for (int i=0; i<this.sharingSets.size(); i++)
		{
			linkInformation +=this.sharingSets.get(i).toString();
		}
		return linkInformation;
	}
	
	
	/**
	 * This function returns an array list of requestsId, requestsBackupBw to share of requests
	 * that may be able to share that backup bandwidth
	 * 
	 * Requests able to share bandwidth on this link are those that have no bandwidth reuse on this link
	 * 
	 * @return  null if the link has no requests that can share bandwidth. An array of  <requestsIds array,requestsBackupBw>
	 */
	public ArrayList<ArrayList> getSharingRequests()
	{		
		ArrayList<ArrayList> requestsInfo  = null;
		
		// list of requests ids that may be able to share backup bw 
		 ArrayList <Integer> requestsIds = new ArrayList<Integer>();
		
		//list of requests backup bw needed on link l (without sharing) 
		 ArrayList <Integer> requestsBackupBw = new ArrayList<Integer>();
		 int[] requestBandwidth;
		
		 for (int i=0; i<this.bandwidthForRequests.size(); i++)
		{
			requestBandwidth = this.bandwidthForRequests.get(i);
			
			//if no primary bandwidth is reserved on this link for the request than no bw reuse (backup+primary) exists, make sure that the backup bandwidth is not 0
			//this happens when releasing bandwidth for a request
			if (requestBandwidth[1] == 0 && requestBandwidth[2]!=0 )
			{
				requestsIds.add(requestBandwidth[0]);
				requestsBackupBw.add(requestBandwidth[2]);
			}
			requestBandwidth=null;
		}
		
		if (requestsIds.size()!=0 && requestsBackupBw.size()!=0)
		{
			requestsInfo = new ArrayList<ArrayList>();
			requestsInfo.add (requestsIds);
			requestsInfo.add(requestsBackupBw);
		}
		
		return requestsInfo;
	}
	
	
	
	/**
	 * This function checks if a link is used by a certain request and returns the request bandwidth
	 * allocation on this link
	 * 
	 * @param requestId id of the request to search for its bandwidth allocation
	 * @return bandwidth reservation of request or null if request is no using the link
	 */
	public  int[]  getBandwidthAllocatedForRequest(int requestId)
	{
		int [] requestReservation =null;
		
		for (int i =0; i<this.bandwidthForRequests.size(); i++)
		{
				
			if ( this.bandwidthForRequests.get(i)[0]== requestId)
			{
				requestReservation = this.bandwidthForRequests.get(i);
			}
		}
		
		
		return requestReservation;
		
	}
	
	
	/**
	 * This function will order the list of requests and bandwidth requests in descending order
	 * This will only order requests that may be able to share bandwidth between tenants
	 * this is using the insertion sort complexity O(n^2)
	 * @param requestsInfo array list of requestsId and backupBw or requests able to share bandwidth 
	 */
	public ArrayList<ArrayList> orderSharingRequests(ArrayList<ArrayList> requestsInfo)
	{
		
		// list of requests ids that may be able to share backup bw 
		 ArrayList <Integer> requestsIds = requestsInfo.get(0);
			
		//list of requests backup bw needed on link l (without sharing) 
		ArrayList <Integer> requestsBackupBw = requestsInfo.get(1);
		
		Integer  backupBw1, backupBw2;
		Integer  request1, request2;
		
		for (int i=0; i< requestsBackupBw.size(); i++)
		{	
			backupBw1 = requestsBackupBw.get(i);
			request1 = 	requestsIds.get(i);	
			
			for (int j=i+1; j< requestsBackupBw.size(); j++)
			{	
				backupBw2 =  requestsBackupBw.get(j);
				request2 = 	requestsIds.get(j);
				
				//compare the number of available VMs
				if ( backupBw1 < backupBw2 )
				{
					requestsBackupBw.set (i,backupBw2);
					requestsBackupBw.set (j,backupBw1);
					
					requestsIds.set(i, request2);
					requestsIds.set(j, request1);
				}
			}
		}
		
		requestsInfo.set(0, requestsIds);
		
		requestsInfo.set(1, requestsBackupBw);
		
		return requestsInfo;
	
	}
	
	
	
	
	/**
	 * This function specifies the sets of tenants that are able to share bandwidth on this link
	 * sets the sharingStes attribute of the link using the ShareBwBetweenTenantsModel
	 * 
	 * @param requestsIds decreased order of requests id that can share bandwidth on this link(no bandwidth reuse)
	 * @param requestsBackupBw decreased order of requests that can share bandwidth on this link(no bandwidth reuse) backup bandwidth 
	 * requestsIds and requestsBackupBw are given by orderSharingRequests()
	 * @param vmsProtection needed to get the requests
	 */
	public void buildSharingSetUsingShareBwModel( ArrayList <Integer> requestsIds,ArrayList <Integer> requestsBackupBw, VMsProtectionWithBandwidthGuarantee vmsProtection ) throws IloException
	{
		 IloIntVar[][] y ;	
		 SharingSet s;
		 Request r = null;		 

		 //the sharing set information returned by the model
		 ArrayList <IloIntVar[][]> sharingSetInfo = new  ArrayList <IloIntVar[][]>();
	 
		//prevent multiple objective error with cplex so declare new model instance at each iteration
		ShareBwBetweenTenantsModel shareBwModel = new ShareBwBetweenTenantsModel(vmsProtection);
		sharingSetInfo = shareBwModel.modelFormulation(this,requestsIds, requestsBackupBw, true, null, null);
			
		//get the list of requests assignment sets (have y[i][j] = 1)
		y = sharingSetInfo.get(0);
	
		//loop over the sets 
		for (int i=0; i<requestsIds.size(); i++)
		{
			s = new SharingSet(this);
			
			//set the bandwidth to reserve for the set = b returned by the model
			s.bandwidthToReserve = (int) shareBwModel.cplex.getValue(sharingSetInfo.get(1)[0][i]);
			
			//loop over tenants
			for (int j = 0; j<requestsIds.size(); j++)
			{
				//the request is not part of the set 
				if ((int)shareBwModel.cplex.getValue(y[j][i]) == 0 )
				{
					continue;
				}
				
				//get the request as object 
				r =  vmsProtection.getRequest(requestsIds.get(j), Request.Type.ARRIVAL);
				
				//add the request to the set and specify its backup bandwidth
				s.requests.add(r);
				s.requestsBackupBw.add(requestsBackupBw.get(j));
				
				//adding the sharing set to the request sharing set array
				r.sharingSets.add(s);
			}
			
			//if the set contains only one request than this request will not share bandwidth and hence we won't consider it as a sharing set
			if (s.requests.size() >1)
			{
				this.sharingSets.add(s);
			}
			// make sure that a set contains at least one request (r is set to prevent null pointer exception)
			else if (r!=null)
			{
				/**
				 * if the sharing contains only one request, then it will not be considered as a set
				 * we should remove it from the request sharingSets for the request it contains
				 * this sharing set will be the last element in the sharing set array of the request
				 */
				r = s.requests.get(0);
				r.sharingSets.remove (r.sharingSets.size()-1); 
				
			}
			
			r=null;
		}
		
	}
	
	
	/**
	 * This function specifies the sets of tenants that are able to share bandwidth on this link
	 * sets the sharingStes attribute of the link
	 * 
	 * @param requestsIds decreased order of requests id that can share bandwidth on this link(no bandwidth reuse)
	 * @param requestsBackupBw decreased order of requests that can share bandwidth on this link(no bandwidth reuse) backup bandwidth 
	 * requestsIds and requestsBackupBw are given by orderSharingRequests()
	 * @param vmsProtection needed to get the requests
	 */
	public void buildSharingSets (  ArrayList <Integer> requestsIds,ArrayList <Integer> requestsBackupBw , VMsProtectionWithBandwidthGuarantee vmsProtection)
	{
		Request r = null;
		
		SharingSet s = new SharingSet (this);
		
		//get the request as object 
		r = vmsProtection.getRequest(requestsIds.get(0), Request.Type.ARRIVAL);
		
		//add the request of max backup bandwidth to the set
		s.requests.add(r);
		s.requestsBackupBw.add(requestsBackupBw.get(0));
		
		//set the bandwidth to reserve to the first request added to the set since requests are ordered descendly based on bw
		s.bandwidthToReserve = requestsBackupBw.get(0);
		
		//adding the sharing set to the request sharing set array
		r.sharingSets.add(s);
		
		//remove the request from the arrays since it was added to a sharing set
		requestsIds.remove(0);
		requestsBackupBw.remove(0);
		r=null;
	
		//loop over the remaining requests
		for (int i=0; i<requestsIds.size(); i++)
		{
			r = vmsProtection.getRequest(requestsIds.get(i), Request.Type.ARRIVAL);
			
			if (r.canShareBandwidth(s))
			{
				//add the request to the sharing set if it can share bw with its requests
				s.requests.add(r);
				s.requestsBackupBw.add(requestsBackupBw.get(i));
				
				//adding a copy of the sharing set to the request sharing set array to keep as record after request departure
				r.sharingSets.add(s);
				
				/**
				 * remove the request from the arrays since it was added to a sharing set 
				 * 	(don't use .remove() because it shifts back element and so not all requests will be evaluated)
				 */	
				requestsIds.set(i,null);
				requestsBackupBw.set(i,null);
			}
		}
		
		//if the set contains only one request than this request will not share bandwidth and hence we won't consider it as a sharing set
		if (s.requests.size() >1)
		{
			this.sharingSets.add(s);
		}
		else
		{
			/**
			 * if the sharing contains only one request, then it will not be considered as a set
			 * we should remove it from the request sharingSets for the request it contains
			 * this sharing set will be the last element in the sharing set array of the request
			 */
			r = s.requests.get(0);
			r.sharingSets.remove (r.sharingSets.size()-1); 
			
		}
		
		//remove null elements from the arrays
		Search arrayHelper = new Search();
		requestsIds = arrayHelper.removeNullElements(requestsIds);
		requestsBackupBw = arrayHelper.removeNullElements(requestsBackupBw);
		
		if(!requestsIds.isEmpty())
		{
			this.buildSharingSets (requestsIds,requestsBackupBw, vmsProtection);
		}
		
	}
	
	
	/**
	 * This function reserves the shared bandwidth between tenants
	 * based on the sharing sets and set the shared bandwidth for each request on this link
	 * and releases its backup bandwidth
	 * 
	 * @return boolean
	 */
	public boolean reserveTenantsSharedBandwidth()
	{
		SharingSet s = null;
		
		for (int i=0; i<this.sharingSets.size(); i++)
		{
			s = this.sharingSets.get(i);
						
			for (int j=0;j<s.requests.size(); j++)
			{
				//set the shared bandwidth equal to the backup bandwidth for the record
				this.reserveBandwidth(0, s.requests.get(j).id,  BandwidthType.SHAREDBACKUP);
				
				//release the reserved backup bandwidth because it is now shared
				this.releaseBandwidth(s.requests.get(j).id, BandwidthType.BACKUP);
				
				//update the reserved bandwidth array of the request
				s.requests.get(j).updateReservedBandwidth(s.requests.get(j).subtree.fatTreeNetwork);
			}		
			

			if (this.bandwidth < s.bandwidthToReserve)
			{	System.out.println(" ISSSUE reservation of tenants share ");
				return false;
			}
			
			this.bandwidth-=s.bandwidthToReserve;	
			
		}
		
		return true;
	}
	
	
	/**
	 * This function releases the shared bandwidth between tenants on this link
	 * it sets the shared bandwidth to 0 and reserve the backup bandwidth again
	 */
	public void releaseTenantsSharedBandwidth()
	{
		SharingSet s = null;
		
		for (int i=0; i<this.sharingSets.size(); i++)
		{
			s = this.sharingSets.get(i);			
		
			this.bandwidth+=s.bandwidthToReserve;	
		
			for (int j=0;j<s.requests.size(); j++)
			{
				//reserve the backup bandwidth and set it equal to the shared one
				this.reserveBandwidth(0, s.requests.get(j).id, BandwidthType.BACKUP);
				
				//release the shared bandwidth 
				this.releaseBandwidth( s.requests.get(j).id,  BandwidthType.SHAREDBACKUP);
				
				s.requests.get(j).updateReservedBandwidth(s.requests.get(j).subtree.fatTreeNetwork);
				
				//update the request sharing sets
				s.requests.get(j).removeSet(s);
			}
		}
		
		//remove all the sharing sets of the links since their bandwidth was released
		this.sharingSets = new ArrayList<SharingSet>();
	}
}
