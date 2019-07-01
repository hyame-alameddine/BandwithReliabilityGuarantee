/**
 * This class contains all the functions that provide information about the network status including :
 * 
 * 
 */
package ExperimentalCalculations;
import java.util.ArrayList;

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
		int rejectedRequests = 0 ;
		int requestsNb = this.requests.size();
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
	
	
		System.out.println("rejected requests = "+rejectedRequests+"requestNb ="+requestsNb);
		rejectionRate = (double)rejectedRequests/(double)requestsNb*100;
		System.out.println("rejection rate"+rejectionRate);
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
	 * 
	 * @return ArrayList<int[]> <request id, bw reserved by algo1, bw reserved by algo2, backup VMs reserved by algo1, backup VMs reserved by algo2>
	 */
	public ArrayList<int[]> requestsBandwidthConparison(ArrayList<int[]> requestsBw1, ArrayList<int[]> requestsBw2)
	{		
		ArrayList<int[]> requestsBwComparison = new ArrayList<int[]>();
		int [] requestConsumption = new  int [5];
		
		//loop over requests 
		for (int i=0; i< requestsBw1.size(); i++)
		{		
			//skip the requests that were not admitted by both algorithms
			if (requestsBw1.get(i)[1] == -1 || requestsBw2.get(i)[1] == -1 )
			{
				continue;
			}
			
			//request id
			requestConsumption [0] = requestsBw1.get(i)[0];
			//bw reservation given by requestsBw1
			requestConsumption [1] = requestsBw1.get(i)[1];
			//bw reservation given by requestsBw2
			requestConsumption [2] = requestsBw2.get(i)[1];
			//reserved backup VMs given by requestsBw1
			requestConsumption [3] = requestsBw1.get(i)[2];
			//reserved backup VMs given by requestsBw2
			requestConsumption [4] = requestsBw2.get(i)[2];
			requestsBwComparison.add(requestConsumption);
			
			requestConsumption = null;
			requestConsumption = new int[5];	
			
		}
		
		return requestsBwComparison;		
	}
}
