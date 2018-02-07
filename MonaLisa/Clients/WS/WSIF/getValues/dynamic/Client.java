

import javax.xml.namespace.QName;
import javax.wsdl.*;

import org.apache.wsif.WSIFMessage;
import org.apache.wsif.WSIFOperation;
import org.apache.wsif.WSIFPort;
import org.apache.wsif.WSIFService;
import org.apache.wsif.WSIFServiceFactory;

import lia.ws.Result;

public class Client {

    public static void main(String[] args) throws Exception {

        if (args.length!=7) {
            System.out.println ("Bad arguments ....");
            System.out.println ("Arguments:  "+"\n"+"Farm: String "+"Cluster: String "+"Node: String  "+"parameterName: String"+"fromTime: long "+"toTime: long"+" wsdl file location");
            return;
        }
    	
        // create a service factory
        WSIFServiceFactory factory = WSIFServiceFactory.newInstance();
        WSIFService service =
            factory.getService(
                args[6], //location of the wsdl file ...
                null,
                null,
		null,
		null);

        // map types
        service.mapType(new QName("http://ws.lia", "Result"),Class.forName("lia.ws.Result"));

        // get the port
        WSIFPort port = service.getPort();

        // create the operation
        WSIFOperation operation = port.createOperation("getValues");

        // create the input, output and fault messages associated with this operation
        WSIFMessage input = operation.createInputMessage();
        WSIFMessage output = operation.createOutputMessage();
        WSIFMessage fault = operation.createFaultMessage();
	
        // populate the input message
	// args[0] - farm name - string
	// args[1] - cluster name - string
	// args[2] - node name - string
	// args[3] - parameter name - string 
	// args[4] - fromTime - long
	// args[5] - toTime - long
        input.setObjectPart("in0", args[0]);
        input.setObjectPart("in1", args[1]);
        input.setObjectPart("in2", args[2]);	
	input.setObjectPart("in3", args[3]);	
        input.setObjectPart("in4", new Long(args[4]));
        input.setObjectPart("in5", new Long(args[5]));	

        // do the invocation
        if (operation.executeRequestResponseOperation(input, output, fault)) {
            // invocation succeeded, extract information from output 
            // message
            Result[] result =
                (Result[]) output.getObjectPart("getValuesReturn");
		
	    if (result == null) {
		System.out.println ("Some error occured in accessing the database ...");
		return;
	    }	
	
            System.out.println ("\n      --------> Received values <--------\n");
	    
            for (int i=0;i<result.length;i++) {
                System.out.println ("\tFarmName:      "+result[i].getFarmName());
                System.out.println ("\tClusterName:   "+result[i].getClusterName());
                System.out.println ("\tNodeName:      "+result[i].getNodeName());
                System.out.println ("\tParameters:");
		for (int j=0;j<result[i].getParam_name().length;j++) {
		    System.out.println ("\t\t "+result[i].getParam_name()[j]+" : "+result[i].getParam()[j]);
		}
                System.out.println ("\tTime:          "+result[i].getTime());
                System.out.println ("");
            }


        } else {
            System.out.println("Invocation failed");
            // extract fault message info
        }
    }
}
