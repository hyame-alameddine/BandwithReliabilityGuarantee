package Network;

import java.util.ArrayList;

public class SharingSet {
	
	//link where the specified sharing set is applied
	public Link l;
	
	//list of requests sharing backup bw
	public ArrayList <Request> requests;

	//DECREASING ORDERED list of requests backup bw needed on link l (without sharing) 
	public ArrayList <Integer> requestsBackupBw;
	
	//bandwidth to reserve for the set. This corresponds to the maximum bandwidth of all the requests in this set (requestsBackupBw[0])
	public int bandwidthToReserve;
	
	
	public SharingSet(Link l)
	{
		this.l =l;
		this.requests = new ArrayList <Request>();
		this.requestsBackupBw = new ArrayList <Integer>();
	}

	
	/**
	 * This function clones a sharing set with all its attributes
	 * Note: the requests will be added later
	 * 
	 * @param link the cloned link for which the cloned sharing set should belong
	 * 
	 * @return cloned sharing set
	 */
	public SharingSet clone(Link link)
	{
		ArrayList <Integer> newRequestsBackupBw = new ArrayList <Integer>();
			
		SharingSet s = new SharingSet(link);
		
		
		for (int i=0;i<this.requestsBackupBw.size(); i++)
		{
			newRequestsBackupBw.add (this.requestsBackupBw.get(i));
		}
		
		//this will be set temporarily (to be able to use the request.equals) and updated to the cloned request in vmProtection.clone()
		s.requests = this.requests;
		
		s.requestsBackupBw = newRequestsBackupBw;
		s.bandwidthToReserve = this.bandwidthToReserve;
		
		return s;
	}
	
	
	/**
	 * This function checks if 2 sharing sets are equals
	 * 1-if they belong to the same link
	 * 2-They have the same set of tenants
	 * 
	 * @param s sharing set to compare with
	 * @return boolean
	 */
	public boolean equals (SharingSet s)
	{
		if (this.l.continuousId != s.l.continuousId)
		{
			return false;
		}
		
		for (int i =0; i<this.requests.size(); i++)
		{
			for (int j =0; j<s.requests.size(); j++)
			{
				if (this.requests.get(j).id != s.requests.get(j).id)
				{
					return false;
				}
			}
		}
		
		return true;
	}
	
	/**
	 * This function return a string of the information related to the sharing set
	 */
	public String toString()
	{
		String s= "";
		s +="\nSharing set on link "+this.l.continuousId+" Reserved bandwidth for this set : "+this.bandwidthToReserve;
		
		for (int i=0; i<requests.size(); i++)
		{
			s+="\n Request "+requests.get(i).id+" ( "+requests.get(i).N+" , "+ requests.get(i).B +")\n";
		}
	
		return s;
		
	}
}
