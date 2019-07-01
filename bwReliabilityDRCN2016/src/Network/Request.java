package Network;
import java.util.ArrayList;


public class Request {
	
	public int id;
	
	//number of primary VMs
	public int N;
	
	//set of primary and backups virtual machines of the request. This is set when reserving Vms
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
	 * BACKUP_EMBEDDING  : backup embedding was not possible because not enough available VMs
	 * BACKUP_MAPPING_BANDWIDTH : there was not enough bandwidth for the provided backup embedding and mapping
	 * (this is used if the BackuptoVmMappingEmbedding returned false)
	 */
	public enum RejectionReason {PRIMARY_EMBEDDING, BACKUP_EMBEDDING, BACKUP_MAPPING_BANDWIDTH};
	
	public Type processType;
	public ArrayList <RejectionReason> rejectionReason;
	
	public Request (){
		
	}
	
	
	public Request (int id, int N, int B)
	{
		this.id = id;
		this.N = N;
		this.B = B;
		this.virtualMachinesSet = new ArrayList <VirtualMachine>();
		this.rejectionReason =  new ArrayList<RejectionReason>();
		this.subtree = null;
		this.reservedBandwidth = new ArrayList<int[]>();
		this.reservedBackupVms =0;
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
		r.virtualMachinesSet = this.virtualMachinesSet;
		r.admitted =  this.admitted;
		r.arrivalTime = this.arrivalTime;
		r.departureTime =  this.departureTime;
		r.processType = this.processType;
		r.subtree = this.subtree;
		r.rejectionReason = this.rejectionReason;
		r.reservedBandwidth = this.reservedBandwidth;
		r.reservedBackupVms = this.reservedBackupVms;
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
				bandwidthForRequest = new int[3];
				bandwidthForRequest = l.bandwidthForRequests.get(j);
				
				if (bandwidthForRequest[0] == this.id)
				{
					reservedBandwidth  = new int[3];
					reservedBandwidth[0] = l.continuousId;
					
					//set bandwidth reserved for primary Vms
					reservedBandwidth[1] = bandwidthForRequest[1];
					
					//set the bandwidth reserved for backup Vms
					reservedBandwidth[2] = bandwidthForRequest[2];
					
					
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
	 * This function return a string of request information
	 * 
	 * @return request information
	 */
	public String toString()
	{
		String requestInfo = " ";
		
		requestInfo +=" --------------Request "+this.id+" : (N="+this.N+", B="+this.B+") : has "+this.getBackupNeeded()+" backups : admitted ="+this.admitted+"------------- \n";
		
		if (!this.admitted)
		{
			requestInfo +="---- Rejection Reassons: "+this.rejectionReason.toString()+" ----";
		}
		
		return requestInfo;
	}
	
	
}
