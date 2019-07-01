This version journal submit 2 is a copy from version 4 contains additional code for WCS and WCS+PPD model and bandwidth sharing
1- Added the following:
VirtualMachinesPlacement.java:
	1-getFaultDomains(int)
	2-WCSVmPlacementForSingleRequest(Request, int)
	3-WCSForMultipleRequests(ArrayList<Request>, int)
FatTreeNetwork:
	getActiveVMs()
NetworkStatus.java:
	1-getFaultTolerance(int, int, SubTree)
	2-calculateFaultToleranceOverTime(SubTree, int)
VMsProtection
	modified main() and automatedTesting()
	modified the backupToVmMappingModelTest()  to accept fault domains
Request
added faultDomains attribute
calculateFaultTolerance()

Added RandomGenerator Class