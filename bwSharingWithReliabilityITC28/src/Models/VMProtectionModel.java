package Models;
/**
 * Model Objective function:
 * -Allows Vm protection with bandwidth guarantee while reducing 
 * the number of needed backup nodes and the backup bandwidth
 * 
 * Model Functionality:
 * Given the primary VMs placement + hose model bandwidth guarantee for them, the model:
 * 1-Specify the number of needed backups
 * 2-Allocate backups by reducing the total backup bandwidth
 * 3-Specify which backup node will backup which Vm
 * 4-Share bandwidth between primary and backup nodes
 * 5-Specify additional backup bandwidth needed to reserve on each link
 * 
 * Note:
 * All primary and backup bandwidth are guaranteed based on the hose model
 * 
 * Scalability:
 *  The model is not scalable
 */
import java.util.ArrayList;

import Network.FatTreeNetwork;
import Network.Link;
import Network.PhysicalMachine;
import Network.Request;
import Network.Switch;
import Network.VirtualMachine;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;


public class VMProtectionModel {
	
	public Request request;
	
	public FatTreeNetwork treeNetwork;
	
	public IloCplex cplex;
	
	public VMProtectionModel (Request request, FatTreeNetwork treeNetwork) throws IloException
	{
		this.request = request;
		
		//considering that the network is already build
		this.treeNetwork = treeNetwork;
		this.cplex = new IloCplex();
	}
	
	
	/**
	 * This function will populate the input parameters needed for the model
	 * This is used only for testing purposes when the network is empty and only contains the specified allocation
	 * 
	 * @param pmAllocation array specifying the embedding of tenant VM on servers
	 * @param primaryBandwdith array specifying the bandwidth reserved on links for primary VMs embedding
	 * @return ArrayList of x,c,v,l,f
	 */
	public ArrayList <int[][]> populateParameters (int[][]pmAllocation, int[][]primaryBandwdith)
	{
		
		ArrayList<int[][]> parameters =  new ArrayList<int[][]>();
			
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int torSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int V = P+torSwitchesNb + aggCoreSwitchesNb;
		
		int count = 0;
		
		//x specifies if vm n is hosted on server p
		int [][] x = new int [this.request.N][P];
		
		// f specifies the bandwidth reserved on link (ij) for primary VMs communication (i=source = physical server; j=destination = tor switch)
		int [][] f = new int [V][V];
		
		//v[j][i] specifies that node j is under node i 
		int [][] v = new int [V][V];
		
		//c specifies the capacity of link (ij) (i=source = physical server; j=destination = tor switch)
		int [][] c = new int [V][V];
		
		//l holds the level of the nodes (it is only formed as a double array to be able to return it in the function)
		int [][] l = new int [1][V];
		
		//cp holds the physical servers capacity
		int [][] cp =  new int[1][P];
				
		//populating x
		for (int i = 0; i<x.length; i++)
		{
			for (int j = 0; j< x[i].length; j++)
			{
				x[i][j] = pmAllocation [i][j];

			}			
		}
		parameters.add(x);
		
		
		//Working on links between TOR and physical servers
		//looping over TOR switches 
		for (int i=P; i<(P+torSwitchesNb); i++)
		{			
			l[0][i] = 1;
			for(int j=0; j<treeNetwork.nbOfPhysicalMachinesPerTor; j++)
			{
				
				//setting physical server count is under TOR switch i 
				v[count][i] = 1;
			
				//setting link capacity between TOR i and physical server count
				c[count][i] = treeNetwork.pmToTorLinkCapacity;
				
				//will hold the total number of physical machines
				count++;
			}		
			
		}
		
		
		
		//Working on links between Aggregate switches and TOR
		//looping over aggregate switches
		for (int i=P+torSwitchesNb; i<(V-coreSwitchesNb); i++)
		{			
			l[0][i] = 2;
			for(int j=0; j<treeNetwork.nbOfTorPerAgg; j++)
			{
				//setting TOR switch 'count' is under Aggregate switch i 
				v[count][i]= 1;
				
				//setting link capacity between Aggregate i and TOR switch count
				c[count][i] = treeNetwork.torToAggregateLinkCapacity;
				
				//will hold the total number of TOR
				count++;
			}		
			
		}
		
		//Working on links between Core switches and Aggregate switches 
		//looping over core switches
		for (int i=V-coreSwitchesNb; i<V ; i++)
		{	
			l[0][i] = 3;
			for(int j=0; j<treeNetwork.nbOfAggPerCore; j++)
			{
				//setting Aggregate switch count is under Core switch i 
				v[count][i]= 1;
				
				//setting link capacity between core switch i and aggregate switch count
				c[count][i] = treeNetwork.aggregateToCoreLinkCapacity;
				
				//will hold the total number of aggregate switch
				count++;
			}		
			
		}
						
		parameters.add(c);
		parameters.add(v);
		parameters.add(l);
		
		//populating f 
		for (int i = 0; i<f.length; i++)
		{
			for (int j = 0; j< f[i].length; j++)
			{
				if (v[j][i] == 1 )
				{
					f[j][i]= primaryBandwdith[j][i];
				}
			}
		}
		
		parameters.add(f);	
		
		//this contains the full capacity of the server because of constraint 2
		for(int i=0; i<P; i++)
		{
			cp[0][i] = this.treeNetwork.nbOfVMPerPhysicalMachine;
		}
		parameters.add(cp);
		
		return parameters;
	}
	
	
	/**
	 * This function populates the placement of the tenant VMs
	 * 
	 * @return int[][] array specifying which VM is mapped to which server
	 */
	public int[][] populateVMPlacement ()
	{
		PhysicalMachine physicalMachine;
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int n = 0;	
		
		//x specifies if vm n is hosted on server p
		int [][] x = new int [this.request.N][P];		
		
		//populating x
		for (int i = 0; i< P; i++)
		{
			physicalMachine = this.treeNetwork.physicalMachinesSet[i];
			
			for (int j=0; j<physicalMachine.virtualMachines.length; j++)
			{
				//verify that the VM of the specified server is reserved for the specified request (tenant)
				if (physicalMachine.virtualMachines[j].reserved && physicalMachine.virtualMachines[j].request.id == this.request.id)
				{
					x[n][i] = 1;
					n++;
				}
				
				//if all the primary VMs are allocated no need to continue looping
				if (n==this.request.N)
				{
					return x;
				}
			}
				
		}
		
		return x;		
	}
	
	
	/**
	 * This function will return an array of links residual bandwidth
	 * based on the state of the network
	 * 
	 * @return  an array of links residual bandwidth
	 */
	public int[][] populateLinkCapacity ()
	{
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int torSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int V = P+torSwitchesNb + aggCoreSwitchesNb;
		int[][] c = new int [V][V];
		Link l;
		int [][] f = this.populateReservedLinkBandwidth();
		
		//Set the residual bandwidth for links between physical machines(source) and TOR  switches (Destination)
		for (int i=0; i<treeNetwork.pmToTorLinkSet.length; i++)
		{
			l = treeNetwork.pmToTorLinkSet[i];
			
			//we need to add the bandwidth reserved for this request because of constraint 16
			c[l.sourceNode.id][l.destinationNode.id + P] = l.bandwidth+f[l.sourceNode.id][l.destinationNode.id + P];
		}
		
		//Set the residual bandwidth for links between TOR  switches (Source) and Aggregate switches (Destination)
		for (int i=0; i<treeNetwork.torToAggregateLinkSet.length; i++)
		{
			l = treeNetwork.torToAggregateLinkSet[i];
			c[l.sourceNode.id+P][l.destinationNode.id + P+torSwitchesNb] = l.bandwidth+f[l.sourceNode.id+P][l.destinationNode.id + P+torSwitchesNb] ;
		}
		
		//Set the residual bandwidth for links between Aggregate  switches (Source) and Core switches (Destination)
		for (int i=0; i<treeNetwork.aggregateToCoreLinkset.length; i++)
		{
			l = treeNetwork.aggregateToCoreLinkset[i];
			c[l.sourceNode.id + P + torSwitchesNb][l.destinationNode.id +V-coreSwitchesNb] = l.bandwidth+f[l.sourceNode.id + P + torSwitchesNb][l.destinationNode.id +V-coreSwitchesNb];
		}
		
		return c;
	}
	
	/**
	 * 
	 * This function populates the bandwidth reserved for the request on all the links
	 * 
	 * @return array of reserved bandwidth for thre request on all the links
	 */
	public int[][] populateReservedLinkBandwidth ()
	{
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int torSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int V = P+torSwitchesNb + aggCoreSwitchesNb;
		
		int [][] f = new int [V][V];
		int[] requestBandwidth;
		Link l;
		
		//Set the reserved bandwidth for links between physical machines(source) and TOR  switches (Destination)
		for (int i=0; i<treeNetwork.pmToTorLinkSet.length; i++)
		{
			l = treeNetwork.pmToTorLinkSet[i];
			
			for (int j=0; j<l.bandwidthForRequests.size();j++)
			{
				requestBandwidth = l.bandwidthForRequests.get(j);
				
				//check if a bandwidth is reserved for the specified request
				if (requestBandwidth[0] == this.request.id)
				{
					f[l.sourceNode.id][l.destinationNode.id + P] = requestBandwidth[1];
					break;
				}
				
			}
			
		}
		
		//Set the reserved bandwidth for links between TOR  switches (Source) and Aggregate switches (Destination)
		for (int i=0; i<treeNetwork.torToAggregateLinkSet.length; i++)
		{
			l = treeNetwork.torToAggregateLinkSet[i];
			for (int j=0; j<l.bandwidthForRequests.size();j++)
			{
				requestBandwidth = l.bandwidthForRequests.get(j);
				if (requestBandwidth[0] == this.request.id)
				{
					f[l.sourceNode.id+P][l.destinationNode.id + P+torSwitchesNb] = requestBandwidth[1];
					break;
				}
			}
			
		}
		
		//Set the reserved bandwidth for links between Aggregate  switches (Source) and Core switches (Destination)
		for (int i=0; i<treeNetwork.aggregateToCoreLinkset.length; i++)
		{
			l = treeNetwork.aggregateToCoreLinkset[i];
			for (int j=0; j<l.bandwidthForRequests.size();j++)
			{
				requestBandwidth = l.bandwidthForRequests.get(j);
				if (requestBandwidth[0] == this.request.id)
				{
					f[l.sourceNode.id + P + torSwitchesNb][l.destinationNode.id +V-coreSwitchesNb] = requestBandwidth[1];
					break;
				}
			}
			
		}
		
		return f;
	}
	
	
	/**
	 * This function will return an array of physical servers residual capacity
	 * 
	 * @return array of physical servers residual capacity
	 */
	public int[] populatePhysicalMachineCapacity ()
	{
		int[] cp = new int [this.treeNetwork.physicalMachinesSet.length];
		PhysicalMachine [] physicalMachinesSet = this.treeNetwork.physicalMachinesSet;
		int pmId;
		int [][] x = this.populateVMPlacement();
		
		for (int i = 0; i<physicalMachinesSet.length; i++)
		{ 
			 pmId = physicalMachinesSet[i].id;
			cp[pmId] = physicalMachinesSet[i].getAvailableVM();
			
			//since we are considering in constraint 2 that the server is empty and does not contain the primary embedding of the request
			//we need to add the VM reserved on those servers for this request
			for(int n=0;n<this.request.N;n++)
			{
				cp[pmId]+=x[n][i];
			}
		}
		
		return cp;
	}
	
	
	/**
	 * This function will populate the node information that specifies
	 * 1- which node is under which v[j][i] specifies that node j is under node i 
	 * 2- level of the node l 
	 * 
	 * @return array list of v,l
	 */
	public ArrayList<int[][]> populateNodesInformation()
	{
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int torSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int V = P+torSwitchesNb + aggCoreSwitchesNb;
		int count =0;
		
		ArrayList<int[][]> nodeInformation =  new ArrayList<int[][]>();
		
		//v[j][i] specifies that node j is under node i 
		int [][] v = new int [V][V];
		
		//l holds the level of the nodes (it is only formed as a double array to be able to return it in the function)
		int [][] l = new int [1][V];
				
				
		//Working on links between TOR and physical servers
		//looping over TOR switches 
		for (int i=P; i<(P+torSwitchesNb); i++)
		{			
			l[0][i] = 1;
			for(int j=0; j<treeNetwork.nbOfPhysicalMachinesPerTor; j++)
			{			
				//setting physical server count is under TOR switch i 
				v[count][i] = 1;
						
				//will hold the total number of physical machines
				count++;
			}		
			
		}
		
		//Working on links between Aggregate switches and TOR
		//looping over aggregate switches
		for (int i=P+torSwitchesNb; i<(V-coreSwitchesNb); i++)
		{			
			l[0][i] = 2;
			for(int j=0; j<treeNetwork.nbOfTorPerAgg; j++)
			{
				//setting TOR switch 'count' is under Aggregate switch i 
				v[count][i]= 1;
				
				//will hold the total number of TOR
				count++;
			}		
			
		}
		
		//Working on links between Core switches and Aggregate switches 
		//looping over core switches
		for (int i=V-coreSwitchesNb; i<V ; i++)
		{	
			l[0][i] = 3;
			for(int j=0; j<treeNetwork.nbOfAggPerCore; j++)
			{
				//setting Aggregate switch count is under Core switch i 
				v[count][i]= 1;
				
				//will hold the total number of aggregate switch
				count++;
			}		
			
		}
						
		nodeInformation.add(v);
		nodeInformation.add(l);
		
		return nodeInformation;
	}
	
	
	
	/**
	 * This function will populate the input parameters needed for the model
	 * 
	 * @return ArrayList of x,c,v,l,f,cp
	 */
	public ArrayList <int[][]> populateParameters ()
	{
		
		ArrayList<int[][]> parameters =  new ArrayList<int[][]>();
	
		ArrayList<int[][]> nodesInformation = this.populateNodesInformation();
		
		//v[j][i] specifies that node j is under node i 
		int [][] v = nodesInformation.get(0);			
		
		//l holds the level of the nodes (it is only formed as a double array to be able to return it in the function)
		int [][] l = nodesInformation.get(1);
				
		//x specifies if vm n is hosted on server p
		int [][] x = this.populateVMPlacement();
		
		// f specifies the bandwidth reserved on link (ij) for primary VMs communication (i=source = physical server; j=destination = tor switch)
		int [][] f = this.populateReservedLinkBandwidth();
		
		//c specifies the capacity of link (ij) (i=source = physical server; j=destination = tor switch)
		int [][] c = this.populateLinkCapacity();
		
		//capacity of the physical servers
		int[][] cp = new int [1][];
		cp[0] = this.populatePhysicalMachineCapacity();
		
		
		parameters.add(x);		
		parameters.add(c);
		parameters.add(v);
		parameters.add(l);		
		parameters.add(f);	
		parameters.add(cp);
		
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
	public double modelFormulation(int[][]pmAllocation, int[][]primaryBandwdith, double alpha, boolean printResults) throws IloException{
		
		ArrayList<int[][]> parameters =  new ArrayList<int[][]>();
			
		int P = treeNetwork.nbOfPhysicalMachines;
		int torSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int V = P+torSwitchesNb + aggCoreSwitchesNb;
			
		
		
		
		/**********************************************************************************
		 ************************** INITIALIZING PARAMETERS **************************
		 **********************************************************************************/
		if (pmAllocation != null && primaryBandwdith != null)
		{
			parameters = this.populateParameters(pmAllocation,primaryBandwdith);
		}
		else
		{
			parameters = this.populateParameters();
		}
		
		//x specifies that primary vm n is hosted on server p
		int [][] x = parameters.get(0);
		
		//c specifies the capacity of link (ij)
		int [][] c = parameters.get(1);
				
		//v specifies if node i is under node j
		int [][] v = parameters.get(2);
	
		//l specifies the level of the node
		int[][] ls = parameters.get(3);
		int [] l =  ls[0];
		
		//f specifies the bandwidth reserved on link (ij) for primary VMs
		int [][] f = parameters.get(4);
		
		//cp specifies the physical servers capacity
		int[][]serverCapcity = parameters.get(5);
		int [] cp = serverCapcity[0];
		
		/**********************************************************************************
		 ************************** INITIALIZING CPLEX VARIABLES **************************
		 **********************************************************************************/
		
		
		//y specifies that backup vm k is hosted on p : nb of backup node can maximum be = N
		IloIntVar[][] y = new IloIntVar[this.request.N][P];
		for(int i=0;i<y.length;i++)
		{
			for(int j=0;j<y[i].length;j++)
			{
				y[i][j] = cplex.intVar(0, 1,"y "+i+j);
			}
			
		}
		
		//z specifies that backup vm k is backing up vm  n
		IloIntVar[][] z = new IloIntVar[this.request.N][this.request.N];
		
		//loop over primary Vms
		for(int i=0;i<z.length;i++)
		{
			//loop over backup VMs
			for(int j=0;j<z[i].length;j++)
			{
				z[i][j] = cplex.intVar(0, 1,"z "+i+j);
			}
			
		}
		
		
		// wip specifies the number of VM under node i	when server p fails
		IloIntVar [][] wip = new IloIntVar[V][P];
		
		for ( int i=0; i<wip.length; i++)
		{
			for(int j=0; j<wip[i].length;j++)
			{				
				wip[i][j] = cplex.intVar(0, Integer.MAX_VALUE,"wip "+i+j);
				
			}
		}
		
		
		
		// bijp specifies the bandwidth needed  on link (ij) when p fails
		IloIntVar [][][] bijp = new IloIntVar[V][V][P];
		
		for ( int i=0; i<bijp.length; i++)
		{
			for(int j=0; j<bijp[i].length;j++)
			{	
				for(int u=0; u<bijp[i][j].length;u++)
				{
					bijp[i][j][u] = cplex.intVar(0, Integer.MAX_VALUE,"bijp "+i+j+u);
				}
			}
		}
		
		// t specifies the bandwidth needed on link (ij) when there is a single failure
		IloIntVar [][] t = new IloIntVar[V][V];
		
		for ( int i=0; i<t.length; i++)
		{
			for(int j=0; j<t[i].length;j++)
			{	
				t[i][j] = cplex.intVar(0, Integer.MAX_VALUE,"t "+i+j);
				
			}
		}
		
		// tc specifies the backup bandwidth to reserve on link (ij) considering cross share
		IloIntVar [][]tc = new IloIntVar[V][V];
		
		for ( int i=0; i<tc.length; i++)
		{
			for(int j=0; j<tc[i].length;j++)
			{	
				tc[i][j] = cplex.intVar(0, Integer.MAX_VALUE,"tc "+i+j);
				
			}
		}
		
		//Linearizing variable
		IloIntVar[][][] a = new IloIntVar[this.request.N][this.request.N][P];
		
		for ( int i=0; i<this.request.N; i++)
		{
			for(int j=0; j<this.request.N;j++)
			{	
				for(int u=0;u<P;u++)
				{
					a[i][j][u] = cplex.intVar(0, 1,"a "+i+j+u);
				}
				
			}
		}
		
		
		// dijp linearization help variable
		IloIntVar [][][][] dijp = new IloIntVar[V][V][P][2];
		
		for ( int i=0; i<dijp.length; i++)
		{
			for(int j=0; j<dijp[i].length;j++)
			{	
				for(int u=0; u<dijp[i][j].length;u++)
				{
					for(int k=0; k<dijp[i][j][u].length;k++)
					{
						dijp[i][j][u][k] = cplex.intVar(0, 1,"dijpk "+i+j+u+k);
					}
				}
			}
		}
		
		// eijp linearization help variable
		IloIntVar [][][] eijp = new IloIntVar[V][V][P];
		
		for ( int i=0; i<eijp.length; i++)
		{
			for(int j=0; j<eijp[i].length;j++)
			{	
				for(int u=0; u<eijp[i][j].length;u++)
				{
					eijp[i][j][u] = cplex.intVar(0, 1,"eijp "+i+j+u);
					
				}
			}
		}
		
		// gij linearization help variable
		IloIntVar [][][] gij= new IloIntVar[V][V][2];
		
		for ( int i=0; i<gij.length; i++)
		{
			for(int j=0; j<gij[i].length;j++)
			{	
				for(int u=0; u<gij[i][j].length;u++)
				{
					gij[i][j][u] = cplex.intVar(0, 1,"gij "+i+j+u);
					
				}
			}
		}
		
		/**********************************************************************************
		 ************************** SETTING OBJECTIVE FUNCTION ****************************
		 **********************************************************************************/
		
		//Constraint 1 - Objective function
		IloNumExpr objective = cplex.numExpr();
		
		//alpha(sum over P,sum over n (ykp))
		for(int i=0;i<this.request.N;i++)
		{
			for(int j=0;j<P;j++)
			{
				objective =  cplex.sum(objective, y[i][j]);
			}
		}
		objective = cplex.prod(alpha,objective);
		
		//(1-alpha)/b sum over ij (tij)
		IloNumExpr bandwidth = cplex.numExpr();
		for ( int i=0; i<V; i++)
		{
			for(int j=0; j<V;j++)
			{	
				if(v[i][j] == 1)
				{
					bandwidth = cplex.sum(bandwidth, tc[i][j]);	
				}
			}
		}
		bandwidth = cplex.prod((1-alpha)/this.request.B, bandwidth);
		
		objective = cplex.sum(objective, bandwidth);
		cplex.addMinimize(objective);
		
		
		/**********************************************************************************
		 ********************************* CONSTRAINTS ************************************
		 **********************************************************************************/
		
		/**
		 * Constraint 2 - all virtual machines hosted on physical server should not exceed the number of Vm on this server
		 * sum over N (xnp)+sum over k (ykp)<= cp
		 */
		
		for(int i=0; i<P;i++)
		{
			IloNumExpr vmPerPmConstraint = cplex.numExpr();
			
			for(int j=0; j<this.request.N;j++)
			{
				vmPerPmConstraint = cplex.sum(vmPerPmConstraint,x[j][i]);
				vmPerPmConstraint = cplex.sum(vmPerPmConstraint,y[j][i]);
			
			}		
			
			cplex.addLe(vmPerPmConstraint, cp[i], "Constraint2 "+i);
		}
		
			
		/**
		 * Constraint 3 - A VM can only be backed up by a node hosted on a different server
		 * ykp+sum over N (xnp znk) <=1
		 */
		
		for(int i=0; i<P;i++)
		{	
			for(int k=0; k<this.request.N;k++)
			{
				IloNumExpr canBackupConstraint = cplex.numExpr();
				for(int j=0; j<this.request.N;j++)
				{				
					canBackupConstraint  = cplex.sum(canBackupConstraint,cplex.prod(x[j][i],z[j][k]));				
				}
				
				canBackupConstraint  = cplex.sum(canBackupConstraint,y[k][i] );
				cplex.addLe(canBackupConstraint, 1,"Constraint3 "+i+k);			
			}
		}
		
		
		/**
		 * Constraint 4 - A VM should be backed up by one backup node
		 * sum over k (znk) = 1
		 */
		
		for(int i=0; i<this.request.N;i++)
		{
			IloNumExpr backupPerVmConstraint = cplex.numExpr();
			
			//loop over backup nodes
			for(int j=0; j<this.request.N;j++)
			{
				backupPerVmConstraint = cplex.sum(backupPerVmConstraint,z[i][j]);		
			}		
			
			cplex.addEq(backupPerVmConstraint, 1, "Constraint4 "+i);
		}
		
		
		/**
		 * Constraint 5 - VMs hosted on the same server should not be backed up by the same node
		 * sum over N (xnp znk <=1)
		 */
		
	/*	for(int i=0; i<P;i++)
		{	
			//loop over backup nodes
			for(int k=0; k<this.request.N;k++)
			{
				IloNumExpr sameNodebackupConstraint = cplex.numExpr();
				
				//loop over primary Vms n
				for(int j=0; j<this.request.N;j++)
				{
					sameNodebackupConstraint = cplex.sum(sameNodebackupConstraint, cplex.prod(x[j][i],z[j][k]));
				}
				
				cplex.addLe(sameNodebackupConstraint, 1,  "Constraint5 "+k+i);
			}
		}
		*/
		
		/**
		 * Constraint 6/7 - if a backup node is hosted, it should be used for backing up at least one VM
		 * sum over N (znk) <= N sum over P (ykp)
		 */
		
		for(int k=0; k<this.request.N;k++)
		{
			IloNumExpr backupedVms = cplex.numExpr();
			
			//loop over primary VMs
			for(int i=0;i<this.request.N;i++)
			{
				backupedVms = cplex.sum (backupedVms, z[i][k]);
			}
			
			IloNumExpr backupHosted = cplex.numExpr();
			for(int j=0; j<P;j++)
			{
				backupHosted = cplex.sum(backupHosted, y[k][j]);
			}
			
			cplex.addLe(backupedVms,cplex.prod(this.request.N,backupHosted),  "Constraint6 "+k);
			cplex.addGe(backupedVms,cplex.sum(backupHosted, -1/this.request.N),  "Constraint7 "+k);
		}
		
		
		/**
		 * Constraint 8 - backup node can not be hosted on more than one physical server
		 * sum over P (ykp) <= 1
		 */
		
		for(int k=0; k<this.request.N;k++)
		{
			IloNumExpr backupNodeHostedConstraint = cplex.numExpr();
			
			for(int j=0; j<P;j++)
			{
				backupNodeHostedConstraint = cplex.sum(backupNodeHostedConstraint, y[k][j]);
			}
			
			cplex.addLe(backupNodeHostedConstraint,1,  "Constraint8 "+k);
		}
		
		
		
		/**
		 * Constraint 9 - active VMs on p' when p fails
		 * wp'p = sum over N (xnp' + sum over K (ykp' xnp znk))
		 */
				
		//loop over failed servers
		for(int i=0; i<P;i++)	
		{	
			//loop over active servers
			for(int j=0; j<P;j++)
			{
				//p = p'
				if (i == j )
				{
					continue;
				}
				
				IloNumExpr vmsOnServerConstraint = cplex.numExpr();
				
				//loop over primary Vms
				for(int u=0; u<this.request.N;u++)
				{
					//loop over backup Vms
					for(int k=0; k<this.request.N;k++)
					{
						/*
						 * linerization of 2 binary variables ykp' znk because it's the multiplication of 2 unknown variables
						 * Define aukj = ykp' znk
						 * aukj <= ykp'
						 * aukj <= znk
						 * aukj >= ykp'+znk - 1
						 * 
						 */
						cplex.addLe(a[u][k][j],y[k][j],"Constraint9 ay "+u+k+j);
						cplex.addLe(a[u][k][j],z[u][k],"Constraint9 az "+u+k+j);
						cplex.addGe(a[u][k][j],cplex.sum(cplex.sum(y[k][j],z[u][k]),-1),"Constraint9 ayz "+u+k+j);
						vmsOnServerConstraint = cplex.sum(vmsOnServerConstraint,cplex.prod(a[u][k][j],x[u][i]));
												
					}
					
					vmsOnServerConstraint = cplex.sum(vmsOnServerConstraint, x[u][j]);
				}
				
				cplex.addEq(wip[j][i],vmsOnServerConstraint, "Constraint9 "+j+i);
				
			}
		}
		
			
		
		/**
		 * Constraint 10 - active VMs under TOR switch when p fails
		 * 	wip = sum over p' different than p (wp'p vp'i)
		 */
			
		//loop over failed servers
		for(int i=0; i<P;i++)
		{	
			//loop over TOR switches
			for(int j=P; j<P+torSwitchesNb;j++)
			{
				IloNumExpr vmsUnderTorConstraint = cplex.numExpr();
				
				//loop over active servers (p')
				for(int u=0; u<P;u++)
				{				
					//p = p'
					if (i == u)
					{
						continue;
					}
					
					//add constraint only if p' is under tor switch i 
					if(v[u][j] == 1)
					{
						vmsUnderTorConstraint = cplex.sum(vmsUnderTorConstraint, wip[u][i]);
					}
					
				}
				
				cplex.addEq(wip[j][i], vmsUnderTorConstraint, "Constraint10 "+j+i);
				
			}
		}
		
		
		/**
		 * Constraint 11 - active VMs under Agg/core switch when p fails
		 * sum over all switched (wjp vji)
		 */	
		
		//loop over failed servers
		for(int i=0; i<P;i++)
		{	
			//loop over Agg/core switches
			for(int j=V-aggCoreSwitchesNb; j<V;j++)
			{
				IloNumExpr vmsUnderAggCoreConstraint = cplex.numExpr();
				
				//loop over switches 
				for(int u=P; u<V;u++)
				{	
					//check that node u is under node j
					if (v[u][j] == 1)
					{
						vmsUnderAggCoreConstraint = cplex.sum(vmsUnderAggCoreConstraint,wip[u][i]);
					}
				}
				
				cplex.addEq(wip[j][i], vmsUnderAggCoreConstraint, "Constraint11 "+j+i);
				
			}
		}
		
			
		/**
		 * Constraint 12 - bandwidth on Tor - server link
		 * bip'p = min (wp'pB; sum over all p'' different than p'p'' (wp''p B))
		 * 
		 * Linerization
		 * w =  min (x1,x2,...)
		 * xi>=w,∀i∈1...n
		 * xi<=w+(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */		
		
		for(int i=P; i<P+torSwitchesNb; i++)
		{
			//loop over active servers p'
			for(int j=0; j<P; j++)
			{
				//check if link exists
				if(v[j][i] == 0)
				{
					continue;
				}
				
				//loop over failed servers
				for(int u=0; u<P; u++)
				{
					//p = p'
					if(u == j)
					{
						/*
						 * need to set the bandwidth of the link 
						 * between TOR and failed server to zero, to avoid setting it to the
						 *  link capacity by the model when alpha = 0
						 */						
						cplex.addEq(bijp[j][i][u],0);
						continue;
					}
					
					
					//wip B >= bijp
					cplex.addGe(cplex.prod(wip[j][u],this.request.B),bijp[j][i][u],  "Constraint12 a1 " +j+i+u);
					
					IloNumExpr constraint12 = cplex.numExpr();
					
					//loop over active servers p''
					for(int k=0; k<P; k++)
					{
						//check if p'' = p' or p'' = p
						if(k == u || k==j)
						{
							continue;
						}
						
						
						constraint12 = cplex.sum(constraint12, wip[k][u]);
					}
					
					//sum (wp''p B) >= bijp
					cplex.addGe(cplex.prod(constraint12,this.request.B),bijp[j][i][u],  "Constraint12 a2 " +j+i+u);
					
					//wip B <= bijp + (1-dijp)M
					cplex.addLe(cplex.prod(wip[j][u], this.request.B),cplex.sum(
							bijp[j][i][u],
							 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[j][i][u][0])),Integer.MAX_VALUE)													
									
							),"Constraint12 b1 " +j+i+u
						);
					
					//sum (wp''p B) <= bijp + (1-dijp) M
					cplex.addLe(cplex.prod(constraint12,this.request.B),cplex.sum(
									bijp[j][i][u],
									 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[j][i][u][1])),Integer.MAX_VALUE)													
									
									),"Constraint12 b2 " +j+i+u
					);
					
					//sum (dijp) >= 1
					cplex.addGe(cplex.sum(dijp[j][i][u][0],dijp[j][i][u][1]),1,"Constraint12 c ");
				}
			}
		}
		
	
		/**
		 * Constraint 13 - bandwidth on upper links
		 * bijp = min (wjp B; sum over all switches at the same level of j wj'p B)
		 * 
		 * Linerization
		 * w =  min (x1,x2,...)
		 * xi>=w,∀i∈1...n
		 * xi<=w+(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */
		
		//loop over Agg/core switches
		for(int i=V-aggCoreSwitchesNb; i<V; i++)
		{
			//loop over switches
			for(int j=P; j<V; j++)
			{
				//check if link exists
				if(v[j][i] == 0)
				{
					continue;
				}
				
				//loop over failed servers
				for(int u=0; u<P; u++)
				{
					// wip >= bijp
					cplex.addGe (cplex.prod(wip[j][u], this.request.B),bijp[j][i][u],  "Constraint13 a1 " +j+i+u);
					
					IloNumExpr constraint13 = cplex.numExpr();
					//looping over j'<> j
					for(int k=P; k<V;k++)
					{
						if(k==j || l[k]!=l[j])
						{
							continue;
						}
						constraint13 = cplex.sum(constraint13, wip[k][u] );
					}
					
					//sum over switches at the same level of j (wj'p)B >= bijp
					cplex.addGe(cplex.prod(constraint13,this.request.B),bijp[j][i][u],  "Constraint13 a2 " +j+i+u);
					
					//wipB <= bijp + (1-dijp)M
					cplex.addLe(cplex.prod(wip[j][u], this.request.B),cplex.sum(
							bijp[j][i][u],
							 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[j][i][u][0])),Integer.MAX_VALUE)													
									
							),"Constraint13 b1 " +j+i+u
						);
					
					//sum over switches at the same level of j (wj'p)B <= bijp + (1- dijp)M
					cplex.addLe(cplex.prod(constraint13,this.request.B),cplex.sum(
									bijp[j][i][u],
									 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[j][i][u][1])),Integer.MAX_VALUE)													
									
									),"Constraint13 b2 " +j+i+u
					);
					
					//sum dijp >= 1
					cplex.addGe(cplex.sum(dijp[j][i][u][0],dijp[j][i][u][1]),1,"Constraint13 c ");
					
				}				
			}
		}
		
		/**
		 * Constraint 14 - total bandwidth needed on each link
		 * tij = max bijp 
		 * 
		 * Linerization
		 * w =  max (x1,x2,...)
		 * xi<=w,∀i∈1...n
		 * xi>=w-(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */
		
		//loop over nodes
		for(int i=0; i<V; i++)
		{
			//loop over nodes
			for(int j=0; j<V; j++)
			{
				//check if link exists
				if(v[i][j] ==0)
				{
					continue;
				}
				
				IloNumExpr constraint14 = cplex.numExpr();
				
				//loop over servers
				for(int k=0; k<P; k++)
				{
					//bijp <= tij
					cplex.addLe( bijp[i][j][k],t[i][j],"Constraint14 a "+i+j+k);
					
					//bijp >= tij - (1-eijp)M
					cplex.addGe( bijp[i][j][k],cplex.sum( t[i][j], cplex.prod(-1,
							cplex.prod(
									Integer.MAX_VALUE,cplex.sum(1, cplex.prod(-1, eijp[i][j][k])))
							)
						),"Constraint14 b "+i+j+k);
					
					constraint14 = cplex.sum(constraint14, eijp[i][j][k]);
					
				}	
				
				//sum eijp >= 1
				cplex.addGe(constraint14, 1,"Constraint14 c "+i+j);
				
			}
		}
		
		/**
		 * Constraint 15 - additional backup bandwidth needed on each link
		 * tcij =  max (0, tij-fij)
		 * 
		 * Linerization
		 * w =  max (x1,x2,...)
		 * xi<=w,∀i∈1...n
		 * xi>=w-(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */
		
		//loop over nodes
		for(int i=0; i<V; i++)
		{
			//loop over nodes
			for(int j=0; j<V; j++)
			{
				//check if link exists
				if(v[i][j] ==0)
				{
					continue;
				}
				
				//tij -fij <= tcij
				cplex.addLe( cplex.sum(t[i][j], -f[i][j]),tc[i][j],"Constraint15 a1 "+i+j);
				
				//0<= tcij
				cplex.addLe( 0,tc[i][j],"Constraint15 a2"+i+j);
				
				//tij-fij >= tcij - M(1-gij)
				cplex.addGe( cplex.sum(t[i][j], -f[i][j]),cplex.sum(tc[i][j], 
							cplex.prod(-Integer.MAX_VALUE,cplex.sum(1,cplex.prod(-1, gij[i][j][0]))
									)
							),"Constraint15 b1"+i+j);
				
				//0>= tcij -M(1-gij)
				cplex.addGe( 0,cplex.sum(tc[i][j], 
							cplex.prod(-Integer.MAX_VALUE,cplex.sum(1, cplex.prod(-1,gij[i][j][1]))
									)
							),"Constraint15 b2"+i+j);
				//sum gij >= 1
				cplex.addGe(cplex.sum(gij[i][j][0],gij[i][j][1]), 1,"Constraint15 c"+i+j);
							
			}
		}
		
		/**
		 * Constraint 16 - link capacity constraint
		 * tcij +fij <= cij
		 */
		
		//loop over nodes
		for(int i=0; i<V; i++)
		{
			//loop over nodes
			for(int j=0; j<V; j++)
			{
				//check if link exists
				if(v[i][j] ==0)
				{
					continue;
				}
				
				cplex.addLe(cplex.sum(tc[i][j], f[i][j]),c[i][j],"Constraint16 "+i+j);
								
			}
		}
		
		
		
		
		
				
		/**********************************************************************************
		 ************************** SOLVING/PRINTING SOLUTION *****************************
		 **********************************************************************************/
		cplex.exportModel("VMProtectionWithBandwidthGuarantee.lp");
		if (cplex.solve()) {
			
			this.updateNetwork(y, tc);
			
			if(printResults)
			{ 
					printResults(t,tc,z,y,bijp,wip,dijp,eijp,gij );
			}
			return cplex.getObjValue();
		}
		else
		{
			
			return -1;
		}
	}
	
	

	/**
	 * This functions update the network with the backup VMs embedding specified by the model and the additional bandwidth
	 * 
	 * @param y [n][p] array specifying the VM n is hosted on server p
	 * @param tc [i][j] bandwidth to reserve on link ij
	 * @throws IloException 
	 * @throws UnknownObjectException 
	 */
	public void updateNetwork (IloIntVar[][] y,IloIntVar[][] tc ) throws UnknownObjectException, IloException
	{
		int P = treeNetwork.nbOfPhysicalMachines;
		int torSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int V = P+torSwitchesNb + aggCoreSwitchesNb;
		
		PhysicalMachine pm;
		Link l;
		int bandwidthToReserve = 0 ;
		
		//loop over requests virtual machines
		for (int i=0; i<y.length; i++)
		{
			//loop over physical servers
			for (int j=0;j<y[i].length;j++)
			{
				if (cplex.getValue(y[i][j]) == 1)
				{System.out.println("=============================================RESERVE==========================");
					//get physical server
					pm = this.treeNetwork.physicalMachinesSet[j];
					pm.reserveVM(1, this.request, VirtualMachine.Type.BACKUP);
				}
			}
		}
		
		//Set the reserved bandwidth for links between servers (Source) and TOR switches (Destination)
		for (int i=0; i<treeNetwork.pmToTorLinkSet.length; i++)
		{
			l = treeNetwork.pmToTorLinkSet[i];
			bandwidthToReserve = (int)cplex.getValue(tc[l.sourceNode.id][l.destinationNode.id + P] );
			
			//check if a bandwidth is reserved for the specified request
			if (bandwidthToReserve!= 0)
			{
				l.reserveBandwidth(bandwidthToReserve, this.request.id,Link.BandwidthType.BACKUP);			
			}
		}
		
		//Set the reserved bandwidth for links between TOR  switches (Source) and Aggregate switches (Destination)
		for (int i=0; i<treeNetwork.torToAggregateLinkSet.length; i++)
		{
			l = treeNetwork.torToAggregateLinkSet[i];
			bandwidthToReserve = (int)cplex.getValue(tc[l.sourceNode.id+P][l.destinationNode.id + P+torSwitchesNb]  );
			if (bandwidthToReserve!= 0)
			{
				l.reserveBandwidth(bandwidthToReserve, this.request.id,Link.BandwidthType.BACKUP);			
			}
			
		}
		
		//Set the reserved bandwidth for links between Aggregate  switches (Source) and Core switches (Destination)
		for (int i=0; i<treeNetwork.aggregateToCoreLinkset.length; i++)
		{
			l = treeNetwork.aggregateToCoreLinkset[i];
			bandwidthToReserve = (int)cplex.getValue(tc[l.sourceNode.id + P + torSwitchesNb][l.destinationNode.id +V-coreSwitchesNb] );
			if (bandwidthToReserve!= 0)
			{
				l.reserveBandwidth(bandwidthToReserve, this.request.id,Link.BandwidthType.BACKUP);			
			}			
		}
		
	}
	
	
	/**
	 * Print the results based on Cplex
	 * 
	 * @param x array that holds if tenant is accepted or not
	 * @param y array that holds the nb of hosted VMs for tenant t on physical machines P 
	 * @throws IloException
	 */
	public void printResults(IloIntVar[][] t,IloIntVar[][] tc,IloIntVar[][] z,IloIntVar[][] y,IloIntVar[][][] bijp,IloIntVar[][]wip,IloIntVar[][][][] dijp,IloIntVar[][][]eijp,IloIntVar[][][]gij) throws IloException
	{
		
		System.out.println ("Request : VMs  "+ this.request.N+"     Bandwidth per VM: "+this.request.B);
		System.out.println("===================================================================================================");
		
		//displaying the solution status (optimal or feasible)
		cplex.output().println("Solution status = " + cplex.getStatus());
		
		//displaying the objective value
		cplex.output().println("Solution value  = " + cplex.getObjValue());
		System.out.println ();
		System.out.println ();
		
		
		System.out.println ("y[k][p]: Backup VM K is mapped to server p");
		System.out.println("===================================================================================================");
		for(int i =0; i<y.length; i++)
		{	
			for(int j =0; j<y[i].length; j++)
			{
				if(cplex.getValue(y[i][j]) ==1)
				{
					System.out.println ("Backup VM "+i+" is mapped to server "+j);
				}
			}
			
		}	
		
		System.out.println ("\n");
		System.out.println ("z[n][k]: Backup VM K is backing up primary Vm n");
		System.out.println("===================================================================================================");
		for(int i =0; i<z.length; i++)
		{	
			for(int j =0; j<z[i].length; j++)
			{
				if(cplex.getValue(z[i][j]) ==1)
				{
					System.out.println ("Backup VM "+j+" is backing up "+i);
				}
			}
			
		}	
		
	/*	System.out.println ("\n");
		System.out.println ("w[i][p]: Nb of active VMs under node i" );
		System.out.println("===================================================================================================");
		for(int i =0; i<wip.length; i++)
		{	
			for(int j =0; j<wip[i].length; j++)
			{
				
					try
					{
						System.out.println ("wip "+i+j+" is : "+cplex.getValue(wip[i][j]));
					}
					catch(IloException e) {}
				
				
			}	
		}
		
		System.out.println ("\n");
		System.out.println ("bijp: Bandwidth needed upon failure of physical server p ");
		System.out.println("===================================================================================================");
		for(int i =0; i<bijp.length; i++)
		{	
			for(int j =0; j<bijp[i].length; j++)
			{
				for(int k =0; k<bijp[i][j].length; k++)
				{
					try
					{
						System.out.println ("bandwith on link ("+i+j+")  if server "+k+" fails is : "+cplex.getValue(bijp[i][j][k]));
					}
					catch(IloException e) {}
				}
				
			}	
		}
		
		System.out.println ("\n");
		System.out.println ("t[i][j]: Backup bandwidth on link ij");
		System.out.println("===================================================================================================");
		for(int i =0; i<t.length; i++)
		{	
			for(int j =0; j<t[i].length; j++)
			{
				try
				{
					System.out.println ("bandwith on link"+i+j+" is : "+cplex.getValue(t[i][j]));
				}
				catch(IloException e) {}
				
			}
			
		}
		*/
		System.out.println ("\n");
		System.out.println ("tc[i][j]: Additional backup bandwidth to reserve on link ij");
		System.out.println("===================================================================================================");
		for(int i =0; i<tc.length; i++)
		{	
			for(int j =0; j<tc[i].length; j++)
			{
				try{System.out.println ("bandwith on link"+i+j+" is : "+cplex.getValue(tc[i][j]));}
				catch(IloException e) {}
			}	
		}
		
		
		
		/*System.out.println ("\n");
		System.out.println ("dijp: Bandwidth needed upon failure of physical server p ");
		System.out.println("===================================================================================================");
		for(int i =0; i<dijp.length; i++)
		{	
			for(int j =0; j<dijp[i].length; j++)
			{
				for(int k =0; k<dijp[i][j].length; k++)
				{
					for(int u =0; u<dijp[i][j][k].length; u++)
					{
						try
						{
							System.out.println ("bandwith on link ("+i+j+")  if server "+k+"_"+u+" fails is : "+cplex.getValue(dijp[i][j][k][u]));
						}
						catch(IloException e) {}
					}
				}
				
			}	
		}
	
		System.out.println ("\n");
		System.out.println ("eijp: Bandwidth needed upon failure of physical server p ");
		System.out.println("===================================================================================================");
		for(int i =0; i<eijp.length; i++)
		{	
			for(int j =0; j<eijp[i].length; j++)
			{
				for(int k =0; k<eijp[i][j].length; k++)
				{
					
						try
						{
							System.out.println ("bandwith on link ("+i+j+")  if server "+k+" fails is : "+cplex.getValue(eijp[i][j][k]));
						}
						catch(IloException e) {}
					
				}
				
			}	
		}
		
		System.out.println ("\n");
		System.out.println ("gij: Bandwidth needed upon failure of physical server p ");
		System.out.println("===================================================================================================");
		for(int i =0; i<gij.length; i++)
		{	
			for(int j =0; j<gij[i].length; j++)
			{
				for(int k =0; k<gij[i][j].length; k++)
				{
					
						try
						{
							System.out.println ("bandwith on link ("+i+j+")  if server "+k+" fails is : "+cplex.getValue(gij[i][j][k]));
						}
						catch(IloException e) {}
					
				}
				
			}	
		}*/
		
	}

	public static void main(String [] args) throws IloException
	{	
		
		
		Request request = new Request(0,4,1);//id/VM/bw
		int [][] pmAllocation = {
				{1,0,0,0},	
				{1,0,0,0},
				{0,1,0,0},
				{0,0,1,0}
		};
		int[][] primaryBandwdith = {
				{0,0,0,0,2,0,0,0,0},
				{0,0,0,0,1,0,0,0,0},
				{0,0,0,0,0,1,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,1,0,0},
				{0,0,0,0,0,0,0,1,0},
				{0,0,0,0,0,0,0,0,1},
				{0,0,0,0,0,0,0,0,1},
				{0,0,0,0,0,0,0,0,0},
				
		};
	/*	int [][] pmAllocation = {
				{0,1,0,0,0,0,0,0,0,0,0,0},	
				{0,1,0,0,0,0,0,0,0,0,0,0},
				{0,1,0,0,0,0,0,0,0,0,0,0},
				{0,0,1,0,0,0,0,0,0,0,0,0},
				{0,0,1,0,0,0,0,0,0,0,0,0},
				{0,0,0,1,0,0,0,0,0,0,0,0}
		};
		int[][]primaryBandwdith = {
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
				
		};*/
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(12, 4, 3, 2, 2, 1000,10000, 50000);
		FatTreeNetwork treeNetwork = new FatTreeNetwork(4,4, 2, 1, 2, 5,10, 20);
		treeNetwork.buildTreeNetwork();
		
		VMProtectionModel cplexModel = new VMProtectionModel(request, treeNetwork);
		
		
		cplexModel.modelFormulation(pmAllocation,primaryBandwdith,0,true);
	}
}