/**
 * General information:
 * This class realizes 100% protection of the requests
 * It uses Oktopus to embed the primary Vms 
 * backupEmbeddingBaseline to embed the backup nodes
 * OR backupToVMMappingModelTest to embed the backup nodes
 * It also uses the BackupToVmMappingModelEnhanced to map backup Vms to primary and embed backup bandwidth
 * 
 * 
 * 
 * 
 * 
 */


import ilog.concert.IloException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import ExperimentalCalculations.NetworkStatus;
import ExperimentalCalculations.Poisson;
import HelperClasses.FileManipulation;
import Network.FatTreeNetwork;
import Network.Link;
import Network.PhysicalMachine;
import Network.Request;
import Network.SubTree;
import Network.Switch;
import Network.VirtualMachine;




public class VMsProtectionWithBandwidthGuarantee {
	
	FatTreeNetwork treeNetwork;
	ArrayList<Request> requests;
	
	
	/**
	 * Constructor
	 * @param treeNetwork
	 * @param requests
	 */
	VMsProtectionWithBandwidthGuarantee(FatTreeNetwork treeNetwork, ArrayList<Request> requests)
	{
		
		this.treeNetwork = treeNetwork;
		this.requests = requests;
	}
	
	
	/**
	 * This function acts as baseline to compare against the BackupToVmMappingSolution
	 * It tries a specified number of backup embedding plan and choose the one that is the less costly
	 * 
	 * @param requests list of requests to allocate
	 * @param enumerationNb number of backup embedding plans to try
	 * @throws IloException
	 * @throws IOException 
	 */
	
	public void backupEmbeddingBaseline ( int enumerationNb) throws IloException, IOException
	{
		FileManipulation mainFile  = new FileManipulation("PPDRExecutionTime");
		int k =0;
		Request request = null;	
		ArrayList <int []> physicalMachinesAllocation;
				
		//create a vmPlacement object
		VirtualMachinesPlacement vmPlacement = new VirtualMachinesPlacement(this.treeNetwork);

		//loop over the requests
		for (int j=0; j<this.requests.size(); j++)
		{
			//get the request 
			request = this.requests.get(j);
					
			 //if we are dealing with an arrival, we need to allocate
			 if (request.processType == Request.Type.ARRIVAL)
			 {mainFile.writeInFile("Request "+k+"-----\n"); k++;
				 System.out.println(" ---------------------------------------Allocate Request "+ request.id+"--------------------------------------");
				//allocate requests to the smallest subTree based on hose model
				physicalMachinesAllocation = vmPlacement.hoseVMPlacementAlgorithmSingleRequest(request);
				
				//check if request was not admitted
				if (physicalMachinesAllocation == null|| physicalMachinesAllocation.size() == 0)
				{
					//specify that the request is rejected
					request.admitted = false;
					request.rejectionReason.add(Request.RejectionReason.PRIMARY_EMBEDDING);
					
					//print request information
					vmPlacement.printAllocationForRequest(request, physicalMachinesAllocation);					
					continue;
				}			
				
				//print request information
				vmPlacement.printAllocationForRequest(request, physicalMachinesAllocation);
			
				//protect the request by embedding backups on random  servers + mapping backup to Vm+reserving backup bandwidth
				this.randomRequestProtection(request, enumerationNb);
							
			 }
			//release request; note that all the departure request has admitted = false (we only update arrival requests)
			 else if (request.processType == Request.Type.DEPARTURE )
			 {
				 System.out.println(" ---------------------------------------Release Request "+ request.id+"--------------------------------------");
				 //unallocate request from the tree
				 treeNetwork.releaseAllocatedRequest(request,null);
			 }
		}
					
	}
	
	
	/**
	 * This function protect the request (having its primary vms embedded) by trying enumerationNb of 
	 * backup embedding and choosing the embedding with the least cost
	 * This function will embed the backups and map them to primary Vm
	 * If backups can not be admitted it will release the whole request from the network
	 * 
	 * @param request request to protect
	 * @param enumerationNb number of backup plan to try
	 * @throws IloException
	 * @throws IOException 
	 */
	public boolean randomRequestProtection (Request request, int enumerationNb) throws IloException, IOException
	{	
		long startTime = System.currentTimeMillis();
		long endTime ,executionTime;
		long startEnumerationLoop, endEnumerationLoop, enumerationLoopExecutionTime;
		long startModelRun, endModelRun,modelRunExecutionTime;
		long startPlacement, endPlacement, placementExecutionTime;
		long startRequestAllocation, endRequestAllocation, requestAllocationExecutionTime;
		FileManipulation mainFile  = new FileManipulation("PPDRExecutionTime");
		mainFile.writeInFile("----Starting randomRequestProtection (PPDR)-----\n");
		
		Random rand = new Random();
		rand.setSeed(2);
		int randomServer = 0;
		Double objectiveValue = null;
		Double bestObjectiveValue = null;
		int vmsToAllocateOnPm = 0;
		PhysicalMachine pm =null;
		int backupNeeded = request.getBackupNeeded();
		int remainingBackups = backupNeeded;
		int testedpm = 0;
		ArrayList<Integer> usedPm = new ArrayList<Integer>();
		ArrayList<Integer> bestUsedPm = new ArrayList<Integer>();
		BackupToVmMappingModelEnhanced backupToVmMappingModel = null;	
		Double allocationValue = null;
		
		//create a subtree that has the level 3 switch as rootNode
		SubTree network = new SubTree (this.treeNetwork.getSwitchSetPerTreeLevel(3)[0]);
		this.treeNetwork.buildSubTree(network, network.rootNode);
	
		//check if the network has enough availableVms to admit the backup of the request
		if (network.getAvailableVms() < remainingBackups)
		{
			// if we got here then we tried all possible allocations enumerated, reject the request
			 request.admitted = false;
			 request.rejectionReason.add(Request.RejectionReason.BACKUP_EMBEDDING);
			 
			 //unallocate the whole request from the tree
			 treeNetwork.releaseAllocatedRequest(request,null);	
				endTime = System.currentTimeMillis();
				executionTime = endTime - startTime;
				mainFile.writeInFile("----End function (rejected Request):  execution time : " +executionTime+" -----\n\n\n");
			 return false;
		}
		
		startEnumerationLoop =  System.currentTimeMillis();
		mainFile.writeInFile("----Starting enumeration loop-----\n");
		//at this point there is enough available Vms in the network to allocate backups, we can enumerate multiple backup embedding
		for (int i = 0; i<enumerationNb; i++)
		{
			
			backupToVmMappingModel = null;		
			usedPm = null;
			usedPm = new ArrayList<Integer>();
			
			//reset the remaining backup since we are trying a new allocation
			remainingBackups = backupNeeded;
			
			/**
			 * Try embedding all backup nodes. 
			 * Because we are trying to embed on random servers, we want to prevent infinite loops
			 * since the same server may be selected randomly multiple times.
			 * So we stop the loop either when all backups are embedded or when we tried embedding on nbOfPhysicalMachines
			 */
			startPlacement = System.currentTimeMillis();
			mainFile.writeInFile("----Starting placement loop for enumeration "+ i +" -----");
			while (remainingBackups!=0 && testedpm <= this.treeNetwork.nbOfPhysicalMachines)
			{
				//choose random server to allocate backups, nextInt is normally exclusive of the top value,
				randomServer = rand.nextInt(this.treeNetwork.nbOfPhysicalMachines);
				pm = this.treeNetwork.physicalMachinesSet[randomServer];
				
				//allocate backup based on server available vms
				vmsToAllocateOnPm = pm.getAvailableVM();
				vmsToAllocateOnPm =	vmsToAllocateOnPm > remainingBackups ? remainingBackups : vmsToAllocateOnPm ;				
				
				if(vmsToAllocateOnPm!=0)
				{						
					pm.reserveVM(vmsToAllocateOnPm, request, VirtualMachine.Type.BACKUP);					
					remainingBackups -= vmsToAllocateOnPm;
					
					//add server where Vms are allocated to the usedPm list
					usedPm.add(pm.id);
				
				}
					
				testedpm++;
			}
			endPlacement = System.currentTimeMillis();
			placementExecutionTime =  endPlacement-startPlacement;
			mainFile.writeInFile(" ExecutionTime:" +placementExecutionTime +"-----\n");
			
			//Prevent running the model and having error if all the checked pm were not allocated any backups (no available vms)
			if (usedPm.size() == 0)
			{
				continue;
			}
			
			//get the cost of mapping backup to Vm based on the done allocation and reserve the backup bandwidth
			backupToVmMappingModel = new BackupToVmMappingModelEnhanced(request, this.treeNetwork);	
			
			startModelRun = System.currentTimeMillis();
			mainFile.writeInFile("----Starting model run for enumeration "+ i +" -----");
			objectiveValue =  backupToVmMappingModel.modelFormulation(null,null,null,true);
			endModelRun = System.currentTimeMillis();
			modelRunExecutionTime = endModelRun-startModelRun;
			mainFile.writeInFile(" ExecutionTime:" +modelRunExecutionTime +"-----\n");
			
			//release the already reserved backup with their bandwidth to try another allocation 
			 this.treeNetwork.releaseAllocatedRequest(request,VirtualMachine.Type.BACKUP);
			 
			 //get the value of the best embedding
			 if (bestObjectiveValue == null && objectiveValue>=0)
			 {
				 
				 bestObjectiveValue = objectiveValue;
				 bestUsedPm = new ArrayList<Integer>();
				 bestUsedPm.addAll(usedPm);
			
			 }
			 else if (bestObjectiveValue != null  && objectiveValue >=0 && objectiveValue < bestObjectiveValue )
			 {
				 bestObjectiveValue = objectiveValue;
				 bestUsedPm = null;
				 bestUsedPm = new ArrayList<Integer>();
				 bestUsedPm.addAll(usedPm);
				
			 }
										
		}
		endEnumerationLoop =System.currentTimeMillis();
		enumerationLoopExecutionTime = endEnumerationLoop - startEnumerationLoop;
		mainFile.writeInFile("----End enumeration loop:  execution time : " +enumerationLoopExecutionTime+" -----\n");
		
		//choose the best allocation for the request
		if (bestObjectiveValue!=null && bestObjectiveValue >=0)
		{ 
			startRequestAllocation = System.currentTimeMillis();
			//this condition is just a double check
			allocationValue = this.allocateRequest(request,bestUsedPm );
			endRequestAllocation = System.currentTimeMillis();
			requestAllocationExecutionTime = endRequestAllocation -  startRequestAllocation;
			mainFile.writeInFile("----FinalRequestAllocation:  execution time : " +requestAllocationExecutionTime+" -----\n");
			
			if (allocationValue!=null && allocationValue>=0)
			{
				request.admitted = true;
				request.updateReservedBandwidth(this.treeNetwork);
				request.updateReservedBackupVms();
				
				endTime = System.currentTimeMillis();
				executionTime = endTime - startTime;
				mainFile.writeInFile("----End function:  execution time : " +executionTime+" -----\n\n\n");
				return true;
			}
			endTime = System.currentTimeMillis();
			executionTime = endTime - startTime;
			mainFile.writeInFile("----End function:  execution time : " +executionTime+" -----\n\n\n");
			return false;
		}
		else
		{	
			startRequestAllocation = System.currentTimeMillis();
			 // if we got here then we tried all possible allocations enumerated, reject the request
			 request.admitted = false;
			 request.rejectionReason.add(Request.RejectionReason.BACKUP_MAPPING_BANDWIDTH);	 
			 
			 //unallocate the whole request from the tree
			 treeNetwork.releaseAllocatedRequest(request,null);	
			 endRequestAllocation = System.currentTimeMillis();
			requestAllocationExecutionTime = endRequestAllocation -  startRequestAllocation;
			mainFile.writeInFile("----FinalRequestAllocation:  execution time : " +requestAllocationExecutionTime+" -----\n");
				
			endTime = System.currentTimeMillis();
			executionTime = endTime - startTime;
			mainFile.writeInFile("----End function (rejected Request):  execution time : " +executionTime+" -----\n\n\n");
			 return false;
		}
	

	}
	
	
	/**
	 * This function allocates the backups (nodes +mapping+backup bandwidth) for the request in the usedServers list
	 * When calling this function we are sure it can be admitted to the usedServers list
	 * 
	 * @param request request to allocate
	 * @param usedServers list of servers to allocate backups to
	 * @throws IloException 
	 * 
	 * @return objectiveValue of the backuptoVmMapping
	 */
	public Double allocateRequest (Request request, ArrayList<Integer> usedServers) throws IloException
	{
		PhysicalMachine pm = null;
		int vmsToAllocateOnPm = 0;
		Double objectiveValue = null;
		BackupToVmMappingModelEnhanced backupToVmMappingModel = new BackupToVmMappingModelEnhanced(request, this.treeNetwork);;	
		int remainingBackups = request.getBackupNeeded();
		
		
		for (int i = 0; i<usedServers.size(); i++)
		{
			
			//choose random server to allocate backups, nextInt is normally exclusive of the top value,
			pm = this.treeNetwork.physicalMachinesSet[usedServers.get(i)];
			
			//allocate backup based on server available vms
			vmsToAllocateOnPm = pm.getAvailableVM();
			vmsToAllocateOnPm =	vmsToAllocateOnPm > remainingBackups ? remainingBackups : vmsToAllocateOnPm ;				
			
			if(vmsToAllocateOnPm!=0)
			{				
				pm.reserveVM(vmsToAllocateOnPm, request, VirtualMachine.Type.BACKUP);					
				remainingBackups -= vmsToAllocateOnPm;
		
			}
		}
		
		if (remainingBackups == 0)
		{
			objectiveValue =  backupToVmMappingModel.modelFormulation(null,null,null,true);
		}
					
		return objectiveValue;
	}
	
	
	
	/**
	 * This function allocates backup for the request without checking on bandwidth
	 * It starts allocation by collocation if possible in the sub tree where the request 
	 * is embedded. If number of available vms was not enough it tries allocating in the parent tree
	 * 
	 * This function returns the first subtree where the backup allocation was possible and
	 * null if no backup allocation is possible in the whole tree network
	 * 
	 * @param request request to add backup for
	 * @param subtree subtree where to try to embed the backups. 
	 * This is initially set to request.subtree when calling the function
	 * 
	 * @return subtree where the backup allocation was possible
	 */
	public SubTree addBackupForRequest (Request request, SubTree subtree )
	{
		int backupNeeded = 0;
		int backupToAllocate = 0;
		int minNbOfHostedVms = request.subtree.getMinNbOfHostedVMs(request);	
		int hostedBackupOnNonHostingServers = 0;
		int hostedBackups = 0;
		
		SubTree s = null;
		SubTree childSubTree = null;
		ArrayList <SubTree> childTrees = null;
		
		
		//there is no parent tree -- reject request -- backup can not be allocated
		if (subtree == null)
		{
			return null;
		}
		
		//get the needed number of backups
		backupNeeded = request.getBackupNeeded();
				
		//get minimum nb of primary VMs hosted on the same server for the request		
		backupToAllocate = backupNeeded;
		
		/**
		 * check if request sub tree has enough empty slots to host the backups 
		 * and has servers not hosting any primary VMs.
		 * If all servers in the subTree were hosting primary VMs than we need to use backups > backupNeeded
		 * to host backup in this subTree because we will have to do a lot of collocation on servers hosting primary Vms
		 */
	
		if (subtree.getAvailableVms() < backupNeeded || subtree.getNonHostingServers(request).isEmpty())
		{
			//Try to allocate backup in the parent subTree
			s = subtree.getParentTree();
			return this.addBackupForRequest(request, s);
		}
		
		
		/**
		 * get and order child trees in descending order based on the number of hosted VMs for the specified request
		 * we are interested in hosting backups in the child tree with the max number of VMs 
		 * This will maintain the same number of VMs in the child tree when a server fails and by that 
		 * it will allow sharing the biggest amount of bandwidth
		 */
		//get only child trees without considering pm as child +consider subtree at TOR level (no child)
		if (subtree.rootNode.level >1)
		{
			childTrees = subtree.getChildTrees();		
			this.treeNetwork.orderSubtreesByHostedVMs(childTrees, request);
		}
		else
		{	
			//if the main tree is at the TOR level we don't want the physical server as child which is done by the subtree.getChildTrees()
			childTrees = new ArrayList<SubTree>();
			childTrees.add(subtree);
		}
		
		for (int i =0; i<childTrees.size(); i++)
		{	
			
			childSubTree = childTrees.get(i);
			
			/**
			 * Collocation on a child tree is allowed if:
			 * 1-Child tree has at least one hosting server with min number of primary Vms+available slots on that server
			 * 2-Parent tree (subtree) has enough available slots on non hosting servers to embed min number of primary vms hosted
			 * OR has already backup hosted on non hosting servers >= min number of primary vms hosted
			 * 3- request is embedded on more than one server
			 */
			if (subtree.canCollocateBackups (childSubTree, request, hostedBackupOnNonHostingServers ))
			{	
				/**
				 *	If no backup hosted on non hosting servers yet, we need to make sure to keep 
				 *  minNbOfHostedVms not hosted on servers embedding primary Vms to embed them on non hosting servers
				 */
				if (hostedBackupOnNonHostingServers < minNbOfHostedVms)
				{
					backupToAllocate = backupToAllocate - (minNbOfHostedVms-hostedBackupOnNonHostingServers);
				}
				
						
				/**
				 * Since collocation is possible in the childSubTree, we loop over hosting servers
				 * and try to collocate the specified number of backupToAllocate
				 * Finally we update the backupToAllocate by substracting the number of allocated backups
				 */
				backupToAllocate-= hostBackups (childSubTree, request,backupToAllocate, minNbOfHostedVms, true);
								
				/**
				 * Since we collocated the max number of possible VMs we need to add the minNbOfHostedVms 
				 * to ensure they are hosted on a server with no primary Vms for the request if they are
				 * not yet hosted
				 */			
				if (hostedBackupOnNonHostingServers < minNbOfHostedVms)
				{
					backupToAllocate = backupToAllocate + (minNbOfHostedVms-hostedBackupOnNonHostingServers);
				}
			}
			
			/**
			 * After trying to collocate the backups, we try to embed the remaining backup on non hosting servers
			 * starting with the servers having the maximum number of available Vms
			 * Finally we update the backupToAllocate by substracting the number of allocated backups
			 */
			hostedBackups = hostBackups (childSubTree, request, backupToAllocate, minNbOfHostedVms, false);
	
			backupToAllocate-= hostedBackups;
			hostedBackupOnNonHostingServers += hostedBackups;
			hostedBackups = 0;
		
			//break if no remaining Vms to allocate for the next child tree
			if (backupToAllocate == 0)
			{
				return subtree;
				
			}
			
			
		}
		
		//sub tree can not accommodate the request because some of the available Vms were in hosting servers
		if (backupToAllocate != 0)
		{
			//release allocated backups 
			this.treeNetwork.releaseAllocatedRequest(request, VirtualMachine.Type.BACKUP);
			
			//try allocating in parent sub tree
			s = subtree.getParentTree();		
			return this.addBackupForRequest(request, s);
			
		}	
		
		return null;
		
	}
	
	
	
	/**
	 * This function allocates backup for the request without checking on bandwidth
	 * It starts allocation by collocation if possible in the sub tree where the request 
	 * is embedded. If number of available vms was not enough it tries allocating in the parent tree
	 * always by trying to collocate first.
	 * 
	 * If all the network was checked and no allocation was possible with collocation, we try allocating 
	 * without collocation
	 * 
	 * This function returns the first subtree where the backup allocation was possible and
	 * null if no backup allocation is possible in the whole tree network
	 * 
	 * @param request request to add backup for
	 * @param subtree subtree where to try to embed the backups. 
	 * This is initially set to request.subtree when calling the function
	 * 
	 * @return subtree where the backup allocation was possible
	 */
	public SubTree addBackupForRequestEnhanced (Request request, SubTree subtree, SubTree mainSubtree, boolean collocate )
	{
		int backupNeeded = 0;
		int backupToAllocate = 0;
		int minNbOfHostedVms = request.subtree.getMinNbOfHostedVMs(request);	
		int hostedBackupOnNonHostingServers = 0;
		int hostedBackups = 0;
		
		SubTree s = null;
		
		//there is no parent tree -- reject request -- backup can not be allocated
		if (subtree == null)
		{		
			//at this point, no solution was found with collocation in the whole network, try with no collocation by starting with the main network (request sub tree)
			if (mainSubtree.canCollocateBackups (mainSubtree, request, hostedBackupOnNonHostingServers ) && collocate)
			{	
				return this.addBackupForRequestEnhanced(request, mainSubtree,mainSubtree, false);
			}
			else
			{			
				return null;
			}
		}
		
		//get the needed number of backups
		backupNeeded = request.getBackupNeeded();
				
		//get minimum nb of primary VMs hosted on the same server for the request		
		backupToAllocate = backupNeeded;
		
		/**
		 * check if request sub tree has enough empty slots to host the backups 
		 * and has servers not hosting any primary VMs.
		 * If all servers in the subTree were hosting primary VMs than we need to use backups > backupNeeded
		 * to host backup in this subTree because we will have to do a lot of collocation on servers hosting primary Vms
		 */
	
		if (subtree.getAvailableVms() < backupNeeded || subtree.getNonHostingServers(request).isEmpty())
		{
			//Try to allocate backup in the parent subTree
			s = subtree.getParentTree();
			return this.addBackupForRequestEnhanced(request, s,mainSubtree,collocate);
		}
		
	
					
		/**
		 * Collocation on a child tree is allowed if:
		 * 1-Child tree has at least one hosting server with min number of primary Vms+available slots on that server
		 * 2-Parent tree (subtree) has enough available slots on non hosting servers to embed min number of primary vms hosted
		 * OR has already backup hosted on non hosting servers >= min number of primary vms hosted
		 * 3- request is embedded on more than one server
		 */
	
		if (subtree.canCollocateBackups (subtree, request, hostedBackupOnNonHostingServers ) && collocate)
		{	
			/**
			 *	If no backup hosted on non hosting servers yet, we need to make sure to keep 
			 *  minNbOfHostedVms not hosted on servers embedding primary Vms to embed them on non hosting servers
			 */
			if (hostedBackupOnNonHostingServers < minNbOfHostedVms)
			{
				backupToAllocate = backupToAllocate - (minNbOfHostedVms-hostedBackupOnNonHostingServers);
			}
			
					
			/**
			 * Since collocation is possible in the childSubTree, we loop over hosting servers
			 * and try to collocate the specified number of backupToAllocate
			 * Finally we update the backupToAllocate by substracting the number of allocated backups
			 */
			backupToAllocate-= hostBackups (subtree, request,backupToAllocate, minNbOfHostedVms, true);
							
			/**
			 * Since we collocated the max number of possible VMs we need to add the minNbOfHostedVms 
			 * to ensure they are hosted on a server with no primary Vms for the request if they are
			 * not yet hosted
			 */			
			if (hostedBackupOnNonHostingServers < minNbOfHostedVms)
			{
				backupToAllocate = backupToAllocate + (minNbOfHostedVms-hostedBackupOnNonHostingServers);
			}
		}
	
	
		
		/**
		 * After trying to collocate the backups, we try to embed the remaining backup on non hosting servers
		 * starting with the servers having the maximum number of available Vms
		 * Finally we update the backupToAllocate by substracting the number of allocated backups
		 */
		hostedBackups = hostBackups (subtree, request, backupToAllocate, minNbOfHostedVms, false);

		backupToAllocate-= hostedBackups;
		hostedBackupOnNonHostingServers += hostedBackups;
		hostedBackups = 0;
	
		//break if no remaining Vms to allocate for the next child tree
		if (backupToAllocate == 0)
		{
			return subtree;
			
		}
			
		
		//sub tree can not accommodate the request because some of the available Vms were in hosting servers
		if (backupToAllocate != 0)
		{
			//release allocated backups 
			this.treeNetwork.releaseAllocatedRequest(request, VirtualMachine.Type.BACKUP);
			
			//try allocating in parent sub tree
			s = subtree.getParentTree();		
			return this.addBackupForRequestEnhanced(request, s,mainSubtree, collocate);
			
		}	
		
		return null;
		
	}
	
	
	
	/**
	 * This function ensure hosting the needed number of backups and reserve it for the specified request
	 * It does not reserve the bandwidth for the backups
	 * If we are not collocating, it chooses a random server non hosting server in the mentionned subtree
	 * to host the backup on 
	 * 
	 * @param subtree to reserve the backup in 
	 * @param request request to reserve for
	 * @param backupToAllocate number of backups to reserve
	 * @param collocate specify if collocation should be considered when allocating backups
	 * 
	 * @return number of hosted backups
	 */
	public int hostBackups (SubTree subtree, Request request, int backupToAllocate, int minNbOfHostedVms, boolean collocate)
	{
		int vmsToAllocateOnPm = 0;
		int hostedBackups = 0;
		PhysicalMachine pm = null;	
		ArrayList <PhysicalMachine> servers = null;
		Random rand = new Random();
		int j=0;
		int testedpm =0;
		
		// if no backup to allocate return 0
		if (backupToAllocate == 0)
		{
			return hostedBackups;
		}
		
		if (collocate)
		{
			//get servers hosting the primary VMS
			servers = subtree.getHostingServers(request);
		}
		else
		{
			//get servers not hosting any primary Vms
			servers = subtree.getNonHostingServers ( request);
		
			//order servers in descending order of their available VMs because we want to collocate the biggest number of backup to reduce bandwidth
			//servers = subtree.orderMachinesByAvailableVms (servers);
		}
	
		while (backupToAllocate!=0 && testedpm<servers.size())
		{
			testedpm++;
			
			if(!collocate)
			{
				//choose random server to allocate backups, nextInt is normally exclusive of the top value,
				j = rand.nextInt(servers.size());				
			}
		
			pm = servers.get(j);
		
			//if we are collocating we only need to host on servers with min nb of primary VMs
			if (collocate && pm.getHostedVms(request) != minNbOfHostedVms)
			{
				j++;
				continue;
			}
						
			//get the number of backup that can be accommodated by the request 
			//@TODO enhancement develop getVmsToAllocate based on available bandwidth in pm to tor link
			//vmsToAllocateOnPm = pm.getVmsToAllocate ();
			vmsToAllocateOnPm = pm.getAvailableVM();
			
			vmsToAllocateOnPm =	vmsToAllocateOnPm > backupToAllocate ? backupToAllocate : vmsToAllocateOnPm ;
			
			//collocate backups on server
			pm.reserveVM(vmsToAllocateOnPm, request, VirtualMachine.Type.BACKUP);
			backupToAllocate -= vmsToAllocateOnPm;
			hostedBackups+=vmsToAllocateOnPm;
			
			//reset vmsToAllocateOnPm and pm
			vmsToAllocateOnPm = 0;
			pm = null;
			
			if (collocate)
			{
				j++;
			}
			
		
		}
		
		
		//free memory
		pm = null;
		servers = null;
		
		return hostedBackups;
	}
	
	
	/**
	 * Recursive function that protects the request by embedding backups 
	 * and mapping Primary to backup +allocating backup bandwidth with sharing
	 * 
	 * @param request request to protect
	 * @param subTree subtree to embed the backups in. Set to request.subtree initially
	 * 
	 * @return boolean true is request was protected
	 * @throws IloException 
	 * @throws IOException 
	 */
	public boolean protectRequest (Request request, SubTree subTree, boolean collocate) throws IloException, IOException
	{
		long startTime = System.currentTimeMillis();
		long endTime, executionTime;
		long startModel, endModel, modelExecutionTime;
		long startPlacement, endPlacement, placementExecutionTime;
		FileManipulation mainFile  = new FileManipulation("SVMPBGExecutionTime");
		mainFile.writeInFile("-----Starting protect request (SVMP-BG)---\n");
		SubTree s = null;
		//BackupToVMMappingModel backupToVmMappingModel;
		BackupToVmMappingModelEnhanced backupToVmMappingModel;
		double objectiveValue = 0;
			
		//add backups for the protected requests
		//s = this.addBackupForRequest(request, subTree);	
		startPlacement = System.currentTimeMillis();
		s = this.addBackupForRequestEnhanced(request, subTree,subTree, collocate);	
		endPlacement = System.currentTimeMillis();
		placementExecutionTime = endPlacement - startPlacement;
		mainFile.writeInFile("-----Starting Placement -- execution time "+placementExecutionTime+"--\n");
		//at this point the main network can not admit the backups needed
		if (s == null)
		{	
			// if no backup assigned reject request
			request.admitted = false;
			request.rejectionReason.add (Request.RejectionReason.BACKUP_EMBEDDING);
			
			 //unallocate the whole request from the tree
			 treeNetwork.releaseAllocatedRequest(request,null);	
			 endTime = System.currentTimeMillis();
			 executionTime = startTime - endTime;
			 mainFile.writeInFile("End function (request rejected - no placement)- exectionTime "+executionTime+"--\n\n");
			return false;
		}
		
		/**
		 * initial allocation for the backups is available we try mapping backup to primary
		 * and check on bandwidth by running the model that will directly update the network with the bandwidth
		 * 
		 */
		
		//backupToVmMappingModel = new BackupToVMMappingModel(request, treeNetwork);
		backupToVmMappingModel = new BackupToVmMappingModelEnhanced(request, treeNetwork);
		startModel = System.currentTimeMillis();
		objectiveValue =  backupToVmMappingModel.modelFormulation(null,null,null,true);
		endModel = System.currentTimeMillis();
		modelExecutionTime = endModel - startModel;
		mainFile.writeInFile("-----Starting Model --- ExectionTime "+modelExecutionTime+"--\n");
		 //backup and bandwidth allocation and mapping is accurate
		 if (objectiveValue >= 0)
		 { 
			request.admitted = true;
			request.updateReservedBandwidth(this.treeNetwork);
			request.updateReservedBackupVms();
			 endTime = System.currentTimeMillis();
			 executionTime = startTime - endTime;
			 mainFile.writeInFile("-----End function- exectionTime "+executionTime+"--\n\n");
			return true;
		 }
		
		
		 //At this point the bandwidth is not available based on the backup embedding, try another embedding
		 if (s.rootNode.level < FatTreeNetwork.HEIGHT)
		 { 
			 //release the already reserved backup to try another allocation since the bandwidth 
			 //for the existing allocation is not accurate
			 this.treeNetwork.releaseAllocatedRequest(request,VirtualMachine.Type.BACKUP);
			 
			 /**
			  * try to allocate in parent tree (more non hosting servers, allocation may change based on server ordering)
			  * parent tree is the parent of the tree where backup Vms were hosted.
			  * No need to take the parent tree of the "subtree" because we will be looping on tested subtrees where vms can not be accommodated
			  */
			 
			 return this.protectRequest(request, s.getParentTree(), collocate); 
		 }
		
		 /**
		  * if no backup embedding was possible with collocation  in the whole network, try protecting without collocation
		  * by starting with the request sub tree
		  */
		 if (collocate)
		 {
			//release the already reserved backup to try another allocation since the bandwidth 
			 //for the existing allocation is not accurate
			 this.treeNetwork.releaseAllocatedRequest(request,VirtualMachine.Type.BACKUP);
			 
			 return this.protectRequest(request, request.subtree, false); 
		 }
		 
		 // if we got here then we tried all possible allocation till the whole network with collocation and no collocation, reject the request
		 request.admitted = false;
		 request.rejectionReason.add(Request.RejectionReason.BACKUP_MAPPING_BANDWIDTH);	 
		 
		 //unallocate the whole request from the tree
		 treeNetwork.releaseAllocatedRequest(request,null);	
		 endTime = System.currentTimeMillis();
		 executionTime = startTime - endTime;
		 mainFile.writeInFile("-----End function (rejected-no bw)- exectionTime "+executionTime+"--\n\n");
		 return false;
	}
	
	
	/**
	 * Generates the specified number of requests and set the requests attribute
	 * to the generated requests
	 * 
	 * @param nbRequests nb if requests to generate
	 * @param minVms minimum nb of VMs in each request
	 * @param maxVms maximum nb of VMs in each request
	 * @param minBw minimum bandwidth for each request
	 * @param maxBw maximum bandwidth for each request
	 * 
	 * @return an arrayList of the generated random vms and random bw
	 */
	public ArrayList<int[]> generateRequests (int nbRequests, int minVms , int maxVms,int minBw,int maxBw )
	{
		Request request;
		Random rand = new Random();
		int randomVms;
		int randomBw;
		
		ArrayList<int[]> vmsBw = new ArrayList<int[]>();
		int[] vms = new int[nbRequests];
		int[] bw = new int[nbRequests];
		
		
		for (int i =0; i<nbRequests; i++)
		{
			// nextInt is normally exclusive of the top value,
		    // so add 1 to make it inclusive
		    randomVms = rand.nextInt((maxVms - minVms) + 1) + minVms;
		    vms[i] = randomVms ;
		 		   
		    randomBw = rand.nextInt((maxBw - minBw) + 1) + minBw;
		    bw[i] = randomBw; 
		 			
		    request = new Request (i,randomVms,randomBw);
			this.requests.add (request);
		}
		
		vmsBw.add(vms);
		vmsBw.add(bw);
				
		return vmsBw;
	}
	
	
	/**
	 *  Sets the arrival and departure time of the requests based on the specified
	 *  poisson distribution
	 *  
	 * @param poissonDistribution poisson distribution
	 * 
	 * @return 
	 */
	public void poissonDistribution (Poisson poissonDistribution)
	{
		Request request;
		
		for (int i = 0; i<this.requests.size(); i++)
		{
			request =  this.requests.get(i);
			request.arrivalTime = poissonDistribution.arrivals[i];
			request.departureTime = poissonDistribution.departures[i];
			
		}
		
	}
	
	
	
	/**
	 * This function sort the requests based on their arrival and departure times
	 *  
	 *  if (r1.arrival<r2.arrival && r1.departure >r2.departure && r2.departure>r1.arrival) the returned array will be
	 *   <arrival r1, arrival r2, departure r2, departure r1>
	 *   
	 * @param requests
	 * @return
	 */
	public ArrayList<Request> sortRequests ()
	{
		ArrayList<Request> sortedRequests = new ArrayList<Request>();
		Request request;
	
		
		for (int i=0; i<this.requests.size(); i++)
		{
			request = new Request();
			request = this.requests.get(i).clone();
			request.processType = Request.Type.ARRIVAL;
			sortedRequests.add (request);			
		}
		
		for (int j=0; j<this.requests.size(); j++)
		{
			request = this.requests.get(j).clone();
			request.processType = Request.Type.DEPARTURE;
			sortedRequests.add (request);	
		}
		
		
		for (int i=1; i<sortedRequests.size(); i++)
		{
						
			for (int j=i; j>0; j--)
			{
				
				if (sortedRequests.get(j).processType == Request.Type.ARRIVAL && sortedRequests.get(j-1).processType == Request.Type.ARRIVAL 
						&& sortedRequests.get(j).arrivalTime < sortedRequests.get(j-1).arrivalTime)
				{
					request = sortedRequests.get(j);
					sortedRequests.remove(j);
					sortedRequests.add(j,sortedRequests.get(j-1));
					sortedRequests.remove(j-1);
					sortedRequests.add(j-1,request);		
					
				}			
				else if (sortedRequests.get(j).processType == Request.Type.ARRIVAL && sortedRequests.get(j-1).processType == Request.Type.DEPARTURE 
						&& sortedRequests.get(j).arrivalTime < sortedRequests.get(j-1).departureTime)
				{	
					request = sortedRequests.get(j);
					sortedRequests.remove(j);
					sortedRequests.add(j,sortedRequests.get(j-1));
					sortedRequests.remove(j-1);
					sortedRequests.add(j-1,request);			
					
				}				
				else if ( sortedRequests.get(j).processType == Request.Type.DEPARTURE && sortedRequests.get(j-1).processType == Request.Type.ARRIVAL 
							&& sortedRequests.get(j).departureTime < sortedRequests.get(j-1).arrivalTime)
				{
					request = sortedRequests.get(j);
					sortedRequests.remove(j);
					sortedRequests.add(j,sortedRequests.get(j-1));
					sortedRequests.remove(j-1);
					sortedRequests.add(j-1,request);	
				}				
				else if ( sortedRequests.get(j).processType == Request.Type.DEPARTURE && sortedRequests.get(j-1).processType == Request.Type.DEPARTURE 
						&& sortedRequests.get(j).departureTime < sortedRequests.get(j-1).departureTime)
				{
					request = sortedRequests.get(j);
					sortedRequests.remove(j);
					sortedRequests.add(j,sortedRequests.get(j-1));
					sortedRequests.remove(j-1);
					sortedRequests.add(j-1,request);	
				}
				
			}				
			
		}
	

		return sortedRequests;
		
	}
	
	
	
	
	/**
	 * 
	 */
	public  void backupToVmMappingSolution ()
	{
		Request r1,r2,r3;
		SubTree allocationTree = null;
		PhysicalMachine pm = null;
		Link l = null;
		Switch [] subtreesRoots = treeNetwork.getSwitchSetPerTreeLevel(2);
		SubTree s = new SubTree(subtreesRoots[0]);	
		this.treeNetwork.buildSubTree(s, subtreesRoots[0]);
		
		
		r1 = new Request(0,4,1);
		r1.subtree = s;
		pm = this.treeNetwork.physicalMachinesSet[0];		
		pm.reserveVM(3, r1, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);				
		l.reserveBandwidth(1, r1.id, Link.BandwidthType.PRIMARY);
		
		//reserve TOr to agg bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(1, r1.id, Link.BandwidthType.PRIMARY);
		
		pm = this.treeNetwork.physicalMachinesSet[4];		
		pm.reserveVM(1, r1,  VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(1, r1.id, Link.BandwidthType.PRIMARY);
		
		//reserve TOr to agg bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(1, r1.id, Link.BandwidthType.PRIMARY);
		
		
		subtreesRoots = treeNetwork.getSwitchSetPerTreeLevel(1);
		s = new SubTree(subtreesRoots[0]);	
		this.treeNetwork.buildSubTree(s, subtreesRoots[0]);
		r2 = new Request(1,9,1);
		r2.subtree = s;
			
		pm = this.treeNetwork.physicalMachinesSet[1];
		pm.reserveVM(4, r2, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(4, r2.id, Link.BandwidthType.PRIMARY);
		
		pm = this.treeNetwork.physicalMachinesSet[2];
		pm.reserveVM(3, r2, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(3, r2.id, Link.BandwidthType.PRIMARY);	
		
		pm = this.treeNetwork.physicalMachinesSet[3];
		pm.reserveVM(2, r2, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(2, r2.id, Link.BandwidthType.PRIMARY);
		
		
		/*subtreesRoots = treeNetwork.getSwitchSetPerTreeLevel(1);
		s = new SubTree(subtreesRoots[1]);	
		this.treeNetwork.buildSubTree(s, subtreesRoots[1]);
		r3 = new Request(1,15,1);
		r3.subtree = s;
	
		
		pm = this.treeNetwork.physicalMachinesSet[4];
		pm.reserveVM(3, r3, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(3, r3.id, Link.BandwidthType.PRIMARY);
		
		pm = this.treeNetwork.physicalMachinesSet[5];
		pm.reserveVM(4, r3, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(4, r3.id, Link.BandwidthType.PRIMARY);
		
		pm = this.treeNetwork.physicalMachinesSet[6];
		pm.reserveVM(4, r3, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(4, r3.id, Link.BandwidthType.PRIMARY);
		
		pm = this.treeNetwork.physicalMachinesSet[7];
		pm.reserveVM(4, r3, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(4, r3.id, Link.BandwidthType.PRIMARY);*/
		
		System.out.println("Protecting request 1");
		//allocationTree = this.addBackupForRequest(r1, r1.subtree);
		System.out.println("Protecting request 1 result "+allocationTree.rootNode.level+" id "+allocationTree.rootNode.id);
		System.out.println("\n\n Protecting request 2");
		allocationTree = this.addBackupForRequest(r2, r2.subtree);
		System.out.println("Protecting request 2 result " +allocationTree.rootNode.level+" id "+allocationTree.rootNode.id);
		
	}
	
	
	/**
	 * This function is used to be called from the main
	 * It allows testing the VMProtectionModel by applying a hose allocation for primary Vms
	 * The model ensure backup placement, mapping and update of the network
	 * This function generates the requests and the distribution
	 * 
	 * @throws IloException
	 * @throws IOException 
	 */
	public void VMProtectionModelTestIndividual() throws IloException, IOException
	{
		ArrayList<Request> sortedRequests = new ArrayList<Request>();
		Request request;
		VMProtectionModel vmProtectionModel;
		ArrayList <ArrayList <int []>> requestsAllocation= new ArrayList <ArrayList <int []>>();
		ArrayList <int []> physicalMachinesAllocation;
		double alpha =0.5; 
		int[]pmAllocation;
		//this specifies the objective value specified by the model; -1 if no backups were admitted for the request
		double objectiveValue = 0;
		String fileName = "SimulationSetupResults.txt";		
		FileManipulation fileManipulation = new FileManipulation(fileName);
		//defining the network with capacity in MB
	//	FatTreeNetwork treeNetwork = new FatTreeNetwork(24, 10, 4, 2, 3, 1000,10000, 50000);//network on which we run the model for days
		FatTreeNetwork treeNetwork = new FatTreeNetwork(12,4,3,2,2,500,1000,1000);
		
		//build the network
		treeNetwork.buildTreeNetwork();
				
		//create a vmPlacement object
		VirtualMachinesPlacement vmPlacement = new VirtualMachinesPlacement(treeNetwork);
		
		//createcmsProtection object
		VMsProtectionWithBandwidthGuarantee  vmsProtection = new VMsProtectionWithBandwidthGuarantee(treeNetwork, sortedRequests);		
		
		//generate the requests	
		vmsProtection.generateRequests(15, 5,10,100,500);		
		//vmsProtection.requests = vmPlacement.staticRequestsGeneration();
		
		// Generate Poisson Arrival/Departure		
		Poisson poissonDistribution = new Poisson(5,1,vmsProtection.requests.size());
		
		//update the arrival and departure time of the requests
		vmsProtection.poissonDistribution(poissonDistribution);
		
		//sort the requests using the poisson distribution ascendley
		sortedRequests = vmsProtection.sortRequests();
		
		/**
		 * loop over arrivals and departures
		 */
		 for(int i=0;i<sortedRequests.size();i++)
		 {
			 request = sortedRequests.get(i);
			
			 //if we are dealing with an arrival, we need to allocate
			 if (request.processType == Request.Type.ARRIVAL)
			 {
							
				//allocate requests to the smallest subTree based on hose model
				physicalMachinesAllocation = vmPlacement.hoseVMPlacementAlgorithmSingleRequest(request);
				
				//check if request was not admitted
				if (physicalMachinesAllocation == null)
				{
					//add null if request rejected
					requestsAllocation.add(physicalMachinesAllocation);
					request.admitted = false;
					continue;
				}			
				
				for( int j = 0; j<physicalMachinesAllocation.size(); j++)
				{
					//get each physical machine
					pmAllocation = physicalMachinesAllocation.get(j);
				
				}
				
				//create the model and run it for the request
				 vmProtectionModel = new VMProtectionModel(request, treeNetwork);
				 objectiveValue =  vmProtectionModel.modelFormulation(null, null, alpha, true);
				  
				 //no backups for the request
				 if (objectiveValue == -1)
				 {
					 
					 // if no backup assigned reject request
					 request.admitted = false;
					 
					 //unallocate request from the tree
					 treeNetwork.releaseAllocatedRequest(request,null);	
					 
					 //specify that the request is rejected (even if it was accepted for primary embedding)					 
					 requestsAllocation.add(physicalMachinesAllocation);
					 
					 continue;
				 }
				 
				 //specify that the request was accepted
				 requestsAllocation.add(physicalMachinesAllocation);
			 }
			 else if (request.processType == Request.Type.DEPARTURE)
			 {
				
				 //unallocate request from the tree
				 treeNetwork.releaseAllocatedRequest(request,null);
			 }
			 			 
		 }
		 
	
		 vmPlacement.printRequestsAllocation(vmsProtection.requests, requestsAllocation);
		 
		
	}
	
	
	
	/**
	 * This function test the VMProtectionModel model by allocating the primary
	 * Vms of the requests, and running the VMProtectionModel 
	 * This function executes the model on this.requests 
	 * This is used for automatedtesting() 
	 * 
	 * @param alpha double that specifies the weight to give to adding backup vs bandwidth
	 * @throws IloException
	 */
	public void VMProtectionModelTest(double alpha) throws IloException
	{
	
		Request request;
		ArrayList <int []> physicalMachinesAllocation;
		VMProtectionModel vmProtectionModel;
		double objectiveValue ;
		
		//create a vmPlacement object
		VirtualMachinesPlacement vmPlacement = new VirtualMachinesPlacement(this.treeNetwork);
				
		
		/**
		 * loop over arrivals and departures
		 */
		 for(int i=0;i<this.requests.size();i++)
		 {
			 request = this.requests.get(i);
			
			 //if we are dealing with an arrival, we need to allocate
			 if (request.processType == Request.Type.ARRIVAL)
			 {
				//allocate requests to the smallest subTree based on hose model
				physicalMachinesAllocation = vmPlacement.hoseVMPlacementAlgorithmSingleRequest(request);
				
				//check if request was not admitted
				if (physicalMachinesAllocation == null|| physicalMachinesAllocation.size() == 0)
				{	
					//specify that the request is rejected
					request.admitted = false;
					request.rejectionReason.add(Request.RejectionReason.PRIMARY_EMBEDDING);
					
					//print request information
					vmPlacement.printAllocationForRequest(request, physicalMachinesAllocation);
					
					continue;
				}			
			
				//print request information
				vmPlacement.printAllocationForRequest(request, physicalMachinesAllocation);

				//protect the request	
				 vmProtectionModel = new VMProtectionModel(request, this.treeNetwork);
				 objectiveValue =  vmProtectionModel.modelFormulation(null, null, alpha, true);

				 //backup and bandwidth allocation and mapping is accurate
				 if (objectiveValue >= 0)
				 { 
					request.admitted = true;
					request.updateReservedBandwidth(this.treeNetwork);
					request.updateReservedBackupVms();
					
				 }
				
			 }
			 //release request; note that all the departure request has admitted = false (we only update arrival requests)
			 else if (request.processType == Request.Type.DEPARTURE )
			 {
				 //unallocate request from the tree
				 treeNetwork.releaseAllocatedRequest(request,null);
			 }
			 			 
		 }
		 		
	}
	
	/**
	 * This function test the backupToVmMappingEnhanced model by allocating the primary
	 * Vms of the requests, allocating backups based on collocation in the request subtree and
	 * then by trying on other sub trees.
	 * It maps the primary to backup vms and finally allocates the backup bandwidth
	 * 
	 * @throws IloException
	 * @throws IOException 
	 */
	public void backupToVmMappingModelTest() throws IloException, IOException
	{FileManipulation mainFile  = new FileManipulation("SVMPBGExecutionTime");
	long start, end, execution;
	int k=0;
		Request request;
		ArrayList <int []> physicalMachinesAllocation;
		
				
		//create a vmPlacement object
		VirtualMachinesPlacement vmPlacement = new VirtualMachinesPlacement(this.treeNetwork);
	
		/**
		 * loop over arrivals and departures
		 */
		 for(int i=0;i<this.requests.size();i++)
		 {
			 request = this.requests.get(i);
			
			 //if we are dealing with an arrival, we need to allocate
			 if (request.processType == Request.Type.ARRIVAL)
			 {mainFile.writeInFile("Request "+k+":----------------------\n");k++;
				//allocate requests to the smallest subTree based on hose model
				physicalMachinesAllocation = vmPlacement.hoseVMPlacementAlgorithmSingleRequest(request);
				
				//check if request was not admitted
				if (physicalMachinesAllocation == null|| physicalMachinesAllocation.size() == 0)
				{	
					//specify that the request is rejected
					request.admitted = false;
					request.rejectionReason.add(Request.RejectionReason.PRIMARY_EMBEDDING);
					
					//print request information
					vmPlacement.printAllocationForRequest(request, physicalMachinesAllocation);
					
					continue;
				}			
			
				//print request information
				vmPlacement.printAllocationForRequest(request, physicalMachinesAllocation);
				mainFile.writeInFile("----Main protection starting-----------\n");
				start = System.currentTimeMillis();
				//protect the request				
				 this.protectRequest(request, request.subtree, true);
				 end = System.currentTimeMillis();
				 execution = end - start;
				 mainFile.writeInFile("----Main protection ending "+execution +"-----------\n\n\n");
			 }
			 //release request; note that all the departure request has admitted = false (we only update arrival requests)
			 else if (request.processType == Request.Type.DEPARTURE )
			 {
				 //unallocate request from the tree
				 treeNetwork.releaseAllocatedRequest(request,null);
			 }
			 			 
		 }
		 		
	}

	
	/**
	 * This function is used to create requests based on the parameter passed
	 * This is used to test multiple 
	 * @param randomVms list of VMs
	 * @param randomBw list of bw
	 * @param fileManipulation fileManipulation object to print the info
	 */
	public void copyGeneratedRequests (int [] randomVms,int [] randomBw, FileManipulation fileManipulation )
	{
		
		 Request request = null;
		 String rVms ="", rbw="", content ="";
		 for (int i =0; i<randomVms.length; i++)
		 {			
			    rVms+=randomVms[i]+",";		  
			    rbw+=randomBw[i]+",";
				
			    request = new Request (i,randomVms[i],randomBw[i]);
				this.requests.add (request);
		 }
		content +="-----Requests -----\n";
		content +="public int [] randomVms = {"+rVms+"} \n public int [] randomBw = {"+rbw+"}";
		fileManipulation.writeInFile(content);
		
		System.out.println(content);
		System.out.println();
		
	}
	
	/**
	 * This function prints the bandwidth and the nb of backup VMs reseved for each requests admitted bu both algorithm (baseline and backupToVmMappingModelTest)
	 * 
	 * @param load load which the requests follow
	 * @param setNb the id of the set we are printing the information for
	 * @param backupToVmMappingModelTestBw array list containing the <reqestId, requestsBW, requestBackupVMs>obtained by runnning networkStatus.requestsBandwidthBackupConsumption()
	 * @param backupEmbeddingBaselineBw array list containing the <reqestId, requestsBW, requestBackupVMs>obtained by runnning networkStatus.requestsBandwidthBackupConsumption()
	 * @throws IOException
	 */	
	public void printRequestsInformation (int load, int setNb, ArrayList<int[]> backupToVmMappingModelTestBw ,	ArrayList<int[]> backupEmbeddingBaselineBw ) throws IOException
	{
		//writing reserved bandwidth for each admitted request by both algo
		
		//file containing ids of requests admitted by both algorithms
		String admittedRequestsIdFile = "TestResults/admittedRequestsId.txt";
		FileManipulation admittedRequestsFile  = new FileManipulation(admittedRequestsIdFile);
		
		String backupToVmMappingModelTestRequestsBwFile = "TestResults/backupToVmMappingModelTestRequestsBw.txt";
		FileManipulation  backupToVmMappingModelTestBwFile  = new FileManipulation(backupToVmMappingModelTestRequestsBwFile);
		
		String backupEmbeddingBaselineRequestsBw = "TestResults/backupEmbeddingBaselineRequestsBw.txt";
		FileManipulation backupEmbeddingBaselineBwFile  = new FileManipulation(backupEmbeddingBaselineRequestsBw);
		
		String backupEmbeddingBaselineRequestsBackupVMs = "TestResults/backupEmbeddingBaselineRequestsBackupVMs.txt";
		FileManipulation backupEmbeddingBaselineBackupFile  = new FileManipulation(backupEmbeddingBaselineRequestsBackupVMs);
		
		String backupToVmMappingModelTestRequestsBackupVMs = "TestResults/backupToVmMappingModelTestRequestsBackupVMs.txt";
		FileManipulation backupToVmMappingModelTestBackupFile  = new FileManipulation(backupToVmMappingModelTestRequestsBackupVMs);
		
		NetworkStatus networkStatus = new NetworkStatus(treeNetwork, this.requests);
		ArrayList<int[]> requestsBwComparision = null;
		requestsBwComparision = networkStatus.requestsBandwidthConparison(backupToVmMappingModelTestBw, backupEmbeddingBaselineBw);
		
		admittedRequestsFile.writeInFile("\n\n====Admitted requests by both algo====\n");
		admittedRequestsFile.writeInFile("====LOAD: "+load+" SET NB: "+setNb+"====\n");
		
		backupToVmMappingModelTestBwFile.writeInFile("\n\n====Reserved bandwidth for admitted requests  by backupToVmMappingModelTest () ====\n");
		backupToVmMappingModelTestBwFile.writeInFile("====LOAD: "+load+" SET NB: "+setNb+"====\n");
		
		backupEmbeddingBaselineBwFile.writeInFile("\n\n====Reserved bandwidth for admitted requests  by backupEmbeddingBaseline () ====\n");
		backupEmbeddingBaselineBwFile.writeInFile("====LOAD: "+load+" SET NB: "+setNb+"====\n");
		
		backupToVmMappingModelTestBackupFile.writeInFile("\n\n====Reserved Backup VMS for admitted requests  by backupToVmMappingModelTest () ====\n");
		backupToVmMappingModelTestBackupFile.writeInFile("====LOAD: "+load+" SET NB: "+setNb+"====\n");
		
		backupEmbeddingBaselineBackupFile.writeInFile("\n\n====Reserved Backup VMS  for admitted requests  by backupEmbeddingBaseline () ====\n");
		backupEmbeddingBaselineBackupFile.writeInFile("====LOAD: "+load+" SET NB: "+setNb+"====\n");
		System.out.println("====Admitted requests by both algo");
		for (int i=0; i< requestsBwComparision.size(); i++)
		{
			//skip requests that were not admitted by both algorithms
			if (requestsBwComparision.get(i)[1] == -1 || requestsBwComparision.get(i)[2] == -1)
			{
				continue;
			}
			
			admittedRequestsFile.writeInFile(requestsBwComparision.get(i)[0]+"\n");
			backupToVmMappingModelTestBwFile.writeInFile(requestsBwComparision.get(i)[1]+"\n");
			backupEmbeddingBaselineBwFile.writeInFile(requestsBwComparision.get(i)[2]+"\n");
			backupToVmMappingModelTestBackupFile.writeInFile(requestsBwComparision.get(i)[3]+"\n");
			backupEmbeddingBaselineBackupFile.writeInFile(requestsBwComparision.get(i)[4]+"\n");
		}
	}
	
	/**
	 * This is a helper function to automate the test of the 2 main algorithms
	 * 1-BackupToVmMappingModelTest()
	 * 2-backupEmbeddingBaseline()
	 * 3-VMProtectionModelTest()
	 * 
	 * It prints the results in a file
	 * 
	 * @param algorithm the name of the algorithm to execute
	 * @param ArrayList<int[]> randomVmsBw array of generated Vm bw corresponding to the requests
	 * @param poissonDistribution
	 * @param setNb number of the set we are testing. This is needed to put the results of each set in an independent file
	 * @param alpha double that specifies the weight to give to adding backup vs bandwidth (only needed when running the VMProtectionModelTest)
	 * @throws IOException 
	 * @throws IloException 
	 * 
	 * 
	 * @retun ArrayList <int[]> with request id and bw reserved for it in terms of B bandwidth
	 *  If the bandwidth = -1, the the request was not admitted
	 * 
	 * 
	 */
	public ArrayList<int[]> automatedTesting (String algorithm, ArrayList<int[]> randomVmsBw, Poisson poissonDistribution, int setNb, double alpha ) throws IOException, IloException
	{
		String load = ""+poissonDistribution.lambda/poissonDistribution.mu;
		String mainFileName = "TestResults/"+algorithm+"_GeneralExperimentalResults.txt";
		String timersFile = "TestResults/ArrivalDepartureTime/"+algorithm+"_ArrivalDepartureTime_"+load+"_"+setNb+".txt";
		String revenueOverTimeFile = "TestResults/RevenueOverTime/"+algorithm+"_RevenueOverTime_"+load+"_"+setNb+".txt";
		
		long startTime =0;
		long endTime = 0;
		long executionTime = 0;
		
		double rejectionRate = 0;
		double revenue = 0 ;		
		int reservedBandwidth =0;
		int reservedBackups =0;
		
		ArrayList<Double> timeRevenue = new ArrayList<>();
		ArrayList<Request> sortedRequests = new ArrayList<Request>();
	
		FileManipulation mainFile  = new FileManipulation(mainFileName);
		FileManipulation timerFile = new FileManipulation(timersFile);
		FileManipulation revenueTimeFile = new FileManipulation(revenueOverTimeFile);
		
		
		//FatTreeNetwork treeNetwork1 = new FatTreeNetwork(12,4,2,2,3,500,1000,1000);
		FatTreeNetwork treeNetwork1 = new FatTreeNetwork(128,6,2,2,32,1000,10000,10000);
		//FatTreeNetwork treeNetwork1 = new FatTreeNetwork(256,6,2,2,64,1000,10000,10000);
		treeNetwork1.buildTreeNetwork();	

		mainFile.writeInFile("\n\n==================================================================================================================================\n");
		mainFile.writeInFile(treeNetwork1.toString());	
		
		//create vmsProtection object
		VMsProtectionWithBandwidthGuarantee  vmsProtection = new VMsProtectionWithBandwidthGuarantee(treeNetwork1, sortedRequests);	
		
		//generate the requests and store them to run them for the 2 algorithms
		vmsProtection.copyGeneratedRequests(randomVmsBw.get(0), randomVmsBw.get(1), mainFile);
		
		// set Poisson Arrival/Departure and update the arrival and departure time of the requests	
		vmsProtection.poissonDistribution(poissonDistribution);	
		mainFile.writeInFile(poissonDistribution.toString());
		
		/**
		 * sort the requests using the poisson distribution ascendley
		 * 	the sorted requests will contain 2 copy of each request one for arrival and another for departure.
		 * Only the arrival requests have updated information regarding admitted 
		 */	
		sortedRequests = vmsProtection.sortRequests();
		vmsProtection.requests = sortedRequests;		
		
		//execute the backupToVmMappingModelTest()
		startTime = System.currentTimeMillis();		
		if (algorithm.equals("backupToVmMappingModelTest"))
		{	
			vmsProtection.backupToVmMappingModelTest();		
		}
		else if(algorithm.equals("backupEmbeddingBaseline"))
		{
			vmsProtection.backupEmbeddingBaseline(2);
		}
		else
		{
			vmsProtection.VMProtectionModelTest(alpha);
		}
		endTime = System.currentTimeMillis();
		executionTime = endTime - startTime;
		
				
		NetworkStatus networkStatus = new NetworkStatus(treeNetwork1, vmsProtection.requests);
		
		//calculate the rejection rate, reservedBandwidth, reservedBackups
		rejectionRate = networkStatus.rejectionRate();
		reservedBandwidth = networkStatus.calculateTotalReservedBandwidth();
		reservedBackups = networkStatus.calculateTotalReservedBackupVMs();
		
		//calculate the total revenue 60$ per Vm/ 3$per GB = 0.003$/Mb
		revenue = networkStatus.calculateRevenue(60, 0.003);
		mainFile.writeInFile("\n---- Revenue :"+revenue+" considering VM cost = 60$ and bandwidth cost per 0.003$/Mb = 3$/Gb -----\n ");
		
		//calculate the revenue over time
		timeRevenue = networkStatus.calculateRevenueOverTime(0.6, 0.003, 1000);
		timerFile.writeInFile("----------------------------Time----------------------------------------------\n");
		revenueTimeFile.writeInFile("--------------------------------Revenue over time---------------------------------- \n");
				
		for (int i =0; i<timeRevenue.size(); i++)
		{
			//print only revenue when we have an arrival/departure
			if(timeRevenue.get(i) == 0.0)
			{
				continue;
			}
			timerFile.writeInFile(i+"\n");
			revenueTimeFile.writeInFile(timeRevenue.get(i)+"\n");
		}
		

		mainFile.writeInFile("\n------------------ Execution of : "+algorithm+" -------------------\n ");
		mainFile.writeInFile("\n---- Alpha: "+alpha+" -----\n ");
		mainFile.writeInFile("\n---- ExecutionTime:"+executionTime+" -----\n ");
		mainFile.writeInFile("\n---- Rejection Rate :"+rejectionRate+" -----\n ");		
		mainFile.writeInFile("\n---- Total Reserved bandwidth :"+reservedBandwidth+"\n");		
		mainFile.writeInFile("\n---- Total Reserved backup vms :"+reservedBackups+"\n");
		mainFile.writeInFile("==================================================================================================================================\n");
		
		return networkStatus.requestsBandwidthBackupConsumption();
	}
	
	
	/**
	 * This is a helper function to set the Vms and bandwidth which we want to 
	 * run on several networks Each 5 sets correspnds to a load
	 * 
	 * @return array list of array of vms and bw
	 */
	public ArrayList <ArrayList<int[]>> intializeVmsSets ()
	{
		ArrayList <ArrayList<int[]>> sets = new ArrayList <ArrayList<int[]>>();
		ArrayList<int[]> randomVmsBw = new ArrayList<int[]>();

		/*int [] randomVms = {2,3} ;
		 int [] randomBw = {141,374} ;
		 randomVmsBw.add(randomVms);
		 randomVmsBw.add(randomBw);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();*/
		 
		 
		//load 2
		 int [] randomVms = {21,9,18,21,23,16,11,17,19,22,7,16,16,19,25,12,19,8,11,7,24,8,19,12,16,22,18,21,13,10,5,23,25,19,6,8,24,13,12,21,9,14,14,13,10,20,12,17,15,22,24,21,13,8,24,17,12,21,14,10,21,17,18,8,25,20,7,24,24,14,12,18,23,8,25,12,9,8,21,19,8,6,13,15,14,21,16,17,18,8,17,17,5,25,8,25,11,10,5,7,} ;
		 int [] randomBw = {141,374,203,138,292,320,409,433,207,270,458,109,192,367,367,339,174,249,156,225,107,424,220,414,158,265,444,255,304,433,231,215,426,489,163,447,117,350,440,454,238,339,157,470,277,389,211,356,383,302,316,483,151,425,445,349,161,161,500,114,354,351,205,437,114,457,207,371,301,416,202,398,328,411,144,136,480,468,333,270,313,178,244,369,274,236,224,225,180,304,197,120,188,378,309,486,203,204,440,423,} ;
		 randomVmsBw.add(randomVms);
		 randomVmsBw.add(randomBw);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms1 = {12,20,15,6,7,22,15,17,22,12,13,16,9,23,18,19,19,8,6,18,23,8,6,5,7,16,10,17,17,9,6,9,21,22,19,22,20,20,15,8,14,15,15,24,17,22,9,25,18,14,12,16,16,5,17,10,25,24,8,8,19,25,15,20,5,15,11,18,22,13,15,16,24,19,20,8,10,9,23,13,13,9,9,7,14,14,23,13,11,10,17,9,6,24,6,12,25,24,19,24,} ;
		 int [] randomBw1 = {360,105,338,349,107,261,306,434,492,203,358,436,386,205,206,340,166,452,101,145,340,137,222,355,112,468,484,474,427,141,148,115,422,442,137,425,112,221,233,312,130,195,178,334,362,449,318,489,390,317,465,251,159,148,182,123,454,257,148,232,245,245,316,328,251,475,359,494,311,320,313,497,333,238,405,206,223,194,103,244,146,425,235,280,291,418,146,441,500,405,241,306,496,335,130,246,476,340,489,303,} ;
		 randomVmsBw.add(randomVms1);
		 randomVmsBw.add(randomBw1);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms2 = {12,10,22,21,17,5,13,9,17,12,6,18,10,25,21,15,13,20,6,19,21,15,7,16,16,9,20,6,12,22,18,16,13,17,6,7,10,9,21,9,20,13,8,24,12,5,11,8,17,20,19,9,16,15,11,20,8,6,10,13,6,9,14,25,13,11,25,19,6,8,19,25,23,13,6,17,10,24,17,16,25,22,23,19,15,18,7,13,15,11,22,15,12,5,8,13,5,24,9,19,} ;
		 int [] randomBw2 = {460,317,272,198,324,375,283,108,166,235,332,230,178,480,172,352,408,150,476,404,356,321,227,327,325,231,109,221,371,347,105,332,243,482,436,172,290,198,357,144,423,351,362,324,124,396,260,344,113,104,400,485,454,131,141,453,178,410,252,198,479,432,303,171,374,469,500,407,211,464,220,328,379,177,447,228,335,389,467,205,235,230,137,143,365,152,189,110,436,106,248,119,435,315,276,108,171,419,285,235,} ;
		 randomVmsBw.add(randomVms2);
		 randomVmsBw.add(randomBw2);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms3 = {11,17,18,16,21,21,25,15,18,14,25,21,14,22,9,15,18,17,6,14,21,22,20,25,19,16,6,11,13,21,11,25,20,13,24,9,25,14,13,15,18,12,22,5,9,17,11,10,7,22,14,16,8,8,10,6,14,19,20,14,21,16,6,19,18,7,6,5,8,5,22,7,10,19,20,17,9,24,13,18,25,10,17,25,23,5,10,22,13,10,20,6,15,13,19,16,21,15,9,18,} ;
		 int [] randomBw3 = {448,242,281,294,330,356,296,121,447,418,283,162,308,105,457,425,350,300,138,139,239,498,350,402,257,364,230,446,174,481,308,242,245,222,111,427,142,334,118,304,253,230,248,263,229,329,349,210,167,471,475,305,304,160,114,262,412,466,205,423,138,191,116,283,230,466,378,347,407,475,194,130,345,436,233,164,168,383,101,251,211,339,219,287,311,293,315,324,500,400,489,267,219,381,303,250,467,232,396,476,} ;
		 randomVmsBw.add(randomVms3);
		 randomVmsBw.add(randomBw3);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms4 = {25,12,12,18,5,22,8,6,18,13,25,13,20,25,6,15,12,9,8,16,19,24,16,8,9,5,8,21,23,5,6,18,12,7,24,10,16,13,9,25,11,20,12,12,11,24,10,11,16,8,10,6,6,15,22,14,5,22,17,5,16,9,8,25,12,17,7,20,17,7,9,25,15,24,25,9,12,25,25,5,20,9,8,11,5,16,18,21,25,6,6,5,23,16,22,11,25,6,12,9,}; 
		 int [] randomBw4 = {238,126,195,407,389,389,471,136,270,429,439,345,141,198,447,419,164,307,436,156,313,286,222,138,257,349,268,297,286,224,469,420,361,272,359,477,167,243,430,238,314,114,194,436,439,331,247,385,302,356,106,481,487,168,213,444,401,141,424,201,102,235,190,214,275,481,136,208,127,353,109,164,104,339,178,312,413,219,466,136,104,419,138,421,401,455,100,142,430,380,427,146,356,146,304,330,136,117,294,394,}; 
		 randomVmsBw.add(randomVms4);
		 randomVmsBw.add(randomBw4);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 //load 4
		 int [] randomVms5 = {12,18,6,25,17,17,18,22,19,15,10,5,24,22,9,7,7,16,16,21,19,17,7,22,23,15,14,22,21,18,16,11,7,17,18,23,11,8,11,21,6,13,5,14,20,21,8,19,22,9,23,24,10,25,24,9,13,13,19,22,11,10,5,11,12,24,25,7,6,5,17,23,24,19,14,13,15,20,21,7,12,21,21,7,7,17,6,18,12,21,15,11,24,14,24,7,5,7,20,16,}; 
		 int [] randomBw5 = {318,348,312,421,351,328,448,348,442,221,185,138,453,482,303,189,495,270,262,444,165,384,416,263,338,158,472,230,115,492,156,238,192,403,442,496,488,246,413,152,328,111,351,169,187,224,128,480,172,466,116,330,167,248,200,226,316,214,420,255,121,116,268,186,383,441,202,188,478,207,431,371,252,287,100,420,297,110,288,402,160,347,128,467,395,409,153,486,194,224,385,332,336,490,325,246,325,110,327,458,}; 
		 randomVmsBw.add(randomVms5);
		 randomVmsBw.add(randomBw5);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms6 = {14,7,9,21,20,7,7,14,12,7,12,12,8,7,8,11,23,12,12,12,18,24,8,12,5,8,23,19,6,21,21,9,13,21,23,24,25,24,19,14,18,19,6,16,8,20,23,6,11,6,12,8,21,11,20,14,18,6,17,19,20,12,5,16,16,20,14,5,12,15,7,9,24,10,23,19,22,7,5,19,15,19,25,15,22,10,10,18,20,22,7,7,12,15,23,15,25,25,18,5,}; 
		 int [] randomBw6 = {464,418,417,483,223,489,292,237,389,334,291,215,388,229,398,186,169,296,122,465,181,259,360,265,399,352,480,288,480,213,430,175,311,285,100,448,162,494,138,200,315,426,106,319,249,286,343,351,369,203,334,377,101,152,492,215,417,140,377,267,436,204,380,385,326,320,368,237,421,216,143,200,249,152,260,490,126,135,421,263,145,250,255,486,236,240,104,367,382,238,133,298,177,268,357,114,174,376,312,421,}; 
		 randomVmsBw.add(randomVms6);
		 randomVmsBw.add(randomBw6);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms7 = {14,13,18,5,12,25,14,20,22,5,12,8,23,22,23,25,25,25,6,15,24,15,7,5,8,7,13,11,15,23,11,18,11,8,7,17,10,12,7,11,15,15,18,23,13,25,20,6,10,11,19,12,5,23,10,10,16,17,6,10,11,19,11,25,9,10,11,21,20,17,20,14,20,6,23,8,19,16,15,13,17,5,20,14,24,6,23,17,21,7,11,16,18,18,23,17,12,13,10,11,}; 
		 int [] randomBw7 = {173,283,435,253,231,386,499,347,109,210,352,464,259,455,477,421,406,222,466,135,392,286,477,121,484,180,384,460,235,128,486,207,256,236,442,211,139,189,352,160,488,129,227,198,277,197,402,199,113,391,448,364,163,180,462,318,250,447,348,275,324,239,106,442,362,315,218,325,225,433,381,391,329,214,485,307,392,236,201,380,127,348,189,136,301,235,199,303,303,422,258,417,469,314,293,244,413,392,115,111,}; 
		 randomVmsBw.add(randomVms7);
		 randomVmsBw.add(randomBw7);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms8 = {23,5,25,17,9,6,21,8,13,13,23,15,21,22,14,25,13,20,10,13,24,22,19,25,20,14,17,12,21,18,19,16,19,9,8,17,6,8,19,7,11,7,18,12,25,13,24,8,8,21,9,18,11,17,9,11,10,17,24,23,23,22,16,9,14,13,14,18,12,14,6,21,23,17,6,14,14,10,23,16,20,5,21,25,12,6,21,12,20,6,24,5,18,25,23,8,12,8,16,5,}; 
		 int [] randomBw8 = {107,487,308,310,475,494,336,496,247,186,230,427,313,173,444,126,145,103,215,314,217,349,408,480,468,276,191,293,331,292,348,494,188,485,360,438,120,355,441,183,394,344,464,489,137,396,363,209,137,128,155,239,442,497,420,438,321,429,114,484,434,143,496,480,266,284,400,391,444,182,267,240,428,245,491,306,373,322,318,207,427,213,285,115,160,380,298,198,296,415,342,437,115,122,478,401,228,355,387,423,} ;
		 randomVmsBw.add(randomVms8);
		 randomVmsBw.add(randomBw8);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 		 
		 int [] randomVms9 = {16,23,13,22,19,8,6,21,24,18,14,19,22,13,23,20,18,20,24,18,8,7,18,12,9,17,8,18,15,13,22,11,11,13,7,13,18,7,22,21,11,20,23,13,15,18,19,17,20,14,17,22,5,7,9,25,24,9,15,16,15,12,25,7,9,19,11,18,7,12,18,15,22,9,15,23,19,24,15,11,19,18,17,15,18,13,5,14,8,21,7,13,24,11,22,10,13,14,16,23,} ;
		 int [] randomBw9 = {451,406,256,473,251,412,202,183,123,498,398,457,458,492,250,316,431,110,193,386,201,183,339,437,388,447,491,298,399,128,180,456,339,174,442,295,358,391,436,290,319,205,467,165,185,483,497,412,462,467,419,248,276,281,179,153,314,335,417,465,291,352,208,448,435,147,328,395,204,410,128,128,499,343,352,446,237,368,296,122,272,117,459,179,314,279,206,203,162,114,165,167,101,313,423,386,352,433,248,471,};
		 randomVmsBw.add(randomVms9);
		 randomVmsBw.add(randomBw9);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 //load 6
		 int [] randomVms10 = {11,13,9,23,19,24,25,13,23,18,15,19,16,19,18,11,10,13,12,23,18,19,11,21,15,6,23,14,12,10,17,22,6,25,18,5,12,25,9,7,8,9,13,17,12,13,23,11,21,14,23,22,18,5,15,14,19,16,5,24,23,15,19,11,17,14,15,22,16,24,18,14,14,24,23,19,15,9,9,13,25,16,16,11,14,13,24,17,13,12,9,12,15,5,18,20,12,7,18,25,} ;
		 int [] randomBw10 = {321,216,435,199,460,217,365,354,166,167,409,476,269,334,371,128,140,429,247,299,422,422,103,186,481,278,263,246,470,336,256,170,316,125,349,196,170,487,357,277,496,226,408,227,318,218,350,471,467,240,398,245,206,423,430,212,325,367,335,176,150,124,114,257,438,488,381,123,250,455,350,387,475,130,258,482,453,301,493,183,344,255,434,207,256,436,402,443,119,487,388,469,100,430,208,486,373,494,443,457,} ;
		 randomVmsBw.add(randomVms10);
		 randomVmsBw.add(randomBw10);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms11 = {14,5,14,11,25,18,11,8,13,18,16,7,24,21,15,9,12,12,6,12,8,16,20,5,6,16,20,25,5,13,13,7,17,19,24,12,20,6,12,16,15,21,13,11,17,13,11,24,22,20,25,21,14,15,13,9,13,10,23,19,15,21,20,9,7,24,7,6,12,24,12,10,7,7,15,22,14,13,18,5,18,20,9,5,22,8,12,24,8,9,8,14,10,12,24,11,21,8,25,18,} ;
		 int [] randomBw11 = {132,179,379,321,280,310,133,345,491,414,261,388,453,408,281,302,171,430,354,185,485,416,104,190,192,140,297,295,248,241,385,412,499,190,120,136,164,367,441,350,260,496,270,450,251,324,257,153,419,185,336,377,126,328,299,299,127,124,473,238,471,232,240,472,389,249,178,463,352,241,170,411,133,125,183,254,104,315,384,178,135,208,440,335,245,214,156,454,300,413,156,208,108,155,354,411,248,119,282,415,} ;
		 randomVmsBw.add(randomVms11);
		 randomVmsBw.add(randomBw11);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms12 = {6,20,12,7,5,6,5,14,21,19,13,6,22,13,14,25,18,12,16,7,14,19,10,14,14,20,19,14,18,13,13,13,19,10,21,20,19,23,15,7,15,17,9,12,25,17,15,11,5,17,19,7,5,5,12,18,21,14,13,7,7,14,25,13,8,22,16,21,10,19,16,7,11,24,23,21,14,22,12,24,6,24,18,10,7,14,11,25,20,5,13,6,8,8,10,22,17,8,24,13,} ;
		 int [] randomBw12 = {188,174,278,259,463,102,192,202,479,306,215,465,440,418,236,199,269,140,440,124,350,221,207,325,158,452,163,484,424,446,145,302,336,413,103,177,433,159,483,454,297,497,377,367,404,268,186,352,392,238,132,325,417,400,409,484,499,363,362,461,375,228,185,431,179,151,457,139,424,203,362,202,151,267,395,456,267,327,243,327,422,190,298,233,134,492,461,412,363,371,253,394,189,165,127,219,134,293,380,133,} ;
		 randomVmsBw.add(randomVms12);
		 randomVmsBw.add(randomBw12);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms13 = {23,10,16,24,10,13,18,22,11,7,18,16,23,8,9,16,11,10,13,12,9,12,14,15,21,19,25,10,21,17,12,25,22,14,25,22,24,16,21,8,21,18,8,16,10,17,15,10,19,13,23,10,13,9,14,13,11,15,12,25,22,25,9,11,19,16,5,13,24,24,10,15,14,19,6,24,21,20,17,7,21,24,25,8,8,19,20,17,14,22,15,21,17,5,18,7,14,7,16,6,} ;
		 int [] randomBw13 = {381,409,467,423,188,498,156,211,349,123,299,341,285,291,218,381,162,409,198,174,309,465,394,369,139,403,266,106,266,202,311,171,348,241,180,255,222,453,370,258,286,221,498,307,381,347,268,428,175,424,469,468,159,346,312,156,399,436,229,282,207,109,147,362,403,291,107,463,447,413,233,474,332,178,397,108,128,420,496,309,161,452,425,158,163,472,351,474,218,208,258,412,297,427,222,457,474,481,109,141,} ;
		 randomVmsBw.add(randomVms13);
		 randomVmsBw.add(randomBw13);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms14 = {10,9,11,18,18,5,25,12,8,21,19,25,9,11,21,15,18,9,23,14,7,25,10,22,7,10,16,10,14,13,11,21,8,25,24,21,12,5,9,12,17,9,8,14,15,15,23,20,12,11,22,25,25,21,25,6,6,24,16,23,21,18,13,23,8,12,21,5,8,24,16,25,7,21,10,12,25,10,18,17,24,13,10,21,22,8,16,5,5,7,24,21,5,21,23,17,17,21,14,24,} ;
		 int [] randomBw14 = {319,335,422,304,142,416,250,132,120,383,199,317,483,344,311,491,354,215,197,495,269,453,144,300,110,410,315,236,462,173,299,281,338,419,307,149,333,463,251,397,260,348,449,283,119,493,324,411,334,125,339,170,172,100,402,249,332,457,435,261,247,484,411,154,183,224,128,322,366,175,238,228,179,383,490,163,182,404,466,151,495,177,314,375,227,190,462,173,101,224,140,316,160,341,305,407,240,257,141,197,} ;
		 randomVmsBw.add(randomVms14);
		 randomVmsBw.add(randomBw14);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 //load 8
		 int [] randomVms15 = {10,14,12,11,8,20,5,15,19,23,13,24,22,20,7,9,10,20,13,5,12,23,9,23,24,13,17,9,21,6,7,6,24,12,10,17,23,18,23,6,10,6,14,20,17,17,24,19,7,18,14,6,10,13,20,20,19,13,12,23,21,9,11,25,13,10,18,8,22,12,8,11,7,9,14,23,24,25,13,5,16,19,11,16,8,17,6,11,12,5,25,6,19,15,9,5,25,5,18,23,} ;
		 int [] randomBw15 = {429,215,346,450,323,352,162,107,237,165,271,352,105,423,417,444,171,441,301,136,196,110,481,144,182,127,469,492,284,451,500,182,327,148,136,281,397,104,309,179,245,212,372,298,282,433,375,239,185,232,475,224,463,336,142,180,407,397,119,147,224,427,442,352,330,219,452,295,369,279,160,281,172,235,424,167,400,320,300,340,350,263,489,271,160,116,349,299,413,305,247,239,256,384,482,185,355,344,100,348,} ;
		 randomVmsBw.add(randomVms15);
		 randomVmsBw.add(randomBw15);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms16 = {22,14,6,18,14,14,18,17,13,24,18,14,20,7,21,22,23,20,11,16,21,24,21,10,9,13,10,13,23,13,10,16,11,16,19,9,10,5,10,19,14,22,13,6,7,24,22,5,20,19,16,21,23,14,10,21,22,13,21,16,10,21,24,10,18,21,14,11,20,8,19,8,13,7,25,12,13,10,15,18,17,9,12,10,9,25,5,12,18,22,5,23,11,17,24,12,18,22,19,19,} ;
		 int [] randomBw16 = {235,398,487,381,452,199,374,262,155,170,100,420,485,495,460,424,278,467,438,121,316,418,389,301,447,266,328,261,491,291,360,132,347,301,406,457,427,423,108,200,140,190,197,343,497,446,422,343,310,357,220,260,454,313,342,107,234,372,283,483,157,471,415,442,299,139,483,492,355,410,190,368,406,270,228,200,168,473,447,300,262,106,443,486,496,167,326,237,338,354,252,264,129,406,370,127,500,475,236,264,}; 
		 randomVmsBw.add(randomVms16);
		 randomVmsBw.add(randomBw16);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms17 = {21,17,21,16,10,14,19,18,13,18,17,18,11,11,13,5,8,11,18,8,14,14,11,23,12,5,16,25,23,19,23,9,9,6,14,13,24,6,23,22,16,25,7,15,9,20,21,9,8,12,17,17,12,19,5,22,7,9,25,7,7,7,18,25,16,12,18,18,6,14,9,9,19,20,20,7,22,23,19,17,13,21,9,18,5,8,24,6,5,5,23,7,16,11,16,11,22,12,24,12,} ;
		 int [] randomBw17 = {440,444,176,351,152,471,296,408,169,223,347,233,153,494,225,100,307,140,360,307,158,147,153,122,197,350,332,253,249,486,309,348,147,470,166,379,224,359,118,257,380,218,247,220,407,233,128,488,485,102,157,211,456,366,488,230,105,203,326,443,235,151,393,428,137,325,119,328,237,399,289,319,270,386,323,155,447,374,413,180,199,485,160,188,261,388,401,496,197,213,470,266,494,387,477,291,476,315,256,239,} ;
		 randomVmsBw.add(randomVms17);
		 randomVmsBw.add(randomBw17);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms18 = {11,16,20,5,19,8,5,16,20,7,20,7,5,10,18,21,11,17,23,17,22,23,21,21,17,17,6,17,22,13,25,7,11,20,23,9,9,7,10,18,6,17,14,17,9,24,6,15,9,19,6,11,15,25,18,16,15,6,11,8,17,10,12,17,23,14,15,20,22,8,15,12,17,6,18,11,11,8,20,12,14,23,20,23,23,16,22,19,14,22,24,22,21,14,23,13,5,14,25,12,} ;
		 int [] randomBw18 = {233,230,393,160,318,312,282,459,106,319,221,147,251,354,146,134,198,424,281,197,303,440,355,139,440,300,190,269,108,105,347,151,208,267,307,396,485,321,126,230,235,197,421,122,419,274,141,251,371,215,412,476,447,268,426,179,388,264,461,243,458,475,246,129,176,432,323,145,471,381,474,409,415,209,498,451,342,495,345,297,344,467,177,183,151,464,458,399,199,383,274,255,475,345,249,456,226,296,476,245,} ;
		 randomVmsBw.add(randomVms18);
		 randomVmsBw.add(randomBw18);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms19 = {21,5,22,6,10,19,5,9,18,18,18,23,20,18,16,11,12,11,13,25,24,10,23,15,16,8,6,12,6,22,8,7,14,10,22,18,17,20,15,15,20,25,18,9,23,13,8,10,14,23,22,7,8,23,18,20,15,25,7,15,20,21,16,12,5,14,21,12,13,21,15,19,17,18,6,14,12,11,5,9,24,20,14,21,24,15,10,13,8,11,10,13,20,23,7,5,5,13,16,12,} ;
		 int [] randomBw19 = {381,135,103,302,187,149,107,264,279,441,482,215,499,209,218,242,422,446,157,247,215,388,136,425,334,305,459,168,394,408,462,242,417,141,305,236,111,339,365,384,147,134,185,166,288,292,354,305,165,308,242,425,325,456,440,394,195,307,490,332,286,191,179,349,218,440,220,300,492,225,189,239,485,487,239,136,360,365,139,159,266,141,143,155,132,326,407,226,171,154,396,343,498,451,403,119,254,101,171,319,} ;
		 randomVmsBw.add(randomVms19);
		 randomVmsBw.add(randomBw19);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 //load 10
		 int [] randomVms20 = {24,19,25,5,22,8,20,21,17,19,20,16,5,23,12,13,8,9,14,10,6,23,13,6,16,19,19,14,10,7,24,14,13,18,7,9,18,24,7,12,11,15,13,5,15,7,16,8,23,19,9,15,14,10,14,5,17,16,19,21,5,5,11,6,21,11,11,16,12,24,7,7,17,5,21,10,15,18,25,9,24,21,6,22,15,18,6,9,14,20,15,17,16,23,8,13,8,5,15,24,} ;
		 int [] randomBw20 = {307,301,181,116,164,269,496,165,179,495,442,158,451,196,148,453,473,341,437,444,441,364,468,483,408,150,131,244,388,337,383,242,176,350,466,453,361,291,257,204,452,410,144,292,214,316,348,288,295,196,118,249,213,147,169,283,255,331,453,218,147,442,197,289,266,351,129,258,208,335,390,299,244,465,220,176,204,495,200,115,106,495,376,457,323,388,147,161,285,344,104,244,226,332,338,213,275,153,101,312,} ;
		 randomVmsBw.add(randomVms20);
		 randomVmsBw.add(randomBw20);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms21 = {22,24,21,17,18,12,5,19,20,16,19,21,21,23,16,18,21,6,24,16,13,25,15,22,13,8,19,15,8,5,10,13,18,11,10,12,22,12,12,6,25,5,14,25,18,18,14,6,19,22,13,17,14,24,19,21,21,21,5,18,21,7,19,15,20,8,14,20,9,16,14,18,17,14,5,23,8,19,12,8,18,21,15,24,6,17,15,14,25,5,10,20,25,22,10,15,14,16,25,14,} ;
		 int [] randomBw21 = {169,248,133,233,284,151,202,100,170,159,410,464,271,305,236,375,388,220,412,258,345,169,258,297,191,121,215,148,230,408,266,366,376,118,284,120,480,206,282,193,355,368,303,373,231,369,200,189,377,420,137,306,225,156,242,445,305,281,128,363,286,132,483,421,432,493,132,494,145,424,406,310,114,475,237,100,459,162,430,275,128,261,469,189,207,114,228,117,351,223,266,173,468,110,462,252,211,348,253,478,}; 
		 randomVmsBw.add(randomVms21);
		 randomVmsBw.add(randomBw21);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms22 = {22,7,23,10,20,20,18,6,18,9,20,11,25,16,8,20,10,8,20,17,14,10,16,25,14,6,21,10,5,22,20,19,22,13,20,11,9,5,15,9,22,14,21,22,5,25,14,17,5,19,19,9,12,20,16,6,11,25,12,14,8,10,10,25,25,5,21,14,18,16,10,8,8,21,5,22,8,15,18,21,21,13,18,24,8,20,23,17,14,12,21,23,6,12,12,16,16,7,6,19,}; 
		 int [] randomBw22 = {353,408,441,360,328,344,291,298,431,444,426,364,297,369,488,160,335,348,346,285,240,367,446,239,128,467,314,339,374,313,125,484,298,354,326,360,160,462,218,488,260,102,313,131,397,498,498,119,415,255,244,112,251,433,424,372,128,291,336,191,445,138,484,114,106,242,230,163,135,350,347,166,333,398,237,432,251,338,190,194,259,372,314,162,300,158,426,176,401,165,485,195,230,153,265,271,349,489,163,125,} ;
		 randomVmsBw.add(randomVms22);
		 randomVmsBw.add(randomBw22);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms23 = {19,24,8,14,9,6,24,14,13,17,19,19,6,6,21,5,16,15,13,6,8,15,10,5,8,15,6,6,12,23,12,21,8,23,11,13,25,19,11,18,12,13,24,22,8,21,23,5,14,6,5,24,21,15,7,10,9,20,15,15,23,10,18,15,23,18,7,7,21,21,25,13,11,15,15,14,7,21,21,12,13,7,17,7,18,15,19,9,8,17,14,25,16,15,17,15,12,19,25,24,} ;
		 int [] randomBw23 = {455,339,285,211,222,362,112,228,310,250,378,195,447,402,405,142,136,179,231,206,177,352,398,282,491,278,107,108,200,176,241,170,309,400,329,263,199,352,102,256,158,357,312,137,371,350,408,259,282,485,299,375,370,431,204,436,480,391,221,179,491,497,422,179,472,215,290,229,295,381,451,466,326,138,246,244,289,289,269,223,345,292,304,100,278,290,418,353,290,122,249,165,245,293,408,340,282,492,129,308,} ;
		 randomVmsBw.add(randomVms23);
		 randomVmsBw.add(randomBw23);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		 int [] randomVms24 = {16,21,23,9,11,17,10,24,13,14,15,9,5,16,10,16,9,18,20,23,9,12,8,6,5,11,6,22,12,18,14,8,24,17,22,15,17,14,14,8,15,21,11,21,20,20,8,7,14,6,23,6,17,21,9,12,23,18,20,16,9,9,15,21,18,16,22,17,25,10,20,15,7,6,16,25,23,18,19,21,23,23,21,22,22,23,7,18,12,24,24,10,5,14,25,14,6,22,15,25,}; 
		 int [] randomBw24 = {274,464,356,475,288,302,382,397,249,453,470,268,118,116,360,337,199,317,245,285,130,275,418,476,165,107,251,115,214,442,389,144,482,469,434,410,266,299,411,310,362,486,237,469,162,478,174,101,455,354,160,294,305,242,115,164,314,130,330,496,335,349,219,358,147,420,350,437,446,250,108,268,386,144,141,180,483,353,422,180,422,116,182,250,314,402,395,419,479,497,272,344,225,149,116,281,313,163,130,215,}; 
		 randomVmsBw.add(randomVms24);
		 randomVmsBw.add(randomBw24);
		 sets.add(randomVmsBw);
		 randomVmsBw = new ArrayList<int[]>();
		 
		return sets;
	}
	
	/**
	 * This is a helper function to set the arrivals and departures of
	 * a set of requests that we want to run for several networks
	 * 
	 * Their order corresponds to the ones mentioned in intializeVmsSets()
	 * 
	 * @return Array list of arrays of departures and arrivals
	 */
	public ArrayList <ArrayList<double[]>> intializePoissonSets()
	{
		ArrayList <ArrayList<double[]>> sets = new ArrayList <ArrayList<double[]>>();
		ArrayList<double[]> distribution = new ArrayList<double[]>();
		/*double[] arrivals = {0.0,0.0685296384407159,0.12163520209560337, }; 
		double[] departures = {0.13403040133984687,0.27027418517019075,};
		distribution.add(arrivals);
		distribution.add(departures);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();*/
		//load 2
		double[] arrivals = {0.0,0.0685296384407159,0.12163520209560337,0.17215781955930975,0.26270748258341886,0.2907052487746953,0.2907737486298687,0.36850249942987295,0.5333245565107163,0.6363217221963083,0.697582958157718,0.7198853064438852,0.7211451040913107,0.773156256672772,0.8328462617559405,0.8610881451062807,0.9386958326380002,0.9392547944865242,1.0544255392576023,1.0919195376366984,1.114874454776926,1.1570389874244773,1.1583488168833662,1.1962261152025888,1.2355022559856454,1.2927636491671042,1.3016880035907619,1.458386356587213,1.5765022176233103,1.5996587673687404,1.7685399095847412,1.8008319545202054,1.834797392640551,1.8408152611613082,1.9136547649007547,1.9504673207984236,2.0116386801266364,2.0127550082710783,2.0429370402743907,2.1603648448020483,2.1842330076054144,2.293114461961093,2.300747599166946,2.30641009872689,2.308399379703867,2.4224575772550425,2.4390103959338276,2.4815382849560415,2.522184683336824,2.5253528816642095,2.594884781283803,2.6074887379404275,2.67056523040669,2.7046195370631585,2.7131542269912443,2.7260275757405004,2.730674053893922,2.765885034715627,2.7754784796241294,2.884157558644534,2.8872699557074637,2.9099496086563756,2.930748738506131,2.954875777939134,3.101299464508132,3.1411882601598697,3.2218137123643698,3.2241778405923007,3.2724556684603137,3.30186212010923,3.385162372846124,3.390131747205095,3.4428223012587136,3.486573596327912,3.4868045994311254,3.503665666541325,3.5268620026270274,3.571881893388062,3.575206443781479,3.595583799704635,3.627251710420164,3.7069661528817095,3.729816210926469,3.839811061059487,3.9037769958791566,3.9347378598973535,3.979950947427347,4.012701903761752,4.041222805382316,4.205364809437191,4.230347506282678,4.266566287067429,4.276175068487163,4.325514750530302,4.394799663228339,4.444479721773598,4.453060050866188,4.573658551647996,4.593412336507982,4.6662654907833065, }; 
		double[] departures = {0.13403040133984687,0.27027418517019075,0.12548765605931572,0.2943056791293614,0.3066874399467417,0.43429743080500116,0.43432029101170444,0.3872106755694756,0.5686405014811368,0.7809480634629584,0.8540659626103321,0.9333438814033673,1.367702461776628,0.8826655975625078,1.0672176924797703,0.8716065579020277,1.036338605975644,1.1866256069687586,1.128117782429269,1.1974640133947925,1.3314704624981115,1.470348674665916,1.4209686455735953,1.2052367847919419,1.3405495881238143,1.3400806080804801,1.3917526914392107,1.5604774294639603,1.7483875266739952,1.7082437073879193,2.081093974457346,1.9325216154906044,1.9317647249971055,1.9980561801932606,2.0702964239322066,1.972938826292914,2.0655015455735435,2.1167675983933396,2.0601058365667937,2.662727913740634,2.432814432027488,2.455133475354999,2.3428233613508715,2.310744771342196,2.34019772612452,2.707713726879262,2.4706130274526616,2.5293879723722186,2.5506938825146173,2.690096381639389,2.6513330087139497,2.741753879272359,2.8240159629971817,2.7439594131217078,2.786137533694658,2.772374437089894,2.828071906841777,2.879921156186192,2.7793034644471737,2.916407389002342,3.189715353807271,2.956360482096895,2.946025407931489,3.0022004768420594,3.2282959153997077,3.217826845464583,3.3539505295663528,3.261664856081744,3.2996274017660414,3.3881817898383546,3.433238938779479,3.5851060622198525,3.4667403532251737,3.569454595234844,3.507615005315428,3.5864162279895244,3.5877438933082617,3.608133300597737,3.6057300262463072,3.5990300532312594,3.6534747308928046,3.7227680183056897,3.919914533158613,3.899581430090217,4.043216729097382,3.96973333729358,3.98936963383281,4.023737851212127,4.108379319971091,4.208997319497689,4.278885451334397,4.274667435968872,4.2899128618157185,4.382951587438672,4.397880950654871,4.681101652184435,4.582565461646852,4.608587157725217,4.597078179856787,4.738152538797513,};
		distribution.add(arrivals);
		distribution.add(departures);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals1 = {0.0,0.009612234318227705,0.05848451558927816,0.060762116139497065,0.07094704786734535,0.09113968149877501,0.2237746216931577,0.4233180881615838,0.47045178452906145,0.5087742005970632,0.522008291869492,0.5354120520900093,0.556506496817182,0.5913331211976811,0.680164205103656,0.7030959546927528,0.7165158489892953,0.7341858462610684,0.7441917314631236,0.7531204538151312,0.8499166393862541,0.8834491969207799,0.9712202491474013,1.029175951859019,1.0782108743210113,1.1253377552108423,1.1422204850816926,1.1670582806543683,1.204468233577959,1.3123969982970096,1.3409945562625125,1.3564009072463552,1.3635038896456786,1.3680086765547987,1.4508827028754558,1.4920393583824763,1.5642898621257166,1.623926263699847,1.6288322153036274,1.698410447257759,1.7032661060477383,1.7140904580366476,1.7259957964062282,1.7304453820189647,1.7397756375315334,1.758291241781829,2.010057684799899,2.0918339706773796,2.1212194983107473,2.1362160922640503,2.1413522888936205,2.1651250856722726,2.215638027947258,2.2410627075170666,2.300595112884627,2.308595820787106,2.371394980416745,2.371533371089329,2.3947341624870697,2.4891699987231783,2.5368926402741465,2.5546324927875195,2.6052021868613155,2.64347038454432,2.6849727908216945,2.7562415355204135,2.8316755476853763,2.8511476018918747,2.8618061104045363,2.894961521010543,2.898584383534574,2.900374938560481,2.910851520056309,2.932790341939344,2.9638319928040375,2.9968423526075694,3.0409128696177627,3.0519320957048133,3.0673706838304966,3.1406912984647155,3.1866790807096743,3.202463683903682,3.3956512374257777,3.4125988404582337,3.5076831572303075,3.5992458071831646,3.7015193862884845,3.9923811435539944,3.9953619639192137,3.996931878908308,4.021595865184457,4.031140796526594,4.100319419908539,4.1430313825913405,4.162758930311213,4.171502028366034,4.261325133872568,4.342408530270578,4.380370116340896,4.3913438800357785, }; 
		double[] departures1 = {0.11205249013248227,0.1616056004185411,0.06395099829825408,0.11042964606462721,0.12870575641797627,0.13936024632794247,0.28111212813230163,0.5003725857534406,0.6068149163600915,0.5325373406623586,0.6589225328617238,0.5673039924857155,1.1112732693445477,0.6355027876244007,0.7334570717002351,0.7734849670691791,1.017304716921695,0.8997085779809941,0.7666522679805563,0.7565927396720329,0.8752985213343116,0.988387287826641,1.4667296918802226,1.0822475988417788,1.111869353165531,1.429642413594727,1.1746002016924384,1.3661920022170893,1.2673782243216303,1.6978902751350424,1.3489536494651446,1.6519471490592363,1.4462079250120214,1.6233267389707342,1.6014353390805065,1.5135504438920973,1.8394905736870986,1.679838512447596,1.7201151739010427,1.7392668185295783,1.8251630585104506,1.7517250030456224,1.7313650064328594,1.7978088627082178,1.781496173398085,1.7656113700974765,2.0668390192471198,2.09516702938852,2.1355891238878364,2.2258725307711016,2.2761187980677433,2.1824882042495015,2.4109402412684475,2.2670809295245022,2.4903064120144953,2.407136906034342,2.39990911806833,2.4089858894324205,2.5261248968622914,2.585762071510301,2.6185743989597565,2.5709926344488894,2.763091783977767,2.7635166501141457,2.736946533538173,2.849053327617815,2.9488799627800155,2.941635971471897,2.8698878709002495,2.9614661578101695,2.921310804898728,3.1246156612734017,2.9804522722579745,2.992882552054873,3.142798327375642,3.0328165245698426,3.1651449489859824,3.3311201439326776,3.652595891037357,3.4163315098506137,3.1962746916427345,3.208035203220414,3.6253431794372033,3.4593833910456078,3.53141113001428,3.750990302110725,3.7287845039746492,4.353477405266698,4.006166154483629,4.018233509441289,4.09393330093373,4.052862361763065,4.1522960392771315,4.224963793414737,4.204355681139259,4.209490302482417,4.329584996981548,4.6284799914375085,4.421645806187672,4.504032915110596,};
		distribution.add(arrivals1);
		distribution.add(departures1);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals2 = {0.0,0.03286051874622791,0.05116647650118578,0.09207735167727227,0.1687298972650298,0.17705050738438669,0.2399390924653525,0.38134496532799833,0.40317463394288033,0.4399204118631982,0.6744239863050798,0.690732721833435,0.7026152141103111,0.7172048710479593,0.7358560713647093,0.7571693606131978,0.7695181479252948,0.8775403373270083,0.9247421247125639,0.9265407640213855,1.0398632317618535,1.0902581353606045,1.132953197317158,1.2128777580487309,1.2189055537795057,1.2936323587787388,1.314253707196049,1.3473832002705728,1.4319645706966646,1.66461723772182,1.7348553008930316,1.7586588730702357,1.794881743464545,1.7997108633408712,1.8034814711275753,1.8099371373747202,1.8219785813254026,1.8279860171381304,1.8725613625373478,1.9342264306599244,1.9583778649467398,1.9713683421253856,2.0856249143016776,2.115015961805754,2.1514935565603444,2.15569058682736,2.1599477092775388,2.1775207632359987,2.1787862856160944,2.2026019246930164,2.228429468439204,2.248703737133841,2.288585743287664,2.289217090181834,2.3271007458456228,2.337532542712396,2.341527420515558,2.3576471970394333,2.360207072284438,2.4372334094036576,2.5269678789859578,2.5306898843712347,2.5566742851126496,2.558991601684186,2.577173762149233,2.6121493176398993,2.641300868346053,2.645372779894501,2.821411671406395,2.9460665632187415,2.9757359862734067,3.0035247567083316,3.0130796013016172,3.022448320011476,3.0294136874467568,3.0667396479292948,3.0700657749855536,3.091841276359236,3.2118137115045173,3.238860575045219,3.296257284402634,3.3995435311268514,3.4299385006410725,3.452760236646129,3.4561141434182137,3.481370125894556,3.5638605801221144,3.649699856478999,3.755777172251064,3.786911243604171,3.9010702984470327,3.99630056973508,4.038889275705522,4.069696180004984,4.141498622845894,4.259430412683139,4.324675821132041,4.337964401360696,4.368451205821397,4.394888803793837, }; 
		double[] departures2 = {0.001233675929780193,0.17339555655468397,0.36524759972894794,0.12109810055539286,0.6593215064245261,0.2535275546439662,0.24468721665492119,0.3918368821101413,0.4925310371621658,0.6144460700579301,0.8298869328386848,0.7093262857266752,0.8978064694140679,0.965801473812,0.7502174944644667,0.7686077821917622,0.952890411386573,1.165172886911546,1.1159427637149029,1.1486947922662416,1.2323805203293716,1.1475348204538844,1.1564670626182527,1.5207112935541773,1.2267854523898634,1.3348407594640663,1.4138545989561526,1.3710571651415155,1.5713690255361565,1.892400533615976,1.7559440355449658,1.8722639312045875,2.029757824665019,2.0182105047265297,2.2596480955506375,1.8397063876234576,1.9147827138653994,1.8458248865909317,1.8769482911376842,1.9454127420186864,2.0819488954188317,2.23274384610745,2.193596440551877,2.145530979421792,2.3161834232838516,2.187179958181743,2.338544607871708,2.3021963899372526,2.295407582946576,2.238144320801446,2.3555679209743006,2.306650138397072,2.37632577468196,2.3201784961313274,2.3691241480277077,2.478753452994782,2.3540653664839324,2.4732197830178038,2.406868654251542,2.6047068031849383,2.552495538256084,2.5401150546454243,2.709561673368682,2.9153378766930684,2.622699559625592,2.645180087629672,3.0490238793445053,2.6686955708902316,2.848500414816529,2.9643506050869473,2.9821671435139145,3.1255363516453234,3.1568710681696963,3.061374164255923,3.0778479748075322,3.0742675919289773,3.169854605224691,3.1878826563999545,3.2222199185510165,3.305611978669467,3.4264508690874704,3.517349235380985,3.439385909193824,3.4955047259463714,3.649903378733016,3.4817642444944124,3.641597243351862,3.798419875213418,3.840008613839904,4.110392567519053,3.9063592899329453,4.029471620865308,4.086441035104306,4.131283226570806,4.160970374428437,4.495526505247744,4.335290869449989,4.4380441293792625,4.379704652431333,4.557519372987262,};
		distribution.add(arrivals2);
		distribution.add(departures2);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals3 = {0.0,0.07751164301132003,0.10677393432522182,0.17013892600503472,0.208406329246974,0.22150321034690043,0.44705378286186315,0.4876747364041275,0.5239228037113639,0.6213962462590696,0.6273036333099843,0.6321725126378257,0.724346058224407,0.8712276307823323,0.9475688168615417,0.9619223804457006,0.9833827927660852,1.0250373302070979,1.1426562203586141,1.2270887035802802,1.242628853178931,1.3657326901012055,1.368793371764238,1.3969194519915022,1.4133222073103118,1.4466911820343475,1.4699973054499293,1.4796289215996146,1.5575535600881054,1.5746135148658758,1.6205879085977102,1.6473464743752784,1.7232486865294785,1.7526647876358548,1.826639692224146,1.8445188327790105,1.8463815964291361,1.891226145131144,1.8951353898180636,1.9925969212016448,2.004102491135149,2.0309214544211733,2.0366910887399126,2.135987321881234,2.2192210290777212,2.274112394360371,2.2931246703599126,2.4019944481623723,2.4423241202497947,2.5400140484118414,2.818897384128271,2.861627268940977,2.8639755833003195,2.87442178239505,2.8772476229410904,2.9636889525570513,2.9652176541098987,3.075376259921327,3.1389453905471316,3.1642284597586467,3.1921732839848103,3.2092971545308417,3.226351026950457,3.2395072916513956,3.2943204858270723,3.2985544826107756,3.316487959216405,3.335682105596699,3.3468696192065663,3.3650584939487755,3.4576568639409913,3.490256694133956,3.5119847598169174,3.5127948893540943,3.5362999157166315,3.550625748044334,3.568195197863038,3.6155471434251942,3.6202120442055254,3.6683310832601523,3.710262012970233,3.711283072019625,3.727821393435901,3.874598917388294,4.001453629284885,4.149986037929234,4.169899443840399,4.188579067600545,4.279497981334254,4.353559337535186,4.353601039429155,4.35487887541471,4.355577674076636,4.386194572871142,4.543914223907367,4.5886766387794555,4.633551749201848,4.699470682827946,4.718718419844767,4.829884280629171, }; 
		double[] departures3 = {0.39366921340642264,0.24188672796539723,0.7162281797562284,0.2568485835292501,0.3971328249231575,0.24659336930304374,0.6522631534981267,0.550547824280566,0.5873601650797737,0.6285129706598654,0.6291120254130409,0.8707771437370427,0.8034595961872254,0.9145932752827554,1.3575311876941427,0.986177456208675,1.0464316293816718,1.352007386293258,1.1638521862116242,1.431599410370789,1.3582212822717963,1.4914672866130676,1.3803314362966013,1.4803592285567349,1.4661309170438281,1.4474324600350499,1.5176330367583162,1.5131113800560385,1.6805421352608052,1.6948896884459599,1.6523230016768848,1.6770442836203667,1.851921856421227,1.9892067772963704,1.9563714746209342,1.9475423579013083,1.9983405234769678,2.023673678199103,2.3104671594974473,2.039999483849064,2.0926382248694937,2.0825090345501205,2.0956018882331904,2.194711839102009,2.306544401694924,2.334927733206159,2.4634814962520264,2.657002538314763,2.5347345315586587,2.5604321138346364,2.9081687998259382,2.9287514028257084,3.0294341287443602,2.8750153250955393,2.9537442269740644,3.0378432097263706,2.9955774912034316,3.0829903752264385,3.285040119199812,3.3729204133660846,3.242901624177396,3.2245237753280094,3.254374863326613,3.303358415827602,3.5218670532935894,3.5136825337213935,3.4028511607738485,3.5531633003797802,3.3895610775691782,3.560783322229282,3.5151717700905314,3.5389357257850462,3.601477465602603,3.513492834010179,3.5626277381677154,3.698683124938981,3.621819835952401,3.6604807696695043,3.65966545936643,3.671120918658635,3.7374308758571093,3.7194610424148444,3.7363333992478958,3.971253020495507,4.173733007067735,4.2016655267766545,4.307007410237691,4.311050571546666,4.393036696854554,4.709251361207905,4.568164435089306,4.665981946327751,4.407693253056066,4.4294513460893485,4.77085872414119,4.65776182072379,4.704335213783938,4.881518775958598,4.758662355712189,4.928582098481781,};
		distribution.add(arrivals3);
		distribution.add(departures3);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		
		double[] arrivals4 = {0.0,0.10519632149087628,0.21711398312164548,0.2432537035967259,0.28526298044418763,0.287980457548886,0.48724143148578114,0.5505280604871523,0.5551887475396137,0.5718252586312015,0.599762250351449,0.6376175712014815,0.6483274327545421,0.7051241146511572,0.7076514939441554,0.7163349535679879,0.7235147809183773,0.7557101368969328,0.8062594076447952,0.8531606942958052,0.8956035344238615,0.9323168821512048,0.9493926100735539,0.9511342085201897,1.1337226671938556,1.1521488993567714,1.173328746701061,1.1844452487252675,1.2114255750966019,1.2979773249509046,1.3309439299056076,1.3590684775954014,1.3827689115231134,1.468304655450156,1.492535674257011,1.5435226440727177,1.603564133526119,1.6670900421849535,1.7560072384714789,1.8331121668990311,1.8801980903536313,1.9071898691115652,1.9589242141026793,2.0215377444200815,2.0318351895609648,2.081755342746196,2.2017114951173267,2.2274768127795217,2.3324644984517517,2.4132302454824925,2.5134324613843213,2.552969774836355,2.569983064048871,2.6088579256757085,2.6825374763814644,2.7192921296751553,2.7536868641894436,2.8014087084850137,2.8292817728183874,2.882454446930066,2.9023866125770033,3.00656865213429,3.051924387876226,3.0916788686413903,3.1460350771480834,3.16602011954498,3.2476821446612334,3.2906271051570535,3.3190224262243553,3.3252443477420344,3.342015877347403,3.4021659812249876,3.419247840298111,3.496957425177848,3.5624943671750553,3.5680154825969557,3.576893541204045,3.612096326211334,3.612142887190495,3.6693038697120874,3.695325618355289,3.7107662803335835,3.7673352972411536,3.7704482715461074,3.844658297489806,3.8819754503402475,3.9860621950959314,3.9965961677537925,4.097001081501319,4.168226743775881,4.269104574792749,4.316996825057573,4.363972962894612,4.367921064582284,4.4673677583411004,4.547339151847712,4.632685133856722,4.6798046648629406,4.745777725973733,4.780184563530095, }; 
		double[] departures4 = {0.04712142884031044,0.18019308730181255,0.22042429255121246,0.458539786734277,0.40771278219144536,0.3173407544871461,0.5342440641809868,0.5885078031064234,0.6761635687689813,0.5776168076318062,0.6584631260902016,0.6616709863096342,0.6845603930551654,0.8590082440274475,0.8736203621146187,0.73536975844414,0.8253235387313669,0.9586738551476737,1.1320463665736942,1.0117670892029953,1.031228425876926,1.251580475510768,0.959184902119178,1.0033513157238965,1.1478006858015604,1.2044794564331112,1.2878193333362271,1.2983618049206533,1.5394142327748206,1.4255863995731481,1.392294237719992,1.369624799443786,1.4308272789206098,1.550478207298735,1.519837742519597,1.6089920419828232,1.8717323048318026,1.8328861043477747,1.8987130339442528,1.9395860909330087,2.2091300957075104,2.1518983371135723,2.1194658934624484,2.048989830147263,2.1270161314499503,2.125735013266884,2.3267864565775023,2.233411822021161,2.618357184296166,2.414566239610118,2.8863870069421362,2.6176187409408596,2.804540928092263,2.6186265421872505,2.7219611199149343,3.097172385572107,2.808929167112451,2.9300350554414725,3.0151652273340352,2.9663113567373545,2.9633180186864614,3.078602292616546,3.094305498139855,3.0941946375518907,3.2055994214683885,3.415596647211104,3.2736949314211183,3.2961115312586404,3.451606388220121,3.339797769538682,3.509939645716173,3.4591601575666213,3.442960436479009,3.926377341786838,3.671942341884287,3.670383514556795,3.968627531397226,3.6738197849188263,3.7427227999254202,3.7954559917901705,3.6973889090509706,4.182291302045921,3.9607093427762554,3.9367063702316445,3.857863413491587,3.9053940567837198,4.06112357278191,4.059150497609415,4.143589363205759,4.291868472887209,4.281301526895314,4.490096387122965,4.72680603092774,4.370768382874201,4.5282094563181,4.608964539314718,4.887369902528193,4.724858469099533,4.7635994126508425,4.90239443404041,};
		distribution.add(arrivals4);
		distribution.add(departures4);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		//load 4
		 double[] arrivals5 = {0.0,0.018788027939583927,0.05136950188290766,0.06110123125291243,0.09466712288003098,0.0995242638150107,0.10465627227366138,0.1381241701615252,0.13840610890821514,0.33914600721291666,0.3681564495534047,0.372220445907981,0.44063315028914585,0.44196754060959065,0.47189891778831666,0.4791224169123588,0.48778079402926866,0.5333578716225703,0.6117039000448821,0.6417249517644039,0.6461740274158517,0.7009536359714209,0.7348222099399189,0.7398040411409011,0.787890132814574,0.794252266804478,0.8149718919148126,0.8283240474146355,0.9854455994006986,0.9946267215467773,1.010250913121901,1.0788044328031268,1.0902802783297363,1.0959808092843848,1.122645468821874,1.2098318782214446,1.235608571666245,1.2363675241911773,1.2445661341142922,1.2646274585628354,1.2806375405530466,1.292233914766131,1.3441301831866714,1.350003185139612,1.362069288252996,1.4426006221933236,1.4496506331152872,1.476920597279786,1.4819129883205076,1.5134881595184957,1.5211498469924212,1.593836414473709,1.5978695831769463,1.6132516678405486,1.6147553452843881,1.6174871358475065,1.6517540909679094,1.6760200435367871,1.700053443864454,1.763982058006024,1.7765938823964367,1.8313311897800166,1.8360839270739064,1.8407925729663386,1.9013300910063555,1.9106933029411883,1.9329796419623786,1.9430567545439998,1.9845552400079716,2.0019914417522697,2.009431354284528,2.017246323831805,2.0305356318700496,2.0508955702865057,2.0713974650140043,2.07633567434925,2.1050892487429773,2.1180747248946106,2.138170261480746,2.1566973298564256,2.157227417670611,2.204906139614281,2.2076768619679474,2.229796742154524,2.2315574577496045,2.2461236648350713,2.291511568779688,2.3040634358925445,2.3127782113684927,2.313371452600043,2.3144945607930345,2.3460110503726055,2.347065831697738,2.3789882773479643,2.3970341929214833,2.446215409072801,2.466129432681315,2.5149958533458405,2.5240481598035065,2.539243107766843, }; 
		 double[] departures5 = {0.010627642257986385,0.046530307824366304,0.14432390201389964,0.0694739086741092,0.16857544586646164,0.31418623920120925,0.3664798606629469,0.15998543890379976,0.2804959893064469,0.6282866147706475,0.5008010714916725,0.5376039724652191,0.6095301228139067,0.48470623570641114,0.48811874577563913,0.5015772455605522,0.5702504563781153,0.7108317955558475,0.7969845777744016,0.7213865193106728,0.7661190744063664,0.7545613908596356,0.7825610526618334,0.7746237061689819,0.988648602329402,0.7970300960307075,0.899278782407422,0.9412510191978654,1.274482083778826,1.019083873068614,1.0327043017970317,1.0802597219781633,1.2272501024960212,1.1908563219401571,1.148418116358958,1.213995964263237,1.47291537317741,1.2435110430546028,1.5153987907563689,1.2681590659364335,1.4858049304103236,1.3492402855658572,1.378403229745404,1.5434731923139204,1.4512267968518235,1.52806600984467,1.5045619250057116,1.4829274582002852,1.5555982448397287,1.5689333049696792,1.655026121870303,1.7425995627005455,1.6052598702450394,1.7487845890390545,1.6654105587522312,1.7230753093763478,1.8163270422479056,1.706857415436765,1.8136113988507654,1.7778380786665933,1.7950593427229968,1.9858456745068702,2.0231710171408204,1.8679394372585179,1.9907515831832088,1.9318579461619187,2.257477350931113,2.104428773031738,2.072013299358955,2.1330385137394745,2.3148422280928265,2.1792265745176005,2.1208029396309658,2.1076567147628573,2.082163313925142,2.0880173235547006,2.1244218162308672,2.2018428452552707,2.144492408930486,2.1778586807926685,2.316663934989351,2.2205010842359494,2.3933762271272796,2.3125719181516278,2.3161722431726495,2.347337530868364,2.515304252109095,2.3169293194390415,2.368172387200169,2.467459489480515,2.623121548019442,2.36028440118123,2.5664450794800233,2.651411993625627,2.602796755210556,2.480403832522446,2.56394118312705,2.6660188284170183,2.605021806487365,2.5543828815630745,};
		distribution.add(arrivals5);
		distribution.add(departures5);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
		
		 double[] arrivals6 = {0.0,0.03211987062781748,0.08082338853151244,0.08392258122617348,0.08981879662168657,0.09291020603547351,0.16868244931455278,0.17691821961585494,0.18906748396811893,0.2760207835313041,0.28513926999151323,0.2871121598271143,0.3093430343636825,0.3397470087421111,0.34311926914273067,0.3455682657709598,0.367984924989476,0.3918602908048445,0.4468703053700408,0.4778097900023601,0.4965157583872565,0.547176607438671,0.5872124473874617,0.5888497906963086,0.5971599689201804,0.6473426754549417,0.6756460791992234,0.7236705119286019,0.7442950703307513,0.7451585370625698,0.7636583128727651,0.7952554659081094,0.8068192442324619,0.8407740307887032,0.849500769218249,0.8986699685355408,0.903164481138983,0.9228788371385305,0.9309500867466897,0.9475176690661492,0.972456676730434,0.9777569004749466,1.0607834116766865,1.101929709156344,1.1222221210089887,1.1396125944593476,1.1436680369077552,1.2106405124151227,1.2430217458966886,1.2448503799492157,1.246189388682509,1.2711907571859566,1.2852638995971277,1.320953625666258,1.3521240720883463,1.3557442368770907,1.3684091209405227,1.41032282049313,1.449548343744003,1.505482941204407,1.5429298381604317,1.554648205977335,1.5717895868719136,1.5786735687043572,1.6146612583897313,1.6557403374120196,1.656804105940535,1.70119510777233,1.7082207422311815,1.7093764180004858,1.7205847594270003,1.7248903816802819,1.7366496579823798,1.7689481504612903,1.7714396736848936,1.8643690315955843,1.882103517972879,1.938159124999594,1.9452430042338227,1.955445454239589,1.984102951096923,1.9841320073586448,2.0107607928509457,2.042966013163816,2.076923987332744,2.12706705168276,2.1279902993056643,2.1540479567148485,2.1851929084391783,2.2012439124910568,2.2066093255227432,2.2078533821162574,2.248827193745417,2.2648354947156015,2.351002287950937,2.3751898695032487,2.3811619015453025,2.444443770801924,2.5147472687787724,2.567638131331336, }; 
		 double[] departures6 = {0.2036295589486045,0.10430994923262424,0.10512096863825918,0.14997999929006983,0.5071220208513206,0.32917698773787596,0.27143852122283935,0.19637561903628736,0.2030279584181754,0.299452913179958,0.3837906401077434,0.2921433917617038,0.3364414765706929,0.5781216308994048,0.5031854356716252,0.39203365855991945,0.37368990208875974,0.42101504517916083,0.618762763045458,1.160863792767526,0.496605867552093,0.6000565266375316,0.6105586698620119,0.7327093903654571,0.6075849222095482,0.8266464367793176,0.7411059564394887,0.8082884925087892,0.8610269350923979,0.8454045111401665,0.7796758179404273,0.8443448733950555,0.8367049894172387,0.8549693677949896,0.856085544757715,0.9559301463772175,0.975215149870779,0.9231835050056417,1.1318183177145682,1.035089117239569,1.0232396346144093,1.1497072309475742,1.1420465017342945,1.175664879268289,1.3145735457174967,1.3860564271593663,1.2988065826900308,1.2685027979838503,1.361551551953508,1.2723330202907326,1.2630749159833743,1.3683923611627404,1.331895347369909,1.3764425823826194,1.3812254993386628,1.388624628543385,1.6130486729687494,1.4318634964415073,1.5033952865501938,1.6255819443413062,1.7585055077774097,1.5653420455201623,1.8553118595459976,1.5898311073242009,1.6828722043151858,1.9130899502578869,1.6609547832777218,1.9023366668785782,1.7694155243914556,1.7618194360081567,1.7788533130114152,1.7791745080262913,1.9374590414752153,1.8096655626116414,1.7819786485700941,1.9817837915751053,1.9406019064893285,2.0261191482723797,1.9545340702323772,2.017778299273466,1.9964626128409264,2.1745062717253987,2.0189004582758585,2.177539063396895,2.0794245560633553,2.290773119591576,2.1859936509636193,2.2379987302875417,2.1900306708593145,2.2665415615532343,2.315504780015953,2.220785272753691,2.3299004822162788,2.3726661328389493,2.4007630366419046,2.382678696709249,2.520973197099528,2.550651708675113,2.653023041010829,2.5958201831908454,};
		distribution.add(arrivals6);
		distribution.add(departures6);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals7 = {0.0,0.0046080967029696945,0.016000543155189992,0.018422698145895453,0.04804946100028014,0.08305339227315992,0.09527618272929461,0.10873441576988647,0.14680049221625502,0.1483007548744663,0.15938106583734246,0.1729360242736952,0.22591293873943127,0.31144478102268386,0.37346516659304285,0.37560518074799265,0.3914379795375217,0.4066729797687915,0.4167214261097705,0.42316471911114606,0.4597200905038499,0.4718472006783536,0.47751842032904096,0.5255697776719079,0.525968676334038,0.5333074934681336,0.650534636088137,0.6792556421882732,0.7596807041422332,0.795458462689185,0.8279497747385475,0.8391326216454681,0.8530417272107945,0.8613614605218337,1.0277918515288815,1.041636265260363,1.0639406094913006,1.076744249615088,1.0825423956261961,1.0952887829376976,1.148963878826272,1.1873822435807448,1.210607204516484,1.223697021813589,1.24015321018282,1.274542285003349,1.2896140122163282,1.2949343718243629,1.2966463493432987,1.3740146250107048,1.3779626272570138,1.3799638271970076,1.4385910693486301,1.5212207231045984,1.5481203122770173,1.5492601949127185,1.5722560636320546,1.6098860515648514,1.619786190406339,1.7236133483683336,1.7436529602316375,1.7656602954468599,1.796237457853056,1.8029182162170678,1.850407777739116,1.8652796448727325,1.8691409107605765,1.8955603575401405,1.9077888681522732,1.9221578473673473,1.9244984668054705,1.946691038202428,1.9835668727058442,2.002830992997487,2.008061832107139,2.0547699161082433,2.0854791330954128,2.0994140051831875,2.1228447526692826,2.2208544677304087,2.2294279969663076,2.273519217083793,2.3609741567923987,2.370094497442864,2.3774360427051393,2.4029526488241717,2.460407229256712,2.5006316998479154,2.521266916771343,2.530541634439878,2.5582121404833744,2.5770056853055894,2.5882841499382163,2.595610309655033,2.607304006519861,2.619483217100489,2.6456924616005764,2.652489196424696,2.6769174455866453,2.684432472196916, }; 
		double[] departures7 = {0.044267676574584726,0.016665463353748543,0.2868461682316683,0.04349599056327372,0.1646938915725008,0.12818798207087845,0.14769556881684753,0.11072601761472416,0.26109224732070113,0.1980718077441111,0.2051668347869638,0.2862360110569174,0.3712512974769966,0.47232127742834057,0.571311056608074,0.43381192820213627,0.6243268988384584,0.5590473553965665,0.4955142686703747,0.60791101949149,0.5280917108597507,0.5325217940709622,0.6612156068239137,0.7140764487693613,0.6070977727225022,0.7751008631130256,0.6865319742042846,0.7379408350752058,0.7947591712367794,0.825486717888256,0.8395870325315737,0.8544138743860185,0.908305863082418,1.282157609466357,1.1328082420131187,1.0952689434860088,1.1795803333285597,1.2029813459691443,1.9065704769089653,1.337728273387913,1.3471167062508127,1.1887672090054933,1.2116159519616134,1.2610172279943734,1.3512401317171039,1.441889777669035,1.2921823058436477,1.3261530469620049,1.3388489637508758,1.6397947140494509,1.4376492059350436,1.482203846071786,1.4832114561692373,1.687264737114658,1.6277204368796623,1.869127846414839,1.7384034365974186,1.797395473529668,1.7309604918361854,1.7527571095727639,1.7970157965415117,1.7695788425208538,1.834556397469945,1.8091188390154778,1.978254141735238,2.0272488146555796,1.879474428326262,1.9025599133392803,2.027261218761714,1.9272304630727355,1.9287695202455895,2.0650829724305164,2.0815188171841426,2.0853460930563474,2.0319521855165363,2.0974116401275635,2.1088093300428974,2.1544303562452773,2.1454736274765747,2.352852228967654,2.2673407242444155,2.317689365581415,2.400667881860598,2.450242940818048,2.379902747851654,2.768096260439509,2.4692094750185687,2.534149487613443,2.5911890231292456,2.7100595809203214,2.58489837439396,2.6338437627744096,2.701245772877872,2.615912006453622,2.6691556660426317,2.931332063103343,2.6575079132095216,3.104668077971461,2.7732286396939982,2.760681051517249,};
		distribution.add(arrivals7);
		distribution.add(departures7);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals8 = {0.0,0.116907994146001,0.12281237359178875,0.13764332000485047,0.13883684752175982,0.18174078011423545,0.18836531191508327,0.20265032672560268,0.208401071047663,0.21076494098493634,0.23233958824604176,0.39629817840609716,0.398335107360447,0.42800364155531895,0.4567781556201949,0.4935734494558318,0.5761712032871812,0.5869070659079111,0.5883102500194312,0.6024429905801455,0.6435176928989372,0.6732645429076328,0.688234768083536,0.7988089135533409,0.8269115734338781,0.8478835089404992,0.864057275689998,0.9537861814758548,0.9547494931937787,0.9877118728136574,1.0109893552770004,1.0412826433107065,1.08520558945939,1.1187762801561543,1.133093094676959,1.1344239313810283,1.1416470127508835,1.1782052587900957,1.2489246583393363,1.2579298434280608,1.330154923889442,1.3348754708304418,1.3632961185624102,1.3726765604176328,1.3758619831294663,1.382074282202743,1.4047785565476205,1.4337156363711376,1.476221672676982,1.5087942397127512,1.532823996672724,1.5353396939540482,1.5396261472422972,1.5542720012378228,1.589741887563989,1.6140775260401705,1.617929629128225,1.6272752981725938,1.6607889618070655,1.681428523024181,1.6844065263040748,1.6850945190297892,1.7790536155581895,1.8359610190933873,1.8551990732206405,1.861474677263952,1.8776046531085764,1.8996584388744258,1.920825419286839,1.9410611768612955,1.9490966768651963,1.9551131429529855,1.968202869694505,1.9717901636160808,1.978543503058574,2.0931620073683597,2.0979737074090488,2.1240747613033957,2.146984944437298,2.172493141120418,2.180410566991668,2.192027572093389,2.226715330083277,2.250141813517676,2.299813000275474,2.3160978730015414,2.332200382936447,2.3364875260736846,2.375966061374775,2.410164623445006,2.4412824456665594,2.4427555980654607,2.4719245672241796,2.491017107462912,2.5307894922541356,2.555023718873064,2.5585910923844404,2.578136495285387,2.6295770425483957,2.6935883278061787, }; 
		double[] departures8 = {0.11470009858850293,0.13390856541834356,0.16266290645338885,0.1465220094938176,0.173885057029218,0.3839892201007812,0.32523950810064856,0.22524877929918627,0.34159148829561775,0.3016241573257788,0.23380024199968635,0.4157106881199134,0.747162841246094,0.4576913442103646,0.5172542719229205,0.5869548962406818,0.6641318442284305,0.8605694745743755,0.5892616227578282,0.6064804801536681,0.6573884821228969,0.7738910622247909,0.8087620870497758,0.8040073028147339,0.8975568491980533,0.874973573082311,0.8679619449169773,1.3103166525213434,1.0383381808042946,1.1312255635359634,1.1135742107792428,1.1165261407492437,1.2167620123335967,1.213391177612588,1.165398328793639,1.237570741981266,1.3718540130635009,1.2296853247743795,1.2854884234720239,1.4524685570163964,1.3595021986667075,1.4887833193916922,1.4247242524616224,1.5339346695377398,1.5096189470609758,1.4063891229842167,1.5069754383993972,1.558808983286492,1.6089387216449291,1.5398821193166574,1.5400887756335147,1.555105386137729,1.6957678234568383,1.7327225505691235,1.7566441499556036,1.795328337472861,1.681224495272161,1.7007036084135554,2.021898457218078,1.716720882288448,1.806920819687964,1.9636783920386365,2.019706248591923,1.9333104521495692,1.8842521111411112,1.8839762230212032,1.8868199269142827,1.9583079980606317,1.9388197160613996,1.9830439370333346,1.9690766489843115,2.179453759351179,2.021207940275957,2.191442378110318,2.010152781698163,2.1323234124837875,2.199027996717687,2.162659557615817,2.31382579623647,2.1833518253583333,2.219798517478592,2.3470236715720776,2.2889769293167506,2.3041992644553435,2.320415001992296,2.3268870836742424,2.6931892958777057,2.4500390984160356,2.4244723043613434,2.5348035736652688,2.4987154082344007,2.4616631590386127,2.6120116088610277,2.5088791199129616,2.5813124416847377,2.668989485037125,2.6431900702276487,2.5937658389143454,2.6346674433017103,2.81444619528365,};
		distribution.add(arrivals8);
		distribution.add(departures8);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals9 = {0.0,0.005957719823750138,0.010807029771347686,0.034530491578478664,0.050960273700540265,0.06144461063923763,0.09433708752559031,0.09688407266043329,0.11002092002325617,0.12435616382474765,0.143646074481336,0.1551142123232735,0.15638823937115803,0.16110392552308953,0.2402768288787095,0.29889461952236185,0.36383626287648246,0.3907029083947641,0.39587962265064347,0.4402707652465695,0.47927042668626796,0.5344633526602323,0.5578388949291345,0.5996482834998789,0.6119585427367942,0.6332572198557352,0.6438323329654341,0.6876513196884299,0.7127013935422766,0.7322242517054,0.7335162735796079,0.776620923690436,0.789126670500054,0.8290628397490002,0.843897942384465,0.8546180406086068,0.9193322616808334,0.9470721123031647,0.9634703097424531,0.9908005458471966,0.9930076349418582,1.0193194608158935,1.0791770398493687,1.0898071023853815,1.0947497983201957,1.1317205003351865,1.1402293626827649,1.1407196364917904,1.172894922654048,1.2792694553684376,1.2803955702527472,1.357181346887682,1.359974067110134,1.4180679527461373,1.421614556203787,1.422815202551443,1.4316287952578965,1.4479895267102465,1.5052258648655543,1.5202546985541656,1.5225544667457638,1.5340042397445504,1.5550476568013778,1.5559352920202074,1.6125371382397733,1.629321734898465,1.6349538403269956,1.6357663075908935,1.6393841766590695,1.6465396976579119,1.6627241010043292,1.6674163584918922,1.675185022225141,1.6805762839179585,1.7143346018054872,1.772978859457736,1.7908711968004036,1.8245008754058507,1.8249295601916784,1.8975197268402615,1.9100625801426419,1.9233577891953153,1.9265600793641227,1.9440293765111427,1.9838044455747417,1.9842470167192638,2.0001124276604396,2.0112714428862604,2.0210622174188915,2.0211942574897064,2.0245002455375327,2.0397741599326404,2.1577015373022026,2.178503086090232,2.2526409248912738,2.253700999274145,2.276869520533077,2.2889013152189057,2.3247858722912667,2.350252146709818, }; 
		double[] departures9 = {0.02306449062246591,0.13410293330862705,0.06978560846779662,0.25997578011087263,0.09710495214278783,0.1619275943288948,0.09693157739643993,0.1136902133012262,0.20084047102197283,0.34569630003211615,0.24043971405197218,0.1695486005377247,0.20254351797599232,0.2669959585975614,0.29971950928264224,0.31880109681145596,0.9185648006265588,0.4280389838336733,0.4097559515042005,0.48693831621100764,0.5267298724862237,0.5943417367181864,0.7716112683866627,0.7339694955755615,1.0540214222641702,0.654529736123649,0.719861531987398,0.7459979324710924,0.9376426854477479,0.7795636477130796,0.8396052233646157,0.8204808121939443,1.0035402928285098,1.005387184901906,1.0778072466940312,0.9605154700904476,0.9269651152329842,0.9950884660716314,1.4546141834541455,1.1408934938050301,1.1856911448035186,1.0343880607869727,1.14793518368448,1.2762444440596978,1.1879583818316033,1.2102959920052134,1.1869760460288077,1.1702809006254833,1.316864516716429,1.4494611918912752,1.3248752518565554,1.628739595289015,1.3701260918027918,1.520812616645473,1.4656238406287896,1.4811770178034087,1.4452454751592771,1.5886540000527556,1.522121681633557,1.656068026477278,1.7073293188306966,1.5843511482569386,1.7641290726655172,1.7224544027454838,1.617523137827664,1.6587559281341566,2.0279581301364384,1.8980894310357153,1.6416280423507954,2.016109311054601,1.7418207975593318,1.7344407916163314,1.7357111412222428,1.7074561883899415,1.7589535079574112,1.8327142025997731,1.7986374422656588,1.9123703438089004,1.9139221038693215,1.9505496418894477,2.0731705977234425,1.9454057002736607,1.937551229142908,1.977653093672266,2.0706090798955405,2.06226829989534,2.084849201594497,2.2436091861282894,2.0740923857047844,2.033088176683766,2.02972488868508,2.243060959099612,2.1776554770080145,2.2026230946205647,2.2639627266439226,2.6692872587441654,2.4081461306773853,2.3864469558612487,2.3372116194116797,2.372385101781995,};
		distribution.add(arrivals9);
		distribution.add(departures9);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		// load 6
		double[] arrivals10 = {0.0,0.021050230052749135,0.042516609253046116,0.04952910010715816,0.06811472528531996,0.07695228476833951,0.08970409066274981,0.17898217374334063,0.19101003430121435,0.2012637704391286,0.21353635456377937,0.21933960392494264,0.24034896395990496,0.24049059658972632,0.24788932506858743,0.250844285160494,0.26618476434504623,0.3015572535000535,0.308977115911686,0.3216817843654283,0.3299579512593299,0.3569219792107627,0.37538768054057026,0.38100637867202775,0.42053305586424117,0.4280781614641154,0.5041168736724284,0.5557641064813842,0.5621372558990105,0.5831640722800698,0.5869823323171022,0.6039129103734382,0.6201537439101733,0.6252328596159112,0.632017997901691,0.6580497032592539,0.6764965179872724,0.688619256735748,0.7021460729173002,0.7094854093347438,0.7314056682224006,0.7380379409997084,0.765766806807634,0.8140663485774965,0.8368793627687849,0.8787410159613739,0.8832853327216524,0.913969217559467,0.9172449623923824,0.9175811619538604,0.9269531713059801,0.9290069402509624,0.9480109502022936,0.9517873878615608,0.9582746678972783,0.9671834799429297,0.9721154300919677,0.9836425624238299,0.9942579354128633,1.0004662539681946,1.054491311751468,1.0619220312336302,1.1105063860988198,1.1635389183064748,1.1708964291989954,1.2156439205301264,1.2190774947114267,1.2522328258657538,1.2558762631114386,1.2747106012178955,1.303904220876758,1.322452861859348,1.3337258327536077,1.3410376626529983,1.3593464916470377,1.361360813091557,1.3623139250236946,1.3932476669231906,1.3956016980382464,1.4083265619790817,1.4182468317535126,1.4510277181137707,1.4660502051927726,1.4812612203242765,1.4928574189114794,1.496542374207478,1.5234143514991882,1.5441731715100275,1.5468735263674518,1.5498571570628343,1.5535242337374022,1.583608131718035,1.5911545855513,1.607084943832067,1.6225674185829684,1.6473650060591822,1.665559116880172,1.6949665226230073,1.70065396514273,1.719012990038261, }; 
		double[] departures10 = {0.05888271043671313,0.2510202878234317,0.07870375817937925,0.11162064153206462,0.0709077723800654,0.234154440715471,0.09718531217465097,0.21952811831163488,0.22067938728313827,0.2380738766574164,0.2854875164620978,0.2973285480112756,0.24416983680333992,0.30715325698054163,0.34688803226545534,0.5450634520943121,0.3196975292353017,0.30432296157059874,0.34211427173706016,0.3693080913439312,0.3785115573924945,0.3586929493532941,0.38568472121089153,0.6996626154857939,0.4894391379866082,0.5425316535304578,0.7568892609102849,0.5753983215252785,0.5625880051751534,0.6148951458167358,0.5937369674454995,0.7032793545231729,0.8249483485248792,0.6476343932372829,1.132150770810561,0.7526414053364227,0.858529963864607,0.6891580474426916,0.7662604195613142,0.7133635554765064,0.7904139759329248,1.356389823995083,0.8570419102353674,0.8151490720079582,0.8688046887223534,1.0518643745240808,1.0640771408337943,0.9614247066394244,1.283133823430759,1.1027084187838319,0.9337740660811538,0.9445295650189403,0.9528024233746027,0.9634628473414714,0.966359911566212,0.9960515947272871,1.2934990814386558,1.1687204130755822,1.01935053803176,1.0325859720864727,1.146355210676484,1.072037869390437,1.2624027592320306,1.2946496169721615,1.1900453645168296,1.4017837843626102,1.331681786550495,1.5251199402356261,1.2967839380689592,1.4071196592373,1.3511677433305929,1.4449021971624465,1.4042602941586293,1.3982806280877733,1.4291710420505819,1.3952516274340705,1.7328350456287167,1.3995055403937187,1.4984274786638947,1.6959831292077099,1.4768310187079912,1.5329766702668939,1.4898973569039418,1.4965628852571369,1.5439336395549665,1.5121326723517134,1.6928984101756042,1.6061280306790149,1.8027973253749991,1.6308100034049193,2.0458162630831684,1.664462379302861,1.7349835661767468,1.7081907260766325,1.7278985959998372,1.6684927848285938,1.7433581515875252,1.7199400527407267,1.9537266156353963,1.8039656097655627,};
		distribution.add(arrivals10);
		distribution.add(departures10);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
		
		double[] arrivals11 = {0.0,0.008075840211593854,0.020527696718955345,0.060332789969099715,0.07334327696695292,0.0735153436864441,0.09891928598139003,0.1038363666280385,0.11952070093778525,0.18913422935049928,0.22400775964086808,0.22411504287194378,0.22845189386836276,0.23660534736849942,0.24558898398614523,0.2810909899312267,0.3205332725754195,0.3267407779788666,0.3701613822354588,0.40463713742303614,0.4157221804898068,0.43775751881289704,0.4581292939047353,0.47441732727976754,0.4749013088273118,0.49604430516780595,0.5080412762160409,0.5283970052742134,0.551093948677453,0.5898615839938964,0.590713959825863,0.599730920503988,0.6102900407964201,0.6218629408020653,0.62256537296435,0.6434331061435064,0.6610176936181904,0.6646004701803149,0.6666747714122717,0.6948823343957887,0.7048875960682582,0.7535014857658536,0.7540787797626288,0.7886059712013519,0.7890727938349896,0.813461812220028,0.825171205730885,0.851175457473326,0.8724848152128958,0.8882051275316493,0.8912434807992256,0.8916836347262332,0.9241107157648817,0.9311027353019817,0.9603617415540225,0.9698060173699995,0.9981548501112039,1.0063434893611594,1.0076957137169582,1.0312371082020428,1.047558686493771,1.0480381616944094,1.0753298659615864,1.090768496271154,1.0928725089947362,1.0970483405087743,1.1373157367180953,1.13905796181008,1.1457436358174622,1.165426538955902,1.1678395963576502,1.197093481532383,1.2081633797791353,1.2323696672187117,1.2326269817091964,1.2547140254111853,1.2589452072335914,1.2671864430833155,1.2767385068537818,1.287068259908068,1.3223071608612167,1.411356607488818,1.4258926258448028,1.4716322636543382,1.4843104084101753,1.4914331541400323,1.5023242874285008,1.5103273133229855,1.511019743193227,1.5287120952070363,1.5312877332815087,1.5326899438168315,1.539322009543889,1.5561956673666655,1.5576483981409428,1.5582583147695053,1.5595510356956572,1.5739687259901385,1.6132794802056252,1.6141118240736951, }; 
		double[] departures11 = {0.2913547049171819,0.29411616441572963,0.11381162212140392,0.5828499196515281,0.18377149001356594,0.23110289757637129,0.18492459155248403,0.10399632434717797,0.14393761615393028,0.31279176272868414,0.3249998636745105,0.2866217423119506,0.25843829182512157,0.252761348075819,0.2677548237843402,0.30336264596191365,0.5606977116529018,0.3461764286034515,0.42442282200685905,0.41391753595017716,0.45289672882738763,0.49519512849020264,0.5015562415571346,0.7267448680945467,0.5412546057797036,0.624926676518271,0.5221800033680846,0.535281336607235,0.6354778696010286,0.749063290452011,0.6461048582238113,0.686165623213856,0.6204491315048339,0.6508819289043672,0.7336002831287968,0.6652217823785865,0.7657653322952076,0.6745606300767114,0.7091932889773361,0.7563125725435504,0.7423912184734749,0.7630306452907314,0.9168829292484979,0.8138782354451115,0.9594225800365062,0.8464435223900393,1.216193757259288,0.8623700416103227,0.9958042312284129,1.1891861616436092,0.9696874848916047,0.8990847553664731,1.1881104178628115,0.9819077824128535,0.9800813527886515,0.9920246098624637,1.10808146280204,1.1386271189326604,1.1231973662571757,1.127166500596858,1.1306791859774483,1.1111480848457758,1.1115006936105136,1.150669749419289,1.146076833070251,1.1471429704407012,1.1422124033298886,1.4782348975024233,1.2011843295989135,1.206129595685443,1.1916422643027125,1.3390628662434583,1.2323518556500657,1.253164830401492,1.3052518736511687,1.2569425066862951,1.3521360349838474,1.4218620295569697,1.3262694279248788,1.3855339268735793,1.4881829666219395,1.5244340549779531,1.4922241099278046,1.5591599656055009,1.6334927616509316,1.672910929474561,1.5048602718182278,1.6414020594880385,1.5694338820034652,1.656236024974903,1.7007670854607668,1.5641434787636623,1.5820900172519643,1.8312050415054997,1.61012325305529,1.6210510482510494,1.6269444487438491,1.6215704974085805,1.8219420846838275,1.6351002946320752,};
		distribution.add(arrivals11);
		distribution.add(departures11);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
		
		double[] arrivals12 = {0.0,0.03142855896830133,0.04459397180746781,0.05865414301879615,0.09508240344262889,0.09594797709593152,0.1552639691541908,0.16406394689342227,0.17406569439887018,0.18697086089692247,0.18805962852228927,0.20745645257904305,0.2586249970268375,0.26302710170706467,0.28218556134928047,0.28820410554605097,0.3237216105636016,0.33361505097653527,0.33867780178449086,0.34545544160979064,0.3466250251913687,0.37040437496404177,0.3716436394057051,0.3722284641885405,0.4115771810844915,0.45536213875849657,0.4556677044920695,0.4899006991528263,0.501569778385518,0.5050422850239622,0.517747590071469,0.519063852950874,0.5193173109894363,0.5459994668370417,0.5575031413163749,0.5750572210804434,0.6041587249604504,0.6234681671409741,0.6282828757240528,0.6317499189773553,0.6426052869875984,0.6504260017639207,0.6670090962048225,0.6738997594738648,0.6823618608627122,0.6865770503651191,0.6877370204959862,0.7293785788772998,0.7422532655734017,0.7656776107175052,0.7738278596209507,0.7880178095646526,0.8007884253205534,0.8119793248406303,0.8283661434385275,0.8653338091642573,0.8737457545849101,0.9209049721539471,0.9404796922290626,0.9412427876178611,0.9790890663017724,0.9813220400548577,0.990183409543587,0.9960853316271544,0.9973737825983604,0.9989488911593356,1.0375882886076822,1.0450389678699479,1.0524176486218504,1.0603652539076853,1.0677399477990732,1.1125419513040296,1.1137963001688482,1.1188091372072753,1.1296321480408882,1.1392246590176702,1.148313648465045,1.1648683454677637,1.1825296710114657,1.2011479965264658,1.208182841278727,1.2172339160658376,1.2294911037746368,1.2458149013970239,1.2517845624341704,1.2621155211544968,1.2704171804156565,1.280372545597119,1.2886806895291287,1.293532909014284,1.3037742899851859,1.3099370611281898,1.3117578459474382,1.3118709391798509,1.3258897030467907,1.3355881289252953,1.341692367081543,1.3495748774956338,1.352884489272595,1.3621360940525682, }; 
		double[] departures12 = {0.42393973351964287,0.12251926554680093,0.05944265326947065,0.18299433631168766,0.16952327123698704,0.13248439887491748,0.5346801145210757,0.4070882526934596,0.39907449172175513,0.3594818935442544,0.35190252301734637,0.3144372873337885,0.3983755876129321,0.4911496811915185,0.3172000201175127,0.3183829002725315,0.7050766092326233,0.4824967626278952,0.36710855712543256,0.41304414922742494,0.5102951623313131,0.8680578193990043,0.43155769632050894,0.5547014135012845,0.47835437112934,0.4754822360575506,0.5263374706386365,0.7426164395886858,0.7802995867725482,0.5073318119942454,0.5378928079930166,0.5330395363778888,0.5913473256865293,0.6539345903923379,0.5676423283822344,0.5763486452907206,0.6270875418433703,0.7868468940050101,0.6574036413384223,0.7455510329981091,0.6731055382301194,0.7322710715970046,0.7915259851659292,0.911379779505747,0.6864357803306151,0.7305806056663322,1.1986182655406175,0.9585328680244103,0.831731532069493,0.8381152091842364,0.8931932778444527,0.812444084155927,1.1338478351196075,0.8596984934797999,0.9689280369553136,0.8933545139151671,1.0010875569940805,0.9715483953571324,0.966179002980107,1.2220502306260435,0.9836101811511916,1.008774212059205,1.3327479472345178,1.0386669800381203,1.0015795477611267,1.0014296120679236,1.3207755607429406,1.150119995725543,1.1773586212375635,1.0849977100632833,1.182366859492924,1.3090527786595458,1.2101031532773086,1.127510643371994,1.4878789068956044,1.3343750815248643,1.2857030649064203,1.2757763991459887,1.2505517651407156,1.2798196157501154,1.2618356379628204,1.2203480706606151,1.2309675626328065,1.3346575032779013,1.3401947126681582,1.311775166796608,1.4541340500936704,1.3426780798536901,1.343072134029445,1.3632128471158844,1.3322648789362888,1.3852573172535214,1.494040292249293,1.3125597608917727,1.4415099721525626,1.3469957075927874,1.4585728173756194,1.5134508186996145,1.4326042039939473,1.4195813682273009,};
		distribution.add(arrivals12);
		distribution.add(departures12);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
		
		double[] arrivals13 = {0.0,0.013155984342137972,0.05199342028641681,0.05730789096408829,0.07870982943964808,0.08760878565921837,0.09210840003513554,0.09838976688379843,0.10299163050861307,0.18063018484876908,0.19027331973252948,0.2005768374638645,0.21104031637948314,0.2253762972009834,0.2584539929580488,0.27575912509648637,0.276906323159581,0.28174202181592856,0.2827572226599322,0.2835305459955673,0.28681643521840355,0.28964582542360784,0.2934693103357057,0.30674799226402916,0.3118864873639508,0.3398280507452528,0.3720269136192709,0.3914177679917158,0.40700963324269424,0.4193781670993229,0.42151467248997027,0.44283141722153224,0.4480234379975981,0.5428300911974032,0.5447683764649613,0.5497087918285954,0.5588650061024608,0.5856008863003883,0.6085025489659417,0.622027616146257,0.6302076682374834,0.6531433642118522,0.6833171515261404,0.703219806324211,0.7101211044730799,0.710921591654967,0.7312046197471379,0.7428644319744915,0.7686071887081701,0.7707729500154434,0.8159071359283931,0.8160689578175987,0.8170388893231256,0.837740379171846,0.8478236663121373,0.8591747073055066,0.884836722440148,0.9010818523624304,0.9189997594962999,0.923069736146417,0.9317619119953529,0.9480096987542754,0.9697562305945383,0.9721485786333147,0.9841032095570615,0.9934673224435984,0.9964009247357168,1.010297722171528,1.0176945212810373,1.0382312427429345,1.0540122540124748,1.0542115357176072,1.0724600755258424,1.1257332314051571,1.1683415856826536,1.1963496493103276,1.2141197663186487,1.2191463819711892,1.2222100203035877,1.247360125987567,1.2548037233666647,1.2553557065333172,1.2585970485452136,1.2633519575485879,1.2901686188138557,1.2970645265534613,1.3052720617620228,1.3252825117212734,1.3367063329052224,1.3417986456879671,1.361375954154983,1.3636535509105003,1.4038035973342844,1.4173632693322973,1.4383240624716855,1.4409991801545976,1.4952247562902272,1.4959763101278383,1.514774379036653,1.525649815747338, }; 
		double[] departures13 = {0.16427671438012237,0.2535050630773703,0.21373179644491744,0.2480551343521249,0.22036193409786275,0.24284459469987274,0.11698894813157555,0.18311914165131554,0.2485482969245243,0.2240772960561037,0.33284103521461395,0.3414964652854051,0.32006704920884477,0.33077414561775403,0.42611089467636376,0.2907508682131821,0.519122272735648,0.35404137584185547,0.3218650216374298,0.38209071429959063,0.3108521495953864,0.30654240441648006,0.30357746565102095,0.44823571238853865,0.6046085520178722,0.40525072577052523,0.4015430964567053,0.4709492649386855,0.4310893664500753,0.4657219113461363,0.4442092732746482,0.45538041093024445,0.4616618872723296,0.718580677110677,0.7428571484521306,0.7863698587214232,0.5819092532575697,0.6074752912342432,0.6358378131336266,0.6340542374769249,0.6321430911490614,0.749990822567045,0.7983508804406981,0.7046839438816762,0.9057112820185876,0.9352837833656928,0.746433732316512,0.7809940740660677,0.9233161467303694,0.7891087594219354,0.8614261676860822,0.9004842482332216,0.8219078735894972,0.9566254343413464,0.8714358173914724,0.9166755454766026,0.8870018136845986,0.9465595292195192,1.117062005012358,0.959113097889579,0.9994027853079308,0.9562323822268287,0.997928770296571,1.0012240146342442,1.0524352774806807,1.1074850256101945,1.137968458534395,1.1995061156982028,1.2505458137861125,1.194069021622612,1.1044013383148885,1.21596081701627,1.2134869977142273,1.1611825402217033,1.6479745135083852,1.2636477659638217,1.244888197735376,1.4374179931250322,1.270099534525636,1.2876607899480286,1.2587354744679433,1.36273625628754,1.3492330695476316,1.6082951581555018,1.303362614354333,1.2993992902680487,1.3989200322293076,1.4416859419600068,1.4462793598620634,1.4074366751658607,1.3861813034565007,1.3831380053678932,1.4994284796967148,1.570380878532461,1.7252490460223102,1.5118059226688898,1.7117608860421687,1.575173018646841,1.5446612011313636,1.526222794462164,};
		distribution.add(arrivals13);
		distribution.add(departures13);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals14 = {0.0,0.05782593299881942,0.0674784254357745,0.07954073754387735,0.08697692856726232,0.08764559186552592,0.13518119119762184,0.14326208330050053,0.16929041663549577,0.17247262192850324,0.18051748586979188,0.18306107570850452,0.20549447396125486,0.21427051556694007,0.2152798682921021,0.21601578382792758,0.22525621248919264,0.2541577554600368,0.2941083399769829,0.31617626613755895,0.3475676302936135,0.3583661191147755,0.3761965188478927,0.383930593137723,0.4019580855676172,0.4519545023605087,0.480543749912994,0.49005931805213565,0.4992411275163881,0.5374537510369686,0.5421408655002611,0.5726459262642928,0.6435860253465507,0.66946600865627,0.7009742788502885,0.7047652342804186,0.7167378551738067,0.7402700090152546,0.7570102388426244,0.8058689051282792,0.8098832152424209,0.8359149158635186,0.8596496997388634,0.8797488003124132,0.9047923453514644,0.9549756903407902,0.9603736885841004,0.9749976410815617,1.0273651810155888,1.0347520054728616,1.042163557699295,1.0444373293980638,1.0537181572333505,1.0564807449911937,1.0587936410838539,1.0631731121321815,1.0785435715976435,1.0897186374211665,1.0921992453871419,1.130199335026246,1.1387371261250951,1.1407966347268361,1.1573818358020211,1.1821526101298194,1.1874318362783778,1.2166471713773295,1.3005963754570933,1.3158294334109397,1.3160056530367896,1.3263064114590029,1.3364924802168974,1.3554250168746549,1.4043214941540965,1.421147324411042,1.4362944387243228,1.458600521444141,1.4941902665005737,1.5064432804473928,1.52234609117604,1.5249361181707064,1.5308273917908102,1.5742023179901035,1.583078432467615,1.588149534745555,1.6144553310332999,1.6286017697338722,1.6309710932296546,1.651961503037787,1.668973227146846,1.6786212173777992,1.6811839073477874,1.7121164924789936,1.7431982428742057,1.7632808218132772,1.7679605800046272,1.7883273778249564,1.8506091725654783,1.8578447965143632,1.8697862090255304,1.8764214710077047, }; 
		double[] departures14 = {0.06433423677173382,0.11148892294929076,0.2740470621605516,0.1568888584066831,0.14729923139816065,0.0961924098129167,0.17902352163610208,0.39024879756430886,0.18991284731594157,0.2992506873372278,0.24881250676562758,0.21699246854187912,0.293317569597602,0.23575306860427478,0.3472572607199699,0.24202919682460583,0.2901393740994373,0.3189931563640749,0.3091349540301871,0.43531217110808856,0.3565097851100655,0.4133499791695892,0.5246722247769045,0.4616736370200891,0.4094375277903225,0.7536831855873498,0.4962631639125944,0.9646738164152444,0.5269057984327555,0.5597391633614889,0.8395671947763212,0.643982871563233,0.7025522204575554,0.6945277672858792,0.7935679880962943,0.8526099964848619,0.8465487814764289,0.7813725798899414,0.8394107574490486,0.8820137392295738,0.9580912713954003,0.8371221221526364,0.8788507512774801,0.9288474496505779,0.9158403540707034,0.9996081933936104,1.0017427705514148,0.9762992155816517,1.3134256025512727,1.1312128293351127,1.102960218293028,1.1384539561695437,1.2215316714090703,1.2350812703938783,1.0740366018195255,1.0742785916064914,1.2132181399614108,1.2840323748191016,1.2800461681054887,1.1325146925015352,1.2109289223590338,1.2479305097118887,1.1824051121461376,1.1885962902349012,1.2190182810855836,1.4484094066411208,1.6819116810315171,1.456719987593529,1.3632042427727546,1.3717969100759413,1.4595190079852016,1.4072412014093965,1.628555257788082,1.5610467161285244,1.6147285864127243,1.6812648212284942,1.52954573423981,1.655549938429711,1.6360353146448903,1.5626133459588616,1.5691027271497362,1.698571573659824,1.7356092083327566,1.6239327478114174,1.6371087685886696,1.64342538199618,1.6744226039637187,1.9357727459412115,1.686702952114725,1.8291247100621053,1.8193105924967843,1.7889834881701414,1.7954256492508074,2.0550232260299963,1.8469531752301993,1.8983754509635204,1.861848005352333,1.887299760920641,1.9887747844709571,1.879104496604082,};
		distribution.add(arrivals14);
		distribution.add(departures14);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		//load 8
		 double[] arrivals15 = {0.0,0.008180430234945893,0.07511736965572532,0.08636981817941779,0.08724742945976427,0.09851838386524588,0.12479464649015448,0.12554634278855903,0.1331129988738619,0.15865916870004035,0.19520882088382516,0.20032153775603703,0.20309354367422014,0.2168301303176963,0.22124002431793158,0.2275656154249993,0.23450555863483924,0.25249026349737097,0.25459902273707385,0.26425126839868973,0.2670320159851989,0.2755039414504902,0.2990718205495229,0.3061954339171335,0.31285156548118187,0.3193221492878501,0.33001380193162305,0.34726322067227544,0.35675305684783215,0.4043180125502869,0.40912789732263855,0.43636049714926883,0.444184857473901,0.45935289179582484,0.49228268212067816,0.49913225835336833,0.5031029287959553,0.5068669768894644,0.5075709081619152,0.5110361565652692,0.5304162654827147,0.5721887085366095,0.5822816849018936,0.5895331046208885,0.6053394333058022,0.6103640540563854,0.6213164156234916,0.6403647924167328,0.6457646420102068,0.6553222433211386,0.6553904423978508,0.6618160477615928,0.6681593610706559,0.6689045977950593,0.6781365357437207,0.6865480628616342,0.6967336081350202,0.704263703099711,0.7339248949144613,0.7454921030460809,0.7511395618669293,0.7572179326113808,0.7742962369332916,0.7747087357031702,0.7872286719142183,0.7887596406918116,0.8007130572864917,0.8025249249723593,0.8396493254781245,0.8606528017300337,0.8848160177911112,0.905780168157121,0.906812560208734,0.9135063846200832,0.9135748908455708,0.9191382074019633,0.9847584873105689,0.9980498497618336,1.0013712027607737,1.0174639012670281,1.020071515451484,1.0487225826952282,1.0509696597462983,1.054391942216698,1.0729052922749978,1.0735236445901426,1.0800449329724557,1.0962329107359812,1.0992989371759287,1.1016109226936441,1.1017589778040675,1.110554888948279,1.1211875955105812,1.1261797676965897,1.1285918874101355,1.1439187698902282,1.1508731433267656,1.199901087597818,1.2171503612309682,1.2266559315504733, }; 
		 double[] departures15 = {0.12786704310586378,0.18378928844194378,0.09739093223897281,0.3321474159391533,0.18806082096211874,0.15072807632121688,0.26389413110446414,0.19450301376747012,0.2886587562151987,0.20122463152894868,0.24296273880087021,0.32451602979287075,0.5674568494351617,0.6194244446862103,0.23262219947409157,0.27883812536903685,0.39780324288663194,0.2770654775868816,0.281217002767039,0.3139255469802986,0.3930548571944574,0.3281416947535174,0.3214607301334654,0.5425910418682481,0.3940746843239789,0.32647698082148774,0.6321620135273711,0.43327392143518423,0.394337715458888,0.5144574393176926,0.43536676282119646,0.4373399632495301,0.44740757628541633,0.7855954719968883,0.5756379756338309,0.8989486986633015,0.5046767775204107,0.5095141001760989,0.7410556019819978,0.5195029435239632,0.58679527477742,0.7148584021550842,0.5845854955912618,0.7096220452713229,0.6657338483148043,0.6385859340737273,0.671695585635187,0.7742611216871056,0.8936710320206486,0.779621709757908,0.6773745807802692,0.6831027071350463,0.9761050843793502,0.6782018908265263,0.8353063144985788,0.7678062362428015,0.7568890839546889,0.7795012556539017,0.7762893785724603,0.7507323787240578,0.7543858966639169,0.9044433974134105,1.245931111088243,0.8092290782255191,0.8104822091910193,0.9091235242588471,0.8561419327943777,0.9397395088523295,1.0728162035069024,1.0217146559952068,0.907859822142717,0.9253396343311396,0.969596437652976,1.0261035457853238,1.0485271442851782,1.2374743812551563,1.0006929855720121,1.0524225255453565,1.20844118867794,1.0820816978687955,1.184905852123066,1.3648824244387259,1.1150385343506746,1.1695587982267883,1.2149130669125545,1.164735275941899,1.0881045048195943,1.2514945919163285,1.1642878486294392,1.4371510623374775,1.3345298026138628,1.1221407408245299,1.2662086980788712,1.1553227870252476,1.192140276462856,1.255021966368638,1.1981080416297483,1.4091217896021,1.2683644144187278,1.2603724541210863,};
		distribution.add(arrivals15);
		distribution.add(departures15);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals16 = {0.0,0.010026902826246934,0.0392898231782121,0.041678476601869435,0.06280401732008178,0.07714101353631002,0.09380950943223337,0.11350782398506605,0.11797556065166512,0.12037261310982801,0.1330578042429099,0.141822481758854,0.14239981557446724,0.14473938509531692,0.14661643476725064,0.1558152526140924,0.1615087098184628,0.16788001548548007,0.19232828724977458,0.19431464845120955,0.20122337888460395,0.20685293005405864,0.21163102414410825,0.23684430355549205,0.2547951084675302,0.2592998160984122,0.26243413944799165,0.28463862607866236,0.29653222621542746,0.3042493533821237,0.3077798234627106,0.3217256613791514,0.33329590362779615,0.33707682892278645,0.3494407843682527,0.38276271243489723,0.4307109549268574,0.43628323237979033,0.43877084291236657,0.451801531017542,0.46608729015084605,0.47712639026649367,0.48823294735477674,0.49797784109082216,0.5053704657332796,0.5255150598311894,0.5336645926643171,0.5402115993638004,0.5664922343505485,0.6424818952173549,0.6519261096923114,0.654783823220545,0.6552285687926347,0.6570514440867414,0.6763520893072568,0.6895031175849106,0.70903776441058,0.7139498451106817,0.7147099416318717,0.7353147449729265,0.7509610609402041,0.7525516753298219,0.756806190274985,0.7586171769095932,0.760011884231141,0.7753945277767108,0.7902634552222272,0.7935926795237963,0.7990521490484942,0.7995373061313508,0.804748909999481,0.8058214943146267,0.82959473427212,0.833204975024261,0.8338384139685574,0.8724212906357944,0.8768213642804064,0.8944121390790425,0.9182862567524007,0.9328763130195605,0.9395622727789019,0.952719385299473,0.9749825432006618,0.9750397146445141,0.9867490850304343,1.0096682359438918,1.014660230403134,1.0252801738455113,1.028555625683645,1.0439219027140518,1.0526791153937918,1.063986134838746,1.0684116230780851,1.0957850884463758,1.1048281587373294,1.1082263198298472,1.111695736168772,1.1361380675291084,1.1522511163681106,1.1526797582114572, }; 
		double[] departures16 = {0.017425088943873193,0.20917057989485108,0.15933181234256955,0.058880886755135836,0.31748018750982704,0.17529025708273266,0.3949834431111104,0.21682644821309294,0.19397935446001888,0.14575172933469138,0.19045821134392876,0.24389378580512616,0.19581933814500235,0.17698626956691482,0.22619045523485776,0.2440550244741214,0.3855932504524955,0.17809074086732757,0.2287422239162371,0.5081679367751052,0.4273738093600341,0.25722847986826747,0.29549688203768076,0.23933618226837994,0.35958371842095943,0.3368755285215317,0.29106953246704537,0.3258487367920172,0.3352719504285001,0.3103763438781437,0.3515773393673399,0.37285308233725833,0.3858137509233205,0.43222027422195525,0.3628845842109291,0.4250748394369299,0.46827506195523455,0.4903306032608206,0.4596812462643114,0.4586401671235215,0.6601026464859305,0.5311426268410065,0.5538732749776882,0.504330648436636,0.5278744524250474,0.5544358282312166,0.5622297702102328,0.6805425993881149,0.6735759394719888,0.7204768200117457,0.6720714588466798,0.7792700959474184,0.6668387044790063,0.805153683773277,0.720596560138701,0.8761050721511728,0.7249580668074415,0.7297787160108354,0.73319864024327,0.7916610060402022,0.8452452539741157,0.7884780541625939,0.8621932486513115,0.9152183924896142,1.0265866231324832,0.955432005287171,0.8034417141925835,0.8571060892040325,1.0116879718739624,0.8786555907019069,0.9538151747213656,0.8167895078205291,0.9528859231192913,0.8964206613711203,0.8340274856762724,0.9298347250017542,0.880250098081873,1.048250734502321,1.0670299086724593,1.025513468345444,1.0645101692999623,1.5262806737658154,0.9838605747898995,0.9923559819718297,1.0395203954799823,1.0432935966282468,1.08449040045734,1.038067548479898,1.029910637523536,1.0745954281497374,1.1318833117357094,1.137525418502158,1.072937627171779,1.2596759456695137,1.1154064617017847,1.1879435476938436,1.2317301430822842,1.2368572530818152,1.211399500477976,1.1579263843215435,};
		distribution.add(arrivals16);
		distribution.add(departures16);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals17 = {0.0,0.005939316300420296,0.013364300940527696,0.02037107868804021,0.05074720395046817,0.06601362927401094,0.07501530200746365,0.10504901786813617,0.1158847924782129,0.12176068783491784,0.12267572311382172,0.1339280066770519,0.14450926279468845,0.14645110507789064,0.15195824393869165,0.15666640319850839,0.1588343115330829,0.16912577670302192,0.2309662067125558,0.23235484956856736,0.2584403649595901,0.26497098277595205,0.2794888072381072,0.2804701521349726,0.3102602587403668,0.32762153379295844,0.3350698494418282,0.33986515230879133,0.34502945527137235,0.3487878642403998,0.3813215832839734,0.3888497881615403,0.4043998245421684,0.42478190632949747,0.4368041491619913,0.4382496762799664,0.4400996357633494,0.4415587056402556,0.4508227854265913,0.45102755637805353,0.46007970131381054,0.49090295741537293,0.4915760962270937,0.4974023456525448,0.5375335987317543,0.5495681361919258,0.5575014249179063,0.5613573428949998,0.5845248948454171,0.5952140171460922,0.618768290084632,0.6476821073696964,0.6575069604807426,0.6639732666209679,0.6863986057978242,0.6865235395074057,0.6883626453246662,0.7109448637368685,0.7344366788589809,0.7542197504040519,0.76010800875558,0.777940119357323,0.7925171071394606,0.7941173748915827,0.7942450842843128,0.7971485635588463,0.80555752084598,0.8058001057972285,0.8081095107573485,0.8214208603119506,0.8265735788970474,0.829931202246657,0.8355421547190626,0.8404676084460025,0.8478101570162168,0.8481571405376154,0.8968582423412783,0.9013298578602182,0.9014785545860494,0.9073603883794517,0.9175132362707973,0.9248428293802101,0.9552938801454287,0.9638618007439229,0.9948596317195864,0.9964108761602322,0.9990459477780242,1.0001399042586676,1.0134423943048645,1.0321582580444297,1.0397922001340414,1.043032127741856,1.052727942753699,1.0542233961696152,1.0608452810553113,1.0796086342844804,1.1077626615134533,1.1300290793993701,1.1363583328487405,1.13755941958862, }; 
		double[] departures17 = {0.035961608789289265,0.04384712351454556,0.062203257393023736,0.11214583334356222,0.0760687644001705,0.21233643535272584,0.0968778983835421,0.11477672480674084,0.1641391610564231,0.1453915972151862,0.227600622540406,0.22599301443685954,0.3054193294569963,0.16446778148707725,0.24635569074440192,0.4217907210622585,0.28637516204374025,0.19541184348788132,0.23248680364902283,0.3695516403037874,0.26801882100989727,0.29608811577343197,0.5899571154085783,0.3981298536352196,0.4206187937037235,0.38153189624614314,0.4715169705294099,0.3874894728056377,0.40205395714149894,0.3638044486236431,0.43275561141220187,0.5293956402973624,0.4693453294299685,0.45519226398987217,0.4846326309470851,0.4607794643815033,0.4505593986491764,0.4635177249585125,0.5609042753152493,0.7082894439927618,0.5724961374312317,0.709586651024088,0.5948145084876517,0.5937075904531425,0.7116701273150217,0.6017540609539875,0.5880556656863152,0.6209237526079057,0.8020766668348863,0.6995805666739499,0.6332638723607606,0.9805460911395368,0.6883357974045127,0.7678531958691162,0.7138059158776925,0.7723381859903838,0.7027214812053819,0.7237513550959884,0.7370301838764775,0.8313961305543482,0.8410416926107654,0.7800744013783119,0.8151800843817668,0.822182546866558,0.8992954838480752,0.951652904207646,0.8304106986247967,0.8178297319938636,1.0360234328699702,0.8959175192923345,0.850816569219207,1.0932808588886456,0.8836663821933255,0.8780092593680335,0.9293421918177862,0.8928009229706833,0.9457540498502541,1.048535682217417,0.9437105703650115,0.9695624938765314,0.9989840818861306,0.9404073006678819,1.0454684028790548,0.9999433236397687,1.009451447874059,1.077124354735965,1.0122939520495817,1.0034215872638907,1.014699921236008,1.050046526609921,1.0753980669919758,1.1441417763023243,1.0868822669363993,1.2113733462003926,1.0654211641081055,1.1088468101847109,1.153324860773986,1.1834925213444865,1.2403367928807298,1.1810027204836107,};
		distribution.add(arrivals17);
		distribution.add(departures17);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals18 = {0.0,0.012543852901626887,0.0472348742346486,0.07073465531272274,0.09563668452879208,0.09790273002445729,0.12218770393180929,0.14549762683391246,0.16774037718778506,0.1760119127342486,0.1932425407487594,0.1934166465707029,0.23064507812019708,0.2454832906557725,0.28983772540966046,0.30373529217537765,0.321120021221729,0.3242785880499796,0.32598820103905507,0.326697713363339,0.3486410842602544,0.3794727643409914,0.38661500129100873,0.39545620855741653,0.39773662135356513,0.4209156466453491,0.4619546628083917,0.46441988282612273,0.46514920793252346,0.48394285585012053,0.5100524975101228,0.5150051831993763,0.5162681338545206,0.5260561619080348,0.5537098523804441,0.5840250685106592,0.5954363813252829,0.6299215772714108,0.6341308881422189,0.6436550885063691,0.6756567591443188,0.6760837394848903,0.6832526582971475,0.6991406696896221,0.6996046686510745,0.7048027627695618,0.7241536004003082,0.7291517990190066,0.7608010431827729,0.8153040620386725,0.8171528319861145,0.8213683117135211,0.8232185070442736,0.8280297427727926,0.8413802889796639,0.847349041078186,0.8576055133323244,0.8685741818486566,0.8686286471051183,0.8728227028482689,0.8882418166379631,0.9124826390164896,0.9272308321889102,0.9671562481348839,0.9736521666630439,0.9891703684820373,0.9943949580411302,1.0157940404400925,1.0199088352580672,1.0641126930713922,1.102565645435136,1.1174257562903178,1.1292170142851872,1.129740432708636,1.1542508103603413,1.1807821932462417,1.1963507412964314,1.1966951856875645,1.205481491480703,1.2084340859882305,1.222598261644262,1.2226575215037632,1.2244089638643625,1.226925420218774,1.2319257638997,1.234663400222956,1.237392909811519,1.2470948790892054,1.2512263359479698,1.2606691991849055,1.2805293636376063,1.29233271020277,1.3073744820847406,1.3131611986224445,1.323400287143167,1.3250359094565807,1.335185491302056,1.3414488878258224,1.3493326883466705,1.3522927930900341, }; 
		double[] departures18 = {0.02563704138913231,0.2995158030369683,0.16642527650427935,0.11258184692200943,0.1524065459456398,0.10280457971310512,0.1286914520161125,0.16950686797779013,0.1720596017561743,0.3496583370443657,0.2502626676701789,0.45545680063508287,0.3089071850960681,0.4047781165663765,0.35476837389788507,0.35904387510305324,0.39716153638293805,0.5849677269487181,0.3299593390127496,0.3304411527974752,0.40395629967793084,0.4435044756216342,0.40061068129118343,0.6542423483768269,0.472296753190383,0.5411737041413667,0.48136614480488893,0.5355061413207749,0.6221762846276538,0.5009269387536136,0.5236068122330385,0.6985144146808194,0.5775444930969846,0.7637344131875382,0.5877596108971731,0.6017266793063517,0.6044627713338622,0.6878634673127637,0.6806539431278631,0.7076121981731066,0.8605970311021356,0.7293815623938115,0.7079304953425718,0.78191075863784,0.7022365962578061,0.7073008014170878,0.7994114176068796,0.8450134456101144,0.9232425400548419,0.8570091028258756,0.9178284059184478,0.9444567855576678,0.9449810466992135,0.9139910806364872,0.8571397827947932,0.8492382291034495,0.9939703466330398,0.8952349045822047,0.8922297562104119,1.0272927624522366,1.107176461945152,0.9521061619773732,1.2113569055689417,1.1179730076878545,1.0167379173840219,1.0238873173283531,1.0563747724655188,1.0562355323670056,1.1099256814576448,1.1857180726597267,1.1425226815754783,1.152324682417243,1.2138266182138895,1.2441786677526234,1.171963442449661,1.2415481835292626,1.2284992435317306,1.20161362553204,1.536082011914912,1.2174147370950903,1.2315662311338003,1.253120389444365,1.271217212557398,1.2833818027424522,1.2410028739985839,1.2597863965781115,1.2859224006420418,1.2523529194031406,1.4589807460038908,1.848653666233707,1.3249502069259436,1.4303766150973023,1.4058360964293624,1.4071893635384938,1.4526146308672747,1.4951617975411844,1.3399304028884196,1.4181641681279802,1.6145965628386585,1.4816012703852446,};
		distribution.add(arrivals18);
		distribution.add(departures18);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals19 = {0.0,0.007379430309715169,0.012672183413402979,0.0398654069194624,0.04916476348351344,0.0536841733740938,0.05440351516351071,0.07635107277030462,0.10297288164226012,0.10551814185383167,0.11811851113775498,0.1339493227418988,0.16386310353561545,0.2032608031757565,0.23804521525133623,0.26005442048801064,0.2641145525773886,0.28458777973073934,0.2921702124334997,0.34267781863902963,0.35506881856252753,0.36043905894323414,0.3700993672915973,0.3852251122977216,0.38758154378425774,0.4092912135216237,0.41592057461356113,0.4199718474714225,0.4301929772345034,0.4447660390057972,0.46825944496096567,0.5162525414990431,0.5168974415263659,0.5415308524894579,0.5674910132217492,0.5952758589269423,0.5958000278763189,0.6109304213094691,0.6121064399817481,0.6121667059982717,0.6224749600021212,0.640159649358536,0.651854474942436,0.6533290445572797,0.6605388486351604,0.6692843047187413,0.6828335891436531,0.6926959697861886,0.6932610280729583,0.7034713726939446,0.7054947939006676,0.7078737722892395,0.710147351029325,0.7200457701316016,0.7220212616027825,0.7235687648306118,0.7393840227726479,0.7470439169386782,0.7634805364445164,0.7677106860817677,0.7685024753013384,0.7810461818713526,0.7862397857660147,0.79798840220361,0.8057609178262971,0.8169406177943642,0.835425697464015,0.8379044554482087,0.8846761389581885,0.8997246536621469,0.91842462004675,0.9213301079826883,0.926511317659313,0.9273910974870663,0.9383988139122748,0.9408662247967219,0.9542802557712523,0.956175393327609,0.9681915518293674,1.0145633469206978,1.0358004166647152,1.0635710512562917,1.0904713859511677,1.113827821906297,1.1166772797484041,1.12840940853747,1.128927741509261,1.1559556009197118,1.1574808175586069,1.1626005450503913,1.196491919820737,1.2095210400948624,1.2258928107876326,1.240818582145828,1.245233476193002,1.2508662048613486,1.251428267846215,1.25201367076825,1.2763834446649465,1.2778213929714135, }; 
		double[] departures19 = {0.05236470268101347,0.053441511373171484,0.060637701170155406,0.17715166552904715,0.20889733845658198,0.1494705845318589,0.28870603059268224,0.09965835368703009,0.12857853338871555,0.11849154827031685,0.132356922765665,0.18695989240250943,0.23058347698151774,0.4565810106515837,0.4885268375585531,0.32072307679308043,0.3086996048913505,0.3233540951599627,0.39485371163457855,0.39884191393429214,0.3928290145770314,0.5681339950327249,0.4602915735146039,0.5479570264584092,0.4928181076172508,0.641151155944153,0.5100706581616299,0.43686794654425504,0.4379641250437466,0.5377798600489886,0.5495931818349502,0.7186547887636936,0.5676907599378894,0.7459425945788907,0.6739644705391445,0.6510538943184775,0.6596041410847658,0.6117629660533976,0.6909603099988004,0.914662293967774,0.6871302408207136,0.6430118861303289,0.6670952628815688,0.8112415377366912,0.6789358730814924,0.8455746976778858,0.7838952797489627,0.7090757691163418,0.709088411666904,0.7642356666425962,0.8023734989587239,0.971507720113353,0.8595827905841991,0.7793660930624977,0.7353413002214042,0.9525872073633346,0.7797025436514327,0.7513900931148411,0.7689795147981751,1.0334627206947442,0.8448266326331042,0.9041813446645508,1.0110489126425386,0.8687314820256749,1.0209361165338504,0.8357085259454108,0.9794729586778892,0.9277840636381971,0.9968583558332001,1.041696109129995,1.0450913337870336,0.9655976546420826,1.122387839770409,1.1196285941324446,0.9781307179928018,0.9678891959565378,1.2277580100009935,1.003740761115435,1.054867041478924,1.018393688717785,1.038316943505535,1.0822870252544194,1.133981093574929,1.1447943376748613,1.1639074750668452,1.218128646341602,1.239784364368164,1.6232234412850892,1.288626552496262,1.2898544933245488,1.2249878901637252,1.2484080093083223,1.3066301135105405,1.3160156935699536,1.277824348027044,1.2619364428753546,1.5089338355953261,1.2538784983883982,1.4403770864621284,1.3838834496364387,};
		distribution.add(arrivals19);
		distribution.add(departures19);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		//load 10
		 double[] arrivals20 = {0.0,0.001196638172926615,0.0023694506305416236,0.012914402967336164,0.06996118195508312,0.08146325185066333,0.12803689741390767,0.13015647779024148,0.1301672887619965,0.17247759925632974,0.1788824542117228,0.1828862866742254,0.18489894732196344,0.18803914602338273,0.2135192876029796,0.226397254721753,0.23220203876556333,0.24185476203787817,0.2427232338887992,0.24737022280759915,0.24864168273131038,0.2555939794187703,0.25864870288265546,0.2724461248471198,0.28365985275201183,0.28867228089030983,0.2903772126478475,0.3026218744280603,0.3194183553646517,0.3200739367360903,0.33481206374752936,0.33906909082616554,0.3474583118714981,0.35974455199563393,0.3625831059392549,0.3668055992232612,0.3724037731349074,0.37970469242354277,0.42602234850866005,0.42666066440643685,0.42933397069077756,0.43320824341338715,0.4374332100342439,0.4453810639855809,0.4471825467757204,0.4494011716250943,0.449469724310406,0.4705828327196251,0.4729411634346399,0.4799399054946219,0.4801443831541669,0.48322140791332824,0.48997433643830607,0.4918231245511745,0.49952377500140976,0.5243054156671784,0.5400661222714717,0.5613048433404337,0.5748200108474778,0.5809940385898867,0.5931609996377829,0.6035960078872844,0.6100818051820271,0.6102160696902968,0.6195852908668089,0.6343856068282125,0.684965134118362,0.686680061420674,0.711020222556232,0.7257314135681988,0.7591197724591592,0.767436112434198,0.7876680127630247,0.7961707459222557,0.8273670258436017,0.8472524147087911,0.8474488883416926,0.8532442807382469,0.8535228591532598,0.8572843547128997,0.8581861469982305,0.8654128441385591,0.8671456601988422,0.8790453915086044,0.8799534634580335,0.8856228918371373,0.8858821291737055,0.8913840773650925,0.8924621525925727,0.9157646570527029,0.9361677368875534,0.9541553589339308,0.9640625196456547,0.9780748314757786,0.9815069706344394,1.0107577717801388,1.013354306113346,1.0225820530999967,1.0391822372369035,1.0727843814356357, }; 
		 double[] departures20 = {0.12024411054578932,0.11534285549134035,0.01924622777730332,0.0250841682711801,0.09627398276493243,0.3195920767917395,0.1374130495878734,0.15879010886901476,0.26853328586354114,0.23781747909123374,0.1810302548260045,0.33728807065930455,0.527551478653699,0.20817288173987517,0.2178271672813164,0.5704171750052234,0.28962806812051234,0.5098838758315853,0.31733090255420243,0.25662146328196583,0.530101931369686,0.28183386776321095,0.28296329282717936,0.3723976894471797,0.2906877382562962,0.35746410978396204,0.3294035599307854,0.6734211080251885,0.34793506143608754,0.3942586528368945,0.6886154170885119,0.3529369768315854,0.4615721892534197,0.6299817601997382,0.4759991803728844,0.3731986953119594,0.42386577842573814,0.5162169875857248,0.44619911100555715,0.7785387316366307,0.5222148006628776,0.463731502357848,0.5892453864545883,0.6227276870297629,0.5835230727846064,0.4766287732732607,0.5842286182170148,0.559270493760515,0.7390869340483057,0.7032695283861059,0.5348111198100234,0.6131121525183636,0.7619027895065296,0.5354465984799474,0.5704654038460041,0.7501799238564221,0.5849049195267128,0.6628722472072155,0.6669022013525586,0.8430318993636976,0.6556264807700427,0.6961840902376906,0.6166935004610385,0.688984524795577,0.72296922376354,0.7364782679981798,0.6915331235078328,0.7116142253774372,0.7350531778723717,0.989466274951601,0.8667822361541173,0.7742159206741078,1.0146019614599318,0.8489861669776634,0.8668673402297951,0.8473123651457967,0.8547877961073151,0.8862207687856879,0.9787426220795002,0.8693913250914522,1.027782545695727,0.9613324678823987,0.9848015727607395,0.9949724323942016,0.9884883827812984,0.9236216156326813,1.0175676752010665,0.958834456794835,1.0781485014289975,1.031360721952128,1.060941766116997,0.9676891509515952,0.981675484878845,0.9867045904584102,1.1504369278832474,1.048634609919254,1.2557847753151492,1.2164769716738821,1.2907415583913056,1.158567638090502,};
		distribution.add(arrivals20);
		distribution.add(departures20);
		sets.add(distribution);
		distribution = new ArrayList<double[]>();
		
		double[] arrivals21 = {0.0,0.012902878947376994,0.01645881786250439,0.016905473599915514,0.02030608865536156,0.021100312912063518,0.021779333499678262,0.022829720126076754,0.04035551268566197,0.0447781842111616,0.04615263576288369,0.06375575027053876,0.0979322109863442,0.09842487456345217,0.1034145514552206,0.10391908881428345,0.10608826316698802,0.11540160359005938,0.13251950262719392,0.13897122026085781,0.1401841652417218,0.14337912680166878,0.15249804206780365,0.15930459004308237,0.16014635077714054,0.16566809799559434,0.16702843150540875,0.1725433684505612,0.17868971117379076,0.1831861813193167,0.18889516352478755,0.19679725345715512,0.2074935684477892,0.21417868541797916,0.21904855071834572,0.2276527331066453,0.2499201133571243,0.2556890507188225,0.2559893666262466,0.2615374224592164,0.2658840776174494,0.3165461539336428,0.3209357163402701,0.3244240433355733,0.3262788589480953,0.3283475535399132,0.3403793152187926,0.35765573389687877,0.3720610640973101,0.3731802825200654,0.4075198130361345,0.4096085012317894,0.42365444554095216,0.4418736669681367,0.4497047806717167,0.4575098967658029,0.4650853195594609,0.49332987546266455,0.49752254776923127,0.5149189273066641,0.533879401689116,0.5525350259107455,0.5605871708392276,0.5608835786260015,0.574090986144165,0.5928953587699377,0.5979181541161067,0.5979804768207437,0.6049717639953913,0.6121240411657678,0.6142509528864167,0.6210193798606385,0.6351194789723903,0.6458057917563578,0.6528053571457018,0.666719405009183,0.6733369430486698,0.7010041610069413,0.7036729696478755,0.7208758175097345,0.7290001049220497,0.7381274489629069,0.7389780758411568,0.7429534072524631,0.7479838862456029,0.7523665180778856,0.7561452021882068,0.7600466543659613,0.7686987339004269,0.7814564088892899,0.7882112838019505,0.8066657428938788,0.8091484565629848,0.8113225174906665,0.8368465539352503,0.8424610760192309,0.848667119838189,0.8667465732433032,0.8746418964790357,0.8913781328421844, }; 
		double[] departures21 = {0.013999512504909327,0.08328350509456524,0.30867146645367427,0.0772819974099279,0.38685957826777745,0.02328133927575979,0.21284454742196054,0.11733015128746752,0.21412213749789294,0.18753209436323132,0.1489171354449871,0.11336488269425264,0.15455342066220218,0.20390721965905972,0.25070109254811207,0.4182767885238762,0.3215132534385847,0.13843842841666185,0.1472876959639685,0.14785615024170803,0.23167002761689326,0.2729139652300373,0.22515021894115794,0.5039313504013424,0.2722738069407241,0.25884813456866396,0.3514328588247799,0.2830679092131424,0.18407440022224283,0.2233489852291885,0.22301450295719485,0.22422892496221125,0.449906737979892,0.2141935936508593,0.23707853168193505,0.35704428042957753,0.4219922523836947,0.30675186206678706,0.5037183551973099,0.28938540084021036,0.2873498150152541,0.431677190468226,0.33006616476331146,0.6003038237247906,0.3461748995642385,0.34722673208244853,0.44042295483692356,0.3998248845360343,0.4917903817770376,0.5063991994475352,0.4115324194780269,0.47814939372029097,0.5751707275958775,0.4597450364667391,0.46028881488999596,0.4624641556899946,0.736238834678443,0.5215310175707497,0.6263790341851134,0.7445082107607061,0.750544531949523,0.5988063556692623,0.6998003827300792,0.7246586342584441,0.7174350316972794,0.916284821753077,0.7034972644837028,0.682454804047747,0.7913045592761833,0.6433207124546834,0.6161840483538951,0.762455838824606,0.6358431103869724,1.1355555168692868,0.79316636017418,0.8133897754653306,0.7361209991693924,0.9212449885284334,0.7731677006852052,0.7787847839224913,0.8710593358019694,0.800018071188595,0.8122193313690345,0.7746882138008562,0.8894279959249896,0.9170510632777155,0.8267143266677701,0.8786214389758303,1.1230355742449474,0.8636236163842468,0.8856328502154706,0.8693823266813907,0.8758981173162436,0.8388853031199125,1.0278226727785804,1.0288008632727033,0.875309339756035,0.9288103818687296,0.8784026571207131,1.0321307756159164,};
		distribution.add(arrivals21);
		distribution.add(departures21);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
		
		double[] arrivals22 = {0.0,0.02015644538761385,0.021985464316881684,0.02500176089471971,0.02686501809885945,0.029826015319551757,0.04513711647303133,0.04521823932294007,0.06717796761388738,0.07057797552525732,0.08535506374176385,0.09704672516176728,0.12095665663422225,0.12144803669262894,0.12912420494997298,0.1292063427738738,0.14332336780646562,0.14689099790068127,0.1612972720476784,0.19770502285407346,0.19798187982481064,0.2077228601953487,0.21859342134975002,0.24071057010869146,0.2604210290604814,0.27238624902195574,0.28050942305334486,0.2820980969928037,0.287340003384649,0.29879925750059566,0.31236106183862883,0.31687201159422773,0.32318251991951324,0.3277611853780116,0.3696411942721067,0.3739724694706294,0.3880030670815908,0.38856921156703605,0.3893788940631642,0.40273358490934463,0.40283554528011584,0.4142831132544444,0.43226570899542477,0.4322886801669766,0.4327788688917922,0.43607284060237544,0.4411302391807131,0.44195109818064143,0.4427167139237454,0.44332023792130587,0.4490579498089714,0.47074672475659507,0.4722220829355854,0.4994015012858244,0.5033945068219561,0.5071470808554546,0.5081785953846769,0.5230572112077781,0.5308656217845334,0.5309479392978078,0.5310069434106487,0.5436286859525319,0.5614546436157609,0.5780226220491833,0.5909424030520362,0.591405341326025,0.5996463647433609,0.6018452196968074,0.6330995403797369,0.6359300262663504,0.6603189433719389,0.6682623313850832,0.6773770119408138,0.6777235586471814,0.6844437096849411,0.685767546598636,0.698245412415784,0.7073036234127493,0.7074654821255829,0.7138788623440175,0.7327568841857542,0.7482410584442112,0.753158682499598,0.7610248627044225,0.7650186644355247,0.8078179036972035,0.8113408675150057,0.8128412910844972,0.82118777331387,0.8234114787089053,0.825930386633384,0.8277398553792337,0.8409809516168877,0.8498951122134811,0.8626570511271818,0.8650642145864763,0.8668487819429915,0.888259455313813,0.8890939793759538,0.8981862525146957, }; 
		double[] departures22 = {0.06708718183702601,0.3241725547894501,0.10737220031339904,0.03856217795922996,0.14780195817868058,0.09469965489217351,0.321893996850292,0.09896060920045546,0.29992990779617157,0.2956681904812255,0.0957867583353066,0.38144743898425976,0.1851910224590212,0.27794041878226494,0.1533210394448995,0.1854856850340442,0.19733589101118448,0.25349191517189323,0.3237210730464242,0.207558146361966,0.5014044205467258,0.218451477000513,0.3889310284198003,0.4726658902347344,0.4431717231207831,0.4216070545111782,0.2863384194815917,0.35049437764673014,0.38000517466129097,0.31005485200894695,0.3968799150824861,0.31817608608586373,0.3610292775250849,0.4116499617448011,0.44125711861771105,0.44487318940517845,0.6484272741665198,0.5603504745595671,0.4764717068335769,0.5088589948557443,0.5672941870952062,0.5454393127927212,0.7000067074688199,0.47197389541239754,0.5243204271220093,0.4438467199138407,0.6849086062959884,0.6400756099437306,0.504689361243656,0.5776816274865546,0.488049828281681,0.5513642160036172,0.507446202252261,0.5551189902368755,0.91294141676786,0.5338780567558824,0.5492592317211069,0.5821414136691091,0.56561979854138,0.6452758127969485,0.5585462305772164,0.729905755017729,0.6212864052524967,0.6703921475790245,0.6414162477928647,0.6357063745703084,0.7488072060158825,0.6277930846639153,0.7818730582856411,0.8000976935962355,0.6663083744421614,0.8455540153041183,0.7078711745087004,0.7197488754027225,0.7199849747273542,0.6990198379869796,0.946443275467675,0.7748387872440579,0.7127250490388627,0.812837106789519,0.9218305084798618,0.7512923469500739,0.9906896497503034,0.983559805399673,0.7886044994362669,0.8678399029147277,0.8308026187104869,0.9750404001469097,0.9582310681120983,1.0225705713012343,0.8968619108624867,0.8305283485153763,0.849050667343005,0.9470979766066214,0.872758869366847,0.9010459286269081,0.8753161462556508,1.019563803240548,0.8983270322516629,1.0577868309700669,};
		distribution.add(arrivals22);
		distribution.add(departures22);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
		
		 double[] arrivals23 = {0.0,0.015060895267932238,0.015694447298748804,0.026829194866951463,0.02867099023997707,0.050497316020484115,0.05875637997242766,0.06135481548261614,0.08245645054470396,0.14400458684410353,0.15187247137215348,0.15284394928781406,0.1596701809552221,0.1609116873453063,0.16779709208862048,0.1713647741287359,0.1720041981096691,0.19058635379678063,0.1908720215071354,0.19154320506877123,0.20978945034740948,0.23343865546682951,0.24114553116886436,0.2531176123537876,0.25546616330796307,0.27292399114512994,0.2740686626691797,0.2758511276688354,0.300724114679275,0.3026564327975079,0.30388490446231703,0.3069277298581455,0.31981065881667026,0.3248774602371506,0.32553073926461723,0.3302352368868776,0.34105425900109515,0.3440438108176466,0.350747660505705,0.37232825373828254,0.3773109894718609,0.37838945773373595,0.3906170546184407,0.3977491022172666,0.4069105365157073,0.40850771415243814,0.41024990448353593,0.45152783801004215,0.47053930586677295,0.4840513283873852,0.49654567099507335,0.4997419297210262,0.5198700254572912,0.5237475485086838,0.5301422911928239,0.5363364182904602,0.5375805319106587,0.5444291469965279,0.5512291043008273,0.5647279544167998,0.5823491165187273,0.5862798671841842,0.6011736612301537,0.6117067721360706,0.6119445764607491,0.6162203068149144,0.6194969521523197,0.6399852925929805,0.6503239808608345,0.673365153571688,0.7070285177433951,0.721246004708648,0.7375551742471982,0.748667601522758,0.7491167325824623,0.7559173981755527,0.7597435963398622,0.7701792155134136,0.7741822910080197,0.7834413354484583,0.8205886070116938,0.8273674821783749,0.8500146060748707,0.8655338558090093,0.8662004828186589,0.8662273110620563,0.8712917681137442,0.8726387706977446,0.924769996299957,0.9348405490342827,0.9644929206430414,0.9786977260717316,0.9788717512478974,0.9903781582168121,0.9915724399417761,0.9929452298342573,0.9929475695902663,1.006885389229134,1.0086911730044938,1.015166000278697, }; 
		 double[] departures23 = {0.40996308987684743,0.07467872646027054,0.06272097293195955,0.03550316737517707,0.1658259313823966,0.05915100884185998,0.22480472199571036,0.15658851412958188,0.11010743016180782,0.5382724705067655,0.15543544509705312,0.36750164101195004,0.28701368713927616,0.24435556911272985,0.29403319848516374,0.17907472750606354,0.18562983245720618,0.4567634479318931,0.19279552215695023,0.34446730655351454,0.3803594040299798,0.32729474109734535,0.28244144181877257,0.31639798874437247,0.274457031678758,0.34179611234122786,0.31360245330686054,0.4508363791776878,0.4870130868059537,0.3202942521073248,0.48616443640232554,0.31655229099862625,0.5253684301199137,0.5010718277895906,0.49975864757576993,0.3424218735636436,0.43866489740401526,0.4489766079014903,0.4869347884312319,0.40389802406738956,0.429992491667041,0.40763988295020803,0.41959173874055367,0.5285398009631608,0.48507673928418393,0.4127172341479979,0.5311011055644537,0.6129259291160065,0.5383232206055626,0.5863557892675852,0.5623525248071111,0.5215945507325386,0.5245621313898797,0.5590322308924242,0.630782963427716,0.604710010774056,0.6870381270969792,0.7986120302710673,0.5980969371348952,0.607188457039293,0.6850970611281955,1.1489056960328292,0.6722048223021461,0.6404693528610735,0.8489491888521841,0.6566949662039137,0.8124330216597667,1.034370326474976,0.665360701793781,0.8292543726360077,0.7371106692198843,0.9362200074901292,0.8554101229257544,1.0169969139759512,1.361764009842319,0.8641994555741673,0.8132630385384076,0.8077573558298665,0.7746030479490447,0.9089570823882018,0.8661590979193557,0.9159473765315007,0.9498843415905112,0.886860374401176,0.8751785648715986,0.8934325021649032,0.9260405819193082,0.9231107493039161,0.9673652867933463,1.1554264311653175,1.0406349723199662,1.3204616061143801,0.9802974950382631,1.0245934666284349,1.0153058158773742,1.2304879282339651,1.0274706395260753,1.0317509174999493,1.0773831772763214,1.0498556760134752,};
		distribution.add(arrivals23);
		distribution.add(departures23);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
		
		double[] arrivals24 = {0.0,0.013841806317311325,0.014804789425796386,0.016611242250911193,0.02936872298304903,0.03360147551451277,0.08424985636837443,0.08765700746539525,0.09319918432686107,0.09867209969227034,0.11461363211028922,0.11577349433326643,0.11735347703766008,0.12113272214191662,0.15306695183520425,0.1684064715849162,0.19264580987670465,0.1937899581101277,0.21874456723736255,0.23292736814709952,0.24897979385893443,0.26917856006517515,0.270703208100148,0.3009506191004531,0.3141174411642692,0.31697120559675646,0.34495012026849603,0.34532674654026824,0.3509731301299149,0.3521328919511244,0.3608247881166303,0.3875048618753276,0.39519977729488426,0.3954421147968655,0.4005470531957322,0.40641737876739004,0.41511527854946595,0.42008815793565446,0.43040394760521966,0.43931456953802356,0.4412126741050054,0.44182466342667687,0.4434762887049312,0.4452093174131747,0.4489784303379256,0.4596112855502652,0.46391219691357993,0.4895115903453414,0.5050224110756157,0.5137926153186284,0.5314584203984445,0.5334167091598975,0.5493297332478365,0.5555948708746641,0.5626141864669059,0.5641706294171105,0.5681094165118661,0.575131339704315,0.5780474230888116,0.5795685867657057,0.593764162095631,0.6023272019454652,0.6059290691921291,0.6064681639929848,0.6084338169373129,0.6266453932689694,0.6415056606783553,0.6556152564762627,0.6573060267253512,0.6593792502753585,0.6658858869183555,0.667896766207453,0.6684070864250482,0.6741137934346534,0.6935314003382016,0.6950313432989071,0.7003188113131718,0.7066340417807078,0.7076481254013309,0.7357351363698117,0.7477422142935127,0.7674199106574078,0.7786743556959056,0.7793749249860291,0.7871280432673915,0.7885583081864211,0.7997782558328336,0.8034403035568937,0.8059617613740625,0.818866706835024,0.8724364637152563,0.9342353166516922,0.9370182507721135,0.9393730356240307,0.9469504292561521,0.9630728863832866,0.9810737018923638,0.9909201154170608,0.9936993554112464,1.0101454017183058, }; 
		double[] departures24 = {0.0032304321788493417,0.06332782240637895,0.0526032160522322,0.11820277671669335,0.0775740745435913,0.1727902053441066,0.10030153522280798,0.09301009946451234,0.1268025390575075,0.2601034874217497,0.27768916018633005,0.21489494082232843,0.3352333911507718,0.22280521729727631,0.2195312730509908,0.20149462325848216,0.410943066998415,0.2837163168452189,0.48005671060800414,0.24132804093585836,0.3127589548706746,0.5704951688224853,0.3045132491553291,0.5704967063658658,0.6948824501451552,0.36436720946291545,0.35046452216485335,0.3846576040393493,0.4316723276279645,0.3946239567051508,0.4215359521183164,0.39556825855471184,0.4445041790257593,0.4176428371647475,0.510956188029256,0.4460903624111411,0.5691772165355707,0.44051288356157786,0.446200305310172,0.5040853377063176,0.6872454434772438,0.5271538783609926,0.8529416010512894,0.45352712228306064,0.6604147493356661,0.578936989862819,0.5013254815246578,0.7413600039705606,0.5449593353582348,0.5590845564754865,0.5801412053174603,0.5677721393840904,0.5734160944610471,0.5892810108282084,0.6422909190072148,0.6328401862831449,0.5790728406516349,0.7898815789613234,0.6970375265887313,0.6088673451421219,0.6262737771280956,0.7209189221460844,0.7030581787034219,0.6359392058148139,0.6540537241482353,0.7522393594090809,0.8217638911820065,0.6580905639152205,0.8079657074963408,0.745822384892417,0.7830999821480471,0.6707198390707615,0.7171072720543362,0.6786588578852453,0.9906890331539917,0.7224284072012198,0.8079026420941333,0.7962079672967747,0.7410550093914309,0.7421200806508802,0.8543964884534524,0.9390710608118189,0.8202686580444551,0.7900777595477712,0.8113094668555105,0.8291495473836318,0.8483525437750841,0.846475069407428,0.8888668060836535,0.9977488177477165,0.9960414216550968,0.9544416712587332,0.9473143400493078,1.0113466838230294,1.069234719146465,1.0024398495088989,1.0758009220265128,1.1262682189286364,1.013054408781095,1.1925943289158056,};
		distribution.add(arrivals24);
		distribution.add(departures24);
		sets.add(distribution);
		distribution = new ArrayList<double[]>(); 
	
		return sets;
	}
	
	
	public static void main(String [] args) throws IloException, IOException
	{
		//poisson parameters
		Poisson poissonDistribution ;
		int [] arrivalRates = {20,40,60,80,100};
		int departureRate = 10;
		
		//requests parmeters
		int requestsNb = 10;		
		int minVms = 5;
		int maxVms = 10;
		int minBw = 50;
		int maxBw = 200;
		double alpha = 1;
		//number of sets per load to test
		int setsNb = 5;
		
		FatTreeNetwork treeNetwork = null;	
		VMsProtectionWithBandwidthGuarantee  vmsProtection;
		ArrayList <ArrayList<int[]>> vmSets =null;
		ArrayList <ArrayList<double[]>> poissonSets =null;
		ArrayList<int[]> randomVmsBw = new ArrayList<int[]>();
		ArrayList<Request> sortedRequests = new ArrayList<Request>();
		
		ArrayList<int[]> backupToVmMappingModelTestBw = null;
		ArrayList<int[]> backupEmbeddingBaselineBw = null;
		
		int nbsets = 0;
		
		//create a test status file
		String testStatusFile = "TestResults/TestStatus.txt";
		FileManipulation statusFile  = new FileManipulation(testStatusFile);
		
	
		
		vmsProtection = new VMsProtectionWithBandwidthGuarantee(treeNetwork, sortedRequests);	
		
		//get the sets of requests vm/bw /distribution already generated that we want to run on another network
		vmSets = vmsProtection.intializeVmsSets();
		poissonSets = vmsProtection.intializePoissonSets();
		
		statusFile.writeInFile("\n=============================Network : FatTreeNetwork(128,6,2,2,32,1000,10000,10000);=============================\n");
		statusFile.writeInFile("\n=============================Requests: Nb : "+requestsNb+"=== VMs range : <"+minVms+","+maxVms+"> === Bw range : <"+minBw+","+maxBw+" >=============================\n");
		
		//for each load we need to test the 2 algorithm on 5 sets  each
		for (int i = 0; i<arrivalRates.length; i++)
		{
			statusFile.writeInFile("\n=============================LOAD : "+arrivalRates[i]/departureRate+"===Arrival : "+arrivalRates[i]+"===Departure : "+departureRate+"=============================\n");
			//set the load
			poissonDistribution = new Poisson(arrivalRates[i],departureRate,requestsNb);		
		
			for (int j=0; j<setsNb; j++)
			{
				//the below 3 lines are only needed when running a predefined sets of requests
				randomVmsBw = vmSets.get(nbsets);
				poissonDistribution.arrivals = poissonSets.get(nbsets).get(0);
				poissonDistribution.departures = poissonSets.get(nbsets).get(1);
				
				//generate the requests
				//randomVmsBw = vmsProtection.generateRequests(requestsNb, minVms,maxVms,minBw,maxBw);
		
				statusFile.writeInFile("====Starting test for set "+j+"====\n");
				statusFile.writeInFile("=Starting test for backupToVmMappingModelTest()=\n");
				
				backupToVmMappingModelTestBw = vmsProtection.automatedTesting("backupToVmMappingModelTest",randomVmsBw,poissonDistribution, j,0);
				
				statusFile.writeInFile("=Starting test for backupEmbeddingBaseline(2)=\n");
				
				backupEmbeddingBaselineBw = vmsProtection.automatedTesting("backupEmbeddingBaseline",randomVmsBw,poissonDistribution, j,0);
				
				/*statusFile.writeInFile("=Starting test for VMProtectionModelTest()=\n");
				
				vmsProtection.automatedTesting("VMProtectionModelTest",randomVmsBw,poissonDistribution, j,alpha);
				
				statusFile.writeInFile("====Finished test of set "+j+"====\n\n");*/
				vmsProtection.printRequestsInformation(arrivalRates[i]/departureRate, j, backupToVmMappingModelTestBw, backupEmbeddingBaselineBw);
				nbsets++;
			}
		}
		
		
		//Below block is not needed when caling the printRequestsInformation()
		//writing reserved bandwidth for each admitted request by both algo
		
		//file containing ids of requests admitted by both algorithms
		/*String admittedRequestsIdFile = "TestResults/admittedRequestsId.txt";
		FileManipulation admittedRequestsFile  = new FileManipulation(admittedRequestsIdFile);
		
		String backupToVmMappingModelTestRequestsBwFile = "TestResults/backupToVmMappingModelTestRequestsBw.txt";
		FileManipulation  backupToVmMappingModelTestBwFile  = new FileManipulation(backupToVmMappingModelTestRequestsBwFile);
		
		String backupEmbeddingBaselineRequestsBw = "TestResults/backupEmbeddingBaselineRequestsBw.txt";
		FileManipulation backupEmbeddingBaselineBwFile  = new FileManipulation(backupEmbeddingBaselineRequestsBw);
		
		NetworkStatus networkStatus = new NetworkStatus(treeNetwork, vmsProtection.requests);
		ArrayList<int[]> requestsBwComparision = null;
		requestsBwComparision = networkStatus.requestsBandwidthConparison(backupToVmMappingModelTestBw, backupEmbeddingBaselineBw);
		
		admittedRequestsFile.writeInFile("====Admitted requests by both algo====\n");
		backupToVmMappingModelTestBwFile.writeInFile("====Reserved bandwidth for admitted requests  by backupToVmMappingModelTest () ====\n");
		backupEmbeddingBaselineBwFile.writeInFile("====Reserved bandwidth for admitted requests  by backupEmbeddingBaseline () ====\n");
		System.out.println("====Admitted requests by both algo");
		for (int i=0; i< requestsBwComparision.size(); i++)
		{
			//skip requests that were not admitted by both algorithms
			if (requestsBwComparision.get(i)[1] == -1 || requestsBwComparision.get(i)[2] == -1)
			{
				continue;
			}
			
			admittedRequestsFile.writeInFile(requestsBwComparision.get(i)[0]+"\n");
			backupToVmMappingModelTestBwFile.writeInFile(requestsBwComparision.get(i)[1]+"\n");
			backupEmbeddingBaselineBwFile.writeInFile(requestsBwComparision.get(i)[2]+"\n");
		}*/
		
	}
}
