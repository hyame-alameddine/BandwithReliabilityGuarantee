package Network;
import java.util.ArrayList;


public class VirtualMachine extends Node{
		
	//type of this node
	public static final String VIRTUAL_MACHINE_TYPE = "virtualMachine";
	
	//level of this node in the tree
	public static final int VIRTUAL_MACHINE_LEVEL = -1;
	
	// specifies the type of virtual machines
	public enum Type {PRIMARY, BACKUP};
	public Type vmType;
	
	//physical machine that the VM belongs to
	public PhysicalMachine pm;
	
	//cpu capacity of the VM
	public int capacity;
	
	//specify if the VM is reserved or not
	public boolean reserved;
	
	//the request for Which the VM is reserved for; null if it is not reserved
	public Request request;
	
	//list of vms backed up by this
	public ArrayList <VirtualMachine> backedUpVMs;
	
	/**
	 * default constructor
	 */
	VirtualMachine ()
	{
		//virtual machine is not reserved initially
		this.request = null;
	}
	
	
	/**
	 * 
	 * @param id
	 * @param capacity
	 * @param physical machine pm
	 */
	VirtualMachine (int id, int capacity, PhysicalMachine pm)
	{
		super(id,VIRTUAL_MACHINE_LEVEL,VIRTUAL_MACHINE_TYPE );
		this.pm = pm;
		this.capacity = capacity;
		
		//set the type of Vm to primary by default
		this.vmType = Type.PRIMARY;
		
		//virtual machine is not reserved initially
		this.request = null;
		this.reserved = false;
		
	}
	
	
	
	/**
	 * 
	 * This function reserves/releases the virtual machine for the specified tenant
	 * 
	 * @param reserve specifies if we want to reserve/release the VM
	 * @param tenantId specifies the tenantId for which we want to reserve the VM (should be -1 if we want to release it)
	 * @param VMType type of Vm we want to reserve/release {primary, backup, null} (null if we are releasing the VMs)
	 */
	public void reserveRelease (boolean reserve, Request request, Type VMType)
	{
		this.reserved =  reserve;
		
		if (!reserve)
		{	
			//remove this VM from the request VM list it was assigned to
			this.request.virtualMachinesSet.remove(this);
			
			//reset the Vm to primary Vm (if it was set as backup)
			this.vmType = Type.PRIMARY;
		}
		else
		{	
			//set the vm type to backup since it is set to primary by default
			this.vmType = VMType;
			
			//add the VM to the VM request list
			request.virtualMachinesSet.add(this);			
		}
		
		// set this after adding/removing the vm from the request list specially that the request can be null
		this.request = request;
		
	}
	
	
	/**
	 * Checks if 2 vms are equals if they have the same id, level, type
	 * and belong to the same physical server and reserved to the same request
	 * 
	 * @param vm to compare against
	 * @return true if VMs are equals
	 */
	public boolean equals(VirtualMachine vm) 
	{
		//checking on id and level
		if (!super.equals(vm))
		{
			return false;
		}
		
		//checking on request
		if ( vm.request.id != this.request.id )
		{
			return false;
		}
		
		//checking on pm
		if (vm.pm.id != this.pm.id)
		{
			return false;
		}
		
		//checking on type
		if (vm.vmType !=this.vmType)
		{
			return false;
		}
		return true;
	}
	
	
	
	/**
	 * Checks if the current backup can backup the specified VM
	 * A backup VM can backup a vm v if:
	 * 1- it is not backing up another VM of the same request hosted on the same server as v
	 * 2- if the backup and v are not hosted on the same server
	 * 
	 * @return true if the current VM can backup the one passed as parameter
	 */
	public boolean canBackup (VirtualMachine primaryVM)
	{
		VirtualMachine vm;
		// if primary and backup are hosted on the same server the backup can not backup this primary vm
		if (this.pm.id == primaryVM.pm.id)
		{
			return false;
		}
		
		for(int i=0; i<this.request.virtualMachinesSet.size(); i++)
		{
			vm = this.request.virtualMachinesSet.get(i);
			
			//we dont want to check the Vm against itself
			if (vm.equals(primaryVM))
			{
				continue;
			}
			
			//if backup vm is not backing up another VM of the same request hosted on the same server as v
			if (vm.pm.id == this.pm.id && this.isBackinUp(vm))
			{
				return false;
			}
		}
		
		return true;
	}
	
	
	/**
	 * Return true if the current Vm is backing up the vm passed as parameter
	 * 
	 * @param vm virtual machine which we want to check if it is backed up by the
	 * current backup vm
	 * 
	 * @return true if the specified vm is backed up by the current backup
	 */
	public boolean isBackinUp(VirtualMachine vm)
	{
		VirtualMachine v;
		for (int i=0; i<this.backedUpVMs.size(); i++)
		{
			v = this.backedUpVMs.get(i);
			if (vm.equals(v))
			{
				return true;
			}
		}
		
		return false;
	}
}
