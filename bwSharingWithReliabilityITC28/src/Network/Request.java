package Network;
import java.util.ArrayList;


public class Request {
	
	public int id;
	
	//number of primary VMs
	public int N;
	
	//set of primary and backups virtual machines of the request. This is set when reserving Vms and will be kept set after releasing the request
	public ArrayList<VirtualMachine> virtualMachinesSet;
	
	//bandwidth needed for each Vm
	public int B;
	
	//specifies if the request is admitted to the network
	public boolean admitted;
	
	//arrival time
	public double arrivalTime;
	
	//departure time
	public double departureTime;
	
	//subTree where the request is admitted
	public SubTree subtree;
	
	//sharing sets that this request is part of. This specifies the requests that this one is sharing bw with
	//This is left assigned only in offline mode 
	public ArrayList <SharingSet> sharingSets;
	
	/**
	 * array list of links continusId with primary and backup bandwidth reserved on them for the request
	 * int[] {link continousId, primaryBw, backupBw}
	 * This array is kept set even after releasing the request from the network
	 * This array will help calculating the revenue 	 
	 * 
	 */
	
	public ArrayList <int[]> reservedBandwidth;
	
	/**
	 * Actual number of backup Vms reserved
	 * This is kept set after releasing the request
	 * it helps calculating the revenue
	 */
	public int reservedBackupVms;
	
	/*
	 * the type allow us to specify if we want to consider the arrival or departure of the request
	 *  (used when sorting requests for poisson distribution)
	 */
	public enum Type {ARRIVAL, DEPARTURE, NA};
	
	/**
	 * Rejection reason specifies the reason why the request was not admitted to the network
	 * PRIMARY_EMBEDDING : primary embedding was not possible because of not enough available VMs or bandwidth
	 * PRIMARY_BANDWIDTH :primary embedding was not possible because of not enough available upper level bandwidth
	 * BACKUP_EMBEDDING  : backup embedding was not possible because not enough available VMs
	 * BACKUP_MAPPING_BANDWIDTH : there was not enough bandwidth for the provided backup embedding and mapping
	 * (this is used if the BackuptoVmMappingEmbedding returned false)
	 */
	public enum RejectionReason {PRIMARY_EMBEDDING, BACKUP_EMBEDDING,PRIMARY_BANDWIDTH, BACKUP_MAPPING_BANDWIDTH};
	
	public Type processType;
	public RejectionReason rejectionReason;
	
	public Request (){
		
	}
	
	
	public Request (int id, int N, int B)
	{
		this.id = id;
		this.N = N;
		this.B = B;
		this.virtualMachinesSet = new ArrayList <VirtualMachine>();
		this.rejectionReason =  null;
		this.subtree = null;
		this.reservedBandwidth = new ArrayList<int[]>();
		this.reservedBackupVms =0;
		this.sharingSets = new ArrayList<SharingSet>();
		
		//set to arrival by default to be able to use the embedding functions. This will change to departure when using poisson distribution
		this.processType = Type.ARRIVAL;
	}
	
	
	/**
	 * This function clone all the request information
	 * It is mainly used with the poisson process (sortRequests() in VMsProtectionWithBandwidthGuarantee class)
	 */
	public Request clone()
	{
		Request r = new Request();
		r.id = this.id;
		r.N = this.N;
		r.B = this.B;
		r.virtualMachinesSet = new ArrayList <VirtualMachine>();
		r.admitted =  this.admitted;
		r.arrivalTime = this.arrivalTime;
		r.departureTime =  this.departureTime;
		r.processType = this.processType;
		r.subtree = this.subtree;
		r.rejectionReason = this.rejectionReason;
		r.reservedBandwidth = new ArrayList<int[]>();
		r.reservedBackupVms = 0;
		r.sharingSets = new ArrayList<SharingSet>();
		return r;
		
	}
	
	
	/**
	 * This function clone a request with all its element
	 * Note: the virtualMachinesSet and sharingSets will be updated in vmProtection.clone()
	 * 
	 * @return cloned request
	 */
	public Request cloneAll( FatTreeNetwork clonedNetwork )
	{
		int [] reserveredBw = new int[4];
		int[] bandwidthForRequest ;
		Request r = new Request();
		r.id = this.id;
		r.N = this.N;
		r.B = this.B;
		r.admitted =  this.admitted;
		r.arrivalTime = this.arrivalTime;
		r.departureTime =  this.departureTime;
		r.processType = this.processType;
		r.subtree = this.subtree!=null? this.subtree.clone(clonedNetwork):null;
		r.rejectionReason = this.rejectionReason;		
		r.reservedBackupVms = this.reservedBackupVms;
		r.reservedBandwidth = new ArrayList<int[]>();
		
		//those will be updated in vmProtection.clone
		r.virtualMachinesSet = new ArrayList <VirtualMachine>();
		r.sharingSets = new ArrayList<SharingSet>();
		
		
			
		for (int i=0; i<this.reservedBandwidth.size(); i++)
		{			
			bandwidthForRequest = this.reservedBandwidth.get(i);
			for (int j=0; j<bandwidthForRequest.length; j++)
			{
				reserveredBw[j] = bandwidthForRequest[j];
			}
		
			r.reservedBandwidth.add(reserveredBw);
			
			reserveredBw = new int[4];
		}
		
				
		return r;
		
	}
	
	
	
	/**
	 * Sets the subtree information and updates the admitted attributes
	 * 
	 * @param subtree the subtree that admitted the request
	 */
	public void setSubTree (SubTree subtree)
	{
		this.subtree = subtree;
		
		if (subtree == null)
		{
			this.admitted = false;
		}
		else
		{
			this.admitted = true;
		}
	}
	
	
	/**
	 * This function returns the number of needed backups as 
	 * the maximum primary Vms hosted on a server
	 * 
	 * @return nb of backup needed
	 */
	public int getBackupNeeded()
	{
		PhysicalMachine pm = null;
		int backups = 0;
		int hostedVms = 0;
		
		if (!this.admitted)
		{
			return 0;
		}
		
		for (int i = 0; i<this.subtree.physicalMachines.size(); i++)
		{
			pm = this.subtree.physicalMachines.get(i);
			hostedVms  =  pm.getHostedVms(this);
			
			if ( backups < hostedVms )
			{
				backups = hostedVms;
				
			}
		}
		
		return backups;
	}
	
	
	/**
	 * This function sets the reservedBandwidth array of the request
	 * 
	 * @param treeNetwork network where the request is embedded
	 */
	public void updateReservedBandwidth(FatTreeNetwork treeNetwork)
	{
		Link[] links = treeNetwork.getLinks();
		Link l = null;
		int[] reservedBandwidth = null;;
		int[] bandwidthForRequest = null;
		
		//reset the reserved bandwidth
		this.reservedBandwidth = null;
		this.reservedBandwidth = new ArrayList<int[]>();
	
		//no need to set reserved bandwidth if request is not admitted
		if (!this.admitted)
		{
			return;
		}
		
		//loop over network links
		for (int i = 0; i<links.length; i++)
		{
			l = links[i];
			
			//get reserved bandwidth for the request on each link
			for (int j=0; j<l.bandwidthForRequests.size(); j++)
			{
				bandwidthForRequest = new int[4];
				bandwidthForRequest = l.bandwidthForRequests.get(j);
				
				if (bandwidthForRequest[0] == this.id)
				{
					reservedBandwidth  = new int[4];
					reservedBandwidth[0] = l.continuousId;
					
					//set bandwidth reserved for primary Vms
					reservedBandwidth[1] = bandwidthForRequest[1];
					
					//set the backup bandwidth considering bandwidth reuse
					reservedBandwidth[2] = bandwidthForRequest[2];
					
					//set the  backup bandwidth shared with other tenants(this is the actual bandwidth needed by the requests when no sharing is done)
					reservedBandwidth[3] = bandwidthForRequest[3];
				}
				
				bandwidthForRequest = null;
			}
			
			//if no reserved bandwidth on the specified link no need to save an array for it
			if (reservedBandwidth == null)
			{
				continue;
			}
					
			
			this.reservedBandwidth.add(reservedBandwidth);
			reservedBandwidth  = null;			
		}		
		
	}
	
	
	
	/**
	 * This function sets the reservedBackupVms of the request
	 * 
	 */
	public void updateReservedBackupVms ()
	{
		int backups =0;
		
		for( int i=0; i<this.virtualMachinesSet.size(); i++)
		{
			if (this.virtualMachinesSet.get(i).vmType == VirtualMachine.Type.BACKUP)
			{
				backups++;
			}
		}
		
		this.reservedBackupVms = backups;
	}
	
	
	/**
	 * This function checks if the current request can share bandwidth with request r
	 * Two requests can share backup bandwidth if they don't have primary VMs hosted on the
	 * same physical server or if they do have but they don't use the specified link at the same time
	 * (upon the failure of the server hosting their primary VMs)
	 * 
	 * @param r request to check against
	 * @param l link on which we want to check if the request can share bandwidth with the other one
	 * @return boolean true if they can share bandwidth
	 */
	public boolean canShareBandwidth (Request r, Link l)
	{
		VirtualMachine rVm = null;
		VirtualMachine thisVm = null; 
	
		for (int i =0; i<this.virtualMachinesSet.size(); i++)
		{
			thisVm = this.virtualMachinesSet.get(i);
			
			//we only need to check if primary Vms are hosted on the same server
			if (thisVm.vmType ==VirtualMachine.Type.BACKUP )
			{
				continue;
			}
			
			for (int j =0; j<r.virtualMachinesSet.size(); j++)
			{
				rVm = r.virtualMachinesSet.get(j);
				
				if ( rVm.vmType ==VirtualMachine.Type.BACKUP)
				{
					continue;
				}
				
				/**
				 * check if this request and r have PRIMARY VMs hosted on the same server
				 * which means they can fail at the same time (single node failure)
				 * So we need to check that when they fail at the same time they use this link at the same time
				 * and so they can not share bandwidth on this link
				 */				
				
				if (rVm.pm.equals(thisVm.pm) && (this.isUsingLinkUponFailure (l, thisVm.pm) && r.isUsingLinkUponFailure (l, rVm.pm)))
				{			System.out.println("can't share between "+this.id+" and req "+r.id);	
					return false;				
				}
			}
		}
		return true;
	}
	
	
	/**
	 * This function checks if this request can share backup bandwidth with
	 * all the requests in a sharing set s
	 * 
	 * @param s sharing set to check if this request can share backup bandwidth with
	 * all the requests it contains
	 * 
	 * @return boolean true if this request can share bandwidth with the set s
	 */
	public boolean canShareBandwidth (SharingSet s)
	{
		Request r =null;
		
		for (int i =0; i<s.requests.size(); i++)
		{
			r = s.requests.get(i);
			if (!this.canShareBandwidth(r, s.l))
			{
				return false;
			}
		}
		
		return true;
	}
	
	
	/**
	 * This function returns true if the request is using the link upon failure of the specified server
	 * This is done by checking if there is any active VMs (primary VMs or backup VMs activated upon failure)
	 * for the request under the source node of the specified link. If so, then the request is using the link
	 * upon failure of the specified pm so the function will return true
	 * 
	 * @param l link which we want to check if the request is using upon the failure
	 * @param pm server that fails
	 * 
	 * @return true is the request is using the link upon failure of the specified pm
	 */
	public boolean isUsingLinkUponFailure(Link l, PhysicalMachine pm )
	{
		SubTree tree = new SubTree(l.sourceNode) ;	
		
		//build the subTree
		tree = this.subtree.fatTreeNetwork.buildSubTree(tree, tree.rootNode);
		
		//get the number of active Vms for the specified request upon failure of the pm in the mentioned tree
		if (tree.getActiveVms(this, pm) == 0)
		{
			return false;
		}
		
		return true;
		
	}
	
	
	/**
	 * This function remove the sharing s form the request sharing sets
	 * 
	 * @param s sharing set to remove
	 */
	public void removeSet (SharingSet s)
	{
		for (int i =0; i<this.sharingSets.size(); i++)
		{
			if (this.sharingSets.get(i).equals(s))
			{
				this.sharingSets.remove(i);
				return;
			}
		}
	}
	
	
	/**
	 * This function returns true if 2 requests are equal:
	 * 1- have same id
	 * 2- are of the same process type
	 * 
	 * @param r request to check if it equal to this one
	 * 
	 * @return true if equal
	 * 
	 */
	public boolean equals(Request r)
	{
		if (this.id != r.id )
		{
			return false;
					
		}
		
		if (this.processType!=r.processType)
		{
			return false;
		}
		
		return true;
	}
	
	
	/**
	 * This function return a string of request information
	 * 
	 * @return request information
	 */
	public String toString()
	{
		String requestInfo = " ";
		
		requestInfo +=" \n--------------Request "+this.id+" : (N="+this.N+", B="+this.B+") : has "+this.getBackupNeeded()+" backups : admitted ="+this.admitted+" Process Type :"+this.processType+" ------------- \n";
		
		if (!this.admitted)
		{
			requestInfo +=" Rejection Reasons: "+this.rejectionReason.toString()+" ----\n";
		}
		
		requestInfo +=" Reserved VMs --------------\n";
				
		for(int i=0; i<this.virtualMachinesSet.size(); i++)
		{
			requestInfo += this.virtualMachinesSet.get(i).vmType+" Virtual Machine "+i+"---hosted on physical server---"+this.virtualMachinesSet.get(i).pm.id+"\n";
		}
		
		requestInfo +=" Reserved bandwidth for this request --------------\n";
		
		for(int i=0; i<this.reservedBandwidth.size(); i++)
		{
			requestInfo += "On link "+this.reservedBandwidth.get(i)[0]+" --- Primary Bandwidth: "+ this.reservedBandwidth.get(i)[1]+"---Backup Bandwidth: "+ this.reservedBandwidth.get(i)[2]
							+"---Shared Bandwidth: "+ this.reservedBandwidth.get(i)[3]+"\n";
		}
		
		requestInfo +=" Sharing Sets of the request --------------\n";
		
		for(int i=0; i<this.sharingSets.size(); i++)
		{
			requestInfo += this.sharingSets.get(i).toString();
		}
		
		return requestInfo;
	}
	
	
}
