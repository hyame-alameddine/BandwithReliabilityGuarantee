package Network;
import java.nio.file.LinkOption;
import java.util.ArrayList;

import Network.Link.BandwidthType;
import Network.VirtualMachine.Type;

/**
 * This class aims to generate and manage the tree network
 * it includes generation of physical machines switches and
 * set of links between them 
 * 
 * @author Hyame
 *
 */
public class FatTreeNetwork {
	
	public static final int HEIGHT = 3;
	
	/***********************************************
	 * basic information needed to define the tree
	 ***********************************************/
	
	//number of physical machines in the network
	public int nbOfPhysicalMachines;
	
	//number of virtual machines per each physical machine
	public int nbOfVMPerPhysicalMachine;
	
	//number of physical machines per each TOR switch
	public int nbOfPhysicalMachinesPerTor;
	
	//number of TOR switches per aggregate switch
	public int nbOfTorPerAgg;
	
	//number of aggregate switches per each core switch
	public int nbOfAggPerCore;
	
	//capacity of links between physical machines and TOR switches
	public int pmToTorLinkCapacity;
	
	//capacity of links between TOR and aggregate switches
	public int torToAggregateLinkCapacity;
	
	//capacity of link between aggregate and core switches
	public int aggregateToCoreLinkCapacity;
	
	/***********************************************
	 * Defining tree network elements
	 ***********************************************/
	
	//set of physical machines of the network
	public PhysicalMachine physicalMachinesSet [];
	
	//set of TOR switches
	public Switch torSwitchSet [];
	
	//set of aggregate switches
	public Switch aggregateSwitchSet[];
	
	//set of core switches
	public Switch coreSwitchSet[];
	
	//set of links between TOR switches and physical machines
	public Link pmToTorLinkSet[];
	
	//set of links between aggregate switches and TOR switches
	public Link torToAggregateLinkSet[];
	
	//set of switches between core switch and aggregate switches
	public Link aggregateToCoreLinkset [];
	
	
	/**
	 * Constructor
	 * 
	 * 
	 * @param nbOfPhysicalMachines
	 * @param nbOfVMPerPhysicalMachine
	 * @param nbOfPhysicalMachinesPerTor
	 * @param nbOfTorPerAgg
	 * @param nbOfAggPerCore
	 * @param pmToTorLinkCapacity
	 * @param torToAggregateLinkCapacity
	 * @param aggregateToCoreLinkCapacity
	 * 
	 */
	public FatTreeNetwork( int nbOfPhysicalMachines, int nbOfVMPerPhysicalMachine, int nbOfPhysicalMachinesPerTor,
			int nbOfTorPerAgg, int nbOfAggPerCore, int pmToTorLinkCapacity, int torToAggregateLinkCapacity, int aggregateToCoreLinkCapacity )
	{		
		this.nbOfPhysicalMachines = nbOfPhysicalMachines;
		this.nbOfVMPerPhysicalMachine = nbOfVMPerPhysicalMachine;
		this.nbOfPhysicalMachinesPerTor = nbOfPhysicalMachinesPerTor;
		this.nbOfTorPerAgg = nbOfTorPerAgg;
		this.nbOfAggPerCore = nbOfAggPerCore;
		this.pmToTorLinkCapacity = pmToTorLinkCapacity;
		this.torToAggregateLinkCapacity = torToAggregateLinkCapacity;
		this.aggregateToCoreLinkCapacity = aggregateToCoreLinkCapacity;
	
	}
	
	
	/**
	 * This function clones a tree network with all its elements
	 */
	public FatTreeNetwork clone()
	{
		FatTreeNetwork network = new FatTreeNetwork(this.nbOfPhysicalMachines, this.nbOfVMPerPhysicalMachine, this.nbOfPhysicalMachinesPerTor, this.nbOfTorPerAgg, this.nbOfAggPerCore, this.pmToTorLinkCapacity, this.torToAggregateLinkCapacity, this.aggregateToCoreLinkCapacity);
		PhysicalMachine newPhysicalMachinesSet [] = new PhysicalMachine [this.physicalMachinesSet.length];
		Switch newTorSwitchSet [] = new Switch [this.torSwitchSet.length];
		Switch newAggregateSwitchSet[] = new Switch [this.aggregateSwitchSet.length];
		Switch newCoreSwitchSet[] = new Switch [this.coreSwitchSet.length];
		Link newPmToTorLinkSet[] = new Link [this.pmToTorLinkSet.length];
		Link newTorToAggregateLinkSet[] = new Link [this.torToAggregateLinkSet.length];
		Link newAggregateToCoreLinkset [] = new Link [this.aggregateToCoreLinkset.length];
		
		for (int i=0; i< this.physicalMachinesSet.length; i++)
		{
			//this will not clone the backupVm and the request for the virtual machines of the physical server
			newPhysicalMachinesSet[i] =  this.physicalMachinesSet[i].clone();
		}
		
		for (int i=0; i< this.torSwitchSet.length; i++)
		{
			newTorSwitchSet[i] =  this.torSwitchSet[i].clone();
		}
		
		for (int i=0; i< this.aggregateSwitchSet.length; i++)
		{
			newAggregateSwitchSet[i] =  this.aggregateSwitchSet[i].clone();
		}
		
		for (int i=0; i< this.coreSwitchSet.length; i++)
		{
			newCoreSwitchSet[i] =  this.coreSwitchSet[i].clone();
		}
		
		network.physicalMachinesSet = newPhysicalMachinesSet;
		network.torSwitchSet = newTorSwitchSet;
		network.aggregateSwitchSet = newAggregateSwitchSet;
		network.coreSwitchSet = newCoreSwitchSet;
		
		//this will not set the requests for the cloned sharing sets of the links. It will be set in vmProtection.clone()
		for (int i=0; i< this.pmToTorLinkSet.length; i++)
		{
			newPmToTorLinkSet[i] =  this.pmToTorLinkSet[i].clone(network);
		}
		
		for (int i=0; i< this.torToAggregateLinkSet.length; i++)
		{
			newTorToAggregateLinkSet[i] =  this.torToAggregateLinkSet[i].clone(network);
		}
		
		
		for (int i=0; i< this.aggregateToCoreLinkset.length; i++)
		{
			newAggregateToCoreLinkset[i] =  this.aggregateToCoreLinkset[i].clone(network);
		}
		
	
		network.pmToTorLinkSet = newPmToTorLinkSet;
		network.torToAggregateLinkSet = newTorToAggregateLinkSet;
		network.aggregateToCoreLinkset = newAggregateToCoreLinkset;
		
		return network;
		
	}
	
	/**
	 * This function generate the set of physical machines of the network
	 * 
	 */
	public void generatePhysicalMachines()
	{		
		this.physicalMachinesSet = new PhysicalMachine [this.nbOfPhysicalMachines];
		
		for (int i=0; i<this.nbOfPhysicalMachines; i++)
		{
			PhysicalMachine physicalMachine = new PhysicalMachine (i,this.nbOfVMPerPhysicalMachine);
			this.physicalMachinesSet[i] = physicalMachine;
			
		}
				
	}
	
	
	/**
	 * This function returns the number of switches based on switch type
	 * 
	 * @param switchType switch type can have the following values: 
	 * 		TOR_TYPE, AGGREGATE_TYPE, CORE_TYPE
	 * 
	 * @return int number of switches 
	 */
	public int getNbOfSwitchPerType (String switchType)
	{		
		int nbOfSwitches = 0;
		
		switch (switchType) {
			case Switch.TOR_TYPE:
				nbOfSwitches = this.nbOfPhysicalMachines/this.nbOfPhysicalMachinesPerTor;
				break;
			case Switch.AGGREGATE_TYPE:
				nbOfSwitches = this.nbOfPhysicalMachines/this.nbOfPhysicalMachinesPerTor/this.nbOfTorPerAgg;				
				break;
			case Switch.CORE_TYPE:
				nbOfSwitches = this.nbOfPhysicalMachines/this.nbOfPhysicalMachinesPerTor/this.nbOfTorPerAgg/this.nbOfAggPerCore;
				break;		
		}
		
		return nbOfSwitches;
	}
	
	
	/**
	 * This function generates the switches based on the switchType parameter
	 * 
	 * @param switchType switch type can have the following values: 
	 * 		TOR_TYPE, AGGREGATE_TYPE, CORE_TYPE
	 * 
	 * @return switchSet array of switches
	 */
	public Switch[] generateSwitchesbyType (String switchType)
	{		
		//get the number of switches based on the switch type
		int nbOfSwitches = this.getNbOfSwitchPerType(switchType);
		
		//define the switches array
		Switch switchSet[] = new Switch[nbOfSwitches];
		
		for (int i=0; i<nbOfSwitches; i++)
		{			
			Switch s = new Switch (i,switchType);
			switchSet[i] = s;			
		}
		
		return switchSet;
		
	}
	
	
	/**
	 * This function returns the number of links based on link type
	 * 
	 * @param linkType link type can have the following values: 
	 * 		TOR_TO_MACHINE_TYPE, AGGREGATE_TO_TOR_TYPE, CORE_TO_AGGREATE_TYPE
	 * 
	 * @return int number of links 
	 */
	public int getNbOfLinksPerType (String linkType)
	{		
		int nbOfLinks = 0;
		
		switch (linkType) 
		{
			case Link.MACHINE_TO_TOR_TYPE:
				nbOfLinks = this.nbOfPhysicalMachines;
				break;
			case Link.TOR_TO_AGGREGATE_TYPE:
				//number of links between the aggregate switches and the TOR is equal to the number of TOR switches
				nbOfLinks = this.getNbOfSwitchPerType(Switch.TOR_TYPE);
				break;
			case Link.AGGREATE_TO_CORE_TYPE:
				//number of links between the core switches and the aggregate switches is equal to the number of aggregate switches
				nbOfLinks = this.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE);
				break;
		}
			
		return nbOfLinks;
	}
	
	
	/**
	 * This function will generate the set of links of the network
	 * 
	 * @param linkType specified the link type which we want to generate
	 *  can have the following values: MACHINE_TO_TOR_TYPE, TOR_TO_AGGREGATE_TYPE, AGGREATE_TO_CORE_TYPE
	 *  
	 * @return linksSet an array of the network links for a certain type
	 */
	public Link [] generateLinksPerType (String linkType)
	{
		//get the number of links based on the link type
		int nbOfLinks = this.getNbOfLinksPerType(linkType);
		
		//define the links array
		Link linksSet [] = new Link [nbOfLinks];
		
		/**
		 * determines the number of links with the same destination node in order
		 * to know at how many iterations to change the destination node
		 */
		int nbOflinksWithSameDestinationNode = 1;
		
		//defines the link capacity
		int capacity = 0;
		
		//this specifies the link id where to start 
		int continiousIdStart = 0;
		
		//determines the nodes set to use as source and destination nodes
		Node destinationNodesSet [] = null;
		Node sourceNodesSet[] = null;
		
		//defines destination node
		Node destinationNode = new Node();
		
		int count = 0;
		
		/******************************************
		 * Setting the variables based on linkType
		 ******************************************/
		
		switch (linkType)
		{		
			case Link.MACHINE_TO_TOR_TYPE:
				nbOflinksWithSameDestinationNode = this.nbOfPhysicalMachinesPerTor;
				destinationNodesSet = this.torSwitchSet;
				sourceNodesSet =  this.physicalMachinesSet;
				capacity = this.pmToTorLinkCapacity; 
				continiousIdStart = 0;
				break;
			case Link.TOR_TO_AGGREGATE_TYPE:			
				nbOflinksWithSameDestinationNode = this.nbOfTorPerAgg;
				destinationNodesSet = this.aggregateSwitchSet;
				sourceNodesSet =  this.torSwitchSet;
				capacity = this.torToAggregateLinkCapacity; 
				continiousIdStart = this.getNbOfLinksPerType(Link.MACHINE_TO_TOR_TYPE);
				break;
			case Link.AGGREATE_TO_CORE_TYPE:
				nbOflinksWithSameDestinationNode = this.nbOfAggPerCore;
				destinationNodesSet = this.coreSwitchSet;
				sourceNodesSet =  this.aggregateSwitchSet;
				capacity = this.aggregateToCoreLinkCapacity;
				continiousIdStart = this.getNbOfLinksPerType(Link.MACHINE_TO_TOR_TYPE)+this.getNbOfLinksPerType(Link.TOR_TO_AGGREGATE_TYPE);
				break;
		}
		
		
		/******************************************
		 * generating the links
		 ******************************************/
		for (int i = 0; i<nbOfLinks; i++)
		{	
			//updating the source 
			if (i%nbOflinksWithSameDestinationNode == 0)
			{				
				destinationNode = destinationNodesSet[count];
				count++;
			}
	
			Link link = new Link(i,continiousIdStart+i, sourceNodesSet[i], destinationNode,capacity,linkType );
			linksSet[i] = link;
			
		}
		
		//emptying memory
		destinationNodesSet  = null;
		sourceNodesSet = null;
		destinationNode = null;
		
		return linksSet;
		
	}
	
	
	/**
	 * This function returns an array of all the links in the network.
	 * This is useful for the models
	 * 
	 * @return list of links in the network
	 */
	public Link[] getLinks ()
	{
		int totalLinksNb = this.pmToTorLinkSet.length + this.torToAggregateLinkSet.length + this.aggregateToCoreLinkset.length;
		Link [] links =  new Link [totalLinksNb];
		int count = 0;
		
		for (int i = 0; i<this.pmToTorLinkSet.length; i++)
		{
			links[count] = this.pmToTorLinkSet[i];
			count++;
		}
		
		for (int i = 0; i<this.torToAggregateLinkSet.length; i++)
		{
			links[count] = this.torToAggregateLinkSet[i];
			count++;
		}
		
		for (int i = 0; i<this.aggregateToCoreLinkset.length; i++)
		{
			links[count] = this.aggregateToCoreLinkset[i];
			count++;
		}
		
		return links;
	}
	
	/**
	 * This function generate the tree network by generating and creating the 
	 * 1- Physical machines
	 * 2- Switches
	 * 3- Links
	 */
	public void buildTreeNetwork ()
	{
		//generate the physical machines
		this.generatePhysicalMachines();
		
		//generate the switches
		this.torSwitchSet = this.generateSwitchesbyType(Switch.TOR_TYPE);
		this.aggregateSwitchSet = this.generateSwitchesbyType(Switch.AGGREGATE_TYPE);
		this.coreSwitchSet = this.generateSwitchesbyType(Switch.CORE_TYPE);
		
		//generate the links
		this.pmToTorLinkSet = this.generateLinksPerType(Link.MACHINE_TO_TOR_TYPE);
		this.torToAggregateLinkSet = this.generateLinksPerType(Link.TOR_TO_AGGREGATE_TYPE);
		this.aggregateToCoreLinkset = this.generateLinksPerType(Link.AGGREATE_TO_CORE_TYPE);
		
	}
	
	
	/**
	 * This functions returns the switch set based on tree level
	 * 
	 * @param treeLevel the level of the tree we want to get the switch set for
	 */
	public Switch [] getSwitchSetPerTreeLevel (int treelevel)
	{
		Switch switchSet [];
		
		switch (treelevel)
		{		
			case 1:
				switchSet = this.torSwitchSet;
				break;
			case 2:			
				switchSet = this.aggregateSwitchSet;
				break;
			case 3:
				switchSet = this.coreSwitchSet;
				break;
			default:
				switchSet= this.torSwitchSet;
				break;
		}
		
		return switchSet;
		
	}
	
	
	/**
	 * This functions returns the link set based on tree level
	 * 
	 * @param treeLevel the level of the tree we want to get the switch set for
	 */
	public Link [] getLinksSetPerTreeLevel (int treelevel)
	{
		Link linkSet [];
		
		switch (treelevel)
		{		
			case 1:
				linkSet = this.pmToTorLinkSet;
				break;
			case 2:			
				linkSet = this.torToAggregateLinkSet;
				break;
			case 3:
				linkSet = this.aggregateToCoreLinkset;
				break;
			default:
				linkSet= this.pmToTorLinkSet;
				break;
		}
		
		return linkSet;
		
	}
	
	
	/**
	 * This function return the link with the specified continuousId
	 * 
	 * @param continuousId if of the link to get
	 * 
	 * @return link with the continiousId, null if not found
	 */
	public Link getLinkPerContiniousId(int continuousId)
	{
		Link[] links = this.getLinks();
		
		for (int i =0; i<links.length; i++)
		{
			if (links[i].continuousId == continuousId)
			{
				return links[i];
			}
		}
		
		return null;
	}
	
	
	
	/**
	 * This function return an array of all the links used by the request
	 * The links that the request has reserved bandwidth on 
	 * 
	 * @return set of links that the request uses
	 */
	public ArrayList<Link>  getUsedLinks(int requestId)
	{
		Link [] treeLinks = this.getLinks();
		ArrayList<Link> requestLinks = new ArrayList<Link>();
		
		for (int i=0; i<treeLinks.length; i++)
		{ 
			if (treeLinks[i].getBandwidthAllocatedForRequest(requestId)!=null)
			{
				requestLinks.add(treeLinks[i]);
			}
		}
		
		return requestLinks;
	}
	
	
	
	/**
	 * This function allows building a subtree based on a given switch root node
	 * it will populate all the subtree information (links, switches, physicalMachines, availableVms)
	 * 
	 * @param subTree to build where only the root node is set
	 * @param rootNode new rootNode that changes when looping to a new level
	 * @return subTree
	 */	
	public SubTree buildSubTree (SubTree subTree, Node rootNode)
	{
		PhysicalMachine pm;
		
		subTree.fatTreeNetwork = this;
		
		//links in the tree based on tree level
		Link linksPerLevel  [] = this.getLinksSetPerTreeLevel(rootNode.level);
		
		//loop over the link at each level
		for (int j=0; j<linksPerLevel.length; j++)
		{
			//check if the tree link belong to the current subtree
			if (!linksPerLevel[j].destinationNode.equals(rootNode))
			{
				continue;
			}
			
			//add the link to the subtree
			subTree.links.add(linksPerLevel[j]);
			
			
			if (rootNode.level !=1)
			{
				//add the child switch to the switch array
				subTree.switches.add((Switch)linksPerLevel[j].sourceNode);
				
				//call the function again to add the nodes/links at the lower level having linksPerLevel[j].sourceNode as rootNode
				this.buildSubTree(subTree, linksPerLevel[j].sourceNode);
			}
			else
			{
				//if we are at the lowest level at the subtree then we need to add the physical machines
				pm = (PhysicalMachine)linksPerLevel[j].sourceNode;
				
				//add the physical machines as part of the sub tree
				subTree.physicalMachines.add (pm);
								
			}
														
		}					
		
		//empty memory
		linksPerLevel = null;
		
		return subTree;
	}
	
	
	/**
	 * This function sorts the subTrees in the arrayList based on the number of available VMs in it
	 * Ascending sort
	 * 
	 * @param subTrees array list of subtrees
	 * 
	 * @return sorted subTrees array list
	 */
	public ArrayList <SubTree> orderSubtreesByAvailableVMs(ArrayList <SubTree> subTrees )
	{
		SubTree  TempSubTree1, TempSubTree2;
		
		for ( int i=0; i< subTrees.size(); i++)
		{	
			TempSubTree1 = subTrees.get(i);
			
			for ( int j=i+1; j< subTrees.size(); j++)
			{	
				TempSubTree2 = subTrees.get(j);
				
				//compare the number of available VMs
				if (TempSubTree1.getAvailableVms() > TempSubTree2.getAvailableVms())
				{
					subTrees.set (i,TempSubTree2);
					subTrees.set (j,TempSubTree1);
				}
			}
		}
		
		//empty memory
		 TempSubTree1 = null;
		 TempSubTree2 = null;
		 
		return subTrees;
	}
	
	
	/**
	 * This function sorts the subTrees in the arrayList in an ascending order based
	 * on the residual bandwidth on the link that connects the subtree to the rest of the network
	 * 
	 * @param subTrees array list of subtrees
	 * 
	 * @return sorted subTrees array list
	 */
	public ArrayList <SubTree> orderSubtreesByUpperLinkResidualBandwidth(ArrayList <SubTree> subTrees )
	{
		SubTree  TempSubTree1, TempSubTree2;
		Link l1, l2;
		
		for ( int i=0; i< subTrees.size(); i++)
		{	
			TempSubTree1 = subTrees.get(i);
			l1 = this.searchLink(TempSubTree1.rootNode);
		
			for ( int j=i+1; j< subTrees.size(); j++)
			{	
				TempSubTree2 = subTrees.get(j);
				l2 = this.searchLink(TempSubTree2.rootNode);
			
				//compare the number of available VMs
				if (  l1.bandwidth > l2.bandwidth )
				{
					subTrees.set (i,TempSubTree2);
					subTrees.set (j,TempSubTree1);
				}
			}
		}
		
		//empty memory
		 TempSubTree1 = null;
		 TempSubTree2 = null;
		 l1 = null;
		 l2 = null;
		 
		return subTrees;
	}
	
	
	/**
	 * This function orders the subtrees in descending order based on the number of VMs 
	 * they are hosting for the request
	 * 
	 * @param subTrees sub trees to order
	 * @param request request that we are ordering based on its hosted Vms
	 * 
	 * @return descending order of sub tree list
	 */
	public ArrayList <SubTree> orderSubtreesByHostedVMs (ArrayList<SubTree> subTrees, Request request)
	{
		SubTree  TempSubTree1, TempSubTree2;
			
		for ( int i=0; i< subTrees.size(); i++)
		{	
			TempSubTree1 = subTrees.get(i);
					
			for ( int j=i+1; j< subTrees.size(); j++)
			{	
				TempSubTree2 = subTrees.get(j);
							
				//compare the number of available VMs
				if (  TempSubTree1.getHostedVms(request) < TempSubTree2.getHostedVms(request) )
				{
					subTrees.set (i,TempSubTree2);
					subTrees.set (j,TempSubTree1);
				}
			}
		}
		
		//empty memory
		 TempSubTree1 = null;
		 TempSubTree2 = null;
		 
		return subTrees;
	}
	
	
	/**
	 * This function returns the link with the specified source node
	 * 
	 * @param sourceNode 
	 * 
	 * @return link
	 */
	public Link searchLink (Node sourceNode)
	{
		Link l=null;
		Link [] links = null;
		
		/**
		 * search only in the link set at the same level of the source node
		 */
		switch (sourceNode.level) {
			case 0:
				links = this.pmToTorLinkSet;
				break;
			case 1:
				links= this.torToAggregateLinkSet;				
				break;
			case 2:
				links = this.aggregateToCoreLinkset;
				break;	
			default:				
				break;	
		}
		
		//we are at core level that is not a source node for any link
		if (links == null)
		{
			return null;
		}
		
		for(int i=0; i<links.length; i++)
		{
			l = links[i];
			
			if (l.sourceNode.equals(sourceNode))
			{
				return l;
			}
		}
		
		//empty memory
		links= null;
		
		return null;
		
	}
	
	/**
	 * Recursive function which calculates the overall reserved bandwidth in the network
	 * 
	 * @param level the level of the links in the tree, starting with the lowest level
	 * @param reservedBandwidth should be initially 0, and handle the reserved bandwidth in each level
	 * 
	 * @return reserved bandwidth
	 */
	public int calculateTotalBandwidth( int level, int reservedBandwidth)
	{
		Link [] linkSet;
		
		linkSet = getLinksSetPerTreeLevel(level);
		
		for ( int i = 0 ; i<linkSet.length; i++)
		{	
			//reserved bandwidth is the link capacity - remaining free bandwidth
			reservedBandwidth+= linkSet[i].capacity - linkSet[i].bandwidth;			
		}
		
		if (level < FatTreeNetwork.HEIGHT)
		{
			level++;
			reservedBandwidth = this.calculateTotalBandwidth(level, reservedBandwidth);
		}
		
		//empty memory
		linkSet = null;
				
		return reservedBandwidth;
	}
	
	
	/**
	 * This function release the allocated request from the network
	 * 
	 * @param physicalVMAllocation array of <pmId, VMAllocated for the request> that hold the number of Vm allocated to the specified pm
	 * @param vmType (null, backup, primary) type of vm and bandwidth to release (null to release both)
	 *
	 */
	public void releaseAllocatedRequest (Request request, VirtualMachine.Type vmType)
	{
		PhysicalMachine pm ;
		Link l;
		Link.BandwidthType bandwidthType = null;
		Link.BandwidthType bandwidthType2 = null;
		
		//release reserved VMs for the specified request 
		for (int i=0; i<this.physicalMachinesSet.length; i++)
		{
			pm = this.physicalMachinesSet[i];
			pm.releaseVms(request.id, vmType);
			
		}
		
		//set the bandwidth type based on vm type
		if (vmType == VirtualMachine.Type.PRIMARY)
		{
			bandwidthType = Link.BandwidthType.PRIMARY;
		}
		else if (vmType == VirtualMachine.Type.BACKUP)
		{
			bandwidthType = Link.BandwidthType.BACKUP;
			bandwidthType2 = Link.BandwidthType.SHAREDBACKUP;
		}
		else
		{
			bandwidthType =  null;
		}
	
		//release the bandwidth reserved for this request on all the network links
		for(int i=0;i<this.pmToTorLinkSet.length;i++)
		{
			l = this.pmToTorLinkSet[i];
			l.releaseBandwidth(request.id, bandwidthType);
			if (bandwidthType2 != null)
			{
				l.releaseBandwidth(request.id, bandwidthType2);
			}
			
		}
		
		for(int i=0;i<this.torToAggregateLinkSet.length;i++)
		{
			l = this.torToAggregateLinkSet[i];
			l.releaseBandwidth(request.id, bandwidthType);
			if (bandwidthType2 != null)
			{
				l.releaseBandwidth(request.id, bandwidthType2);
			}
			
		}
		
		for(int i=0;i<this.aggregateToCoreLinkset.length;i++)
		{
			l = this.aggregateToCoreLinkset[i];
			l.releaseBandwidth(request.id, bandwidthType);
			if (bandwidthType2 != null)
			{
				l.releaseBandwidth(request.id, bandwidthType2);
			}
			
		}
		
		//empty memory
		pm = null;
		l= null;
	}
	
	
	
	
	
	/**
	 * This function resets all the network information by :
	 * 1- removing the allocated VMs on physical machines
	 * 2- resetting the links reserved bandwidth to 0
	 */
	public void reset()
	{
		//un allocate all VMs of all physical machines
		for(int i=0; i<this.physicalMachinesSet.length; i++)
		{
			for(int j=0; j<this.physicalMachinesSet[i].virtualMachines.length; j++)
			{
				this.physicalMachinesSet[i].virtualMachines[j].reserveRelease(false, null, null); 
			}
		}
		
		//reset reserved bandwidth to 0 for links between physical machines and TOR
		for ( int i = 0; i< this.pmToTorLinkSet.length; i++)
		{
			this.pmToTorLinkSet[i].bandwidth = 0;
		}
		
		//reset reserved bandwidth to 0 for links between TOR and aggregate switch
		for ( int i = 0; i< this.torToAggregateLinkSet.length; i++)
		{
			this.torToAggregateLinkSet[i].bandwidth = 0;
		}
		
		//reset reserved bandwidth to 0 for links between aggregate switch and core switch
		for ( int i = 0; i< this.aggregateToCoreLinkset.length; i++)
		{
			this.aggregateToCoreLinkset[i].bandwidth = 0;
		}
	}
	
	
	/**
	 * This function return a string of network information
	 * 
	 * @return network information
	 */
	public String toString()
	{
		String networkInfo = " ";
		
		networkInfo +=" ---------------------------------------------------------------------- Fat tree network of ------------------------------------------------------------------------------ \n";
		networkInfo +=" ----"+ this.nbOfPhysicalMachines+" Servers----"+ this.nbOfVMPerPhysicalMachine+" VM per server----\n";
		networkInfo +=" ----"+ this.nbOfPhysicalMachinesPerTor+" Servers per TOR switch ("+ this.nbOfPhysicalMachines/this.nbOfPhysicalMachinesPerTor+" TOR)----\n";
		networkInfo +=" ----"+  this.nbOfTorPerAgg+" of TOR switch per Aggregate ("+ (this.nbOfPhysicalMachines/this.nbOfPhysicalMachinesPerTor)/this.nbOfTorPerAgg+" Aggregate)----\n";
		networkInfo +=" ----"+ this.nbOfAggPerCore+" Aggregate per core ("+(this.nbOfPhysicalMachines/this.nbOfPhysicalMachinesPerTor)/this.nbOfTorPerAgg/this.nbOfAggPerCore +" core )----\n";
		networkInfo += " Links capacity : server-TOR = "+this.pmToTorLinkCapacity+" | TOR-AGG =  "+this.torToAggregateLinkCapacity+" | AGG-Core = "+this.aggregateToCoreLinkCapacity+" ----\n";
		networkInfo +=" -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n";
		
	/*	for (int i=0; i< this.physicalMachinesSet.length; i++)
		{
			networkInfo +=  this.physicalMachinesSet[i].toString();
		}
		
		for (int i=0; i< this.torSwitchSet.length; i++)
		{
			networkInfo +=  this.torSwitchSet[i].toString();
		}
		
		for (int i=0; i< this.aggregateSwitchSet.length; i++)
		{
			networkInfo +=  this.aggregateSwitchSet[i].toString();
		}
		
		for (int i=0; i< this.coreSwitchSet.length; i++)
		{
			networkInfo +=  this.coreSwitchSet[i].toString();
		}
		
		for (int i=0; i< this.pmToTorLinkSet.length; i++)
		{
			networkInfo +=  this.pmToTorLinkSet[i].toString();
		}
		
		for (int i=0; i< this.torToAggregateLinkSet.length; i++)
		{
			networkInfo +=  this.torToAggregateLinkSet[i].toString();
		}
		
		
		for (int i=0; i< this.aggregateToCoreLinkset.length; i++)
		{
			networkInfo +=  this.aggregateToCoreLinkset[i].toString();
		}
		*/
		
		return networkInfo;
	}
	
	
	/**
	 * This function return a string of network links and machines information
	 * 
	 * @return network state
	 */
	public String networkState()
	{
		String networkState = "";
		PhysicalMachine pm = null;
		Link [] links = this.getLinks();
		
		for (int i =0; i<this.nbOfPhysicalMachines; i++)
		{
			pm = this.physicalMachinesSet[i];			
			networkState +=pm.toString()+ "\n";
			pm = null;
		}
		
		for (int i =0; i<links.length; i++)
		{
					
			networkState +=links[i].toString()+ "\n";
		}
		
		return networkState;
	}
	

}
