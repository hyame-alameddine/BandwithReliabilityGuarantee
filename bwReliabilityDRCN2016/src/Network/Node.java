package Network;
/**
 * This class defines the nodes of the network
 * It is a general class that can represent:
 * physical machines, switches, racks...
 *  
 * @author Hyame
 *
 */
public class Node {
	
	//id of the node should be unique
	public int id;
	
	//level of the node in the tree
	public int level;
	
	//type of the node (physical machine, switch ...)
	public String type;
	
	
	/**
	 * general constructor 
	 *
	 */
	public Node()
	{
		
	}
	
	
	/**
	 * Constructor of the class that defines the id
	 * and the level of the node
	 * 
	 * @param id
	 * @param level
	 * @param type
	 */
	public Node (int id, int level, String type)
	{
		
		this.id = id;
		this.level = level;
		this.type =  type;
	}
	
	/**
	 * function that returns true if two nodes have the same id
	 * 
	 * @param node
	 */
	public boolean equals ( Node node)
	{
		if (this.id == node.id && this.level == node.level)
		{
			return true;
		}
		
		return false;
	
	}
}
