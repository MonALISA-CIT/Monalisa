

import javax.xml.namespace.QName;
import javax.wsdl.*;

import org.apache.wsif.WSIFMessage;
import org.apache.wsif.WSIFOperation;
import org.apache.wsif.WSIFPort;
import org.apache.wsif.WSIFService;
import org.apache.wsif.WSIFServiceFactory;

import org.apache.wsif.providers.soap.apacheaxis.WSIFPort_ApacheAxis;

import lia.ws.WSConf;
import lia.ws.WSFarm;
import lia.ws.WSCluster;
import lia.ws.WSNode;

public class Client {
    public static void main(String[] args) throws Exception {
    	
	if (args.length!=1){
	    System.out.println ("Bad arguments....");
	    System.out.println ("Arguments: location of the wsdl service file...");
	    return;
	}
    
        // create a service factory
        WSIFServiceFactory factory = WSIFServiceFactory.newInstance();
        WSIFService service =
            factory.getService(
                args[0], //the location of the wsdl file
                null,
                null,
		null,
		null);

        // map types
        service.mapType(
            new QName("http://ws.lia", "WSConf"),
            Class.forName(
                "lia.ws.WSConf"));

        service.mapType(
            new QName("http://ws.lia", "WSFarm"),
            Class.forName(
                "lia.ws.WSFarm"));
		
        service.mapType(
            new QName("http://ws.lia", "WSCluster"),
            Class.forName(
                "lia.ws.WSCluster"));
		
        service.mapType(
            new QName("http://ws.lia", "WSNode"),
            Class.forName(
                "lia.ws.WSNode"));
		
		
        // get the port
        WSIFPort_ApacheAxis port = (WSIFPort_ApacheAxis)service.getPort();

        // create the operation
        WSIFOperation operation = port.createOperation("getConfiguration");

        // create the input, output and fault messages associated with this operation
        WSIFMessage input = operation.createInputMessage();
        WSIFMessage output = operation.createOutputMessage();
        WSIFMessage fault = operation.createFaultMessage();
	

        // populate the input message
        input.setObjectPart("in0", new Long(System.currentTimeMillis()-7*24*60*60*1000));
        input.setObjectPart("in1", new Long (System.currentTimeMillis()));

        // do the invocation
        if (operation.executeRequestResponseOperation(input, output, fault)) {
            // invocation succeeded, extract information from output 
            // message
            WSConf[] result =
                (WSConf[]) output.getObjectPart("getConfigurationReturn");
		
	    if (result ==null) {
		System.out.println ("Some errors occured while getting the configuration...");
		return;
	    }	
	
            for (int i=0;i<result.length;i++) {
                WSFarm farm = result[i].getWsFarm();
                System.out.println("FarmName: "+farm.getFarmName());
                System.out.println ("Configuration time: "+result[i].getConfTime());        
		WSCluster[] clusters = farm.getClusterList();
                System.out.println ("\tClusters: "+clusters.length);
                for (int j=0;j<clusters.length;j++) {
                    System.out.println ("\tCluster:  "+clusters[j].getClusterName());                    
		    WSNode[] nodes = clusters[j].getNodeList();
                    System.out.println ("\t\tNodes: "+nodes.length);
                    for (int k=0;k<nodes.length;k++) {
                        System.out.println ("\t\tNode:  "+nodes[k].getNodeName());
                        String[] parameters = nodes[k].getParamList();
                        System.out.println ("\t\t\tParameters: "+parameters.length);
                        for (int l=0;l<parameters.length;l++)
                            System.out.println ("\t\t\t  "+parameters[l]);
                    }
                }
							
		System.out.println ("");
																																											
	    }																																											            
        } else {
            System.out.println("Invocation failed");
        }
    }
}
