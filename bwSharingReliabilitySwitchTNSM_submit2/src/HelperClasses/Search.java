/**
 * This is a helper class that contains some helper search functions
 */
package HelperClasses;

import java.util.ArrayList;

import Network.FatTreeNetwork;
import Network.Link;
import Network.Node;
import Network.PhysicalMachine;
import Network.Request;
import Network.Request.RejectionReason;
import Network.Request.Type;
import Network.SharingSet;
import Network.VirtualMachine;

public class Search {

	
	public Search (){
		
	}
	
	/**
	 * This function finds a request in requests array
	 * 
	 * @param requests array of requests in which we want to search the requestId
	 * @param requestId id of the request to find
	 * @param requestType type of the request to find (arrival, departure)
	 * 
	 * @return found request, null if request was not found
	 */
	public Request getRequest (ArrayList<Request>requests, int requestId, Request.Type requestType)
	{
		
		Request r = null;
		
		for(int i = 0; i<requests.size(); i++)
		{
			r = requests.get(i);
			
			if (r.id != requestId)
			{
				continue;
			}
			
			if (r.processType != requestType)
			{
				continue;
			}
			
			return r;
		}
		
		return r;
	}
	
	
	/**
	 * This function returns a list of rejected requests
	 * 
	 * @param requests list of requests to get the rejected ones from it 
	 * @param rejectionReasons if specified it will only return the rejected requests for one of the specified reasons
	 * @return rejected requests
	 */
	public ArrayList<Request> getRejectedRequests (ArrayList<Request>requests, ArrayList<RejectionReason> rejectionReasons)
	{
		Request r = null;
		ArrayList<Request> rejectedRequests = new ArrayList<Request>();
		
		for(int i = 0; i<requests.size(); i++)
		{
			r = requests.get(i);
			
			if (r.admitted || r.processType == Type.DEPARTURE)
			{
				continue;
			}
			
			if (rejectionReasons == null)
			{
				rejectedRequests.add(r);
			}
			else
			{
				for (int j=0; j<rejectionReasons.size(); j++)
				{
					if (r.rejectionReason == rejectionReasons.get(j))
					{
						rejectedRequests.add(r);
						break;
					}
				}
			}
			r = null;
		}
		
		return rejectedRequests;
	}
	
	
	/**
	 * This function returns a list of admitted requests
	 * 
	 * @param requests list of requests to get the admitted ones from it 
	 * @return admitted requests
	 */
	public ArrayList<Request> getAdmittedRequests (ArrayList<Request>requests)
	{
		Request r = null;
		ArrayList<Request> admittedRequests = new ArrayList<Request>();
		
		for(int i = 0; i<requests.size(); i++)
		{
			r = requests.get(i);
			
			if (r.admitted && r.processType == Type.ARRIVAL)
			{
				admittedRequests.add(r);
			}		
			
		}
		
		return admittedRequests;
	}
	
	
	/**
	 * This function print the ids, number of primary and backup VMs reserved for the amditted requests in the network
	 * 
	 * @param requests array of requests (regardless if admitted or not)
	 * @param file file to write the results into
	 */
	public void printAdmittedRequestsVMs (ArrayList<Request>requests, FileManipulation file)
	{
		Request r = null;
		String ids ="";//ids of admitted requests
		String primaryVmsNb ="";//nb of primary VMs of admitted requests
		String backupVMsNb ="";//nb of backup VMs of admitted requests
		
		//get the list of admitted requests
		requests = this.getAdmittedRequests(requests);
		
		for(int i = 0; i<requests.size(); i++)
		{
			r = requests.get(i);
			ids+=r.id+"\n";
			primaryVmsNb+=r.N+"\n";
			backupVMsNb+=r.reservedBackupVms+"\n";
		}
		
		file.writeInFile("======IDs of Admitted Requests ========\n\n");
		file.writeInFile(ids+"\n\n");
		file.writeInFile("======Number of Primary VMs for Admitted Requests ========\n\n");
		file.writeInFile(primaryVmsNb+"\n\n");
		file.writeInFile("======Number of Backup VMs for Admitted Requests ========\n\n");
		file.writeInFile(backupVMsNb+"\n\n");
	}
	
	
	/**
	 * This function removed null element from an array list
	 * 
	 * @param a array to remove null elements from
	 * 
	 * @return array without the null elements
	 */
	public ArrayList<Integer> removeNullElements ( ArrayList<Integer> a )
	{
		ArrayList<Integer> updatedArray = new ArrayList<Integer>();
		for (int i=0; i<a.size(); i++)
		{
			if (a.get(i) == null)
			{
				continue;
			}
			
			updatedArray.add(a.get(i));
		}
		
		return updatedArray;
	}
	
	
	/**
	 * This function searches for a vm inside a set of physical machines
	 * This is basically used to search for a cloned Vm
	 * 
	 * @param physicalMachines physical machines array to search for the vm in
	 * @param vm the vm  to search for
	 * 
	 * @return the newVm equal to vm
	 */
	public VirtualMachine getClonedVM (PhysicalMachine [] physicalMachines, VirtualMachine vm)
	{
		VirtualMachine newVm = null;
		
		for(int i=0; i<physicalMachines.length; i++)
		{
			for (int j=0; j<physicalMachines[i].virtualMachines.length; j++)
			{
				newVm = physicalMachines[i].virtualMachines[j];
				
				System.out.println("===new ="+newVm.id);
				System.out.println(" old "+vm.id);
				if (newVm.equals(vm))
				{
					return newVm;
				}
			}
		}
		
		return newVm;
	}
	
	
	/**
	 * This function searches for a request inside a set of new cloned requests
	 * This is basically used to search for a cloned requests
	 * 
	 * @param requests array to search for the request in
	 * @param request the request to search for
	 * 
	 * @return the newRequest equal to r
	 */
	public Request getClonedRequest (ArrayList<Request> requests, Request r)
	{
		Request newRequest = null;
		
		for(int i=0; i<requests.size(); i++)
		{
			newRequest = requests.get(i);
			if (newRequest.equals(r))
			{
				return newRequest;
			}
			
		}
		
		return newRequest;
	}
	
	
	/**
	 * This function searches for a node (physical machine or switch) inside of new cloned network
	 * This is basically used to search for a cloned node
	 * 
	 * @param clonedNetwork network to search for the node in it 
	 * @param node the node to search for
	 * 
	 * @return the newNode equal to n
	 */
	public Node getClonedNode (FatTreeNetwork clonedNetwork, Node n)
	{
		Node newNode = null;
		Node [] nodes;
		if (n.level == 0)
		{
			nodes = clonedNetwork.physicalMachinesSet;
		}
		else
		{
			nodes = clonedNetwork.getSwitchSetPerTreeLevel(n.level);			
		}
	
		for(int i=0; i<nodes.length; i++)
		{
			newNode = nodes[i];
			if (newNode.equals(n))
			{
				return newNode;
			}
			
		}
		
		return newNode;
	}
}
