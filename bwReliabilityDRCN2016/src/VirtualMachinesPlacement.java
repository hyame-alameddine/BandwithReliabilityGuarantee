import ilog.concert.IloException;

import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Random;

import Network.FatTreeNetwork;
import Network.Link;
import Network.PhysicalMachine;
import Network.Request;
import Network.SubTree;
import Network.Switch;
import Network.VirtualMachine;


/**
 * This class contains the main bandwidth guarantee algorithm
 * 
 * A request is defined by a 3 dimensions array [requestId, nbOfVMs, bandwidthPerVM]
 * 
 * @author Hyame
 *
 */
public class VirtualMachinesPlacement{
	
	FatTreeNetwork treeNetwork;

	VirtualMachinesPlacement(FatTreeNetwork treeNetwork)
	{
		this.treeNetwork = treeNetwork;
		
	}
	
	
	/**
	 * Main requests placement algorithm that places VMs and guarantee them bandwidth
	 * based on the hose model
	 * 
	 * @param requests requests to place in the network
	 * 
	 * @return an array list indexed by the request id with allocations for its VMs
	 */
	public ArrayList <ArrayList <int []>> hoseVMPlacementAlgorithm( ArrayList<Request> requests)
	{
		SubTree subTree = null;
		int treeLevel = 1;
		
		//array list of physical machine with VM allocated on them for each request
		ArrayList <int []> physicalVMAllocation = new ArrayList <int []>();
		
		//array of <pmId, VMAllocated for the request> that hold the number of VM allocated to the specified pm
		ArrayList <ArrayList <int []>> requestsAllocation = new ArrayList <ArrayList <int []>>();
		
		for (int i=0; i<requests.size(); i++)
		{
			/**
			 * start the search for the best placement at the lowest level of the network
			 * the returned subtree can be null if the network can not admit the request
			 * or can be at any level of the network since this a recursive function
			 */
			subTree = this.getBestPlacementForRequest(requests.get(i), treeLevel);
			
			if (subTree == null)
			{
				//this means that the request was not admitted 
				requestsAllocation.add(i, null);
				continue;
				
			}
		
			physicalVMAllocation = this.allocateRequest (requests.get(i), subTree);
			requestsAllocation.add(i, physicalVMAllocation);
		}
		
		physicalVMAllocation = null;
		requests = null;
		
		return requestsAllocation;
	}
	
	
	/**
	 * Main requests placement algorithm that places VMs and guarantee them bandwidth
	 * based on the hose model. This function is designed for a single request
	 * 
	 * @param request request to place in the network
	 * 
	 * @return an array list of physicalMachines with the number of VMs allocated on each of them for their requests
	 */
	public ArrayList <int []> hoseVMPlacementAlgorithmSingleRequest ( Request request)
	{
		SubTree subTree = null;
		int treeLevel = 1;
		
		//array list of physical machine with VM allocated on them for each request
		ArrayList <int []> physicalVMAllocation = new ArrayList <int []>();		
		
		/**
		 * start the search for the best placement at the lowest level of the network
		 * the returned subtree can be null if the network can not admit the request
		 * or can be at any level of the network since this a recursive function
		 */
		subTree = this.getBestPlacementForRequest(request, treeLevel);
		
		if (subTree == null)
		{
			//this means that the request was not admitted 
			return null;
		}
		
		physicalVMAllocation = this.allocateRequest (request, subTree);			
		
		subTree = null;
		request = null;
		
		return  physicalVMAllocation;
	}
	
		
	/**
	 * This function allocates the request to the given subtree by allocating 
	 * VMs and reserving bandwidth over all the links of the subtree
	 * 
	 * @param request request to allocate
	 * @param subTree best subtree where to allocate the request
	 * 
	 * @return array list of physical machines id and the number of VMs allocated for the request
	 * 
	 */
	public ArrayList<int[]> allocateRequest (Request request, SubTree subTree)
	{	
		PhysicalMachine pm;
		Link l;
		
		//remaining VM that are not yet allocated
		int remainingVMs = request.N;
		
		//VMs to allocate to this physical machine
		int VMToAllocate = 0;
		
		//array of <pmId, VMAllocated for the request> that hold the number of Vm allocated to the specified pm
		ArrayList <int []> physicalVMAllocation = new ArrayList<int[]>();
		
		//temporary array that holds <pmId, VMAllocated for the request>
		int[] allocation;
		
		//bandwidth needed to reserve on link
		int bandwidthNeeded = 0 ;
		
		//loop over the subtree physical machines
		for(int i=0; i<subTree.physicalMachines.size(); i++)
		{
			pm = subTree.physicalMachines.get(i);
			
			//stop looping through physical machines if no remaining VMs to allocate
			if( remainingVMs ==0 )
			{
				break;
			}
			
			//if the current pm have no available VM continue to the next one
			if (pm.getAvailableVM()==0)
			{
				continue;
			}
			
			//search for the link having the physical machine as source node
			l = subTree.searchLink(pm);
			
			//if there is empty slots in pm but the link has bandwidth<bandwith needed for one VM we continue
			if (l.bandwidth < request.B)
			{
				continue;
			}
			
			//get the VM that should be allocated to the physical machine pm
			VMToAllocate = remainingVMs<pm.getAvailableVM() ? remainingVMs:pm.getAvailableVM();
			
			//if l can not guarantee bandwidth for all VMToAllocate we allocate only the number it can guarantee admit
			bandwidthNeeded =  VMToAllocate < (request.N-VMToAllocate) ? VMToAllocate*request.B :  (request.N-VMToAllocate)*request.B;
			
			if(l.bandwidth < bandwidthNeeded)
			{
				VMToAllocate = l.bandwidth/request.B;
				
			}
			
			//reserve VMs
			pm.reserveVM(VMToAllocate,request, VirtualMachine.Type.PRIMARY);
			
			//reduce the amount of remaining VMs to allocate
			remainingVMs-=VMToAllocate;
			
			//reserve bandwidth
			bandwidthNeeded =  VMToAllocate < (request.N-VMToAllocate) ? VMToAllocate*request.B :  (request.N-VMToAllocate)*request.B;
			l.reserveBandwidth(bandwidthNeeded,request.id,Link.BandwidthType.PRIMARY);
			
			//reset bandwidth for the next iteration
			bandwidthNeeded = 0;
			
			//add the allocation to physicalVMAllocation array list
			allocation = new int[2];
			allocation[0] = pm.id;
			allocation[1] = VMToAllocate;
			physicalVMAllocation.add(allocation);
			allocation = null;
			
		}
		
		//allocate bandwidth on the upper levels link (2,3)
		this.allocateUpperLevelBandwidth (subTree, request, physicalVMAllocation, true);
		
		//update the subtree and reservedBandwidth attribute of the request 
		request.setSubTree(subTree);
		request.updateReservedBandwidth(this.treeNetwork);
		
		return physicalVMAllocation;
	}
	
	

	/**
	 * This function allows reserving/unreserving bandwidth on the upper levels (2,3) of the sub tree 
	 * based on the reserve/unallocate parameters
	 * It returns true if the bandwidth can be reserver on this subtree
	 * 
	 * @param subTree subtree where we want to allocate the bandwidth
	 * @param request request to allocate bandwidth for
	 * @param physicalVMAllocation <pmId, VMsAllocated> the physical machines where we allocated the request
	 * @param reserve true if we want to reserve the bandwidth
	 * @return true if the bandwidth can be reserved
	 */
	public boolean allocateUpperLevelBandwidth (SubTree subTree, Request request, ArrayList <int[]> physicalVMAllocation, boolean reserve)
	{
		Link l = null;
		int bandwidth = 0;
		int allocatedVMs = 0;
		System.out.println("switches size "+subTree.switches.size());
		for (int i=0; i<subTree.switches.size(); i++)
		{ 
			//get link having the switch as source node, this ensures not getting the link related to Pm
			l = subTree.searchLink(subTree.switches.get(i));
			
			//if there is no link with the specified switch as source then we are at core switch
			if(l==null)
			{
				continue;
			}
			
			//get the number of VMs allocated under the switch
			allocatedVMs = subTree.getAllocatedVM(physicalVMAllocation, subTree.switches.get(i));
			
			//get the bandwidth that need to be reserved on the link
			bandwidth = allocatedVMs*request.B<(request.N-allocatedVMs)*request.B ? 
					allocatedVMs*request.B:(request.N-allocatedVMs)*request.B;
			
			System.out.println("Switch "+subTree.switches.get(i).id+" level "+subTree.switches.get(i).level+" allocatedVms "+allocatedVMs+" bw "+bandwidth);
			
			//return false if the link can not guarantee the bandwidth
			if ( l.bandwidth < bandwidth)
			{
				return false;
			}
		
			//reserve the minimum bandwidth on the link
			if (reserve)
			{
				l.reserveBandwidth(bandwidth,request.id,Link.BandwidthType.PRIMARY);
			}
		}		
		
		return true;		
		
	}
	
	
	/**
	 * This function returns the list of subtrees that can fit the request
	 * 
	 * @param subtreeLevel defines at with level we want to find the subtrees that fits the request
	 * @param request a 3 dimensions array [requestId, nbOfVMs, bandwidthPerVM]
	 * 
	 *  @return ArrayList of subTrees that can fit the request 
	 */
	public ArrayList <SubTree> getSubtreesWithAvailableSlots (int subtreeLevel, Request request )
	{
		//list of subtree that can fit the request
		ArrayList <SubTree> availableSubTrees = new ArrayList<SubTree>(); 
		SubTree subTree;
				
		//get the switches at the specified level, they represent all the sub trees
		Switch [] subtreesRoots = this.treeNetwork.getSwitchSetPerTreeLevel(subtreeLevel);
		
		//loop over the switches
		for (int i = 0; i< subtreesRoots.length; i++)
		{	
			subTree = new SubTree(subtreesRoots[i]) ;	
			
			//build the subTree
			subTree = this.treeNetwork.buildSubTree(subTree, subTree.rootNode);
			
			//check for the available VMs
			if ( subTree.getAvailableVms() < request.N)
			{
				continue;
			}			
			
			//add the sub tree information to the array
			availableSubTrees.add (subTree);
			subTree = null;			
		}
		
		subtreesRoots = null;
		subTree = null;
		
		return availableSubTrees;
	}

	
	
	
	

	
	/**
	 * This function checks if the bandwidth of the request can be satisfied 
	 * by the sub tree
	 *
	 * @param subTree 
	 * @param request array [requestId, nbOfVMs, bandwidthPerVM]
	 * 
	 * @return true is the bandwidth of the request can be admitted in the subtree
	 */
	public boolean isBandwidthAvailable (SubTree subTree, Request request)
	{	
		PhysicalMachine pm;
		Link l;
		
		//remaining VM that are not yet allocated
		int remainingVMs = request.N;
		
		//VMs to allocate to this physical machine
		int VMToAllocate = 0;
		
		//array of <pmId, VMAllocated for the request> that hold the number of Vm allocated to the specified pm
		ArrayList <int []> physicalVMAllocation = new ArrayList<int[]>();
		
		//temporary array that holds <pmId, VMAllocated for the request>
		int[] allocation = new int[2];
		
		//bandwidth needed to reserve on link
		int bandwidthNeeded = 0 ;
		
		//loop over the subtree physical machines
		for(int i=0; i<subTree.physicalMachines.size(); i++)
		{
			pm = subTree.physicalMachines.get(i);

			//stop looping through physical machines if no remaining VMs to allocate
			if( remainingVMs ==0 )
			{
				break;
			}
			
			//if the current pm have no available VM continue to the next one
			if (pm.getAvailableVM()==0)
			{
				continue;
			}
			
			//search for the link having the physical machine as source node
			l = subTree.searchLink(pm);
			
			//if there is empty slots in pm but the link has bandwidth<bandwith needed for one VM we continue
			if (l.bandwidth < request.B)
			{				
				continue;
			}
			
			//get the VM that should be allocated to the physical machine pm
			VMToAllocate = remainingVMs<pm.getAvailableVM() ? remainingVMs:pm.getAvailableVM();
		
			//if l can not guarantee bandwidth for all VMToAllocate we allocate only the number it can guarantee admit
			
			bandwidthNeeded =  VMToAllocate < (request.N-VMToAllocate) ? VMToAllocate*request.B :  (request.N-VMToAllocate)*request.B;
			
			if(l.bandwidth < bandwidthNeeded)
			{
				//this is valid because at most we will VMToAllocate*B bandwidth to allocate or less
				VMToAllocate = l.bandwidth/request.B;
				
			}
			
			bandwidthNeeded =0;
			
			//allocate VMs since we are sure the link can support it
			//reduce the amount of remaining VMs to allocate
			remainingVMs-=VMToAllocate;
	
			//add the allocation to physicalVMAllocation array list
			allocation = new int [2];
			allocation[0] = pm.id;
			allocation[1] = VMToAllocate;
			physicalVMAllocation.add(allocation);		
			allocation = null;
			
		}
		
		/**
		 *  if the loop through physical machines ends with remaining VM to allocate, this means that
		 *  some of the links related to the physical machines can not support the requested bandwidth
		 *  and so the subTree is not a valid one for this request
		 */
		if (remainingVMs !=0)
		{	
			return false;
		}
		
			
		/**
		 * we need to check bandwidth on the upper level links related to switches
		 * 
		 */		
		if (subTree.rootNode.level!=1)
		{
			return this.allocateUpperLevelBandwidth (subTree, request, physicalVMAllocation, false);
		}
		
		return	true;		
		
	}
		
	/**
	 * Check if bandwidth available in a subtree starting with the highest level.
	 * This takes into consideration not rejecting a request after the first attempt but trying
	 * to allocate the minimum number of VMs possible
	 * @TODO fix mentioned issue (at the last iteration)
	 * 
	 * @param subTree the main sub tree
	 * @param request the request
	 * @param vmToAllocate the actual number of vms to allocate (should be equal to N initially)
	 * @return
	 */
	public int[] isBandwidthAvailableUpdated (SubTree subTree, Request request, int vmToAllocate)
	{
		ArrayList<SubTree> childTrees;
		int [] returnValues = new int [2];
		SubTree s = null;
		Link l;
		int bandwidthToReserve;
		int childRemainingVms = -1;
		//this holds the VMs that we should try to allocate to each child tree
		int remainingVms = vmToAllocate;
		
		
		/** 
		 * this holds the total number of remaining VMs to allocate to the next child tree 
		 * (considering only child trees of the initial subTree od the base call of the function)
		 */		
		int totalRemaining = request.N;
				
		System.out.println("MAIN SUBTREE :" + subTree.rootNode.id+ " level : "+subTree.rootNode.level);
		System.out.println("MAIN REQUEST :" +vmToAllocate);
		
		//get child trees on level below
		childTrees = subTree.getChildTrees ();
		
		//order child trees by residual link bandwidth
		this.treeNetwork.orderSubtreesByUpperLinkResidualBandwidth(childTrees);
		
		//loop over child trees
		for (int i = 0; i< childTrees.size(); i++)
		{	
					
			//get child tree
			s = childTrees.get(i);
			
			
			//keep looping through the same sub tree until we get childRemainingVms=0; so we are able to allocate the minimum nb of VM that can be admit it
			if ( childRemainingVms >0 )
			{
				//decrease i to make sure it will be called for all sub trees
				i--;
				
				//get the previous sub tree that couldn't accommodate the request
				s = childTrees.get(i);	
				
				//try allocating the nb of VMs that were able to be admitted in the previous iteration
				remainingVms = vmToAllocate - childRemainingVms;
			}
			
			System.out.println("		CHILD :" + s.rootNode.id+ " level : "+s.rootNode.level+" initial allocation "+ remainingVms);	
			
			vmToAllocate = remainingVms < s.getAvailableVms() ? remainingVms : s.getAvailableVms();				
			bandwidthToReserve =  vmToAllocate < (request.N-vmToAllocate) ? vmToAllocate*request.B :  (request.N-vmToAllocate)*request.B;
			
			//get the link in the main subTree that have the child subtree rootNode as source node
			l = subTree.searchLink(s.rootNode);
			
			if (l.bandwidth < bandwidthToReserve)					
			{	
				//if l can not guarantee bandwidth for all VMToAllocate we allocate only the number it can guarantee admit		
				vmToAllocate = l.bandwidth/request.B;													
			}	
			
			//decrease the nb of vm that should be allocated to next child tree
			remainingVms -=  vmToAllocate;
			
			System.out.println("			actual allocation "+ vmToAllocate+" remianing "+remainingVms);	
			
			//if we are not at the pm level we need to check if the child tree can accommodate the vmToAllocate
			if (s.rootNode.level != 0)
			{
				/**
				 * if childRemainingVms == 0 this means that we are checking if the next child of the main SubTree can accommodate the request
				 * So it should try to allocate all VMs that were not allocated by the previous child (totalRemaining)
				 */
				if (childRemainingVms == 0)
				{
					vmToAllocate = totalRemaining;					
				}
				
				//call the function again to check on the child sub tree
				returnValues = isBandwidthAvailableUpdated (s, request, vmToAllocate);
				childRemainingVms = returnValues[0];
			
				System.out.println(" here childRemainingVms "+childRemainingVms);
			}
			
			//here we know we allocated the minimum nb of VM to the child tree and the next loop is for a new one so we need to decrease the totalRemaining
			if (childRemainingVms == 0)
			{
				totalRemaining-= vmToAllocate;
				
				/*allocation[0] = s.rootNode.id;
				allocation[1] = vmToAllocate;
				physicalVMAllocation.add(allocation);*/
				
				/**
				 * @TODO
				 * Problem here is at the the total remaining will not include the last child tree if that one is not able to accommodate the initial request
				 * but no problem because this means that the whole main sub tree can not embed the request 
				 * however the problem is that at the last child iteration it is not calling the same child again to allocate the min number until having childRemainingVms =0
				 * this is needed specially when having a 3rd level tree
				 */
				System.out.println(" here totalRemaining "+totalRemaining);
			}
			
		}
		System.out.println(" return totalRemaining "+totalRemaining);
		returnValues = new int [2];
		returnValues [0] = remainingVms;
		returnValues [1] =  totalRemaining;
		return returnValues;
	}
	
	/**
	 * Find the smallest subtree that can fit the request in the tree network and 
	 * provide it with the needed number of VMs and bandwidth
	 * 
	 * @param request array [requestId, nbOfVMs, bandwidthPerVM]
	 * @param level the level of the subtree where we want to check for placement for the request 
	 * (should be set to 1 initially)
	 * 
	 * @return subtree of the best allocation
	 */
	public SubTree getBestPlacementForRequest ( Request request,  int level)
	{
		SubTree subTree = null;
		int i = 0;
		boolean bestSubTreePlacement = false;
		ArrayList <SubTree> subTreesWithAvailableSlots ;

		subTreesWithAvailableSlots = this.getSubtreesWithAvailableSlots (level,  request);
		
		//ascending sort the subtrees that can fit the request
		//subTreesWithAvailableSlots = this.treeNetwork.orderSubtreesByVMs(subTreesWithAvailableSlots);
		
		//ascending sort the subtrees that can fit the request based on the residual bandwidth on the link connecting the subtree to the rest of the network
		subTreesWithAvailableSlots = this.treeNetwork.orderSubtreesByUpperLinkResidualBandwidth(subTreesWithAvailableSlots);
		
		// getting the first subtree from the sub tree list with available VMS that can fit the request in term of bandwidth
		while ( i < subTreesWithAvailableSlots.size() && !bestSubTreePlacement )
		{
			subTree = subTreesWithAvailableSlots.get(i);
			
			
			if (this.isBandwidthAvailable(subTree, request))
			{				
				bestSubTreePlacement = true;
				return subTree;
			}
			
			i++;			
		}
		

		//if no subtrees that can fit the VM request at the specified level we search at a higher level
		if (!bestSubTreePlacement && level <= FatTreeNetwork.HEIGHT)
		{		
			level++;
			subTree = this.getBestPlacementForRequest ( request, level);
		}
		
		//this is to handle the case where there is a subtree with available slots but with not enough bandwidth
		if (!bestSubTreePlacement && level > FatTreeNetwork.HEIGHT)
		{
			return null;
		}
		
		return subTree;
		
	}
	
	
	/**
	 * This function calculate the revenue of the allocation 
	 * 
	 * @param requestsAllocation array of <pmId, VMAllocated for the request> that hold the
	 * 	 number of VM allocated to the specified pm for each request
	 * 
	 * @return int revenue which represents the number of allocated VMs
	 */
	public int calculateRevenue(ArrayList<ArrayList<int[]>> requestsAllocation)
	{
		ArrayList<int[]> singleRequestAllocation;
		int revenue = 0;
		
		for (int i=0;i<requestsAllocation.size();i++)
		{
			singleRequestAllocation = requestsAllocation.get(i);
			
			//this is to prevent any error when calling .size() if there was no allocation
			if(singleRequestAllocation == null)
			{
				continue;
			}
			
			for(int j=0; j<singleRequestAllocation.size();j++)
			{
				revenue+= singleRequestAllocation.get(j)[1];
			}
		}
		
		return revenue;
		
	}
	
		
	/**
	 * This function generates the number of requests as [id,VMs,Bandwidth] with
	 * VMs exponentially distributed around a mean and 
	 * Bandwidth per VM between a certain range
	 * 
	 * @param nbRequests the number of requests we want to generate
	 * @param mean the mean of exponential distribution of VMs
	 * @param bandwdithRange an array of bandwidth range [min bandwidth, max bandwidth] inclusive
	 * 
	 * @return array of generated requests
	 * @TODO exponential distribution 
	 */
	public ArrayList<int[]> generateRequests(int nbRequests, int mean, int[]bandwidthRange)
	{
		ArrayList <int[]> requests = new ArrayList<int []>();
		int[] request;
		Random rand = new Random();
		
		for (int i=0; i<nbRequests; i++)
		{			
			request = new int[3];
			request[0] = i;
			
			//exponential distribution of VMs around a mean of <mean>
			request[1] =  (int)(Math.log(1-rand.nextDouble())*(-mean));
								
			// nextInt is normally exclusive of the top value,
		    // so add 1 to make it inclusive
			request[2] = rand.nextInt((bandwidthRange[1] - bandwidthRange[0]) + 1) + bandwidthRange[0];
			
			requests.add (request);
			
		}
		
		return requests;
		
	}
	
	
	/**
	 * Calculate the total number of requested VMs by all the requests
	 * 
	 * @param requests
	 * 
	 * @return nb of Vms of all requests
	 */
	public int calculateRequestedVMs(ArrayList <Request> requests)
	{
		int totalVms = 0;
		
		for (int i=0; i<requests.size();i++)
		{
			totalVms+=requests.get(i).N;
		}
		
		return totalVms;
	}
	
	
	
	/**
	 * This function prints all the information passed in the array related to the request allocation
	 * It is designed to print the allocation for a single request
	 * 
	 * @param request to print
	 * @param requestsAllocation array list of physical machines 
	 * 	where VM are allocated for each request <pmId, nb of Vms allocates>
	 */
	public void printAllocationForRequest ( Request request, ArrayList <int []> requestAllocation)
	{
		int [] pmAllocation;
		
		
		//get the request and print it
		System.out.println ("Allocation for request "+request.id+" : VMs requested: "+ request.N+"     Bandwidth per VM: "+request.B);
		
		//get the request physical allocation and print it
		System.out.println("===================================================================================================");
		
		//get the allocation of the request
					
		if (requestAllocation == null || requestAllocation.size() == 0 )
		{
			System.out.println("Request rejected - NO allocation for primary Vms ");
			System.out.println();
			return;
		}
		
		for( int j = 0; j<requestAllocation.size(); j++)
		{
			//get each physical machine
			pmAllocation = requestAllocation.get(j);
			
			System.out.printf ( "Physical Machine ID: %5d   ;  Virtual Machines allocated for request : %5d", pmAllocation[0], pmAllocation[1]  );
			System.out.println();
		}
	}
	
	
	/**
	 * This function prints all the information passed in the array related to the requests allocation
	 * 
	 * @param requests list of all requests <request id, VMs, Bandwidth per VM>
	 * @param requestsAllocation array list indexed by request id of list of physical machines 
	 * 	where VM are allocated for each request <pmId, nb of Vms allocates>
	 */
	public void printRequestsAllocation ( ArrayList<Request> requests, ArrayList <ArrayList <int []>> requestsAllocation)
	{
		//physical machines allocation per request
		ArrayList <int []> requestAllocation;
		int rejectedRequests = 0;
		int acceptedRequests =0;
		int [] pmAllocation;
		
		//request
		Request request;
		
		//loop over the requests
		for (int i=0; i<requestsAllocation.size();i++)
		{
			//get the request and print it
			request = requests.get(i);
			System.out.println ("Allocation for request "+i+" : VMs requested: "+ request.N+"     Bandwidth per VM: "+request.B);
			
			//get the request physical allocation and print it
			System.out.println("===================================================================================================");
			
			//get the allocation of the request
			requestAllocation = requestsAllocation.get(i);
			
			if (requestAllocation == null || requestAllocation.size() == 0 )
			{
				rejectedRequests++;
				System.out.println("Request rejected - NO allocation for it ");
				System.out.println();
				continue;
			}
			
			for( int j = 0; j<requestAllocation.size(); j++)
			{
				//get each physical machine
				pmAllocation = requestAllocation.get(j);
				
				System.out.printf ( "Physical Machine ID: %5d   ;  Virtual Machines allocated for request : %5d", pmAllocation[0], pmAllocation[1]  );
				System.out.println();
			}
			
			
			System.out.println ();
		}
		
		acceptedRequests = requests.size() - rejectedRequests;
		System.out.println ("NB of rejected Requests ="+rejectedRequests);
		System.out.println ("NB of accepted Requests ="+acceptedRequests);
	}
	
	/**
	 * This function generates an array of 10 requests
	 * 
	 * @return array list of requests
	 */
	public ArrayList <Request> staticRequestsGeneration ()
	{
		ArrayList<Request> requests = new ArrayList<Request>();
		Request request;
		/*int[] vms = {4,1,6,2,1,2,2,3,2,1 };
		int[] bandwidth = {441,317,408,511,353,441,382,413,369,398};*/
		
		/*int[] vms = {40,10,12,20,10,42,32,34,22,16 };
		int[] bandwidth = {441,317,408,511,353,441,382,413,369,398};*/
		
		int[] vms = {8,10,9,7};
		int[] bandwidth = {103,492,381,260};


		//request to test with isBandwidthAvailableUpdated ()
		/*int[] vms = {10 };
		int[] bandwidth = {400};*/
		
		for (int i=0; i<vms.length; i++)
		{
			request = new Request (i,vms[i],bandwidth[i]);			
			
			requests.add(request);
		}
		
		
		return requests;
	}
	
	
	public static void main (String [] args) throws IloException
	{
		
		ArrayList<Request> requests;
		ArrayList <ArrayList <int []>> requestsAllocation= new ArrayList <ArrayList <int []>>();
		
		//int [] bandwidthRange = {300,600};
		int hoseRevenue = 0;
		double cplexSolution = 0;
		
		//defining the network with capacity in MB
		FatTreeNetwork treeNetwork = new FatTreeNetwork(12,4,3,2,2,1000,1000,1000);
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(4, 4, 1, 2, 2, 1000,2000, 5000);
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(12, 4, 2, 3, 2, 1000,1000, 1000);
		//build the network
		treeNetwork.buildTreeNetwork();
		
		//create a vmPlacement object
		VirtualMachinesPlacement vmPlacement = new VirtualMachinesPlacement(treeNetwork);
		
		//generate the requests
		//requests = vmPlacement.generateRequests(10,3,bandwidthRange );
		requests = vmPlacement.staticRequestsGeneration();
		
		//allocate requests to the smallest subTree based on hose model
		requestsAllocation = vmPlacement.hoseVMPlacementAlgorithm( requests);
		
		//getting cplex solution
		CplexModels cplexModel = new CplexModels(requests, treeNetwork);
		cplexSolution = cplexModel.model();
			
		//print the request placement information
		System.out.println ("\n\n ========================================== Hose Model Allocation ================================== \n\n ");
		vmPlacement.printRequestsAllocation (requests, requestsAllocation);
		
		//calculate the total reserved VMs
		hoseRevenue = vmPlacement.calculateRevenue(requestsAllocation);		
				
		System.out.println ("Total Virtual machines allocated based on Hose model allocation: "+hoseRevenue);
		
		
		System.out.println ("\n\n =============================== GENERAL RESULTS AND DEVIATION ============================== \n\n ");
		System.out.println("Total number of available VMs in the network    : "+treeNetwork.nbOfPhysicalMachines*treeNetwork.nbOfVMPerPhysicalMachine);
		System.out.println("Total number of requested VMs by all the tenants: "+vmPlacement.calculateRequestedVMs(requests));
		System.out.printf("CPLEX solution:      %.0f\n",cplexSolution);
		System.out.println("Hose model solution: "+hoseRevenue+" deviation: "+(cplexSolution-hoseRevenue)*100/cplexSolution+"%" );
	}

}
