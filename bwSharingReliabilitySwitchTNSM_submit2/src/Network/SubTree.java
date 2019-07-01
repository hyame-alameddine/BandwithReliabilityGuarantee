package Network;
import java.util.ArrayList;

import HelperClasses.Search;
import Network.VirtualMachine.Type;


public class SubTree {
	
	//switch root node
	public Node rootNode;
	
	//tree network for which the subTree belongs
	public FatTreeNetwork fatTreeNetwork;
	
	//list of all switches in the sub tree
	public ArrayList <Switch> switches; 
	
	//list of all physical machines of the sub tree
	public	ArrayList <PhysicalMachine> physicalMachines; 
	
	//list of all links in the sub tree
	public ArrayList <Link> links;
	
	
	public SubTree(Node rootNode)
	{
		this.rootNode = rootNode;
		this.switches = new ArrayList<Switch>();
		
		//at some case the root node can be a physical machine(not best practice but needed in isBandwidthAvailable)
		if(this.rootNode.level !=0)
		{
			this.switches.add((Switch)this.rootNode);
		}
		
		this.physicalMachines =  new ArrayList<PhysicalMachine>();
		this.links =  new ArrayList<Link>();
		this.fatTreeNetwork = null;
	}
	
	
	public SubTree (Node rootNode, ArrayList <Switch> switches, ArrayList <PhysicalMachine> physicalMachines, ArrayList <Link> links, FatTreeNetwork fatTreeNetwork)
	{
		this.rootNode = rootNode;
		this.switches = switches;
		this.physicalMachines = physicalMachines;
		this.links = links;
		this.fatTreeNetwork = fatTreeNetwork;
	}
	
	
	/**
	 * This function clones a sub tree
	 * 
	 * @param clonedNetwork that sub tree elements should be part of
	 * @return cloned subtree
	 */
	public SubTree clone (FatTreeNetwork clonedNetwork)
	{
		Search search = new Search();
		SubTree s;
		Node newRootNode = search.getClonedNode(clonedNetwork, this.rootNode);
				
		s = new SubTree (newRootNode);
		
		//this will build the subtree by setting its element from the cloned network (this will be a valid clone)
		clonedNetwork.buildSubTree(s, newRootNode);
		
		return s;
		
	}
	
	
	/**
	 * This function returns the link with the specified source node
	 * 
	 * @param sourceNode 
	 * 
	 * @return
	 */
	public Link searchLink (Node sourceNode)
	{
		Link l=null;
		
		for(int i=0; i<links.size(); i++)
		{
			l = links.get(i);
			
			if (l.sourceNode.equals(sourceNode))
			{
				return l;
			}
		}
		
		return null;
		
	}
	
	
	/**
	 * This function returns the number of VMs allocated under the specified switch
	 * 
	 * @param physicalVMAllocation array list of <pmId, VmAllocated> for the request
	 * @param switch s, switch  which belongs to this subtree that we want to get the VM allocated under it 
	 * 
	 * @return number of VMs allocated in the pm under the switch for the specified allocation
	 */
	public int getAllocatedVM (ArrayList <int[]> physicalVMAllocation, Switch s)
	{
		int allocatedVMs = 0;
		int [] allocation ;
		
		for (int i=0; i<physicalVMAllocation.size(); i++)
		{
			allocation = physicalVMAllocation.get(i);
			
			if ( this.belongToSwitchSubtree (s,allocation[0], false))
			{
				allocatedVMs+= allocation[1];
			}
		}
		
		return allocatedVMs;
	}
	
	
	/**
	 * This function returns true if a physical machine belong to the sub tree
	 * having switch s as root
	 * 
	 * @param s switch that is the sub tree root
	 * @param physicalMachineId id of the physical machine that we want to check
	 * @param belongsToSubtree should be false when calling the function holds the result of this recursive function
	 * 
	 * @return boolean
	 */
	public boolean belongToSwitchSubtree (Switch s, int physicalMachineId, boolean belongsToSubtree)
	{
		//loop over the sub tree links
		for ( int i=0; i<this.links.size(); i++)
		{
			//check for the link having the switch as destination node
			if (!links.get(i).destinationNode.equals(s))
			{
				continue;
			}
			
			//if we are not at the TOR switch then we need to call the function again
			if (s.level != 1)
			{
				belongsToSubtree = this.belongToSwitchSubtree ((Switch)links.get(i).sourceNode, physicalMachineId, belongsToSubtree);
				
			}
			
			//we are at level 1 then we should check if the physical machine is under the switch
			if (links.get(i).sourceNode.level == 0 && links.get(i).sourceNode.id == physicalMachineId)
			{
				belongsToSubtree = true;
				
			}
		}
		
		return belongsToSubtree;
		
	}
	
	
	/**
	 * This function specifies if collocation if backup nodes with primary is possible in a childSubtree of the current tree
	 * Collocation is possible if:
	 * 1- the child tree has a hosting server with min nb of hosted Vms
	 * 2-number of backup already hosted for this request on a non hosting server are > = minHostedVms
	 * OR
	 *  there is enough available VMS in the parent tree (this) to host minHostedVms- hostedBackupOnNonHostingServers
	 *  = the remaining number of VMs that should be hosted on non hosting server
	 *  3- request is embedded on more than one server
	 *  
	 * @param childSubTree subtree that we want to collocate on
	 * @param request request that we are trying to embed backup for
	 * @param hostedBackupOnNonHostingServers number of backup that are already hosted for this request on non hosting servers
	 * 
	 * @return  true if collocation is possible
	 */
	public boolean canCollocateBackups (SubTree childSubTree, Request request, int hostedBackupOnNonHostingServers )
	{		
		int minHostedVms =  this.getMinNbOfHostedVMs(request);
		
		/**
		 * check if child tree has a pm with min nb of primary Vms hosted
		 */
		if (childSubTree.getMinNbOfHostedVMs(request) != minHostedVms)
		{
			return 	false;
		}
		
		//if the request is embedded on one server only we can not collocate
		if (request.subtree.getHostingServers(request).size() == 1)
		{
			return false;
		}
		
		/**
		 * At this point we are sure that the child tree has a hosting server with min nb of hosted Vms
		 * To decide if we can collocate we need to make sure of :
		 * 1- number of backup already hosted for this request on a non hosting server are > = minHostedVms
		 * OR
		 * 2- there is enough available VMS in the parent tree (this) to host minHostedVms- hostedBackupOnNonHostingServers
		 *  = the remaining number of VMs that should be hosted on non hosting server
		 */
		if (hostedBackupOnNonHostingServers >= minHostedVms)
		{
			return true;
		}
		
		if (this.getAvailableVms() >= minHostedVms - hostedBackupOnNonHostingServers)
		{
			return true;
		}
		
		return false;
		
	}
	
	/**
	 * This function get the available vms in the sub tree
	 * 
	 * @return number of available vms
	 */
	public int getAvailableVms ()
	{
		int availableVms = 0;
		
		for (int i =0; i<this.physicalMachines.size(); i++)
		{
			availableVms+=this.physicalMachines.get(i).getAvailableVM();
		}
		
		return availableVms;
	}
	
	
	/**
	 * This function returns the physical machine in the subtree based on its id
	 * @param id physical machine id
	 * @return physical machine
	 */
	public PhysicalMachine getPhysicalMachine( int id)
	{
				
		for ( int i=0; i<this.physicalMachines.size(); i++)
		{
			if (physicalMachines.get(i).id == id)
			{
				return physicalMachines.get(i);
			}
		}
		
		return null;
	}
	
	
	/**
	 * This function return the child trees (1 level less than the root)
	 * of the specified tree
	 * 
	 * @return arrayList of child subtrees;
	 */
	
	public ArrayList<SubTree> getChildTrees ()
	{
		ArrayList<SubTree> childTrees = new ArrayList <SubTree>();
		Link l;
		SubTree s = null;
		PhysicalMachine pm;
		
		//loop over subtree links
		for(int i = 0; i<this.links.size(); i++)
		{ 
			l = this.links.get(i);
			
			//check  the link directly related to the rootNode; they will specify the child trees
			if (l.destinationNode.equals( this.rootNode))
			{
				//create subtree having link source as rootNode
				if(this.rootNode.level != 1)
				{
					s = new SubTree(l.sourceNode);
					this.fatTreeNetwork.buildSubTree(s, l.sourceNode);
				}
				else
				{
					// in this case the subtree is a physical machine, we will  only set the available Vms instead of building the tree
					s = new SubTree(l.sourceNode);
					pm = (PhysicalMachine)s.rootNode;
					
				}
				
				childTrees.add(s);
			}
		}
		 
		return childTrees;
		
	}
	
	
	
	/**
	 * This function gets the minimum number of primary hosted Vms for the specified request
	 * under a single server
	 * 
	 * @param request request to get the physical server with least hosted Vms
	 * @return minimum number of hosted VMs on a server for the request
	 */
	public int getMinNbOfHostedVMs (Request request)
	{
		PhysicalMachine pm = null;
	
		int hostedVms = 0;
		int leastHostedVms = 0;
		
		for (int i=0; i<this.physicalMachines.size(); i++)
		{
			pm = this.physicalMachines.get(i);
			hostedVms = pm.getHostedVms (request);
			
			//initialize leastHostedVms
			if (hostedVms!=0 && leastHostedVms == 0)
			{
				leastHostedVms = hostedVms;
			}
			else if (hostedVms!=0 && hostedVms < leastHostedVms)
			{
				leastHostedVms = hostedVms;				
			}
		}
		
		return leastHostedVms;
	}
	
	
	/**
	 * This function calculates the number of hosted Vms for a specified request in a sub tree
	 * This number may not be = N total number of request Vms specially if we are checking
	 * the hosted Vms in a chilTree of the main tree where the request is hosted
	 * 
	 * @param request request to check the number of hosted Vms for
	 * 
	 * @return number of hosted Vms for the request
	 */
	public int getHostedVms (Request request)
	{
		int hostedVms =0;
		
		for (int i=0; i<this.physicalMachines.size(); i++)
		{
			hostedVms += this.physicalMachines.get(i).getHostedVms (request);
		}
		
		return hostedVms;
	}
	
	
	/**
	 * This function return an array of physical server of this subtree
	 * that are not hosting any primary Vms for the specified request
	 * 
	 * @param request
	 * 
	 * @return array of servers not hosting any primary Vms for the specified request
	 */
	public ArrayList <PhysicalMachine> getNonHostingServers (Request request)
	{
		ArrayList <PhysicalMachine> nonHostingServers = new ArrayList<PhysicalMachine>();
		
		for (int i=0; i<this.physicalMachines.size(); i++)
		{
			if (this.physicalMachines.get(i).getHostedVms(request) ==0)
			{
				nonHostingServers.add(this.physicalMachines.get(i));
			}			
		}
		
		return nonHostingServers;
	}
	
	
	/**
	 * This function return an array of physical server of this subtree
	 * that are hosting any primary Vms for the specified request
	 * 
	 * @param request
	 * 
	 * @return array of servers hosting any primary Vms for the specified request
	 */
	public ArrayList <PhysicalMachine> getHostingServers (Request request)
	{
		ArrayList <PhysicalMachine> hostingServers = new ArrayList<PhysicalMachine>();
		
		for (int i=0; i<this.physicalMachines.size(); i++)
		{
			if (this.physicalMachines.get(i).getHostedVms(request)!=0)
			{
				hostingServers.add(this.physicalMachines.get(i));
			}			
		}
		
		return hostingServers;
	}
	
	
	/**
	 * This function return the parent tree of the current sub tree
	 * 
	 * @return parent sub tree
	 */
	public SubTree getParentTree()
	{
		SubTree parentTree = null;
		Link [] links = null;
		
		links = this.fatTreeNetwork.getLinksSetPerTreeLevel(this.rootNode.level+1);
		for (int i = 0; i< links.length; i++)
		{
			if (links[i].sourceNode.equals(this.rootNode))
			{
				parentTree = new SubTree (links[i].destinationNode);
				this.fatTreeNetwork.buildSubTree(parentTree, links[i].destinationNode);
				break;
			}
		}
		
		return parentTree;
	}
	
	
	/**
	 * This function returns the number of active VMs (primary+backup) active 
	 * upon failure of the specified pm in this tree regardless if backup is backing up the failed server or not
	 * 
	 * @param r request to check its active Vms
	 * @param pm server that fails
	 * 
	 * @return number of active Vms
	 */
	public Double getActiveVms (Request r, PhysicalMachine pm)
	{
		Double activeVms = 0.0;//needed to calculate fault tolerance
		VirtualMachine vm = null;
		
		for (int i=0; i<r.virtualMachinesSet.size(); i++)
		{
			vm = r.virtualMachinesSet.get(i);
			
			//we will evaluate the primary Vms and check the backup of the failed ones
			if (vm.vmType == Type.BACKUP)
			{
				continue;
			}
			
			//if the vm belongs to the failed server don't count it
			if (vm.pm.equals(pm))
			{
				//prevent null pointer exception if we r running for a request with no protection
				if (vm.backupVm!=null)
				{
					//check if the backups of the failed vm are hosted in the subtree to count them taking into consideration that the subtree may be only a server
				
					if (this.rootNode instanceof PhysicalMachine )
					{
						if(vm.backupVm.pm.equals((PhysicalMachine)this.rootNode))
						{
							activeVms++;
						}
					}
					else if (this.belongToSwitchSubtree((Switch) this.rootNode, vm.backupVm.pm.id, false))
					{
						activeVms++;
					}
				}
				continue;
			}
			
			//if the vM belongs to a server in the subtree , count it  
			if (this.rootNode instanceof PhysicalMachine )
			{
				if (vm.pm.equals((PhysicalMachine)this.rootNode))
				{
					activeVms++;
				}
			}
			else if (this.belongToSwitchSubtree((Switch) this.rootNode, vm.pm.id, false))
			{
				activeVms++;
			}
			
		}
		
		
		return activeVms;
	}
	
	
	/**
	 * This function orders the physical machines by descending order
	 * of their available Vms
	 * 
	 * @param physicalMachinesList array list of physical machines to order
	 * @return descending ordered list of physical machines
	 */
	public ArrayList<PhysicalMachine> orderMachinesByAvailableVms (ArrayList<PhysicalMachine> physicalMachinesList)
	{
		PhysicalMachine tempPm1, tempPm2;
	
		
		for (int i=0; i<physicalMachinesList.size(); i++)
		{	
			tempPm1 = physicalMachinesList.get(i);
			
			for ( int j=i+1; j< physicalMachinesList.size(); j++)
			{	
				tempPm2 = physicalMachinesList.get(j);
				
				//compare the number of available VMs
				if (tempPm2.getAvailableVM() > tempPm1.getAvailableVM())
				{					
					physicalMachinesList.set (i,tempPm2);
					physicalMachinesList.set (j,tempPm1);
				}
			}
		}
		
		return physicalMachinesList;
		
	}
}
