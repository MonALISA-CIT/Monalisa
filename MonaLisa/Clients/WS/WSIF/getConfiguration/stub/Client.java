

import org.apache.wsif.WSIFService;
import org.apache.wsif.WSIFServiceFactory;
import org.apache.wsif.WSIFException;
import java.rmi.RemoteException;

import javax.xml.namespace.QName;

import ws.lia.MLWebService;
import lia.ws.Result;
import lia.ws.WSNode;
import lia.ws.WSFarm;
import lia.ws.WSCluster;
import lia.ws.WSConf;

public class Client {
	
    public static void main(String[] args) {
        try {

	    if (args.length!=1) {
		System.out.println ("Bad arguments...");
		System.out.println ("Argument: wsdl location of the file...");
		return;
	    }

            // create a service factory
            WSIFServiceFactory factory = WSIFServiceFactory.newInstance();

            // parse WSDL
            WSIFService service =
                factory.getService(
                    args[0],
                    null,
                    null,
		    null,
		    null);


            MLWebService stub = 
                    (MLWebService) service.getStub(MLWebService.class);

            WSConf[] result = stub.getConfiguration( System.currentTimeMillis()-7*24*60*60*1000,System.currentTimeMillis());

	    if (result == null) {
		System.out.println ("Some errors occured while geting the configurations ...");
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

        } catch (WSIFException we) {
            System.out.println(
                "Error while executing sample, received an exception from WSIF; details:");
            we.printStackTrace();
        } catch (RemoteException re) {
            System.out.println(
                "Error while executing sample, received an exception due to remote invocation; details:");
            re.printStackTrace();
        }
    }
}
