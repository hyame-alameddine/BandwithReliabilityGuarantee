package Models;
/**
 * General Information:
 * -The model is a subset of the VMProtectionModel.java
 * -The difference is that it considers that the backup placement is done
 * 
 * Model Objective function:
 * -Specifies which backup node will backup which VM while reducing the backup bandwidth
 * -it considers bandwidth sharing between backup and primary Vms
 * 
 * Model Functionality:
 * Given the primary VMs placement + hose model bandwidth guarantee for them, and backup placement (no bandwidth given for backups) the model:
 * 1-Specify which backup node will backup which Vm
 * 2-Share bandwidth between primary and backup nodes
 * 3-Specify additional backup bandwidth needed to reserve on each link
 * 
 * Note:
 * All primary and backup bandwidth are guaranteed based on the hose model
 * 
 * Scalability:
 *  The model is scalable
 *  
 *  Difference from BackupToVMMappingModel:
 *  Both are the same model but the enhanced one is implemented in a different way
 *  it considers looping over links instead of looping over nodes to check if the link is available
 *  This will reduce the number of loops and make the model more scalable.
 *  This is the model used for the experimentation results
 * 
 */
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

import java.util.ArrayList;

import Network.FatTreeNetwork;
import Network.Link;
import Network.PhysicalMachine;
import Network.Request;
import Network.SubTree;
import Network.Switch;
import Network.VirtualMachine;


public class BackupToVmMappingModelEnhanced
{

	public Request request;
	
	public FatTreeNetwork treeNetwork;
	
	public IloCplex cplex;
	
	//this refers to tc variable defined by the model. This holds the backup bandwidth to reserve considering bandwidth reuse
	public int [] backupBandwidth = null;
	
	VirtualMachine [] primaryVms = null;
	VirtualMachine [] backupVms = null;
	
	//mapping of primary Vm to the backup which is protecting it [index]=>[Primary, Backup]
	VirtualMachine [][] primaryToBackupCorrespondance ;
	
	public BackupToVmMappingModelEnhanced (Request request, FatTreeNetwork treeNetwork) throws IloException
	{
		this.request = request;
		
		//considering that the network is already build
		this.treeNetwork = treeNetwork;
		this.cplex = new IloCplex();
		this.backupBandwidth = new int[this.treeNetwork.getLinks().length];
		this.primaryToBackupCorrespondance = new VirtualMachine [request.N][2] ;
		this.primaryVms = new VirtualMachine[request.N];
		this.backupVms = new VirtualMachine[request.N];
	}
		
	/**
	 * This function sets the backupBandwidth attribute
	 * 
	 * @param tc backup bandwidth considering bandwidth reuse required on each link
	 * @throws IloException 
	 * @throws UnknownObjectException 
	 */
	public void setBackupBandwidth( IloNumVar [] tc) throws UnknownObjectException, IloException
	{
		Link [] links =  this.treeNetwork.getLinks(); 
		Link l;
		for ( int i =0; i<links.length; i++)
		{
			l = links[i];
			this.backupBandwidth[l.continuousId] = (int)cplex.getValue(tc[l.continuousId] );
		}
		
		links = null;
		l =null;
		
	}
	
	
	/**
	 * This function sets the primaryToBackupCorrespondance attribute
	 * 
	 * @param z [n][k] specifies the correspondance between primary n and backup VM k
	 * @throws UnknownObjectException
	 * @throws IloException
	 */
	public void setPrimaryToBackupCorrespondance (IloIntVar[][] z ) throws UnknownObjectException, IloException
	{
		//int count =0;
		
		//loop over primary Vms
		for (int i =0; i<z.length; i++)
		{
			//loop over backupVms
			for (int j=0; j<z[i].length; j++)
			{
				if (cplex.getValue(z[i][j]) == 0)
				{
					continue;
				}
				
				//each primary VM will be assigned to one backup VM
				this.primaryToBackupCorrespondance[i][0] = this.primaryVms[i]; 
				this.primaryToBackupCorrespondance[i][1] = this.backupVms[j]; 
				//count++;
			}
		}
	}
	
	
	/**
	 * This function will populate the input parameters needed for the model
	 * This is used only for testing purposes when the network is empty and only contains the specified allocation
	 * 
	 * @param pmAllocation array specifying the embedding of tenant VM on servers
	 * @param primaryBandwdith array specifying the bandwidth reserved on links for primary VMs embedding
	 * @return ArrayList of x,c,v,l,f
	 */
	public ArrayList <int[][]> populateParameters (int[][]pmAllocation,int[][]backupAllocation, int[]primaryBandwdith)
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
		
		//yspecifies if backup vm k is hosted on server p
		int [][] y = new int[this.request.N][P];
		
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
			f[0][i]= primaryBandwdith[i];		
		}
		
		parameters.add(f);	
		
		//this contains the full capacity of the server because of constraint 2
		for(int i=0; i<P; i++)
		{
			cp[0][i] = this.treeNetwork.nbOfVMPerPhysicalMachine;
		}
		parameters.add(cp);
		
		//populating y
		for (int i = 0; i<y.length; i++)
		{
			for (int j = 0; j<y[i].length; j++)
			{
				y[i][j] = backupAllocation [i][j];

			}			
		}
		parameters.add(y);
		
		//unset variable to release space in memory to handle garbage collector issue
		x = null;
		c = null;
		v = null;
		l = null;
		f = null;
		cp = null;
		y = null;
		
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
				if (physicalMachine.virtualMachines[j].reserved 
						&& physicalMachine.virtualMachines[j].vmType == VirtualMachine.Type.PRIMARY 
						&& physicalMachine.virtualMachines[j].request.id == this.request.id
					)
				{
					x[n][i] = 1;
					
					//populate primaryVms in the same order
					this.primaryVms[n] = physicalMachine.virtualMachines[j];
					
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
	
	
	public int [][] populateBackupPlacement()
	{
		PhysicalMachine physicalMachine;
		int P = this.treeNetwork.nbOfPhysicalMachines;
		int n = 0;	
		
		//y specifies if vm n is hosted on server p
		int [][] y = new int [this.request.N][P];		
		int backupNeeded = this.request.getBackupNeeded();
		//populating y
		for (int i = 0; i< P; i++)
		{
			physicalMachine = this.treeNetwork.physicalMachinesSet[i];
			
			for (int j=0; j<physicalMachine.virtualMachines.length; j++)
			{
				//verify that the VM of the specified server is reserved for the specified request (tenant)
				
				if (physicalMachine.virtualMachines[j].reserved 
						&& physicalMachine.virtualMachines[j].vmType == VirtualMachine.Type.BACKUP 
						&& physicalMachine.virtualMachines[j].request.id == this.request.id
					)
				{ 
					y[n][i] = 1;
					
					//populate the backupVms in the same order
					this.backupVms[n] = physicalMachine.virtualMachines[j];
					
					n++;
				}
				
				//if all the primary VMs are allocated no need to continue looping
				if (n==backupNeeded)
				{
					return y;
				}
			}
				
		}
		
		return y;
	}
	
	
	/**
	 * This function will return an array of links residual bandwidth
	 * based on the state of the network
	 * 
	 * @return  an array of links residual bandwidth
	 */
	public int[] populateLinkCapacity ()
	{
		Link [] links = this.treeNetwork.getLinks();
		int [] c = new int[links.length];
		int [] f = this.populateReservedLinkBandwidth();
		Link l =null;
		
		//Set the residual bandwidth for links between physical machines(source) and TOR  switches (Destination)
		for (int i=0; i<links.length; i++)
		{
			l = links[i];
			
			//we need to add the bandwidth reserved for this request because of constraint 16
			c[l.continuousId] = l.bandwidth+f[l.continuousId];
		}
		
		
		//unset variable to release space in memory to handl garbage collector issue
		f = null;
		l = null;
		links =null;
		
		return c;
	}
	
	/**
	 * 
	 * This function populates the bandwidth reserved for the request on all the links
	 * 
	 * @return array of reserved bandwidth for the request on all the links
	 */
	public int[] populateReservedLinkBandwidth ()
	{
		
		Link [] links = this.treeNetwork.getLinks();
		int [] f = new int[links.length];
		int[] requestBandwidth;
		Link l = null;
		
		for (int i = 0; i<links.length; i++)
		{
			l =  links[i];
			for (int j=0; j<l.bandwidthForRequests.size();j++)
			{
				requestBandwidth = l.bandwidthForRequests.get(j);
				
				//check if a bandwidth is reserved for the specified request
				if (requestBandwidth[0] == this.request.id)
				{
					f[l.continuousId] = requestBandwidth[1];
					break;
				}
			}
		}
		
		l = null;
		requestBandwidth = null;
		links = null;
		
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
		
		//unset variable to release space in memory to handl garbage collector issue
		x = null;
		physicalMachinesSet = null;
		
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
		
		//unset variable to release space in memory to handl garbage collector issue
		v = null;
		l = null;
		
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
		int [][] f = new int [1][];
		f[0] = this.populateReservedLinkBandwidth();
		
		//c specifies the capacity of link (ij) (i=source = physical server; j=destination = tor switch)
		int [][] c = new int [1][];
		c[0] = this.populateLinkCapacity();
		
		//capacity of the physical servers
		int[][] cp = new int [1][];
		cp[0] = this.populatePhysicalMachineCapacity();
		
		//y specifies the placement of backup nodes
		int [][] y = this.populateBackupPlacement();
		
		parameters.add(x);		
		parameters.add(c);
		parameters.add(v);
		parameters.add(l);		
		parameters.add(f);	
		parameters.add(cp);
		parameters.add(y);
		
		//unset variable to release space in memory to handl garbage collector issue
		x = null;
		c = null;
		v = null;
		l = null;
		f = null;
		cp = null;
		y = null;
		nodesInformation = null;
		
		return parameters;
	}
	
	


	/**
	 * 
	 * This function formulate, solve and print the model
	 * if the parameters are set to null the information are got from the tree network
	 * 
	 * @param pmAllocation array specifying the embedding of tenant VM on servers
	 * @param primaryBandwdith array specifying the bandwidth reserved on links for primary VMs embedding
	 * @param alpha double representing the weight for backup nodes and bandwidth
	 * @param printResults true if we want to print the model results
	 * @return objective value
	 * @throws IloException
	 */
	public double modelFormulation(int[][]pmAllocation,int[][]backupAllocation, int[]primaryBandwdith,boolean printResults) throws IloException
	{
		Link [] links = this.treeNetwork.getLinks();
		int pmToTorlinksNb = this.treeNetwork.getNbOfLinksPerType(Link.MACHINE_TO_TOR_TYPE);
		int linkSourceNodeId = 0;
		
		
		ArrayList<int[][]> parameters =  new ArrayList<int[][]>();
			
		int P = treeNetwork.nbOfPhysicalMachines;
		int torSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.TOR_TYPE); 
		int coreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.CORE_TYPE);
		int aggCoreSwitchesNb = this.treeNetwork.getNbOfSwitchPerType(Switch.AGGREGATE_TYPE) + coreSwitchesNb; 
		int V = P+torSwitchesNb + aggCoreSwitchesNb;

		int backupNeeded = this.request.getBackupNeeded();
		double objectiveValue = -1;
		
		/**********************************************************************************
		 ************************** INITIALIZING PARAMETERS **************************
		 **********************************************************************************/
		if ( pmAllocation != null && backupAllocation!=null && primaryBandwdith != null)
		{
			parameters = this.populateParameters(pmAllocation,backupAllocation,primaryBandwdith);
		}
		else
		{
			parameters = this.populateParameters();
		}
		System.out.println ("Parameters set -- " +Runtime.getRuntime().freeMemory());
		//x specifies that primary vm n is hosted on server p
		int [][] x = parameters.get(0);
		
		//c specifies the capacity of link (ij)
		int [] c = parameters.get(1)[0];
				
		//v specifies if node i is under node j
		int [][] v = parameters.get(2);
	
		//l specifies the level of the node
		int[][] ls = parameters.get(3);
		int [] l =  ls[0];
		
		//f specifies the bandwidth reserved on link (ij) for primary VMs
		int [] f = parameters.get(4)[0];
		
		//cp specifies the physical servers capacity
		int[][]serverCapcity = parameters.get(5);
		
		//y specifies that backup vm k is hosted on p : nb of backup node can maximum be = N
		int [][] y = parameters.get(6);
		
		parameters = null;
		System.out.println ("parameters populated -- " +Runtime.getRuntime().freeMemory());
		
		System.out.println ("y[k][p]: Backup VM K is mapped to server p");
		System.out.println("===================================================================================================");
		for(int i =0; i<y.length; i++)
		{	
			for(int j =0; j<y[i].length; j++)
			{
				if(y[i][j] ==1)
				{
					System.out.println ("Backup VM "+i+" is mapped to server "+j);
				}
			}
			
		}	
		/**********************************************************************************
		 ************************** INITIALIZING CPLEX VARIABLES **************************
		 **********************************************************************************/

		//z specifies that backup vm k is backing up vm  n
		IloIntVar[][] z = new IloIntVar[this.request.N][this.request.N];
		
		//loop over primary Vms
		for(int i=0;i<z.length;i++)
		{
			//loop over backup VMs
			for(int j=0;j<z[i].length;j++)
			{
				z[i][j] = cplex.intVar(0, 1,"z "+i+j);
				
				//force the z for backups that are not embedded to 0 
				if (j>=backupNeeded)
				{
					cplex.addEq(z[i][j], 0);
				}
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
		

		// t specifies the bandwidth needed on link (ij) when there is a single failure
		IloIntVar [] t = new IloIntVar[links.length];
		
		//tc specifies the backup bandwidth to reserve on link (ij) considering cross share
		IloIntVar []tc = new IloIntVar[links.length];
			
		for ( int i=0; i<links.length; i++)
		{		
			t[i] = cplex.intVar(0, Integer.MAX_VALUE,"t "+i);
			tc[i] = cplex.intVar(0, Integer.MAX_VALUE,"tc "+i);		
		}
		
		
		// bijp specifies the bandwidth needed  on link (ij) when p fails
		IloIntVar [][] bijp = new IloIntVar[links.length][P];
		
		// dijp linearization help variable
		IloIntVar [][][] dijp = new IloIntVar[links.length][P][2];
		
		// eijp linearization help variable
		IloIntVar [][] eijp = new IloIntVar[links.length][P];
		
		// gij linearization help variable
		IloIntVar [][] gij= new IloIntVar[links.length][2];
		
		for ( int i=0; i<links.length; i++)
		{
			for(int u=0; u<P;u++)
			{
				bijp[i][u] = cplex.intVar(0, Integer.MAX_VALUE,"bijp "+i+u);
				eijp[i][u] = cplex.intVar(0, 1,"eijp "+i+u);
		
				
				for(int k=0; k<2;k++)
				{
					dijp[i][u][k] = cplex.intVar(0, 1,"dijpk "+i+u+k);						
				}
			}
			
			
			for(int j=0; j<2;j++)
			{
				gij[i][j] = cplex.intVar(0, 1,"gij "+i+j);
		
			}
				
		}
	
		System.out.println ("Starting model---------------");	
		
		/**********************************************************************************
		 ************************** SETTING OBJECTIVE FUNCTION ****************************
		 **********************************************************************************/
		
		//Constraint 1 - Objective function
		IloNumExpr objective = cplex.numExpr();
	
		for ( int i=0; i<links.length; i++)
		{
		
			objective = cplex.sum(objective, tc[i]);	
		
		}
		
		cplex.addMinimize(objective);
		
		
		/**********************************************************************************
		 ********************************* CONSTRAINTS ************************************
		 **********************************************************************************/
	
		
			
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
		 * Constraint 8 - active VMs on p' when p fails
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
					
						vmsOnServerConstraint = cplex.sum(vmsOnServerConstraint,cplex.prod(y[k][j],cplex.prod(x[u][i],z[u][k])));
												
					}
					
					vmsOnServerConstraint = cplex.sum(vmsOnServerConstraint, x[u][j]);
				}
				
				cplex.addEq(wip[j][i],vmsOnServerConstraint, "Constraint8 "+j+i);
				
			}
		}
		
			
		
		/**
		 * Constraint 9 - active VMs under TOR switch when p fails
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
				
				cplex.addEq(wip[j][i], vmsUnderTorConstraint, "Constraint9 "+j+i);
				
			}
		}
		
		
		/**
		 * Constraint 10 - active VMs under Agg/core switch when p fails
		 * sum over all switches (wjp vji)
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
				
				cplex.addEq(wip[j][i], vmsUnderAggCoreConstraint, "Constraint10 "+j+i);
				
			}
		}
		
			
		/**
		 * Constraint 11 - bandwidth on Tor - server link
		 * bip'p = min (wp'pB; sum over all p'' different than p'p'' (wp''p B))
		 * 
		 * Linerization
		 * w =  min (x1,x2,...)
		 * xi>=w,∀i∈1...n
		 * xi<=w+(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */		
		
		for (int i = 0; i<pmToTorlinksNb; i++)
		{
			//loop over failed servers p
			for(int u=0; u<P; u++)
			{
				//p = p'
				if(u == links[i].sourceNode.id)
				{
					/*
					 * need to set the bandwidth of the link 
					 * between TOR and failed server to zero, to avoid setting it to the
					 *  link capacity by the model when alpha = 0
					 */						
					cplex.addEq(bijp[i][u],0);
					continue;
				}
				
				//wip B >= bijp
				cplex.addGe(cplex.prod(wip[links[i].sourceNode.id][u],this.request.B),bijp[i][u],  "Constraint11 a1 " +links[i].sourceNode.id+i+u);
				
				IloNumExpr constraint11 = cplex.numExpr();
				
				//loop over active servers p''
				for(int k=0; k<P; k++)
				{
					//check if p'' = p' or p'' = p
					if(k == u || k==links[i].sourceNode.id)
					{
						continue;
					}
					
					
					constraint11 = cplex.sum(constraint11, wip[k][u]);
				}
				
				//sum (wp''p B) >= bijp
				cplex.addGe(cplex.prod(constraint11,this.request.B),bijp[i][u],  "Constraint11 a2 " +i+u);
				
				//wip B <= bijp + (1-dijp)M
				cplex.addLe(cplex.prod(wip[links[i].sourceNode.id][u], this.request.B),cplex.sum(
						bijp[i][u],
						 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[i][u][0])),Integer.MAX_VALUE)													
								
						),"Constraint11 b1 " +i+u
					);
				
				//sum (wp''p B) <= bijp + (1-dijp) M
				cplex.addLe(cplex.prod(constraint11,this.request.B),cplex.sum(
								bijp[i][u],
								 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[i][u][1])),Integer.MAX_VALUE)													
								
								),"Constraint11 b2 " +i+u
				);
				
				//sum (dijp) >= 1
				cplex.addGe(cplex.sum(dijp[i][u][0],dijp[i][u][1]),1,"Constraint11 c ");
			
			}
		}
	
		
	
		/**
		 * Constraint 12 - bandwidth on upper links
		 * bijp = min (wjp B; sum over all switches at the same level of j wj'p B)
		 * 
		 * Linerization
		 * w =  min (x1,x2,...)
		 * xi>=w,∀i∈1...n
		 * xi<=w+(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */
		
		//loop over upper level links
		for(int i=pmToTorlinksNb; i<links.length; i++)
		{
			//loop over failed servers
			for(int u=0; u<P; u++)
			{
				//set the source node id to be a continuous id (not based on level)
				linkSourceNodeId = i;
				
				// wip >= bijp
				cplex.addGe (cplex.prod(wip[linkSourceNodeId][u], this.request.B),bijp[i][u],  "Constraint12 a1 " +linkSourceNodeId+i+u);
				
				IloNumExpr constraint12 = cplex.numExpr();
				
				//looping over j'<> j
				for(int k=P; k<V;k++)
				{
					if(k==linkSourceNodeId || l[k]!=l[linkSourceNodeId])
					{
						continue;
					}
					constraint12 = cplex.sum(constraint12, wip[k][u] );
				}
			
				//sum over switches at the same level of j (wj'p)B >= bijp
				cplex.addGe(cplex.prod(constraint12,this.request.B),bijp[i][u],  "Constraint12 a2 " +i+u);
				
				//wipB <= bijp + (1-dijp)M
				cplex.addLe(cplex.prod(wip[linkSourceNodeId][u], this.request.B),cplex.sum(
						bijp[i][u],
						 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[i][u][0])),Integer.MAX_VALUE)													
								
						),"Constraint12 b1 " +i+u
					);
				
				//sum over switches at the same level of j (wj'p)B <= bijp + (1- dijp)M
				cplex.addLe(cplex.prod(constraint12,this.request.B),cplex.sum(
								bijp[i][u],
								 cplex.prod(cplex.sum(1, cplex.prod(-1, dijp[i][u][1])),Integer.MAX_VALUE)													
								
								),"Constraint12 b2 " +i+u
				);
				
				//sum dijp >= 1
				cplex.addGe(cplex.sum(dijp[i][u][0],dijp[i][u][1]),1,"Constraint12 c ");
			}
		}
		
		
		/**
		 * Constraint 13 - total bandwidth needed on each link
		 * tij = max bijp 
		 * 
		 * Linerization
		 * w =  max (x1,x2,...)
		 * xi<=w,∀i∈1...n
		 * xi>=w-(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */
		
		//loop over  links
		for(int i=0; i<links.length; i++)
		{
			IloNumExpr constraint13 = cplex.numExpr();
			
			//loop over failed servers
			for(int k=0; k<P; k++)
			{
				//bijp <= tij
				cplex.addLe( bijp[i][k],t[i],"Constraint13 a "+i+k);
				
				//bijp >= tij - (1-eijp)M
				cplex.addGe( bijp[i][k],cplex.sum( t[i], cplex.prod(-1,
						cplex.prod(
								Integer.MAX_VALUE,cplex.sum(1, cplex.prod(-1, eijp[i][k])))
						)
					),"Constraint13 b "+i+k);
				
				constraint13 = cplex.sum(constraint13, eijp[i][k]);
			}
			
			//sum eijp >= 1
			cplex.addGe(constraint13, 1,"Constraint13 c "+i);
		}
		
		/**
		 * Constraint 14 - additional backup bandwidth needed on each link
		 * tcij =  max (0, tij-fij)
		 * 
		 * Linerization
		 * w =  max (x1,x2,...)
		 * xi<=w,∀i∈1...n
		 * xi>=w-(1−yi)M,∀i∈1...n
		 * ∑ni=1 yi≥1
		 */
		
		//loop over upper level links
		for(int i=0; i<links.length; i++)
		{
			
			//tij -fij <= tcij
			cplex.addLe( cplex.sum(t[i], -f[i]),tc[i],"Constraint14 a1 "+i);
			
			//0<= tcij
			cplex.addLe( 0,tc[i],"Constraint14 a2"+i);
			
			//tij-fij >= tcij - M(1-gij)
			cplex.addGe( cplex.sum(t[i], -f[i]),cplex.sum(tc[i], 
						cplex.prod(-Integer.MAX_VALUE,cplex.sum(1,cplex.prod(-1, gij[i][0]))
								)
						),"Constraint14 b1"+i);
			
			//0>= tcij -M(1-gij)
			cplex.addGe( 0,cplex.sum(tc[i], 
						cplex.prod(-Integer.MAX_VALUE,cplex.sum(1, cplex.prod(-1,gij[i][1]))
								)
						),"Constraint14 b2"+i);
			//sum gij >= 1
			cplex.addGe(cplex.sum(gij[i][0],gij[i][1]), 1,"Constraint14 c"+i);
		}
		
	
		
		/**
		 * Constraint 15 - link capacity constraint
		 * tcij +fij <= cij
		 */
		
		//loop over upper level links
		for(int i=0; i<links.length; i++)
		{
				
			cplex.addLe(cplex.sum(tc[i], f[i]),c[i],"Constraint16 "+i);
			
								
		}
	
		
		
		
		
		
				
		/**********************************************************************************
		 ************************** SOLVING/PRINTING SOLUTION *****************************
		 **********************************************************************************/
		cplex.exportModel("BackupToVmMapping.lp");
		if (cplex.solve()) {
			
			this.setBackupBandwidth(tc);//remove this constraint if no bandwidth reuse is wanted
			//this.setBackupBandwidth(t);//add this constraint if no bandwidth reuse is wanted
			this.setPrimaryToBackupCorrespondance(z);
			this.updateNetwork();//remove this constraint if no bandwidth reuse is wanted
			/*if (!this.updateNetworkNoBwReuse())// add this constraint if no bandwidth reuse is wanted
			{
				cplex.end();
				return -1;
			}*/
			//remove this constraint id no bandwidth reuse is wanted
			if(printResults)
			{ 
					printResults(t,tc,z,y,bijp,wip,dijp,eijp,gij );
			}
			objectiveValue = cplex.getObjValue();
			
			//unset variable to release space in memory to handl garbage collector issue
			x = null;
			c = null;
			v = null;
			l = null;
			f = null;
			y = null;
			 z = null;
			 wip = null;
			 bijp = null;
			 t= null;
			 tc =null;
			 dijp = null;
			 eijp =null;
			gij = null;	
			
			cplex.end();
			return objectiveValue;
		}
		else
		{
			
			cplex.end();
			return -1;
		}
	}
	
	
	/**
	 * This functions update the network with the  backup bandwidth tij when no bandwidth reuse is wanted
	 * 
	 * @throws IloException 
	 * @throws UnknownObjectException 
	 */
	public boolean updateNetworkNoBwReuse () 
	{
	
		Link [] links =  this.treeNetwork.getLinks(); 
		Link l;
		int bandwidthToReserve = 0 ;
		
		for ( int i =0; i<links.length; i++)
		{
			l = links[i];
			bandwidthToReserve = this.backupBandwidth[l.continuousId] ;
			
			//check if a bandwidth is reserved for the specified request
			if (bandwidthToReserve!= 0)
			{
				if (!l.reserveBandwidth(bandwidthToReserve, this.request.id, Link.BandwidthType.BACKUP))
				{
					return false;
				}
			}
		}
		
		//specify that the primary VM is backed up by the corresponding backup Vm
		for (int i=0; i<this.primaryToBackupCorrespondance.length; i++)
		{
			System.out.println ("correspondance "+this.primaryToBackupCorrespondance[i][0].toString());
			//System.out.println ("backup  "+this.primaryToBackupCorrespondance[i][0].backupVm.toString());
			this.primaryToBackupCorrespondance[i][0].backupVm = this.primaryToBackupCorrespondance[i][1];
			System.out.println ("PrimaryVm "+this.primaryToBackupCorrespondance[i][0].id+" "+this.primaryToBackupCorrespondance[i][0].pm.id+" backed up by backup "+this.primaryToBackupCorrespondance[i][0].backupVm.id+" on server "+ this.primaryToBackupCorrespondance[i][0].backupVm.pm.id);
		}
		
		links = null;
		l =null;
		return true;
		
	}
	

	/**
	 * This functions update the network with the additional backup bandwidth
	 * 
	 * @throws IloException 
	 * @throws UnknownObjectException 
	 */
	public void updateNetwork () 
	{
	
		Link [] links =  this.treeNetwork.getLinks(); 
		Link l;
		int bandwidthToReserve = 0 ;
		
		for ( int i =0; i<links.length; i++)
		{
			l = links[i];
			bandwidthToReserve = this.backupBandwidth[l.continuousId] ;
			
			//check if a bandwidth is reserved for the specified request
			if (bandwidthToReserve!= 0)
			{
				l.reserveBandwidth(bandwidthToReserve, this.request.id, Link.BandwidthType.BACKUP);			
			}
		}
		
		//specify that the primary VM is backed up by the corresponding backup Vm
		for (int i=0; i<this.primaryToBackupCorrespondance.length; i++)
		{
			System.out.println ("correspondance "+this.primaryToBackupCorrespondance[i][0].toString());
			//System.out.println ("backup  "+this.primaryToBackupCorrespondance[i][0].backupVm.toString());
			this.primaryToBackupCorrespondance[i][0].backupVm = this.primaryToBackupCorrespondance[i][1];
			System.out.println ("PrimaryVm "+this.primaryToBackupCorrespondance[i][0].id+" "+this.primaryToBackupCorrespondance[i][0].pm.id+" backed up by backup "+this.primaryToBackupCorrespondance[i][0].backupVm.id+" on server "+ this.primaryToBackupCorrespondance[i][0].backupVm.pm.id);
		}
		
		links = null;
		l =null;
		
	}
	
	
	/**
	 * Print the results based on Cplex
	 * 
	 * @param x array that holds if tenant is accepted or not
	 * @param y array that holds the nb of hosted VMs for tenant t on physical machines P 
	 * @throws IloException
	 */
	public void printResults(IloIntVar[] t,IloIntVar[] tc,IloIntVar[][] z,int[][] y,IloIntVar[][] bijp,IloIntVar[][]wip,IloIntVar[][][] dijp,IloIntVar[][]eijp,IloIntVar[][]gij) throws IloException
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
				if(y[i][j] ==1)
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
		
		/*System.out.println ("\n");
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
			
				for(int k =0; k<bijp[i].length; k++)
				{
					try
					{
						System.out.println ("bandwith on link ("+i+")  if server "+k+" fails is : "+cplex.getValue(bijp[i][k]));
					}
					catch(IloException e) {}
				}
				
			
		}*/
		
		System.out.println ("\n");
		System.out.println ("t[i][j]: Backup bandwidth on link ij");
		System.out.println("===================================================================================================");
		for(int i =0; i<t.length; i++)
		{	
				try
				{
					System.out.println ("bandwith on link"+i+" is : "+cplex.getValue(t[i]));
				}
				catch(IloException e) {}
					
		}
		
		System.out.println ("\n");
		System.out.println ("tc[i][j]: Additional backup bandwidth to reserve on link ij");
		System.out.println("===================================================================================================");
		for(int i =0; i<tc.length; i++)
		{	
		
				try{System.out.println ("bandwith on link"+i+" is : "+cplex.getValue(tc[i]));}
				catch(IloException e) {}
				
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
	
	
	public static ArrayList<Request> generateRequests (int requestsNb, FatTreeNetwork treeNetwork)
	{
		PhysicalMachine pm;
		Link l;
		int j=0;//count the nb of servers
		int id =0;//count the requests
		
		ArrayList<Request> requests = new ArrayList<Request>();
		Request r1, r2, r3;
		Switch [] subtreesRoots = treeNetwork.getSwitchSetPerTreeLevel(3);
		SubTree s = new SubTree(subtreesRoots[0]);
		treeNetwork.buildSubTree(s, subtreesRoots[0]);
		
		
		for (int i = 0; i<requestsNb/2; i++)
		{				
			//1st request primary /backup reservation
			r1 = new Request(id, 25,1);
			r1.subtree  = s;
			requests.add (r1);
			id++;
			pm = treeNetwork.physicalMachinesSet[j];
			j++;
			pm.reserveVM(15, r1, VirtualMachine.Type.PRIMARY);
			l = s.searchLink(pm);				
			l.reserveBandwidth(10, r1.id, Link.BandwidthType.PRIMARY);
			
			pm = treeNetwork.physicalMachinesSet[j];
			j++;
			pm.reserveVM(10, r1,  VirtualMachine.Type.PRIMARY);
			l = s.searchLink(pm);
			l.reserveBandwidth(10, r1.id, Link.BandwidthType.PRIMARY);
			
			pm.reserveVM(6, r1, VirtualMachine.Type.BACKUP);
			pm = treeNetwork.physicalMachinesSet[j];
			j++;
			pm.reserveVM(10, r1, VirtualMachine.Type.BACKUP);
						
			
			//2nd request 
			r2 = new Request (id, 32, 1);
			r1.subtree  = s;
			requests.add(r2);
			id++;
			
			pm = treeNetwork.physicalMachinesSet[j];
			j++;
			pm.reserveVM(16, r2, VirtualMachine.Type.PRIMARY);
			l = s.searchLink(pm);				
			l.reserveBandwidth(16, r2.id, Link.BandwidthType.PRIMARY);
			
			pm = treeNetwork.physicalMachinesSet[j];
			j++;
			pm.reserveVM(16, r2,  VirtualMachine.Type.PRIMARY);
			l = s.searchLink(pm);
			l.reserveBandwidth(16, r2.id, Link.BandwidthType.PRIMARY);
			//pm.reserveVM(1, r2, VirtualMachine.Type.BACKUP);
							
			pm = treeNetwork.physicalMachinesSet[j];
			j++;
			pm.reserveVM(16, r2, VirtualMachine.Type.BACKUP);
							
		}
		
		return requests;
	}
	
	
	public static void main(String [] args) throws IloException
	{	
		
		
		/*Request request = new Request(0,3,1);//id/VM/bw
		int [][] pmAllocation = {
					
				{0,1,0,0},
				{0,0,1,0},
				{0,0,0,1}
		};
		int[][] primaryBandwdith = {
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,1,0,0,0,0},
				{0,0,0,0,0,1,0,0,0},
				{0,0,0,0,0,1,0,0,0},
				{0,0,0,0,0,0,1,0,0},
				{0,0,0,0,0,0,0,1,0},
				{0,0,0,0,0,0,0,0,1},
				{0,0,0,0,0,0,0,0,1},
				{0,0,0,0,0,0,0,0,0},
				
		};
		
		int[][] backupAllocation = {
				
				{1,0,0,0},	
				{0,0,0,0},
				{0,0,0,0},
				{0,0,0,0}
		};
		*/
		
		
		
		// Get amount of free memory within the heap in bytes. This size will increase
		// after garbage collection and decrease as new objects are created.
		System.out.println ("Starting -- " +Runtime.getRuntime().freeMemory());
		ArrayList <Request> requests = new ArrayList<Request>();
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(192, 16, 3, 2, 32, 1000,10000, 50000);
	//	FatTreeNetwork treeNetwork = new FatTreeNetwork(96, 16, 3, 2, 16, 1000,10000, 50000);
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(48, 16, 3, 2, 8, 1000,10000, 50000);
		FatTreeNetwork treeNetwork = new FatTreeNetwork(12, 4, 3, 2, 2, 1000,10000, 50000);
		//FatTreeNetwork treeNetwork = new FatTreeNetwork(4,4, 2, 1, 2, 5,10, 20);
		treeNetwork.buildTreeNetwork();
		System.out.println ("network initialized -- " +Runtime.getRuntime().freeMemory());
		
	
		
		requests = BackupToVMMappingModel.generateRequests(4, treeNetwork);
		System.out.println ("requests generated -- " +Runtime.getRuntime().freeMemory());
		for (int i =0; i<requests.size(); i++)
		{ System.out.println ("REQUEST "+i);
		BackupToVmMappingModelEnhanced cplexModel = new BackupToVmMappingModelEnhanced(requests.get(i), treeNetwork);
			cplexModel.modelFormulation(null,null,null, true);				
		}
	
		/*BackupToVMMappingModel cplexModel = new BackupToVMMappingModel(request, treeNetwork);
		cplexModel.modelFormulation(pmAllocation,backupAllocation,primaryBandwdith,true);*/
		
			
		}
	
}

