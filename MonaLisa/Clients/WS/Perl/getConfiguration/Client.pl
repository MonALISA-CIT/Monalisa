#!/usr/bin/perl

use SOAP::Lite;
use Time::HiRes ('gettimeofday', 'usleep', 'sleep');

    ($s,$ms) = gettimeofday;

    $SERVICE_LOCATION="";
    if (@ARGV>=1) {
        $SERVICE_LOCATION=$ARGV[0];
    } else {
	$LOCATION=`../../../conf/MWS.sh`;
	$SERVICE_LOCATION=$LOCATION;
    } 
    	
	# create the service
	# call the function
	# get results
	
	$result = SOAP::Lite
	    ->uri("urn:lia.ws") #the location of the service wsdl file
	    ->proxy($SERVICE_LOCATION);        

	
	my $fromTime = SOAP::Data->type ( long => $s*1000-7*24*60*60*1000) ;
	my $toTime = SOAP::Data->type ( long => $s*1000) ;
	
	$rez = $result->getConfiguration ($fromTime, $toTime);
	
	if ($rez->fault) {
	    print $rez->faultstring;
	} else {


	    $service = $rez->result();
	    $i = -1;	
    	    # show results
    	    while (ref $service->[++$i]) {
		$res = $service->[$i];
		print "\n";
		$farm = $res->{'wsFarm'};
		print "FarmName: ".$farm->{'farmName'};
		$clusters = $farm->{'clusterList'};
		$j=-1;
		while (ref $clusters->[++$j]) {
		    $cl = $clusters->[$j];
		    print "\n\tClusterName: ".$cl->{'clusterName'};
		    $nodes = $cl->{'nodeList'};
		    $k = -1;
		    while (ref $nodes->[++$k]) {
			$nds = $nodes->[$k];
			print "\n\t\tNode: ".$nds->{'nodeName'};
			$parameters = $nds->{'paramList'};
	    	    }
		}
		print "\n";
	    }
	}
