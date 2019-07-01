package Models;
/**
 * General Information:
 * 
 * 
 * Model Objective function:
 * -Specifies the primary Vms placement and the bandwidth to guarantee for them based on the hose model
 * -Its objective is to maximize the number of admitted Vms
 * 
 * Model Functionality:
 * Given a request <N (number of Vms, B (bandwidth to guarantee)>:
 * 1-Specify the placement of primary Vms
 * 2-Specify the bandwidth to guarantee for primary VMs communication
 * 
 * Note:
 * All primary  bandwidth are guaranteed based on the hose model
 * 
 * Scalability:
 *  The model is not scalable (could run for a small network)
 */
import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.util.ArrayList;

import Network.FatTreeNetwork;
import Network.Request;
import Network.Switch;


public class CplexModels {
	
	public ArrayList <Request> requests;
	public FatTreeNetwork treeNetwork;
	
	
	
	public CplexModels (ArrayList <Request> requests, FatTreeNetwork treeNetwork) throws IloException
	{
		this.requests = requests;
		
		//considering that the network is already build
		this.treeNetwork = treeNetwork;
	}

	
	/*
	 * MAIN function:
	 * 1- construct the model 
	 * 2- Solve it
	 * 3- Print the solution
	 * 
	 * @return objective value
	 */
	public double model() throws IloException
	{
		int torswitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreswitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int T = this.requests.size();
		
		int V = P+torswitchesNb + aggCoreswitchesNb;
		int Up = this.treeNetwork.nbOfVMPerPhysicalMachine;
		
		ArrayList<int[][]> zc = this.populatingInputs();
		
		int [][] zij = zc.get(0); 
		int [][] Cij = zc.get(1);
		
		IloCplex cplex = new IloCplex();
		
		/**********************************************************************************
		 ************************** INITIALIZING CPLEX VARIABLES **************************
		 **********************************************************************************/
					
		//x specifies if tenant is admitted
		IloNumVar[] x = new IloNumVar[T];
		for(int j=0;j<T;j++)
		{
			x[j] = cplex.intVar(0, 1,"x"+j);
		}
		
		
		// ytp specifies the number of VM of tenant t hosted on server p		
		IloNumVar [][] ytp = new IloNumVar[T][P];
		
		//looping over requests
		for ( int i=0; i<T; i++)
		{
			//looping over physical servers
			for(int j=0; j<P;j++)
			{				
				ytp[i][j] = cplex.intVar(0, Integer.MAX_VALUE,"y"+i+j);
				
			}
		}
		
		
		//ntj specifies the number of VM under switch j; if j is TOR switch
		IloNumVar [][] wti = new IloNumVar[T][V];
		//looping over tenants
		for (int i = 0; i<T;i++)
		{
			//looping over all switches
			for(int j=0; j<(V); j++)
			{
				//This is to manage indexes with zpi
				if(j<P)
				{
					wti[i][j] = cplex.intVar(0,0, "wti"+i+j);
					continue;
				}
				
				wti[i][j] = cplex.intVar(0,  Integer.MAX_VALUE, "wti"+i+j);
			}
		}
		
		
		//b specified the bandwidth reserved for tenant t on link l(ji) between 2 switches
		IloNumVar [][][] btij =  new IloNumVar[T][V][V];
		for (int i = 0; i<T;i++)
		{
			for(int j=0; j<V; j++)
			{
				for(int k=0; k<V; k++)
				{
					btij[i][j][k] = cplex.intVar(0,  Integer.MAX_VALUE, "btij"+i+j+k);
				}
			}
		}
		
		/**********************************************************************************
		 ************************** SETTING OBJECTIVE FUNCTION ****************************
		 **********************************************************************************/
		
		//Objective function
		IloNumExpr objective = cplex.numExpr();
		for (int i=0; i<T; i++)
		{
			objective = cplex.sum(cplex.prod(requests.get(i).N, x[i]), objective);
		}
		cplex.addMaximize(objective);
		
		
		/**********************************************************************************
		 ********************************* CONSTRAINTS ************************************
		 **********************************************************************************/
		
		//Constraint 1 - all virtual machines hosted on physical server should not exceed the number of Vm on this server
		
		for(int j=0; j<P;j++)
		{
			IloNumExpr vmPerPmConstraint = cplex.numExpr();
			//looping over requests
			for ( int i=0; i<T; i++)
			{
				vmPerPmConstraint = cplex.sum(vmPerPmConstraint,ytp[i][j]);				
			}
			
			cplex.addLe(vmPerPmConstraint, Up, "Constraint1 "+j);
		}
		
		
		//Constraint 2 - A tenant can be admitted if all its VMs can be hosted
		
		//checking if tenant is admitted or not
		for(int i=0; i<T ;i++)
		{
			IloNumExpr tVmConstraint = cplex.numExpr();
			for (int j=0; j<P;j++)
			{
				tVmConstraint = cplex.sum(tVmConstraint, ytp[i][j]);
			}			
			
			cplex.addEq(tVmConstraint,cplex.prod(x[i], requests.get(i).N), "Constraint2 "+i);
		}
		
		
		//Constraint 3 - Number of VMs hosted under TOR switch for tenant t
		
		for (int i = 0; i<T;i++)
		{	
			//looping over TOR only
			for(int j=P; j<(V-aggCoreswitchesNb); j++)
			{
				IloNumExpr exp = cplex.numExpr();
				for (int k=0;k<P;k++)
				{			
					//add constraint only if switch k under switch j
					if (zij[j][k] == 1)
					{
						exp = cplex.sum(exp,ytp[i][k]);
					}
				}
				 cplex.addEq(wti[i][j], exp, "Constraint3 "+i+j);
			}
		}
		
		
		//Constraint 4 - Number of VMs under switch (Aggregate and Core) for tenant t
		
		for (int i = 0; i<T;i++)
		{	
			//looping over Aggregate and Core only
			for(int j= V-aggCoreswitchesNb; j<V; j++)
			{
				IloNumExpr exp = cplex.numExpr();
				
				//looping over all nodes
				for (int k=0;k<V;k++)
				{
					//add constraint only if switch k under switch j
					if (zij[j][k] == 1)
					{
						exp = cplex.sum(exp,wti[i][k]);
					}
				}
				
				cplex.addEq(wti[i][j], exp,"Constraint4 "+i+j);
			}
		}
		
		
		//Constraint 5 - bandwidth reserved on link ip between TOR and Physical server is less or equal that the link capacity
		
		for (int i = 0; i<T;i++)
		{				
			//looping over TOR switches
			for(int k=P; k<V-aggCoreswitchesNb; k++)
			{
				//looping over physical machines
				for(int j=0; j<P; j++)
				{		
					if(zij[k][j] == 1)
					{
						cplex.addEq(btij[i][k][j],cplex.prod(ytp[i][j],requests.get(i).B), "Constraint5 "+k+j);
					}
				}				
			}
		}
		
		//Constraint 6 -  Calculating bandwidth on upper links
		
		for (int i = 0; i<T;i++)
		{				
			//loop over nodes
			for(int j=P; j<V; j++)
			{
				//loop over nodes
				for(int k=P; k<V; k++)
				{
					if (zij[j][k] ==1)
					{
						//btij<= wtj*bt
						cplex.addLe(btij[i][j][k],cplex.prod(wti[i][k],requests.get(i).B));
						
						//btij<= (Nt-wtj)*bt
						cplex.addLe(btij[i][j][k],cplex.prod(
									cplex.sum(requests.get(i).N,cplex.prod(-1,wti[i][k]))
									,requests.get(i).B)
								);
					}
												
				}
			}
		}
		
		//Constraint 7 - reserved bandwidth on each link is less or equal to link capacity
						
		//loop over nodes
		for(int j=0; j<V; j++)
		{
			//loop over nodes
			for(int k=0; k<V; k++)
			{
				IloNumExpr exp = cplex.numExpr();
				
				//loop over tenants
				for (int i = 0; i<T;i++)
				{
					if (zij[j][k] ==1)
					{
						exp = cplex.sum(exp, btij[i][j][k]);
						
					}
				}
				
				cplex.addLe(exp, Cij[j][k],"Constraint7 "+j+k);
		
			}
		}
		
		/**********************************************************************************
		 ************************** SOLVING/PRINTING SOLUTION *****************************
		 **********************************************************************************/
		cplex.exportModel("AlternativeShortestPath.lp");
		if (cplex.solve()) {
			
			printResults(x,ytp, cplex );
		}
		
		return cplex.getObjValue();
	}
	
	
	/**
	 * This function build the inputs zij and cij needed for the model
	 * zij[i][j] have a 0 or 1 value and specifies that node j is directly under node i 
	 * cij[i][j] specifies the capacity of the link ij
	 * 
	 * @return array list having zij and cij
	 */
	public ArrayList <int[][]> populatingInputs()
	{
		ArrayList<int[][]> zc= new ArrayList<int[][]>();
		
		int torswitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreswitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int V = P+torswitchesNb + aggCoreswitchesNb;
						
		int [][] zij = new int [V][V]; 
		int [][] Cij = new int [V][V];
		int count =0;
		
		//Working on links between TOR and physical servers
		//looping over TOR switches 
		for (int i=P; i<(P+torswitchesNb); i++)
		{			
			for(int j=0; j<treeNetwork.nbOfPhysicalMachinesPerTor; j++)
			{
				//setting physical server count is under TOR switch i 
				zij[i][count] = 1;
				
				//setting link capacity between TOR i and physical server count
				Cij[i][count] = treeNetwork.pmToTorLinkCapacity;
				
				//will hold the total number of physical machines
				count++;
			}		
			
		}
		
		
		
		//Working on links between Aggregate switches and TOR
		//looping over aggregate switches
		for (int i=P+torswitchesNb; i<(V-coreSwitchesNb); i++)
		{			
			for(int j=0; j<treeNetwork.nbOfTorPerAgg; j++)
			{
				//setting TOR switch 'count' is under Aggregate switch i 
				zij[i][count] = 1;
				
				//setting link capacity between Aggregate i and TOR switch count
				Cij[i][count] = treeNetwork.torToAggregateLinkCapacity;
				
				//will hold the total number of TOR
				count++;
			}		
			
		}
		
		//Working on links between Core switches and Aggregate switches 
		//looping over core switches
		for (int i=V-coreSwitchesNb; i<V ; i++)
		{			
			for(int j=0; j<treeNetwork.nbOfAggPerCore; j++)
			{
				//setting Aggregate switch count is under Core switch i 
				zij[i][count] = 1;
				
				//setting link capacity between core switch i and aggregate switch count
				Cij[i][count] = treeNetwork.aggregateToCoreLinkCapacity;
				
				//will hold the total number of aggregate switch
				count++;
			}		
			
		}
		
		zc.add(zij);
		zc.add(Cij);
		return zc;
	}

	/**
	 * Print the results based on Cplex
	 * 
	 * @param x array that holds if tenant is accepted or not
	 * @param ytp array that holds the nb of hosted VMs for tenant t on physical machines P 
	 * @throws IloException
	 */
	public void printResults(IloNumVar[] x,IloNumVar[][] ytp, IloCplex cplex) throws IloException
	{
		Request request;
		int P = this.treeNetwork.nbOfPhysicalMachines;
		//displaying the solution status (optimal or feasible)
		cplex.output().println("Solution status = " + cplex.getStatus());
		
		//displaying the objective value
		cplex.output().println("Solution value  = " + cplex.getObjValue());
		System.out.println ();
		
		for (int i=0; i<requests.size();i++)
		{
			//get the request and print it
			request = requests.get(i);
			System.out.println ("Allocation for request "+i+" : VMs requested: "+ request.N+"     Bandwidth per VM: "+request.B);
			
			//get the request physical allocation and print it
			System.out.println("===================================================================================================");
			
			//print if tenant rejected
			if (cplex.getValue(x[i]) == 0)
			{
				System.out.println("Request rejected - NO allocation for it ");
				System.out.println();
				continue;
			}
			
			for( int j = 0; j<P; j++)
			{
				//print only physical machines where the tenant have VM hosted
				if(cplex.getValue(ytp[i][j])>0)
				{
					System.out.printf ( "Physical Machine ID: %5d   ;  Virtual Machines allocated for request : %5.0f",j,cplex.getValue(ytp[i][j])  );
					System.out.println();
				}
			}
			
			System.out.println();
		}
		
	}
	
	
	
	public static void main(String [] args) throws IloException
	{		
		int nbOfRequests=3;
		ArrayList <Request> requests = new ArrayList<Request>();
		Request request;
		int[] requestsBandwidth = {100, 100, 100};
		int[] requestVMs = {20,20,20};
		
		for (int i=0;i<nbOfRequests; i++)
		{
			request = new Request(i,requestVMs[i],requestsBandwidth[i]);
			
			requests.add(request);
		}
				
		FatTreeNetwork treeNetwork = new FatTreeNetwork(12, 4, 3, 2, 2, 1000,10000, 50000);
		treeNetwork.buildTreeNetwork();
		
		CplexModels cplexModel = new CplexModels(requests, treeNetwork);
		cplexModel.model();
	}
}
