

import org.apache.wsif.WSIFService;
import org.apache.wsif.WSIFServiceFactory;
import org.apache.wsif.WSIFException;
import java.rmi.RemoteException;

import javax.xml.namespace.QName;

import ws.lia.MLWebService;
import lia.ws.Result;

public class Client {
	
    public static void main(String[] args) {
        try {

	    if (args.length!=7) {
		System.out.println ("Bad arguments ....");
		System.out.println ("Arguments:  "+"\n"+"Farm: String "+"Cluster: String "+"Node: String  "+"fromTime: long "+"toTime: long"+" wsdl file location");
		System.out.println ("LENGTH: "+args.length);		
		return;
	    }
	
            // create a service factory
            WSIFServiceFactory factory = WSIFServiceFactory.newInstance();

            // parse WSDL
            WSIFService service =
                factory.getService(
                    args[6],
                    null,
                    null,
		    null,
		    null);

	    // map types
	    service.mapType (new QName ("http://ws.lia","Result"), Class.forName ("lia.ws.Result"));

	    // create the stub
            MLWebService stub = (MLWebService) service.getStub(MLWebService.class);

	    // do the invocation - get Results
	    // args[0] - farm - string 
	    // args[1] - cluster - string 
	    // args[2] - node - string
	    // args[3] - parameter
	    // args[4] - fromTime - milliseconds - long
	    // args[5] - toTime   - milliseconds - long
            Result[] result = stub.getValues(args[0],args[1],args[2],args[3],(new Long(args[4])).longValue(),(new Long(args[5])).longValue());

	    if (result==null) {
		System.out.println ("Some error occured in accessing the database ...");
		return;
	    }	    

	    // Print received values
	    System.out.println ("\n      --------> Received values <--------\n");

	    for (int i=0;i<result.length;i++) {
		System.out.println ("\tFarmName:      "+result[i].getFarmName());
		System.out.println ("\tClusterName:   "+result[i].getClusterName());
		System.out.println ("\tNodeName:      "+result[i].getNodeName());
		System.out.println ("\t\t Parameters: ");
		for (int j=0;j<result[i].getParam_name().length;j++) {
		    System.out.println ("\t\t "+result[i].getParam_name()[j]+" : "+result[i].getParam()[j]);
		}
		System.out.println ("\tTime:          "+result[i].getTime());
		System.out.println ("");
	    }	

        } catch (WSIFException we) {
            System.out.println(
                "Error while executing sample, received an exception from WSIF; details:");
            we.printStackTrace();
        } catch (RemoteException re) {
            System.out.println(
                "Error while executing sample, received an exception due to remote invocation; details:");
            re.printStackTrace();
        } catch (ClassNotFoundException cnfe) {
	    cnfe.printStackTrace ();
	}
    }
}
