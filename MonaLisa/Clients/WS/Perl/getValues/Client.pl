#!/usr/bin/perl -w

use SOAP::Lite;

    if (@ARGV==7 || @ARGV==6) { 

	# create the service
	# call the function
	#   - ARGV[0] - farm name
	#   - ARGV[1] - cluster name
	#   - ARGV[2] - node name
	#   - ARGV[3] - parameter name
	#   - ARGV[4] - from time - long
	#   - ARGV[5] - to time - long
	#   - ARGV[6] - URL of the MLWebService - optional
	
	# get results
	$SERVICE_LOCATION="";
	if (@ARGV==7) {
	    $SERVICE_LOCATION=$ARGV[6];
	} else {
	    $SERVICE_LOCATION=`../../../conf/MWS.sh`;
	}
     	
	 $result = SOAP::Lite
	    ->uri ('urn:lia.ws')
	    ->proxy($SERVICE_LOCATION);

	my $fromTime = SOAP::Data->type (long => $ARGV[4]);
	my $toTime = SOAP::Data->type (long => $ARGV[5]);
	
	 $rez =$result->getValues("$ARGV[0]","$ARGV[1]","$ARGV[2]","$ARGV[3]", $fromTime, $toTime);#$ARGV[4],long => $ARGV[5]) ;

	
	if ($rez->fault){
	    print "\n".$rez->faultstring."\n";	    
	} else {
	    $service=$rez->result();
	    $i = -1;
		
		# show results
	    	while (ref $service->[++$i]) {
		    $res = $service->[$i];
		    print "\n";
		    print "FarmName: ".$res->{'farmName'}."\n";
		    print "ClusterName: ".$res->{'clusterName'}."\n";	    
		    print "NodeName: ".$res->{'nodeName'}."\n";	    
		    print "ParameterName: ".$res->{'param_name'}."\n";	    	    	    
		    print "ParameterValue: ".$res->{'param'}."\n";	    
		    print "Time: ".$res->{'time'}."\n";	    
		}
	}
    } else {
	print "\n";
	print "Bad arguments "."\n";
	print "Arguments:  farm name, cluster name, node name, parameter name , from time, to time, URL location of the wsdl service file";
    }   
    
