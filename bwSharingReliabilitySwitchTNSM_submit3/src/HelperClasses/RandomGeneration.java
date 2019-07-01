package HelperClasses;

import java.util.Random;

import MainFunctionalities.VirtualMachinesPlacement;
import Network.FatTreeNetwork;
import Network.PhysicalMachine;
import Network.SubTree;

public class RandomGeneration {
	public FatTreeNetwork treeNetwork;
	
	public RandomGeneration (FatTreeNetwork treeNetwork)
	{
		this.treeNetwork = treeNetwork;
	}
	
	/**
	 * This function is used to simulate for example each 10 seconds a fault, this means
	 * that each 10 requests (arrival+departure)  simulate a fault (faultsPerRequest = 10)
	 * 
	 * @param nbRequests total nb of requests considering arrival and departure 
	 * @param faultsPerRequest nb of faults to simulate after certain nb of requests
	 * 
	 * @return nb of faults to simulate
	 */
	public int calculateNbFaultsPerRequest(int nbRequests, int faultsPerRequest)
	{
		return nbRequests/faultsPerRequest;
	}
	
	/**
	 * This function generates a random set faults related to switch failures
	 * @param nbOfFaults total nb of faults to consider
	 * @param faultLevel fault level (1 for TOR switch
	 * 
	 * @return subtree of faults
	 */
	public SubTree[] generateFaultDomains (int nbOfFaults, int faultLevel)
	{
		SubTree[] faultDomainsAtLevel ;
		SubTree[] faultDomains = new SubTree[nbOfFaults];
		VirtualMachinesPlacement vmPlacement = new VirtualMachinesPlacement(this.treeNetwork);
		Random rand = new Random();
	
		int randomFailure =0;
	
		//get all fault domains (sub tree) related to a certain switch level
		faultDomainsAtLevel = vmPlacement.getFaultDomains (faultLevel);
		
		 for (int i=0; i<nbOfFaults; i++)
		 {
			//choose a random fault
			 randomFailure = rand.nextInt(faultDomainsAtLevel.length);
			 faultDomains[i]=(faultDomainsAtLevel[randomFailure]);
		 }
		 
		 return faultDomains;		
	}
	
	/**
	 * This function generates a random set of faults related to server failures
	 * @param nbOfFaults total nb of faults to consider
	 * 
	 * @return server that fails
	 */
	public PhysicalMachine[] generatePmFaults (int nbOfFaults)
	{	
		PhysicalMachine[] faultDomains = new PhysicalMachine[nbOfFaults];

		Random rand = new Random();
	
		int randomFailure =0;	
		
		 for (int i=0; i<nbOfFaults; i++)
		 {
			//choose a random fault
			 randomFailure = rand.nextInt(this.treeNetwork.nbOfPhysicalMachines);
			 faultDomains[i]=(this.treeNetwork.physicalMachinesSet[randomFailure]);
		 }
		 
		 return faultDomains;		
	}
}
