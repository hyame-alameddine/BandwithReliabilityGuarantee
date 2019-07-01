package Network;
import Network.VirtualMachine.Type;

/**
 * This class defines a physical machine
 * Each physical machines is formed by a number of Virtual machines
 * 
 * @author Hyame
 *
 */
public class PhysicalMachine extends Node {
	
	//type of this node
	public static final String PHYSICAL_MACHINE_TYPE = "physicalMachine";
	
	//level of this node in the tree
	public static final int MACHINE_LEVEL = 0;
	
	//Array of virtual machines held by this physical machine
	public VirtualMachine virtualMachines []; 
	
	//nb of VM that can be held by this physical machine
	public int nbOfVM;
	
	
	/**
	 * Default constructor
	 */
	public PhysicalMachine()
	{
		
	}
	
	
	/**
	 * 
	 * @param id
	 * @param nbOfVM
	 */
	public PhysicalMachine(int id, int nbOfVM)
	{
		
		super (id, MACHINE_LEVEL,PHYSICAL_MACHINE_TYPE);		
		
		this.nbOfVM = nbOfVM;
		this.virtualMachines = new VirtualMachine [this.nbOfVM];
		
		//create the set of VMs
		for(int i= 0; i<this.virtualMachines.length; i++)
		{
			this.virtualMachines[i] = new VirtualMachine(i,0,this);
		}
	}
	
	
	/**
	 * This function returns the number of un-reserved VM
	 * 
	 * @return int availableVMs
	 */
	public int getAvailableVM()
	{
		
		int availableVMs = 0;
		
		for (int i =0; i<this.virtualMachines.length; i++)
		{
			
			if(this.virtualMachines[i].reserved == false){
				availableVMs++;
			}
			
		}
		
		return availableVMs;
	}
	
	
	/**
	 * This function returns the number of VM that 
	 * were able to be reserved on this physical machine
	 *  
	 * @param VMToReserve number of VM to reserve
	 * @param request request for which to reserve the VM
	 * @param VMType specifies if we want to reserve {primary, backup } VM
	 * @return nbOfReservedVMs
	 */
	public int reserveVM (int VMToReserve, Request request, VirtualMachine.Type VMType )
	{
		
		int nbOfReservedVM = 0;
		
		if (VMToReserve > this.getAvailableVM())
		{
			return nbOfReservedVM;
		}
		
		for (int i =0; i<this.virtualMachines.length; i++)
		{
			
			//we reserved all the needed number 
			if (nbOfReservedVM == VMToReserve )
			{
				break;
			}
			
			//reserve the un-reserved VM
			if(this.virtualMachines[i].reserved == false)
			{				
				//reserve the VM
				this.virtualMachines[i].reserveRelease (true, request, VMType);
				nbOfReservedVM++;
			}
		}
		
		return nbOfReservedVM;
	}
	
	
	/**
	 * This function releases the reserved VMs (primary and backup) on this server for the specified tenant
	 * 
	 * @param tenantId id of the tenant to release Vm for
	 * @param vmType Tyoe of Vm to release ()if backup or primary or null if we want to release both
	 * @return 
	 */
	public void releaseVms (int tenantId, VirtualMachine.Type vmType)
	{
		for (int i =0; i<this.virtualMachines.length; i++)
		{
			// release VM reserved for the specified tenant
			if(this.virtualMachines[i].reserved == true && this.virtualMachines[i].request.id == tenantId)
			{	
				if (this.virtualMachines[i].vmType == vmType)
				{
					//release the VM
					this.virtualMachines[i].reserveRelease(false, null, null); 
				
				}
				else if (vmType ==null)
				{
					// if the vmType is not specified, release all the VMs of the request
					this.virtualMachines[i].reserveRelease(false, null, null); 
				}
								
			}
		}		
	}
	
	
	/**
	 * This function returns the number of primary VMs hosted on this server for the specified request
	 * 
	 * @param request
	 * @return nb of hosted Vms
	 */
	public int getHostedVms (Request request)
	{
		int hostedVms = 0;
		
		for ( int i=0; i<this.virtualMachines.length; i++ )
		{
			if (this.virtualMachines[i].reserved  && this.virtualMachines[i].request.equals(request) && this.virtualMachines[i].vmType == VirtualMachine.Type.PRIMARY )
			{
				hostedVms++;
			}
		}
		
		return hostedVms;
	}
	
	
	/**
	 * This function should determine the number of Vms that can be accommodated
	 * by the server based on the pm-tor link bandwidth
	 * 
	 * @TODO
	 * @param request
	 * @return
	 */
	public int getVmsToAllocate (Request request)
	{
		int possibleVmAllocation = 0;
		
		return possibleVmAllocation;
	}
	
	
	/**
	 * This function returns a string of physical machine information
	 * 
	 * @return String machineInformation
	 */
	public String toString()
	{
		String machineInformation = "";
		
		machineInformation +=" Physical server "+this.id+" : capacity = "+this.nbOfVM+" ; availableVms = "+this.getAvailableVM()+" \n";
		
		return machineInformation;
	}
}
