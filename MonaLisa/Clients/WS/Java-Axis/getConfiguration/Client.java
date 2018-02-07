
import org.apache.axis.AxisFault;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.utils.Options;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;

import lia.ws.WSConf;
import lia.ws.WSFarm;
import lia.ws.WSCluster;
import lia.ws.WSNode;
                                           
public class Client
{
    public static void main(String [] args) throws Exception
    {
    
	if (args.length!=1) {
	    System.out.println ("Bad Arguments....");
	    System.out.println ("Arguments: url location of the service");
	    return;
	}

	//create a service
        Service  service = new Service();
	//create a call
        Call     call    = (Call) service.createCall();
	
        QName    qn1      = new QName( "http://ws.lia", "WSConf" );
        QName    qn2      = new QName( "http://ws.lia", "WSFarm" );	
        QName    qn3      = new QName( "http://ws.lia", "WSCluster" );
        QName    qn4      = new QName( "http://ws.lia", "WSNode" );

	//map types
        call.registerTypeMapping(WSConf.class, qn1,
                      new org.apache.axis.encoding.ser.BeanSerializerFactory(WSConf.class, qn1),        
                      new org.apache.axis.encoding.ser.BeanDeserializerFactory(WSConf.class, qn1));        

        call.registerTypeMapping(WSFarm.class, qn2,
                      new org.apache.axis.encoding.ser.BeanSerializerFactory(WSFarm.class, qn2),        
                      new org.apache.axis.encoding.ser.BeanDeserializerFactory(WSFarm.class, qn2));        


        call.registerTypeMapping(WSCluster.class, qn3,
                      new org.apache.axis.encoding.ser.BeanSerializerFactory(WSCluster.class, qn3),        
                      new org.apache.axis.encoding.ser.BeanDeserializerFactory(WSCluster.class, qn3));        


        call.registerTypeMapping(WSNode.class, qn4,
                      new org.apache.axis.encoding.ser.BeanSerializerFactory(WSNode.class, qn4),        
                      new org.apache.axis.encoding.ser.BeanDeserializerFactory(WSNode.class, qn4));        

        WSConf[] result;
	
        try {
            call.setTargetEndpointAddress( new java.net.URL(args[0]) );
	    
	    //set the fonction to be called	    
            call.setOperationName( new QName("MLWebService", "getConfiguration") );
	    
	    //add the parameters to the call
	    //in3 - from time - long
	    //in4 - to time -long
            call.addParameter( "in3",org.apache.axis.encoding.XMLType.XSD_LONG , ParameterMode.IN );
            call.addParameter( "in4",org.apache.axis.encoding.XMLType.XSD_LONG , ParameterMode.IN );

	    //set the returned type - array
            call.setReturnType( org.apache.axis.encoding.XMLType.SOAP_ARRAY );

	    //invoke the call
            result = (WSConf[]) call.invoke( new Object[] { new Long(System.currentTimeMillis()-7*24*60*60*1000),new Long(System.currentTimeMillis()) } );


	    if (result == null) {
		System.out.println ("Some errors occured while getting the configuration...");
		return;
	    }
	    	
	    //show results
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

	} catch (AxisFault fault) {
	    System.out.println ("Exception:");
	    System.out.println (fault.getFaultString());
	    fault.printStackTrace ();
        } catch (Exception e) {
	    e.printStackTrace ();
	}
    }
}
