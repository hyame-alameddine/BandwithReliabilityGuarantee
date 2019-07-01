/**
 * This class only contains main functions to test the sharing between tenants
 * in offline and online mode
 */
package MainFunctionalities;

import ilog.concert.IloException;

import java.io.IOException;
import java.util.ArrayList;

import ExperimentalCalculations.NetworkStatus;
import ExperimentalCalculations.Poisson;
import HelperClasses.FileManipulation;
import HelperClasses.Search;
import Network.FatTreeNetwork;
import Network.Link;
import Network.PhysicalMachine;
import Network.Request;
import Network.SubTree;
import Network.Switch;
import Network.VirtualMachine;
import Network.Request.RejectionReason;

public class BandwidthShareBetweenTenants {
	
	/**
	 * This is a testing function (mainly used for offlineMode) that embeds and protects 2 requests that 
	 * have VMs hosted on the same server that can share their bandwidth
	 * 
	 * This example is embedded on a network new FatTreeNetwork(8,4,2,2,2,1000,1000,1000);
	 * 
	 * @param vmProtection with the network assigned
	 */
	public  void shareTenantsTestFunction ( VMsProtectionWithBandwidthGuarantee vmProtection)
	{
		Request r1,r2;
		PhysicalMachine pm = null;
		Link l = null;
		Switch [] subtreesRoots =  vmProtection.treeNetwork.getSwitchSetPerTreeLevel(3);
		SubTree s = new SubTree(subtreesRoots[0]);	
		 vmProtection.treeNetwork.buildSubTree(s, subtreesRoots[0]);
		
		
		r1 = new Request(0,3,200);
		vmProtection.requests.add(r1);
		r1.admitted = true;
		r1.subtree = s;
		pm = vmProtection.treeNetwork.physicalMachinesSet[0];		
		pm.reserveVM(2, r1, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);				
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.PRIMARY);
		
		//reserve TOr to agg bw
		l=s.searchLink(l.destinationNode);	
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.PRIMARY);
		
		l=s.searchLink(l.destinationNode);	
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.BACKUP);
		
		pm =  vmProtection.treeNetwork.physicalMachinesSet[3];		
		pm.reserveVM(1, r1,  VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.PRIMARY);
	
		//reserve TOr to agg bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.PRIMARY);

		
		//reserve agg to core
		
		pm =  vmProtection.treeNetwork.physicalMachinesSet[4];		
		pm.reserveVM(1, r1,  VirtualMachine.Type.BACKUP);
		l = s.searchLink(pm);
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.BACKUP);
		
		//reserve TOr to agg bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.BACKUP);
		
		//reserve agg to core
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.BACKUP);
		
		
		pm =  vmProtection.treeNetwork.physicalMachinesSet[6];		
		pm.reserveVM(1, r1,  VirtualMachine.Type.BACKUP);
		l = s.searchLink(pm);
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.BACKUP);
		
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(200, r1.id, Link.BandwidthType.BACKUP);
		
		
		//set which vm is backed up by which 
		r1.virtualMachinesSet.get(0).backupVm = r1.virtualMachinesSet.get(3);
		r1.virtualMachinesSet.get(1).backupVm = r1.virtualMachinesSet.get(4);
		r1.virtualMachinesSet.get(2).backupVm = r1.virtualMachinesSet.get(4);
		System.out.println("r1"+r1);
		subtreesRoots =  vmProtection.treeNetwork.getSwitchSetPerTreeLevel(3);
		s = new SubTree(subtreesRoots[0]);	
		 vmProtection.treeNetwork.buildSubTree(s, subtreesRoots[0]);
		r2 = new Request(1,2,300);
		r2.admitted = true;
		vmProtection.requests.add(r2);
		r2.subtree = s;
			
		pm =  vmProtection.treeNetwork.physicalMachinesSet[2];
		pm.reserveVM(1, r2, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(300, r2.id, Link.BandwidthType.PRIMARY);

		//reserve TOr to agg bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(300, r2.id, Link.BandwidthType.BACKUP);
		
		//reserve agg core bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(300, r2.id, Link.BandwidthType.BACKUP);
		
		pm = vmProtection.treeNetwork.physicalMachinesSet[3];
		pm.reserveVM(1, r2, VirtualMachine.Type.PRIMARY);
		l = s.searchLink(pm);
		l.reserveBandwidth(300, r2.id, Link.BandwidthType.PRIMARY);	
		
				
		pm =  vmProtection.treeNetwork.physicalMachinesSet[5];
		pm.reserveVM(1, r2, VirtualMachine.Type.BACKUP);
		l = s.searchLink(pm);
		l.reserveBandwidth(300, r2.id, Link.BandwidthType.BACKUP);
		
		//reserve TOr to agg bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(300, r2.id, Link.BandwidthType.BACKUP);
		
		//reserve agg core bw
		l=s.searchLink(l.destinationNode);
		l.reserveBandwidth(300, r2.id, Link.BandwidthType.BACKUP);
		
		
		r2.virtualMachinesSet.get(0).backupVm = r2.virtualMachinesSet.get(2);
		r2.virtualMachinesSet.get(1).backupVm = r2.virtualMachinesSet.get(2);
		System.out.println("r2"+r2);
		
		Link[] links =  vmProtection.treeNetwork.getLinks();
		
		for (int i=0; i<links.length; i++)
		{
			System.out.println(links[i]);
		}
	}
	
	
	/**
	 * This is a helper function to set the Vms and bandwidth which we want to 
	 * run on several networks Each 5 sets corresponds to a load
	 * 
	 * @return array list of array of vms and bw
	 */
	public ArrayList <ArrayList<int[]>> intializeVmsSets ()
	{
		ArrayList <ArrayList<int[]>> sets = new ArrayList <ArrayList<int[]>>();
		ArrayList<int[]> randomVmsBw = new ArrayList<int[]>();
 
		 
		//load 2
		/*
		 * Example can't share with vm on same server valid for online and offline +	FatTreeNetwork treeNetwork = new FatTreeNetwork(8,4,2,2,2,1000,10000,10000);
		 *  int [] randomVms = {9,3,4,7,7} ;
		 *   int [] randomBw = {400,100,100,50,50} ;
		 */
		
		 
		 /*int [] randomVms = {9,5,4,3,3} ;
		 int [] randomBw = {400,100,100,10,50} ;*/
		 int [] randomVms = {3,3,2,2,2,} ;
		 int [] randomBw = {100,100,100,250,50,} ;
		 randomVmsBw.add(randomVms);
		 randomVmsBw.add(randomBw);
		 sets.add(randomVmsBw);
		return sets;
	}
	
	
	/**
	 * This is a helper function to set the arrivals and departures of
	 * a set of requests that we want to run for several networks
	 * 
	 * Their order corresponds to the ones mentioned in intializeVmsSets()
	 * 
	 * @return Array list of arrays of departures and arrivals
	 */
	public ArrayList <ArrayList<double[]>> intializePoissonSets()
	{
		ArrayList <ArrayList<double[]>> sets = new ArrayList <ArrayList<double[]>>();
		ArrayList<double[]> distribution = new ArrayList<double[]>();
		
		//load 2
		double[] arrivals = {0.0,0.0685296384407159,0.12163520209560337,0.17215781955930975,0.26270748258341886, }; 
		double[] departures = {0.33403040133984687,0.47027418517019075,0.52548765605931572,0.6943056791293614,0.7066874399467417};
		distribution.add(arrivals);
		distribution.add(departures);
		sets.add(distribution);


		return sets;
		
	}
	
	
	/**
	 * THis function performs the following:
	 * 1-Generate requests
	 * 2-Primary Embedding
	 * 3-Backup Embedding
	 * 4-Bandwidth share
	 * 5-Results are put in TestResults/BandwidthSahreResults.txt
	 * 
	 * @throws IloException
	 * @throws IOException
	 */
	public void offlineBandwidthShare () throws IloException, IOException
	{
		FileManipulation initial  = new FileManipulation("TestResults/initialVm.txt");
		FileManipulation cloned  = new FileManipulation("TestResults/CloneVm.txt");
		
		String mainFileName = "TestResults/BandwidthShareResults.txt";
		FileManipulation mainFile  = new FileManipulation(mainFileName);
		VMsProtectionWithBandwidthGuarantee  vmsProtection;
		ArrayList<Request> requests = new ArrayList<Request>();
		int reservedBandwidthBeforeSharing =0;
		int reservedBandwidthAfterSharing =0;
		double bandwidthGain =0.0;
		NetworkStatus networkStatus = null;
		Link [] links = null;
		ArrayList<Link> networkLinks = new ArrayList<Link>();
		ArrayList <ArrayList<int[]>> vmSets =null;
		ArrayList<int[]> randomVmsBw = new ArrayList<int[]>();
		
		//requests parameters
		int requestsNb =10;		
		int minVms = 2;
		int maxVms = 9;
		int minBw = 50;
		int maxBw = 200;
		
		FatTreeNetwork treeNetwork = new FatTreeNetwork(8,4,2,2,2,1000,1000,1000);
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(128,6,2,2,32,1000,10000,10000);
		treeNetwork.buildTreeNetwork();

		vmsProtection = new VMsProtectionWithBandwidthGuarantee(treeNetwork, requests);	
		
		links = vmsProtection.treeNetwork.getLinks();
		
		for (int i=0; i<links.length; i++)
		{
			networkLinks.add(links[i]);
		}
		
		
		//vmsProtection.backupToVmMappingSolution();
		vmsProtection.generateRequests(requestsNb, minVms,maxVms,minBw,maxBw);
		/*vmSets = this.intializeVmsSets();	
		randomVmsBw = vmSets.get(0);
		
		//add only arrival requests
		vmsProtection.copyGeneratedRequests(randomVmsBw.get(0), randomVmsBw.get(1), mainFile);*/
		
		//do primary and backup embedding
		vmsProtection.backupToVmMappingModelTest(vmsProtection.requests,false,false, null,0,null,null);//@todo support fault domains when needed
		initial.writeInFile(vmsProtection.toString());
		VMsProtectionWithBandwidthGuarantee newVmProtection = vmsProtection.clone();
		cloned.writeInFile(newVmProtection.toString());
		
	//	this.shareTenantsTestFunction(vmsProtection);
		/*networkStatus = new NetworkStatus(treeNetwork, vmsProtection.requests);
		
		//optimize bandwidth use by applying bandwidth sharing
		vmsProtection.shareBandwidthBetweenTenants(networkLinks, true);
			
		bandwidthGain = networkStatus.calculateSharedBandwidthGain();
		
		mainFile.writeInFile("\n======================OFFLINE MODE=============================\n");
		mainFile.writeInFile("\n=============================Network :=============================\n");
		mainFile.writeInFile(treeNetwork.toString());
		mainFile.writeInFile("\n=============================Requests: Nb : "+requestsNb+"=== VMs range : <"+minVms+","+maxVms+"> === Bw range : <"+minBw+","+maxBw+" >=============================\n");
		
		mainFile.writeInFile("\n\n=======Bandwidth reserved before sharing between tenants: "+reservedBandwidthBeforeSharing+" =============================\n");
		mainFile.writeInFile("=======Bandwidth reserved after sharing between tenants: "+reservedBandwidthAfterSharing+" =============================\n");
		mainFile.writeInFile("=======Bandwidth gain (in %) "+bandwidthGain+" % =============================\n\n");
		
		mainFile.writeInFile("\n=============================Requests Allocation :=============================\n");
		mainFile.writeInFile(networkStatus.requestsInfoToString());
		mainFile.writeInFile("\n\n=============================Links information :=============================\n");
		mainFile.writeInFile(networkStatus.linksInfoToString());*/
	}
	
	
		
	/**
	 * THis function performs the following:
	 * 1-Generate requests in a poisson distribution
	 * 2-Primary Embedding
	 * 3-Backup Embedding
	 * 4-Bandwidth share
	 * 5-Results are put in TestResults/BandwidthSahreResults.txt
	 * 
	 * @throws IloException
	 * @throws IOException
	 */
	public void onlineBandwidthShare () throws IloException, IOException
	{
		String mainFileName = "TestResults/BandwidthShareResults.txt";
		String bwGainOverTimeFile = "TestResults/BWGain.txt";
		
		FileManipulation mainFile  = new FileManipulation(mainFileName);
		FileManipulation bwGainFile  = new FileManipulation(bwGainOverTimeFile);
		
		VMsProtectionWithBandwidthGuarantee  vmsProtection;

		NetworkStatus networkStatus = null;
		ArrayList<Request> sortedRequests = new ArrayList<Request>();

		//requests parameters
		int requestsNb =5;		
		int minVms = 5;
		int maxVms = 10;
		int minBw = 100;
		int maxBw = 300;
		
		Poisson poissonDistribution =null;
		//int [] arrivalRates = {20 ,40,60,80,100};
		int arrivalRate = 20;
		int departureRate = 10;
		
	
		ArrayList <ArrayList<int[]>> vmSets =null;
		ArrayList<int[]> randomVmsBw = new ArrayList<int[]>();
		ArrayList <ArrayList<double[]>> poissonSets =null;
		
		FatTreeNetwork treeNetwork = new FatTreeNetwork(8,4,2,2,2,1000,10000,10000);
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(128,6,2,2,32,1000,10000,10000);
		treeNetwork.buildTreeNetwork();
		
		vmsProtection = new VMsProtectionWithBandwidthGuarantee(treeNetwork, sortedRequests);	
		//vmsProtection.generateRequests(requestsNb, minVms,maxVms,minBw,maxBw);
		
		//get the sets of requests vm/bw /distribution already generated that we want to run on another network
		vmSets = this.intializeVmsSets();
		poissonSets = this.intializePoissonSets();
		
		poissonDistribution = new Poisson(arrivalRate ,departureRate,requestsNb);		
		poissonDistribution.arrivals = poissonSets.get(0).get(0);
		poissonDistribution.departures = poissonSets.get(0).get(1);
		
		randomVmsBw = vmSets.get(0);
		vmsProtection.copyGeneratedRequests(randomVmsBw.get(0), randomVmsBw.get(1), mainFile);
		
		// set Poisson Arrival/Departure and update the arrival and departure time of the requests	
		vmsProtection.poissonDistribution(poissonDistribution);	
				
		/**
		 * sort the requests using the poisson distribution ascendley
		 * 	the sorted requests will contain 2 copy of each request one for arrival and another for departure.
		 * Only the arrival requests have updated information regarding admitted 
		 */	
		sortedRequests = vmsProtection.sortRequests();
		vmsProtection.requests = sortedRequests;
				
		//do primary and backup embedding with bandwidth share using heuristic for building sharing sets
		vmsProtection.backupToVmMappingModelTest(vmsProtection.requests,true, false, bwGainFile,0,null,null);//@todo support fault domains when needed);	
		
		networkStatus = new NetworkStatus(treeNetwork, vmsProtection.requests);
		System.out.println("req size "+vmsProtection.requests.size());		
	
		mainFile.writeInFile("\n======================ONLINE MODE=============================\n");
		mainFile.writeInFile("\n=============================Network :=============================\n");
		mainFile.writeInFile(treeNetwork.toString());
		mainFile.writeInFile("\n=============================Requests: Nb : "+requestsNb+"=== VMs range : <"+minVms+","+maxVms+"> === Bw range : <"+minBw+","+maxBw+" >=============================\n");
		
		mainFile.writeInFile("\n\n=======Rejection rate: "+networkStatus.rejectionRate()+" =============================\n");
		
		mainFile.writeInFile("\n=============================Requests Allocation :=============================\n");
		mainFile.writeInFile(networkStatus.requestsInfoToString());
		mainFile.writeInFile("\n\n=============================Links information :=============================\n");
		mainFile.writeInFile(networkStatus.linksInfoToString());
		
	}
	

	/**
	 * This is a helper function to automate the test of the 3 main algorithms
	 * 1-offlineMode -> No poisson arrival -> vmsProtection.backupToVmMappingModelTest() then vmsProtection.shareBandwidthBetweenTenants(networkLinks, false); without use of share tenants model
	 * 2-bandwidthShareModel ->No poisson arrival -> vmsProtection.backupToVmMappingModelTest() then vmsProtection.shareBandwidthBetweenTenants(networkLinks, false); with use of share tenants model
	 * 3-onlineMode -> Poisson arrival ->vmsProtection.backupToVmMappingModelTest()+share between tenants upon each arrival/departure using share algo
	 * 
	 * It prints the results in a file
	 * 
	 * @param algorithm the name of the algorithm to execute (offlineMode, onlineMode, bandwidthShareModel)
	 * @param ArrayList<int[]> randomVmsBw array of generated Vm bw corresponding to the requests
	 * @param poissonDistribution
	 * @param setNb number of the set we are testing. This is needed to put the results of each set in an independent file
	 * @param shareBw boolean that specifies if we want to share bandwidth or not for online mode
	 * @param vmsProtection if null the code will create one else it will use it to share bw between tenants (used to test offline mode/model on the same embedding)
	 * @param doEmbedding if set to true, the code will realize the embedding and protection of the requests for offline mode/bandwidthShareModel
	 * @param useModel if true it will run online mode with model sharing
	 * 
	 * @throws IOException 
	 * @throws IloException 
	 * 
	 * @return vmProtection object with the embedding of requests without sharing (if offline mode/bandwidthShareModel) and null if online mode
	 *
	 * 
	 * 
	 */
	public VMsProtectionWithBandwidthGuarantee automatedTesting (String algorithm, ArrayList<int[]> randomVmsBw, Poisson poissonDistribution, int setNb, boolean shareBw, VMsProtectionWithBandwidthGuarantee  vmsProtection, boolean doEmbedding, boolean useModel) throws IOException, IloException
	{
		double load = poissonDistribution.lambda/poissonDistribution.mu;
		String sharingBw = "SharingBw";
		ArrayList<Request> rejectedRequests = null;
		ArrayList<Request> rejectedRequestsAfter2ndEmbedding = null;
		Search searchObj = new Search ();
		NetworkStatus networkStatus = null;
		VMsProtectionWithBandwidthGuarantee newVmProtection =  null;
		
		if (algorithm.equals("onlineMode") && shareBw==false)
		{
			sharingBw = "NoBwShare";
		}
		else if (algorithm.equals("onlineMode") && useModel==true)
		{
			sharingBw = "MODELSharingBw";
		}
		String mainFileName = "TestResults/"+algorithm+"_"+sharingBw+"_GeneralExperimentalResults.txt";
		String timersFile = "TestResults/ArrivalDepartureTime/"+algorithm+"_"+sharingBw+"_ArrivalDepartureTime_"+load+"_"+setNb+".txt";
		String revenueOverTimeFile = "TestResults/RevenueOverTime/"+algorithm+"_"+sharingBw+"_RevenueOverTime_"+load+"_"+setNb+".txt";
		String bwGainOverTimeFile = "TestResults/OnlineModeBWGain/"+algorithm+"_"+sharingBw+"_BwGainOverTime_"+load+"_"+setNb+".txt";
		String admittedRequestsName = "TestResults/"+algorithm+"_"+sharingBw+"_admittedRequestsPrimaryBackupVMs.txt";
		
		FileManipulation initial  = new FileManipulation("TestResults/initialVm.txt");
		FileManipulation cloned  = new FileManipulation("TestResults/CloneVm.txt");
		FileManipulation admittedRequestsVMsNb  = new FileManipulation(admittedRequestsName);
		
		long startTime =0;
		long endTime = 0;
		long executionTime = 0;
		
		double rejectionRate = -1;
		double rejectionRateAfter2ndEmbedding = -1;
		double revenue = -1 ;	
		double revenueAfter2ndEmbedding = -1 ;
		double bandwidthGain =-1;
		double bandwidthGainAfter2ndEmbedding =-1;
		int bandwidthCanBeshared =-1;
		int reservedsharedBandwidth=-1; 
		int bandwidthCanBeshared2ndEmbedding=-1;
		int reservedsharedBandwidth2ndEmbedding=-1; 
		
		Link [] links = null;
		ArrayList<Link> networkLinks = new ArrayList<Link>();			
		ArrayList<Double> timeRevenue = new ArrayList<>();
		ArrayList<Request> sortedRequests = new ArrayList<Request>();
		
		ArrayList<RejectionReason> rejectionReasons = new ArrayList<RejectionReason>();
		rejectionReasons.add(RejectionReason.BACKUP_MAPPING_BANDWIDTH);
		rejectionReasons.add(RejectionReason.PRIMARY_BANDWIDTH);
	
		FileManipulation mainFile  = new FileManipulation(mainFileName);
		
		mainFile.writeInFile("\n\n---------------------------------------load: "+load+"--------set nb :"+setNb+"----------------------\n");
		if (vmsProtection == null)
		{
			//FatTreeNetwork treeNetwork1 = new FatTreeNetwork(12,4,2,2,3,500,1000,1000);
			FatTreeNetwork treeNetwork1 = new FatTreeNetwork(128,6,2,2,32,1000,10000,10000);
			//FatTreeNetwork treeNetwork1 = new FatTreeNetwork(256,6,2,2,64,1000,10000,10000);
			treeNetwork1.buildTreeNetwork();	

			//create vmsProtection object
			vmsProtection = new VMsProtectionWithBandwidthGuarantee(treeNetwork1, sortedRequests);	
			
			//generate the requests and store them to run them for the 2 algorithms (only arrival requests)
			vmsProtection.copyGeneratedRequests(randomVmsBw.get(0), randomVmsBw.get(1), mainFile);
		}
		
		mainFile.writeInFile(poissonDistribution.toString());
		mainFile.writeInFile("\n\n==================================================================================================================================\n");
		mainFile.writeInFile(vmsProtection.treeNetwork.toString());			
		
		links = vmsProtection.treeNetwork.getLinks();
		
		for (int i=0; i<links.length; i++)
		{
			networkLinks.add(links[i]);
		}		
		
		if (algorithm.equals("offlineMode"))
		{	
			
			if (doEmbedding)
			{
				vmsProtection.backupToVmMappingModelTest(vmsProtection.requests,false,false, null,0,null,null);//@todo support fault domains when needed);
				initial.writeInFile(vmsProtection.toString());
			
				newVmProtection = vmsProtection.clone();
				cloned.writeInFile(newVmProtection.toString());
			}
			
			startTime = System.currentTimeMillis();		
			//optimize bandwidth use by applying bandwidth sharing
			vmsProtection.shareBandwidthBetweenTenants(networkLinks, false);
			endTime = System.currentTimeMillis();
			
			//calculate the rejection rate, bandwidth gain in all the network
			networkStatus = new NetworkStatus(vmsProtection.treeNetwork, vmsProtection.requests);		
			rejectionRate = networkStatus.rejectionRate();
			
			bandwidthCanBeshared = networkStatus.getBandwidthToShare();
			reservedsharedBandwidth = networkStatus.getTotalTenantsSharedBandwidth();
			bandwidthGain = networkStatus.calculateSharedBandwidthGain();
			
			//calculate the total revenue 60$ per Vm/ 3$per GB = 0.003$/Mb
			revenue = networkStatus.calculateRevenue(60, 0.003);
			
			//print the number of admitted requests primary and backup VMs to check the backup footprint
			searchObj.printAdmittedRequestsVMs(vmsProtection.requests, admittedRequestsVMsNb);	
			
			//get the rejected requests and try to embed them again after sharing bw 
			rejectedRequests = searchObj.getRejectedRequests(vmsProtection.requests, rejectionReasons);	
			mainFile.writeInFile(" Nb of Rejected request before 2nd run :"+rejectedRequests.size());
			
			
			/*if (rejectedRequests.size()!=0)
			{			
				//try to embed the rejected request because of lack of bandwidth
				vmsProtection.backupToVmMappingModelTest(rejectedRequests,false,false, null);
				//release the shared bandwidth on these link to be able to recompute the sharing sets
				vmsProtection.releaseSharedBandwidth (networkLinks);
				vmsProtection.shareBandwidthBetweenTenants(networkLinks, false);
				
				rejectedRequestsAfter2ndEmbedding = searchObj.getRejectedRequests(rejectedRequests, null);	
				mainFile.writeInFile(" Nb of Rejected request After 2nd run :"+rejectedRequestsAfter2ndEmbedding.size());				
				
				//calculate the new bandwidth gain in the network (considering all requests including admitted in 2nd round)
				bandwidthCanBeshared2ndEmbedding = networkStatus.getBandwidthToShare();
				reservedsharedBandwidth2ndEmbedding = networkStatus.getTotalTenantsSharedBandwidth();
				bandwidthGainAfter2ndEmbedding = networkStatus.calculateSharedBandwidthGain();
				
				//add the revenue of the newly admitted requests
				revenueAfter2ndEmbedding = networkStatus.calculateRevenue(60, 0.003);
				
				networkStatus.treeNetwork = vmsProtection.treeNetwork;
				networkStatus.requests = rejectedRequests;
				//calculate the rejection rate in all the network based on the rejected requests (rejection rate from the rejected requests only)
				rejectionRateAfter2ndEmbedding = networkStatus.rejectionRate();
			}	*/	
			
			/******
			 * 
			 */
			cloned.writeInFile("=====================NEW CLONED NETWORK PRINTED AFTER SHARING and 2nd embedding========");
			cloned.writeInFile(newVmProtection.toString());		
		}
		else if(algorithm.equals("bandwidthShareModel"))
		{
			if (doEmbedding)
			{
				vmsProtection.backupToVmMappingModelTest(vmsProtection.requests,false,false,null,0,null,null);//@todo support fault domains when needed;	
				newVmProtection = vmsProtection.clone();
			}
			startTime = System.currentTimeMillis();		
			//optimize bandwidth use by applying bandwidth sharing
			vmsProtection.shareBandwidthBetweenTenants(networkLinks, true);
			endTime = System.currentTimeMillis();
			
			//calculate the rejection rate, bandwidth gain in all the network
			networkStatus = new NetworkStatus(vmsProtection.treeNetwork, vmsProtection.requests);	
			rejectionRate = networkStatus.rejectionRate();
			
			bandwidthCanBeshared = networkStatus.getBandwidthToShare();
			reservedsharedBandwidth = networkStatus.getTotalTenantsSharedBandwidth();
			bandwidthGain = networkStatus.calculateSharedBandwidthGain();
			
			//calculate the total revenue 60$ per Vm/ 3$per GB = 0.003$/Mb
			revenue = networkStatus.calculateRevenue(60, 0.003);
	
			
			//get the rejected requests and try to embed them again after sharing bw 
			rejectedRequests = searchObj.getRejectedRequests(vmsProtection.requests, rejectionReasons);	
			mainFile.writeInFile("Nb of Rejected request before 2nd run :"+rejectedRequests.size());
		
		/*	if (rejectedRequests.size()!=0)
			{
				//run again for the rejected requests			
				vmsProtection.backupToVmMappingModelTest(rejectedRequests, false,false, null);	
				//release the shared bandwidth on these link to be able to recompute the sharing sets
				vmsProtection.releaseSharedBandwidth (networkLinks);
				vmsProtection.shareBandwidthBetweenTenants(networkLinks, true);
								
				rejectedRequestsAfter2ndEmbedding = searchObj.getRejectedRequests(rejectedRequests, null);	
				mainFile.writeInFile(" Nb of Rejected request After 2nd run :"+rejectedRequestsAfter2ndEmbedding.size());
				
							
				//calculate the new bandwidth gain in the network (considering all requests including admitted in 2nd round)
				bandwidthCanBeshared2ndEmbedding = networkStatus.getBandwidthToShare();
				reservedsharedBandwidth2ndEmbedding = networkStatus.getTotalTenantsSharedBandwidth();
				bandwidthGainAfter2ndEmbedding = networkStatus.calculateSharedBandwidthGain();
				
				//add the revenue of the newly admitted requests
				revenueAfter2ndEmbedding = networkStatus.calculateRevenue(60, 0.003);
				
				networkStatus.treeNetwork = vmsProtection.treeNetwork;
				networkStatus.requests = rejectedRequests;
				//calculate the rejection rate in all the network based on the rejected requests (rejection rate from the rejected requests)
				rejectionRateAfter2ndEmbedding = networkStatus.rejectionRate();
				
			}		*/	
			
		}
		else
		{
			// set Poisson Arrival/Departure and update the arrival and departure time of the requests	
			vmsProtection.poissonDistribution(poissonDistribution);	
			mainFile.writeInFile(poissonDistribution.toString());
			
			/**
			 * sort the requests using the poisson distribution ascendley
			 * 	the sorted requests will contain 2 copy of each request one for arrival and another for departure.
			 * Only the arrival requests have updated information regarding admitted 
			 */	
			sortedRequests = vmsProtection.sortRequests();
			vmsProtection.requests = sortedRequests;		
			
			startTime = System.currentTimeMillis();	
			
			//get the gain over time only if we were sharing bw
			FileManipulation bwGainFile  =  shareBw? new FileManipulation(bwGainOverTimeFile):null;
			
			//do primary and backup embedding with bandwidth share using heuristic for building sharing sets
			vmsProtection.backupToVmMappingModelTest(vmsProtection.requests,shareBw, useModel, bwGainFile,0,null,null);//@todo support fault domains when needed);	
			endTime = System.currentTimeMillis();
			
			 networkStatus = new NetworkStatus(vmsProtection.treeNetwork, vmsProtection.requests);
			
			//calculate the rejection rate, bandwidth gain in all the network
			rejectionRate = networkStatus.rejectionRate();
						
			//calculate the total revenue 60$ per Vm/ 3$per GB = 0.003$/Mb
			revenue = networkStatus.calculateRevenue(60, 0.003);
			
			//print the number of admitted requests primary and backup VMs to check the backup footprint
			searchObj.printAdmittedRequestsVMs(vmsProtection.requests, admittedRequestsVMsNb);	
		}
		
		executionTime = endTime - startTime;		
	
	
		mainFile.writeInFile("\n---- Revenue (if offline mode, it is the revenue for the initial set):"+revenue+" considering VM cost = 60$ and bandwidth cost per 0.003$/Mb = 3$/Gb -----\n ");
		mainFile.writeInFile("\n---- Revenue (if offline mode, it is the revenue after embedding the rejected set):"+revenueAfter2ndEmbedding+" considering VM cost = 60$ and bandwidth cost per 0.003$/Mb = 3$/Gb -----\n ");
		//calculate the revenue over time only needed in online mode
		if (algorithm.equals("onlineMode"))
		{
			FileManipulation timerFile = new FileManipulation(timersFile);
			FileManipulation revenueTimeFile = new FileManipulation(revenueOverTimeFile);
			
			timeRevenue = networkStatus.calculateRevenueOverTime(0.6, 0.003, 1000);
		
			timerFile.writeInFile("----------------------------Time----------------------------------------------\n");
			revenueTimeFile.writeInFile("--------------------------------Revenue over time---------------------------------- \n");
					
			for (int i =0; i<timeRevenue.size(); i++)
			{
				//print only revenue when we have an arrival/departure
				if(timeRevenue.get(i) == 0.0)
				{
					continue;
				}
				timerFile.writeInFile(i+"\n");
				revenueTimeFile.writeInFile(timeRevenue.get(i)+"\n");
			}		
		}
		
		
		mainFile.writeInFile("\n------------------ Execution of : "+algorithm+" -------------------\n ");
		mainFile.writeInFile("\n---- ExecutionTime:"+executionTime+" -----\n ");
		mainFile.writeInFile("\n---- Rejection Rate (if offline mode, it is the rejection rate for the initial set )"+rejectionRate+" -----\n ");		
		mainFile.writeInFile("\n---- Rejection Rate (if offline mode, it is the rejection rate after embedding  the rejected set)"+rejectionRateAfter2ndEmbedding+" -----\n ");	
		
		//no need to export links bandwidth gain and bandwidth gain for online mode after all the requests left the network. there will be no gain
		if (algorithm.equals("onlineMode") )
		{
			//just need to break the code
			return vmsProtection;
		}
		
		//this will export the links bw gain after trying to embed the rejected requests (in offline mode)
		networkStatus.exportLinksBwGain(algorithm, load, setNb);
		//will export the links reserved backup bw reserved (sharing+reuse or reuse without sharing)
		networkStatus.exportLinksBackupBwInfo(algorithm, load, setNb);
			
		//calculate the bandwidth gain in all the network			
		mainFile.writeInFile("\n---- Bandwidth Gain :(if offline mode, it is the bandwidth gain for the initial set :"+bandwidthGain+" bandwidth can be shared :"+bandwidthCanBeshared+"reserved shared bandwidth "+reservedsharedBandwidth+"\n");		
		mainFile.writeInFile("\n---- Bandwidth Gain :(if offline mode, it is the bandwidth gain after trying to embed the rejected requests :"+bandwidthGainAfter2ndEmbedding+" bandwidth can be shared :"+bandwidthCanBeshared2ndEmbedding+"reserved shared bandwidth "+reservedsharedBandwidth2ndEmbedding+"\n");		
		mainFile.writeInFile("==================================================================================================================================\n");
			
		return newVmProtection;
	}
	
	
	
	
	
	public static void main(String[]args) throws IloException, IOException
	{
		BandwidthShareBetweenTenants bwShare =  new BandwidthShareBetweenTenants();
		//bwShare.offlineBandwidthShare();
		//bwShare.onlineBandwidthShare();
		
		
		Poisson poissonDistribution ;
		//int [] arrivalRates = {20 ,40,60,80,100};
		//int departureRate = 10;
		
		int [] arrivalRates  = {20};//{20,40,60,80,100};
		int departureRate = 10;
		
		//requests parameters
		int requestsNb =100;		
		int minVms = 5;
		int maxVms =25;
		int minBw = 100;
		int maxBw = 500;
	
		//number of sets per load to test
		int setsNb = 1;//5;
		
		FatTreeNetwork treeNetwork = null;	
		VMsProtectionWithBandwidthGuarantee  vmsProtection, newVmProtection;
		ArrayList <ArrayList<int[]>> vmSets =null;
		ArrayList <ArrayList<double[]>> poissonSets =null;
		ArrayList<int[]> randomVmsBw = new ArrayList<int[]>();
		ArrayList<Request> sortedRequests = new ArrayList<Request>();

		int nbsets = 0;//15;
		
		//create a test status file
		String testStatusFile = "TestResults/TestStatus.txt";
		FileManipulation statusFile  = new FileManipulation(testStatusFile);
		
		vmsProtection = new VMsProtectionWithBandwidthGuarantee(treeNetwork, sortedRequests);	
		
		//get the sets of requests vm/bw /distribution already generated that we want to run on another network
		vmSets = vmsProtection.intializeVmsSets();
		poissonSets = vmsProtection.intializePoissonSets();
		
		statusFile.writeInFile("\n=============================Network : FatTreeNetwork(128,6,2,2,32,1000,10000,10000);=============================\n");
		statusFile.writeInFile("\n=============================Requests: Nb : "+requestsNb+"=== VMs range : <"+minVms+","+maxVms+"> === Bw range : <"+minBw+","+maxBw+" >=============================\n");
		
		//for each load we need to test the 2 algorithm on 5 sets  each
		for (int i = 0; i<arrivalRates.length; i++)
		{
			statusFile.writeInFile("\n=============================LOAD : "+arrivalRates[i]/departureRate+"===Arrival : "+arrivalRates[i]+"===Departure : "+departureRate+"=============================\n");
			//set the load
			poissonDistribution = new Poisson(arrivalRates[i],departureRate,requestsNb);		
		
			for (int j=0; j<setsNb; j++)
			{
				//the below 3 lines are only needed when running a predefined sets of requests
				randomVmsBw = vmSets.get(nbsets);
				poissonDistribution.arrivals = poissonSets.get(nbsets).get(0);
				poissonDistribution.departures = poissonSets.get(nbsets).get(1);
				
				//generate the requests
			//	randomVmsBw = vmsProtection.generateRequests(requestsNb, minVms,maxVms,minBw,maxBw);
		
				statusFile.writeInFile("====Starting test for set "+j+"====\n");
				
				/*statusFile.writeInFile("=Starting test for Offline mode =\n");				
				newVmProtection =  bwShare.automatedTesting("offlineMode",randomVmsBw,poissonDistribution, j,false, null, true,false);
				
				//test the sharing between tenants model with the same embedding of offline mode
				statusFile.writeInFile("=Starting test for bandwidthShareModel=\n");				
				bwShare.automatedTesting("bandwidthShareModel",randomVmsBw,poissonDistribution, j,false, newVmProtection, false,false);*/
				
				statusFile.writeInFile("=Starting test for onlineMode with bandwidth share =\n");				
				bwShare.automatedTesting("onlineMode",randomVmsBw,poissonDistribution, j,true, null, true,false);
				
				/*statusFile.writeInFile("=Starting test for onlineMode with bandwidth MODEL share =\n");				
				bwShare.automatedTesting("onlineMode",randomVmsBw,poissonDistribution, j,true, null, true, true);
				
				statusFile.writeInFile("=Starting test for onlineMode with NO  bandwidth share =\n"); // can be use with no bw reuse also -- modify backuptoVmMappingEnhanced (search bandwidth reuse for required changes)				
				bwShare.automatedTesting("onlineMode",randomVmsBw,poissonDistribution, j,false, null, true, false);
				*/
				statusFile.writeInFile("====Finished test of set "+j+"====\n\n");
			
				nbsets++;
			}
		}
	
	}
	
}
