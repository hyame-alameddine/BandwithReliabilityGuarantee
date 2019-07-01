package Network;

/**
 * This class defines the switches nodes
 * 
 * @author Hyame
 *
 */
public class Switch extends Node{

	//defining switches type as constant
	public static final String TOR_TYPE = "TORSwitch";
	public static final String AGGREGATE_TYPE = "AggregateSwitch";
	public static final String CORE_TYPE = "CoreSwitch";
	
	
	/**
	 * General constructor
	 */
	public Switch()
	{
		
	}
	
	
	/**
	 * Constructor
	 * 
	 * @param id
	 * @param type
	 */
	public Switch (int id, String type)
	{
		
		this.id = id;
		this.type = type;
		this.level = this.getLevelBasedOnType();
				
		
	}
	
	
	/**
	 * This function clones a switch
	 * 
	 * @return cloned switch
	 */
	public Switch clone()
	{
		Switch s = new Switch (this.id, this.type);
		
		return s;
	}
	
	
	/**
	 * This function returns the level of the switch in the tree
	 * based on its type
	 * 
	 * @return level of the switch
	 */
	public int getLevelBasedOnType()
	{
		
		int level = 0;
		
		switch (this.type) 
		{
			case TOR_TYPE:
				level = 1;
				break;
			case AGGREGATE_TYPE:
				level = 2;
				break;
			case CORE_TYPE:
				level = 3;
				break;
		}
		
		return level;
		
	}
	
	
	/**
	 * This function returns the switch information
	 */
	public String toString()
	{
		String s ="";
		s+=" Swicth Id "+this.id+" at level "+this.level+" of type "+this.type+" \n ";
		
		return s;
		
	}
	
}
