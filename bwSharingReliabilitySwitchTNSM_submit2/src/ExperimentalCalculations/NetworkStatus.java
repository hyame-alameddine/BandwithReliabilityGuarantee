/**
 * This class contains all the functions that provide information about the network status including :
 * 
 * 
 */
package ExperimentalCalculations;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import HelperClasses.FileManipulation;
import MainFunctionalities.VirtualMachinesPlacement;
import Network.*;

public class NetworkStatus {
	
	
	//network that we are checking the status
	public FatTreeNetwork treeNetwork;
	
	//requests embedded in the network 
	public ArrayList<Request> requests;
	
	
	public NetworkStatus(FatTreeNetwork treeNetwork, ArrayList<Request> requests )
	{
		this.treeNetwork = treeNetwork;
		this.requests = requests;
	}
	
	
	/**
	 * This function calculates the rejection rate of requests
	 * 
	 * @return rejection rate
	 */
	public double rejectionRate ()
	{
		double rejectionRate = 0;
		double rejectedRequests = 0 ;
		double requestsNb = this.requests.size();
		Request request = null;
				
		for (int i =0; i<this.requests.size(); i++)
		{

			request = this.requests.get(i);
			
			/**
			 * if the this.requests array contains 2 copies of the requests(arrival+departure) 
			 * consider only arrivals since those have the updated info (admitted)
			 */
			
			if (request.processType == Request.Type.DEPARTURE)
			{
				requestsNb--;
				continue;
			}
			
			if (request.admitted)
			{
				continue;
			}
	
			rejectedRequests++;
		
			
		}
	

		rejectionRate = rejectedRequests/requestsNb*100;

		return rejectionRate;
	}
	
	
	/**
	 * This function calculate the revenue based on embedded requests without considering the time
	 * revenue +=request.N*VMCost + request.N*bandwidthCost;
	 * 
	 * @param VMCost cost of a VM reservation
	 * @param bandwidthCost cost of unit bandwidth reservation
	 * 
	 * @return revenue
	 */
	public double calculateRevenue (double VMCost, double bandwidthCost)
	{
		double cost =0;
		Request request = null;
				
		for (int i =0; i<this.requests.size(); i++)
		{			 
			request = this.requests.get(i);
			
			/**
			 * if the this.requests array contains 2 copies of the requests(arrival+departure) 
			 * consider only arrivals since those have the updated info (admitted)
			 */
			
			if (request.processType == Request.Type.DEPARTURE)
			{
				continue;
			}
			
			if (!request.admitted)
			{
				continue;
			}
		
			cost +=request.N*VMCost + request.N*bandwidthCost;
		
		}
		
		return cost;
	}
	
	
	/**
	 * This function calculates the total bandwidth (primary+backup) reserved in the network for all the requests
	 * even after they leave the network
	 * 
	 * @return reservedBw total bandwidth reserved in the network
	 */
	public int calculateTotalReservedBandwidth ()
	{
		int reservedBw = 0;
		int[] bandwidth = new int [3];
		Request request = null;
		
		for (int i =0; i<this.requests.size(); i++)
		{	
			request = this.requests.get(i);
		
			for (int j=0; j< request.reservedBandwidth.size(); j++)
			{
				bandwidth = request.reservedBandwidth.get(j);	
				reservedBw+= bandwidth[1]+bandwidth[2];
				
				bandwidth = null;
				bandwidth = new int[3];
			}
		
		}
		return reservedBw;
	}
	
	
	
	/**
	 * This function calculates the total bandwidth (primary+backup) reserved in the network for all the requests
	 * 
	 * @return reservedBw total bandwidth reserved in the network
	 */
	public int calculateCurrentReservedBandwidth ()
	{
		Link [] links = this.treeNetwork.getLinks();
		int reservedBandwidth =0;
		
		for (int i =0; i<links.length; i++)
		{	
			reservedBandwidth+= links[i].capacity - links[i].bandwidth;
		
		}
		
		return reservedBandwidth;
	}
	
	/**
	 * This function calculates the total backup Vms  reserved in the network for all the requests
	 * 
	 * @return reservedBackup total backups reserved in the network
	 */
	public int calculateTotalReservedBackupVMs ()
	{
		int reservedBackup = 0;
			
		for (int i =0; i<this.requests.size(); i++)
		{	
			reservedBackup+= this.requests.get(i).reservedBackupVms;
		
		}
		return reservedBackup;
	}
	
	

	/**
	 * This function returns an array list of timer with the revenue. 
	 * The revenue is only calculated for timers when there is an arrival/departure
	 * This function is based on having all requests ordered by arrival and departure
	 * 
	 * @param bwCost bw cost per unit time 
	 * @param VMCost Vm cost per unit time
	 * @param adjustmentValue usually set to 1000 to adjust the value of arrival and departure since they are double
	 * 
	 * @return list of timer with revenue
	 */
	public ArrayList<Double> calculateRevenueOverTime( double VMCost, double bwCost, int adjustmentValue)
	{
		double revenue = 0;
		ArrayList <Double> timeRevenue = new ArrayList<>();
		Request request = null;
		int timer = 0;
		int i=0;
		int arrivalDepartureTime =0;
		
		//loop until all requests arrive and leave
		while (i<this.requests.size())
		{
			request = this.requests.get(i);
			arrivalDepartureTime = (request.processType == Request.Type.ARRIVAL) ? (int)(request.arrivalTime*adjustmentValue): (int)(request.departureTime*adjustmentValue);
			
			//we only calculate the revenue when there is an arrival/departure
			if (timer == arrivalDepartureTime)
			{
				revenue = this.timeRevenue(i, VMCost,bwCost,adjustmentValue);				
				i++;
			}
			else
			{
				//add the revenue for all requests arriving/leaving at the same time
				timeRevenue.add(timer, revenue);				
				revenue = 0;			
				
				//increase the timer once all the requests arriving/leaving at this timer are calculated in the revenue			
				timer++;
			}
			
			//ensure that we are adding the revenue for the last request
			if (i == this.requests.size() && timer == arrivalDepartureTime)
			{
				timeRevenue.add(timer, revenue);	
			}
			
		}

		return timeRevenue;
		
	}
	
	/**
	 * 
	 * This function calculates the average fault tolerance at a specific time upon a certain fault. It calculates the fault tolerance as follows:
	 * 1/NbOfRequestsInNetworkDuringFailure *sum over all RequestsInNetworkDuringFailure (nb of active VMs of request upon failure/N of request)
	 * This function is based on having all requests ordered by arrival and departure
	 * 
	 * @param faultId the index of the request in this.requests array that arrived or left which is also the id of the fault that occurred
	 * we did not send the request and loop to its id because we have in the array 2 requests with the same id (arrival and departure)
	 * @param adjustmentValue usually set to 1000 to adjust the value of arrival and departure since they are double
	 * @param faultType should be "pm" or "switch" 
	 * 
	 * @return faulTolerance at the specific time
	 */
	public Double getFaultTolerance (int requestIndex, int faultId,  int adjustmentValue,String faultType)
	{
		Double faultTolerance = 0.0;
		Request r = null;
		int time =0;
		int arrivalTime =0;
		int departureTime = 0;
		Request  request = this.requests.get(requestIndex);//get last request arrived/left at requestIndex
		int totalRequestsInNetwork=0;
		
		//get the timer which is equal to the arrival/departure of the request
		time = (request.processType == Request.Type.ARRIVAL) ? (int)(request.arrivalTime*adjustmentValue): (int)(request.departureTime*adjustmentValue);
		
		//loop over the requests until reaching the one passed as parameter to reach the time specified
		for (int i =0; i<= requestIndex; i++)
		{
			r = this.requests.get(i);
	
			arrivalTime = (int)(r.arrivalTime*adjustmentValue);
			departureTime = (int)(r.departureTime*adjustmentValue);
			
			
			/**
			 * if the request is arriving and is not admitted, it should not be considered in the calculation of the fault
			 * if it is a departure we should not consider it also because its arrival will be considered 
			 * This is to prevent considering its revenue twice
			 */
			if (!r.admitted && r.processType == Request.Type.ARRIVAL || r.processType == Request.Type.DEPARTURE)
			{
				continue;
			}
			
			/**
			 * check if the request is still in the network at the specified time.
			 * if the request is leaving at the specified time we calculate the fault as if it left.
			 * This is to consider the whole time it stayed in the network
			 */
		
			if (arrivalTime <= time && departureTime >time)
			{ 
				totalRequestsInNetwork++;
				//to prevent having revenue =0 for the request that arrived at the mentioned time
				if (faultType.equals("pm"))
				{	
					faultTolerance= faultTolerance+ r.pmFaultTolerance.get(faultId);
				}
				else
				{
					faultTolerance= faultTolerance+r.switchFaultTolerance.get(faultId);
				}
				
			}
			
			r= null;
			arrivalTime =0;
			departureTime = 0;
			
		}

		faultTolerance = faultTolerance/totalRequestsInNetwork;
		
		return faultTolerance;
	}
	
	
	/**
	 * This function returns an array list of timer with the average fault tolerance of requests upon a random fault 
	 * The fault tolerance is only calculated for timers when there is an arrival/departure
	 * This function is based on having all requests ordered by arrival and departure
	 * 
	 * FT(failure of fault domain f) = 1/all tenants (sum over tenants (active VMs after failure)/N)
	 * 
	 * @param adjustmentValue usually set to 1000 to adjust the value of arrival and departure since they are double
	 * @param switchFaults subtree that represents the failed fault domain. set To null if we want to get WCS based on server fault
	 * @param pmFaults server that represents the failed fault domain. set To null if we want to get WCS based on subTree fault 
	 * @return list of timer with Fault tolerance
	 */
	public ArrayList<Double> calculateFaultToleranceOverTime(SubTree [] switchFaults, PhysicalMachine [] pmFaults,int adjustmentValue)
	{
		Double fault =0.0;
		ArrayList <Double> faultTolerance = new ArrayList<Double>();
	
		int timer = 0;
		int i=0;
	
		String faultType ="";
		int faultId =0;//fault that we are throwing
		//getFaultOcuurrence default to 1, each arrival departure initiate a fault, this is updated based in the number of faults
		int faultOccurence =1;
		
		if (switchFaults != null)
		{
			faultType="switch";
			faultOccurence = this.requests.size()/switchFaults.length;
		}
		else
		{
			faultType="pm";
			faultOccurence = this.requests.size()/pmFaults.length;
		}
	
	
		
		//loop until all requests arrive and leave
		while (i<this.requests.size())
		{												
			//we only calculate the fault tolerance when time is multiple of faultOccurence
			if (timer%faultOccurence== 0)
			{
				fault = this.getFaultTolerance(i,faultId, adjustmentValue, faultType);
				faultId++;
				faultTolerance.add(fault);				
				fault = 0.0;
				
			}
	
		
			timer++;
			i++;
		}

		return faultTolerance;
		
	}
	
	
	/**
	 * 
	 * This function calculates the revenue at a specific time where a specific request 
	 * arrived or left regardless of the time it was in the network
	 * This function is based on having all requests ordered by arrival and departure
	 * 
	 * @param requestIndex the index of the request in this.requests array that arrived or left
	 * we did not send the request and loop to its id because we have in the array 2 requests with the same id (arrival and departure)
	 * @param bwCost bw cost per unit time
	 * @param VMCost vm cost per unit time
	 * @param adjustmentValue usually set to 1000 to adjust the value of arrival and departure since they are double
	 * 
	 * @return revenue at the specific time
	 */
	public double timeRevenue (int requestIndex, double VMCost,double bwCost,  int adjustmentValue)
	{
		double revenue = 0;
		Request r = null;
		int time =0;
		int arrivalTime =0;
		int departureTime = 0;
		Request  request = this.requests.get(requestIndex);
		
		//get the timer which is equal to the arrival/departure of the request
		time = (request.processType == Request.Type.ARRIVAL) ? (int)(request.arrivalTime*adjustmentValue): (int)(request.departureTime*adjustmentValue);
		
		//loop over the requests until reaching the one passed as parameter to reach the time specified
		for (int i =0; i<= requestIndex; i++)
		{
			r = this.requests.get(i);
			arrivalTime = (int)(r.arrivalTime*adjustmentValue);
			departureTime = (int)(r.departureTime*adjustmentValue);
			
			
			/**
			 * if the request is arriving and is not admitted, it should not be considered in the calculation of the revenue
			 * if it is a departure we should not consider it also because its arrival will be considered 
			 * This is to prevent considering its revenue twice
			 */
			if (!r.admitted && r.processType == Request.Type.ARRIVAL || r.processType == Request.Type.DEPARTURE)
			{
				continue;
			}
			
			/**
			 * check if the request is still in the network at the specified time.
			 * if the request is leaving at the specified time we calculate the revenue as if it is still in the network.
			 * This is to consider the whole time it stayed in the network
			 */
			if (arrivalTime <= time && departureTime >=time)
			{ 
				//to prevent having revenue =0 for the request that arrived at the mentioned time
				revenue+= (double)(r.N*VMCost + r.N*r.B*bwCost);
			
			}
			
			r= null;
			arrivalTime =0;
			departureTime = 0;
			
		}
		
		
		return revenue;
	}
	
	
	/**
	 * This function return an array list of requests with the total bandwidth 
	 * (primary+backup) and the backupVMs reserved for them in the network
	 * 
	 * @return ArrayList <int[]> with request id, bw reserved and backup VMs reserved for it in terms of B bandwidth (normalized)
	 *  If the bandwidth = -1, the the request was not admitted
	 */
	public ArrayList<int[]> requestsBandwidthBackupConsumption()
	{
		
		Request r = null;
		int reservedBw = 0;
		int[] bandwidth = new int [3];
		ArrayList<int[]> requestsBw = new ArrayList<int[]>();
		int [] requestConsumption = null;
	
		//loop over requests 
		for (int i=0; i< this.requests.size(); i++)
		{
			requestConsumption = new  int [3];
			r = this.requests.get(i);
			if( r.processType != Request.Type.ARRIVAL)
			{
				continue;
			}
			
			//if the request is not admitted we will save it as bandwidth -1 (needed for comparison between 2algorithms )
			if( !r.admitted)
			{
				requestConsumption [0] = r.id;
				requestConsumption [1] = -1;
				
				requestsBw.add(requestConsumption);
				requestConsumption = null;
				continue;
			}
					
			for (int j=0; j< r.reservedBandwidth.size(); j++)
			{
				bandwidth = r.reservedBandwidth.get(j);	
				reservedBw+= bandwidth[1]+bandwidth[2];				
				bandwidth = null;
				bandwidth = new int[3];
			}
			requestConsumption [0] = +r.id;
			requestConsumption [1] = reservedBw/r.B;
			requestConsumption [2] = r.reservedBackupVms;
			requestsBw.add(requestConsumption);
			
			requestConsumption = null;		
			reservedBw =0;
			
		}
		return requestsBw;
		
		
	}
	

	/**
	 * This function compares the bandwidth reserved for each admitted request by 2 algorithms
	 * It supposes that the 2 parameters has the same set of request in the same order and are of the same size
	 * 
	 * @param requestsBw1 Array list of int <request id, bw reserved, backupVmsReserved> for the 1st algorithm
	 * @param requestsBw2 Array list of int <request id, bw reserved, backupVmsReserved> for the 2nd algorithm
	 *  @param requestsBw3 Array list of int <request id, bw reserved, backupVmsReserved> for the 3rd algorithm
	 * @return ArrayList<int[]> <request id, bw reserved by algo1, bw reserved by algo2, bw reserved by algo3, backup VMs reserved by algo1, backup VMs reserved by algo2,backup VMs reserved by algo3>
	 */
	public ArrayList<int[]> requestsBandwidthComparison(ArrayList<int[]> requestsBw1, ArrayList<int[]> requestsBw2,ArrayList<int[]> requestsBw3)
	{		
		ArrayList<int[]> requestsBwComparison = new ArrayList<int[]>();
		int [] requestConsumption = new  int [7];
	
		//loop over requests 
		for (int i=0; i< requestsBw1.size(); i++)
		{		
			//skip the requests that were not admitted by both algorithms
			if (requestsBw1.get(i)[1] == -1 || requestsBw2.get(i)[1] == -1 ||requestsBw3.get(i)[1] == -1 )
			//if (requestsBw1.get(i)[1] == -1 ||requestsBw3.get(i)[1] == -1 )
			{
				continue;
			}
			
			//request id
			/*requestConsumption [0] = requestsBw1.get(i)[0];
			//bw reservation given by requestsBw1
			requestConsumption [1] = requestsBw1.get(i)[1];
			//bw reservation given by requestsBw2
			requestConsumption [2] = requestsBw2.get(i)[1];
			//reserved backup VMs given by requestsBw1
			requestConsumption [3] = requestsBw1.get(i)[2];
			//reserved backup VMs given by requestsBw2
			requestConsumption [4] = requestsBw2.get(i)[2];
			requestsBwComparison.add(requestConsumption);*/
			//request id
			requestConsumption [0] = requestsBw1.get(i)[0];
			//bw reservation given by requestsBw1
			requestConsumption [1] = requestsBw1.get(i)[1];
			//bw reservation given by requestsBw2
			requestConsumption [2] = requestsBw2.get(i)[1];
			//bw reservation given by requestsBw3
			requestConsumption [3] = requestsBw3.get(i)[1];
			
			//reserved backup VMs given by requestsBw1
			requestConsumption [4] = requestsBw1.get(i)[2];
			//reserved backup VMs given by requestsBw2
			requestConsumption [5] = requestsBw2.get(i)[2];
			//reserved backup VMs given by requestsBw3
			requestConsumption [6] = requestsBw3.get(i)[2];
			requestsBwComparison.add(requestConsumption);
			
			requestConsumption = null;
			requestConsumption = new int[7];	
			
		}
		
		return requestsBwComparison;		
	}
	
	
	/**
	 * This function calculates the shared bandwidth between tenants in the network
	 * It calculates the total bandwidth reserved for all the sharing sets 
	 * This function should be invoked after sharing the bandwidth
	 * 
	 * @return sharedBandwidth
	 */
	public int getTotalTenantsSharedBandwidth()
	{
		int sharedBandwidth = 0;
		Link [] links = this.treeNetwork.getLinks();
		
		for (int i=0; i<links.length; i++)
		{
			for (int j=0; j<links[i].sharingSets.size(); j++)
			{
				sharedBandwidth += links[i].sharingSets.get(j).bandwidthToReserve;
			}
		}
		
		return sharedBandwidth;
	}
	
	
	/**
	 * This function calculates the bandwidth that should have been reserved when no sharing between tenants exists
	 * it calculates the sharedBandwidth values for all tenants on all the links (in links.bandwidthForRequests[3])
	 * This function should be invoked after sharing the bandwidth between tenants
	 * 
	 * @return bandwidth that cab be shared between tenants
	 */
	public int getBandwidthToShare ()
	{
		int bandwidthToShare = 0;
		Link [] links = this.treeNetwork.getLinks();
		
		for (int i=0; i<links.length; i++)
		{
			for (int j=0; j<links[i].bandwidthForRequests.size(); j++)
			{
				bandwidthToShare +=links[i].bandwidthForRequests.get(j)[3];
			}
		}
		
		return bandwidthToShare;
	}
	
	
	/**
	 * This function calculates the percentage of bandwidth gain got by bandwidth share between tenants
	 * works in offline mode or online mode if called inside the sharing funtion (vmsProtection.backupToVmMappingModelTest())
	 * @return bandwidth gain after sharing in %
	 */
	public double calculateSharedBandwidthGain()
	{
		double gain = 0;
		int bandwidthCanBeshared = this.getBandwidthToShare();
		int reservedsharedBandwidth = this.getTotalTenantsSharedBandwidth();
	
		if (bandwidthCanBeshared !=0)
		{	
			gain = bandwidthCanBeshared-reservedsharedBandwidth;			
			gain = gain/bandwidthCanBeshared*100;		
		}
	
		return gain;
	}
	
	
	/**
	 * This function return a string of all the requests information and allocation
	 * 
	 * @return  string
	 */
	public String requestsInfoToString()
	{
		String requestsInfo ="";
		
		for (int i=0; i<this.requests.size();i++)
		{
			requestsInfo +=this.requests.get(i).toString();
		}
		
		return requestsInfo;
	}
	
	/**
	 * This function return a string of all the links information and sharing sets
	 * 
	 * @return  string
	 */
	public String linksInfoToString()
	{
		String linksInfo ="";
		Link [] links = this.treeNetwork.getLinks();
		
		for (int i=0; i<links.length;i++)
		{
			linksInfo +=links[i].toString();
		}
		
		
		return linksInfo;
	}
	
	
	/**
	 * 
	 * This function exports each links bandwidth gain to a file (based on shared bandwidth)
	 * This function should be invoked after sharing the bandwidth
	 * 
	 * @param algorithm name of the algo used for which we are checking on bandwidth gain
	 * @param load load we are testing on
	 * @param setNb set nb we are testing
	 * @throws IOException
	 */
	public void exportLinksBwGain (String algorithm, double load, int setNb) throws IOException
	{
		String mainFileName = "TestResults/"+algorithm+"_linksBandwidthGain.txt";
		String linksFileName = "TestResults/"+algorithm+"_linksIds.txt";
		
		FileManipulation mainFile  = new FileManipulation(mainFileName);
		FileManipulation linksFile  = new FileManipulation(linksFileName);
		
		Link [] links = this.treeNetwork.getLinks();
		int linkSharedBw =0;
		int linkBandwidthToShare =0;
		double gain =0;
		
		mainFile.writeInFile("\n\n==================================================================================================================================\n");
		mainFile.writeInFile("\n\n=============================Load "+load +"===Set Nb "+setNb+"==============================================================\n");
		
		linksFile.writeInFile("\n\n==================================================================================================================================\n");
		linksFile.writeInFile("\n\n=============================Load "+load +"===Set Nb "+setNb+"==============================================================\n");
		
		for (int i=0; i<links.length;i++)
		{
			linksFile.writeInFile(links[i].continuousId+"\n");
			
			for (int j=0; j<links[i].sharingSets.size(); j++)
			{
				linkSharedBw += links[i].sharingSets.get(j).bandwidthToReserve;
			}
			
			for (int j=0; j<links[i].bandwidthForRequests.size(); j++)
			{
				linkBandwidthToShare +=links[i].bandwidthForRequests.get(j)[3];
			}
			
			if (linkBandwidthToShare == 0)
			{
				mainFile.writeInFile ("No sharing on this link \n");
				linkSharedBw = 0;
				linkBandwidthToShare = 0;
				gain = 0;
				continue;
			}
			
			gain = linkBandwidthToShare-linkSharedBw;
			gain = gain/linkBandwidthToShare*100;	
			mainFile.writeInFile (gain+"\n");
			
			linkSharedBw = 0;
			linkBandwidthToShare = 0;
			gain = 0;
		}
	}
	
	
	/**
	 * 
	 * This function exports each links backup bandwidth use (reserved for sharing+reuse or reuse without sharing) to a file (based on shared bandwidth)
	 * This function should be invoked after sharing the bandwidth
	 * 
	 * @param algorithm name of the algo used for which we are checking on bandwidth gain
	 * @param load load we are testing on
	 * @param setNb set nb we are testing
	 * @throws IOException
	 */
	public void exportLinksBackupBwInfo (String algorithm, double load, int setNb) throws IOException
	{
		String shareName = "TestResults/"+algorithm+"_linksBackupBwReservedShareReuse.txt";
		String noShareName = "TestResults/"+algorithm+"_linksBackupBwReservedReuseNoShare.txt";
		
		FileManipulation shareBwFile  = new FileManipulation(shareName);
		FileManipulation noShareFile  = new FileManipulation(noShareName);
		
		Link [] links = this.treeNetwork.getLinks();
		int linkSharedReuseBw =0;
		int linkBandwidthReuseNoShare =0;
			
		shareBwFile.writeInFile("\n\n==================================================================================================================================\n");
		shareBwFile.writeInFile("\n\n=============================Load "+load +"===Set Nb "+setNb+"==============================================================\n");
		
		noShareFile.writeInFile("\n\n==================================================================================================================================\n");
		noShareFile.writeInFile("\n\n=============================Load "+load +"===Set Nb "+setNb+"==============================================================\n");
		
		for (int i=0; i<links.length;i++)
		{
					
			for (int j=0; j<links[i].sharingSets.size(); j++)
			{
				linkSharedReuseBw += links[i].sharingSets.get(j).bandwidthToReserve;
			}
			
			for (int j=0; j<links[i].bandwidthForRequests.size(); j++)
			{
				//if reuse we won't have any sharing
				linkSharedReuseBw +=links[i].bandwidthForRequests.get(j)[2];
				
				//if consider backup bandwidth reused and shared[3] as the backup bw to reserve when reuse no sharing
				linkBandwidthReuseNoShare +=links[i].bandwidthForRequests.get(j)[2] + links[i].bandwidthForRequests.get(j)[3];
			
			}
			
			shareBwFile.writeInFile (linkSharedReuseBw+"\n");
			noShareFile.writeInFile(linkBandwidthReuseNoShare+"\n");

			
			linkSharedReuseBw = 0;
			linkBandwidthReuseNoShare = 0;
			
		}
	}
}
