/**
 * Model Objective function:
 * -Return the maximum independent set (Sharing set) on a specified link
 * 
 * Model Functionality:
 * Given the primary VMs placement + backup VMs placements + primary and backup bandwidth
 * with bandwidth reuse considered, in addition to which backup Vms is protecting which primary one:
 * 
 * 1-Specify for the link of interest the maximum independent set (sharing set ) of tenants that
 * can share bandwidth on this link
 * 2-Determine the bandwidth that needs to be reserved for the determined set
 * 
 * Note:
 * All primary and backup bandwidth are guaranteed based on the hose model
 * 
 * Scalability:
 *  The model is not scalable
 */
package Models;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import HelperClasses.FileManipulation;
import MainFunctionalities.VMsProtectionWithBandwidthGuarantee;
import Network.Link;
import Network.Request;


public class ShareBwBetweenTenantsModel {
	
	//vm protection that contains the network where the requests are embedded
	public VMsProtectionWithBandwidthGuarantee vmsProtection;

	public IloCplex cplex;
	
	
	public ShareBwBetweenTenantsModel(VMsProtectionWithBandwidthGuarantee vmsProtection) throws IloException 
	{
		
		//considering that the network is already build
		this.vmsProtection = vmsProtection;
		this.cplex = new IloCplex();
		 cplex.setParam(IloCplex.IntParam.Threads, 2);  //Sets the number of CPU threads to be used for parallel optimization. Default: 
		 /**
		  *  turn on "strong branching" by setting the parameter VarSel to 3.
		  *  Strong branching causes cplex to spend more time at each node selecting higher-quality child nodes, which usually make the search tree smaller.
		  */
		 cplex.setParam(IloCplex.IntParam.VarSel, 4);
		 cplex.setParam(IloCplex.BooleanParam.MemoryEmphasis, true);//set MemoryEmphasis to True which will instruct cplex to try to conserve memory
		 cplex.setParam (IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Barrier);//optimize problem using barrier method
	}
	
	
	/**
	 * This function will populate the input parameters needed for the model
	 * 
	 * @return ArrayList of q, bt
	 */
	public ArrayList <int[][]> populateParameters (Link l, ArrayList <Integer> requestsIds, ArrayList <Integer> requestsBackupBw)
	{
		Request r1, r2;
		int T =0;
		
		//array that specifies if 2 tenants can share bandwidth
		int[][] q;
		
		//array of backup bandwidth needed by each request on l
		int [][] bt;
		
		ArrayList <int[][]> parameters = new ArrayList<int[][]>();
				
		T = requestsIds.size();
		q = new int[T][T];
		bt = new int[1][T];
		
		for (int i=0; i<requestsIds.size(); i++)
		{
			System.out.println(requestsIds.get(i));
			r1 = this.vmsProtection.getRequest(requestsIds.get(i), Request.Type.ARRIVAL);
			
			for (int j=0; j<requestsIds.size(); j++)
			{System.out.println(requestsIds.get(j)+"-");
				r2 = vmsProtection.getRequest(requestsIds.get(j), Request.Type.ARRIVAL);
				
				//a request can share bandwidth with itself because it will be the same bandwidth for itself only
				if (r1.equals(r2))
				{System.out.println("==r1"+r1.id+" r2 "+r2.id);
					q[i][j] = 0;
					continue;
				}
				System.out.println("in");
				if (!r1.canShareBandwidth(r2, l))
				{
					q[i][j] = 1;
				}
				r2=null;
			}
			
			bt[0][i] = requestsBackupBw.get(i);			
			r1=null;
		}
		
		parameters.add(q);
		parameters.add(bt);
		
		return parameters;
	}
	
	
	
	/**
	 * 
	 * This function formulate, solve and print the model
	 * 
	 * @param pmAllocation array specifying the embedding of tenant VM on servers
	 * @param primaryBandwdith array specifying the bandwidth reserved on links for primary VMs embedding
	 * @param alpha double representing the weight for backup nodes and bandwidth
	 * @param printResults true if we want to print the model results
	 * @return objective value
	 * @throws IloException
	 */
	public ArrayList<IloIntVar[][]> modelFormulation(Link l, ArrayList <Integer> requestsIds, ArrayList <Integer> requestsBackupBw, boolean printResults, int [][]q, int[]bt) throws IloException
	{
		ArrayList<int[][]> parameters =  new ArrayList<int[][]>();
		ArrayList<IloIntVar[][]> results =  new ArrayList<IloIntVar[][]>();
		IloIntVar[][] sharedBw = new IloIntVar[1][1];
	
		
		//number of tenants using this links without bandwidth reuse
		int T = 0;
		int objectiveValue = -1;
		
		if (q == null || bt ==null)
		{
			parameters = this.populateParameters(l, requestsIds, requestsBackupBw );		
			
			//In the below case no requests can share bandwidth on l (no requests or all are reusing their bandwidth)
			if (parameters == null)
			{
				return null;
			}
			
			/**********************************************************************************
			 ************************** INITIALIZING PARAMETERS **************************
			 **********************************************************************************/
			
			
			//array specifying if 2 tenants can not share bandwidth
			q = parameters.get(0);
			
			//backup bandwidth required by each tenant on l
			bt = parameters.get(1)[0];
		
		}
		
		T = bt.length;
		
		
		/**********************************************************************************
		 ************************** INITIALIZING CPLEX VARIABLES **************************
		 **********************************************************************************/
		
		
		//y specifies if a tenant i is in the set j (1) or not (0)
		IloIntVar[][] y = new IloIntVar[T][T];
		for(int i=0;i<y.length;i++)
		{
			for(int j=0;j<y[i].length;j++)
			{
				y[i][j] = cplex.intVar(0, 1,"y "+i+j);
			}		
		}
		
		
		
		//b specifies the bandwidth to be reserved for the set
		IloIntVar [] b = new IloIntVar[T];
		for(int i=0;i<b.length;i++)
		{
			b[i] = cplex.intVar(0, Integer.MAX_VALUE,"b"+i);
		
		}
		
		//z is used for linearization (tenant i and set j)
		/*IloIntVar[][] z = new IloIntVar[T][T];
		for(int i=0;i<z.length;i++)
		{
			for(int j=0;j<z[i].length;j++)
			{
				z[i][j] = cplex.intVar(0, 1,"z "+i+j);
			}
		
		}*/

		/**********************************************************************************
		 ************************** SETTING OBJECTIVE FUNCTION ****************************
		 **********************************************************************************/
		
		//Constraint 1 - Objective function
		IloNumExpr objective = cplex.numExpr();
		
		//alpha(sum over y*bt)
		for(int i=0;i<T;i++)
		{
			objective =  cplex.sum(objective,b[i] );
		}
		
		cplex.addMinimize(objective);
		/**********************************************************************************
		 ********************************* CONSTRAINTS ************************************
		 **********************************************************************************/
		
		/**
		 * Constraint 2 - specify that 2 tenants can not share bandwidth (can not be part of the same set)
		 * y[t][i]+y[t'][i]<= 1
		 */
		
		for(int i=0; i<q.length;i++)
		{
			for(int j=0; j<q[i].length;j++)
			{
				// if i = j then we have the same request no need to add the constraint that says put on of both requests in the set(same request)
				if (q[i][j] == 0 || i==j)
				{
					continue;
				}
								
				for (int k=0; k<T; k++)
				{
					IloNumExpr tenantInSetConstraint = cplex.numExpr();
					tenantInSetConstraint = cplex.sum(y[i][k],y[j][k]);
					cplex.addLe(tenantInSetConstraint, 1, "Constraint2 "+i+j+k);	
				}						
			}				
		}
		
		
		/**
		 * Constraint 3 - specify that a tenant should be part of exactly one set
		 * sum y[t][i]= 1
		 */
		
		for(int i=0; i<y.length;i++)
		{
			IloNumExpr tenantInOneSetConstraint = cplex.numExpr();
			for(int j=0; j<y[i].length;j++)
			{				
				tenantInOneSetConstraint = cplex.sum(tenantInOneSetConstraint,y[i][j]);									
			}	
			cplex.addEq(tenantInOneSetConstraint, 1, "Constraint3 "+i);	
		}
		
		/**
		 * Constraint 4 - Set the backup bandwidth to reserve as the maximum backup bandwidth of all tenants in the set
		 * b[i] = max (bt*y[t][i])
		 * =>to linearize:
		 * b[i]>= bt*y[t][i]
		 * b [i]<= bt*y[t][i]+(1-z[t][i])*M
		 * sum(over i) z[t][i] = 1
		 */
		
		//loop over tenants
		for(int i=0; i<y.length;i++)
		{			
			//loop over sets
			for (int j=0; j<y[i].length; j++)
			{
				IloNumExpr backupBwConstraint1 = cplex.numExpr();
				backupBwConstraint1 = cplex.prod(bt[i],y[i][j]);
				cplex.addGe(b[j], backupBwConstraint1, "Constraint4-a "+i+j);
				

				/*IloNumExpr backupBwConstraint2 = cplex.numExpr();
				backupBwConstraint2 = cplex.sum(cplex.prod(bt[i],y[i][j]),cplex.prod(cplex.sum(1, cplex.prod(-1, z[i][j])),Integer.MAX_VALUE));
				cplex.addLe(b[j],backupBwConstraint2, "Constraint4-b "+i+j);*/
				
			}			
			
		}
		
		//loop over sets
	/*	for(int i=0; i<z.length;i++)
		{
			IloNumExpr backupBwConstraint3 = cplex.numExpr();
			
			//loop over tenants
			for (int j=0; j<z[i].length; j++)
			{
				backupBwConstraint3 = cplex.sum(backupBwConstraint3,z[j][i]);
			}
			cplex.addEq(backupBwConstraint3,1, "Constraint3-c ");
		}
		*/
		
		
		
		/**********************************************************************************
		 ************************** SOLVING/PRINTING SOLUTION *****************************
		 **********************************************************************************/
		cplex.exportModel("ShareBwBetweenTenantsModel.lp");
		if (cplex.solve()) {
			
			results.add(y);
			
			sharedBw[0] = b;
			results.add(sharedBw);
			
			if(printResults)
			{ 
				printResults(y, b, q, bt, requestsIds );//printResults(y, b, q, bt,z, requestsIds );
			}
		
			return results;
		}
		else
		{
			
			return null;
		}
		
	}

	
	/**
	 * Print the results based on Cplex
	 * 
	 * @param y array that holds if tenant is in the set or not
	 * @param b bandwidth to reserve for the set
	 * @param q array that specifies if two tenants can share or not bandwidth
	 * @param z linearization parameter
	 * @param requestsIds array of requests ids provided to the model (requests with no bandwidth reuse)
	 * 
	 * @throws IloException
	 */
	public void printResults(IloIntVar[][] y ,IloIntVar [] b, int[][] q, int[]bt, ArrayList <Integer> requestsIds) throws IloException//IloIntVar[][] z,
	{
		
		//displaying the solution status (optimal or feasible)
		cplex.output().println("Solution status = " + cplex.getStatus());
		
		//displaying the objective value
		cplex.output().println("Solution value  = " + cplex.getObjValue());
		System.out.println ();
		System.out.println ();
		
		
		System.out.println ("y[T][i]: Request T is in the set i");
		System.out.println("===================================================================================================");
		for(int i =0; i<y.length; i++)
		{	
			for(int j=0; j<y[i].length; j++)
			{
				if((int)cplex.getValue(y[i][j]) ==1)
				{
					System.out.println (i+" Request  "+requestsIds.get(i)+" is in the set "+j);
				}
			}
					
		}	
		
		System.out.println ("\n");
		for(int i=0; i<b.length; i++)
		{	
			System.out.println ("Bandwidth to reserve for the sharing set "+cplex.getValue(b[i]));
		}
		
		System.out.println("===================================================================================================");
		
		System.out.println ("\n");
		System.out.println ("z[T]: Request T is in the set");
		System.out.println("===================================================================================================");
		/*for(int i =0; i<z.length; i++)
		{	
			for(int j=0; j<z[i].length; j++)
			{
				if((int)cplex.getValue(z[i][j]) ==1)
				{
					System.out.println (i+ " z for request  "+requestsIds.get(i)+" is set to "+cplex.getValue(z[i][j])+"in set "+j);
				}
			}
			
					
		}	*/
		
		
		System.out.println ("\n");
		System.out.println ("q[t][t']: Request T is in the set");
		System.out.println("===================================================================================================");
		
		for(int i =0; i<q.length; i++)
		{	
			for(int j =0; j<q[i].length; j++)
			{
				System.out.println ("q["+i+"]["+j+"] = "+q[i][j]);
				//if(q[i][j] ==1)
				//{
					//System.out.println ("Requests "+requestsIds.get(i)+" can not share bandwidth with request "+requestsIds.get(j));
				//}
			}
			
		}	
		
		System.out.println ("\n");
		System.out.println ("bt[t]: Request T requires backup bandwidth on the specified link");
		System.out.println("===================================================================================================");
		
		for(int i =0; i<bt.length; i++)
		{	
			System.out.println ("Requests "+requestsIds.get(i)+" requires backup bandwidth of "+bt[i]);
		}
		
	}
	
	
	/**
	 * This is the TBSh algorithm = link.buildSharingSets() but without the use of objects
	 * This function considers one link. used to get the execution time
	 * 
	 * @param q specifies if 2 requests can share bw (0) or not (1)
	 * @param bt backup bandwidth required by each request
	 * @return total bw to reserve for all the sharing sets
	 */
	public int TBSHDesign (int[][]q, int[]bt, ArrayList<Integer>requestIds, int totalBw)
	{
		/*System.out.println("Printing bandwidth");
		for (int i=0; i<bt.length; i++)
		{
			System.out.println(bt[i]);
		}
		System.out.println("totalBw entering func "+totalBw);*/
		int request =0;
		ArrayList<Integer> sharingSet = new ArrayList<Integer>();
		sharingSet.add(requestIds.get(0));
		totalBw = totalBw+ bt[requestIds.get(0)];
		requestIds.remove(0);
		boolean canShare = true;
		for(int i=0; i<requestIds.size(); i++)
		{
			request = requestIds.get(i);
			canShare = true;
			//System.out.print("checking request "+request);
			for (int k=0; k<sharingSet.size(); k++)
			{//System.out.println ("checking q["+request+"]["+sharingSet.get(k)+"] = "+q[request][sharingSet.get(k)]);
				if (q[request][sharingSet.get(k)] == 1)
				{
					canShare = false;
					break;					
				}
			}
			
			if (canShare)
			{
				sharingSet.add(request);
				requestIds.remove(i);
				//make sure not to skip checking the request that was at i+1 and became at i when removing the request at i
				i--;
			}
			
		}
		
		/*System.out.println("sharing set");
		for (int i=0;i<sharingSet.size();i++)
		{
			System.out.println(bt[sharingSet.get(i)]);
		}*/
		if (requestIds.size()>0)
		{
			totalBw = this.TBSHDesign (q, bt, requestIds, totalBw);
		}
		
		return totalBw;
		
	}
	
	/**
	 * 
	 * @param nbRequests
	 * @param minBw
	 * @param maxBw
	 * @throws IloException
	 * @throws IOException 
	 */
	public void largeScaleModelTest(int nbRequests, int minBw, int maxBw) throws IloException, IOException
	{
		String executionTimeFileName = "TestResults/SharingBwExecutionTime/SharingBwExecutionTime.txt";
		FileManipulation executionTimeFile  = new FileManipulation(executionTimeFileName);
		long endTime, startTime, executionTime;
		ArrayList <Integer> requestsIds = new ArrayList<Integer> ();
		ArrayList <Integer> requestsBackupBw = new ArrayList<Integer> ();
		int [][]q = new int [nbRequests][nbRequests];
		int[]bt = new int[nbRequests];
		Random rand = new Random();
		int temp, totalBw;
		int r;
		//initialize q[i][j] to -1
		for (int i=0; i<q.length; i++)
		{
			for(int j=0; j<q[i].length; j++)
			{
				q[i][j] = -1;
			}
		}
		
		for (int i=0; i<q.length; i++)
		{
			for(int j=0; j<q[i].length; j++)
			{
				//a request can share bw with itself
				if (i==j)
				{
					q[i][j] = 0;
				}
				//if not = -1 then it is initialized-this is used to make sure that
				if (q[i][j] == -1)
				{
					q[i][j] = (Math.random()<0.5)?0:1;
					q[j][i] = q[i][j];
				}
				System.out.println("q["+i+"]["+j+"]"+q[i][j]);
			}
		}
		
		//generate requests backup bandwidth
		for (int i=0; i<bt.length; i++)
		{
			requestsIds.add(i);
			bt[i] = rand.nextInt((maxBw - minBw) + 1) + minBw;
			requestsBackupBw.add(bt[i]);
		}
		
		//sort bt in descending order
		for (int i=0; i<bt.length-1; i++)
		{
			for(int j=i+1; j<bt.length; j++)
			{
				if (bt[i]<bt[j])
				{
					temp = bt[i];
					bt[i]=bt[j];
					bt[j]=temp;
				}
			}
			
		}
		
		
	/*	executionTimeFile.writeInFile ("=============Test for Nb of requests = "+nbRequests+" ; Bandwidth generated between <"+minBw+","+maxBw+">=============\n\n");
		startTime = System.currentTimeMillis();
		this.modelFormulation(null, requestsIds, requestsBackupBw, true, q, bt);
		endTime = System.currentTimeMillis();
		executionTime = endTime - startTime;
		executionTimeFile.writeInFile(" ====== ShareBwBetweenTenantModel ===== \n Execution Time :"+executionTime+"\n ObjectiveValue (totalBw to reserve on the link)"+ this.cplex.getObjValue()+" \n\n ");
		*/
		startTime = System.currentTimeMillis();
		totalBw = this.TBSHDesign (q, bt,requestsIds,0);
		endTime = System.currentTimeMillis();
		executionTime = endTime - startTime;
		executionTimeFile.writeInFile(" ====== TBSH-Design ===== \n Execution Time :"+executionTime+"\n ObjectiveValue (totalBw to reserve on the link)"+ totalBw+" \n\n ");
		
	}
	
	public static void main(String[] args) throws IloException, IOException
	{
		// TODO Auto-generated method stub
		/*Link l = null;
		VMsProtectionWithBandwidthGuarantee vmsProtection = null;
		ArrayList <Integer> requestsIds = new ArrayList<Integer> ();
		requestsIds.add(1);
		requestsIds.add(2);
		requestsIds.add(4);
		requestsIds.add(6);
		ArrayList <Integer> requestsBackupBw = new ArrayList<Integer> ();
		requestsBackupBw.add(100);
		requestsBackupBw.add(200);
		requestsBackupBw.add(400);
		requestsBackupBw.add(600);
		
		int [][]q = new int [4][4];
		q[0][0] = 0;
		q[0][1] = 0;
		q[0][2] = 0;
		q[0][3] = 0;
		
		q[1][0] = 0;
		q[1][1] = 0;
		q[1][2] = 0;
		q[1][3] = 0;
		
		q[2][0] = 0;
		q[2][1] = 0;
		q[2][2] = 0;
		q[2][3] = 1;
		
		q[3][0] = 0;
		q[3][1] = 0;
		q[3][2] = 1;
		q[3][3] = 0;
		int [] bt ={ 100,200,400,600};
	
		ShareBwBetweenTenantsModel model = new ShareBwBetweenTenantsModel(vmsProtection);
		model.modelFormulation(l, requestsIds, requestsBackupBw, true, q, bt);*/
		
		//below lines are to compare the model with the share bandwidth algo for execution time and results
		int[]nbRequests = {5000};//{10,15, 20};//{20, 5, 10, 15};
		
		for (int i =0; i<nbRequests.length; i++)
		{	
			ShareBwBetweenTenantsModel model = new ShareBwBetweenTenantsModel(null);		
			model.largeScaleModelTest(nbRequests[i], 100, 500);
		}
	
		
	}

}
