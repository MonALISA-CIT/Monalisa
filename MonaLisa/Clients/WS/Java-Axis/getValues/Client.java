
import org.apache.axis.AxisFault;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.utils.Options;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;

import lia.ws.Result;
                                
public class Client
{
    public static void main(String [] args) throws Exception
    {

        if (args.length!=7) {
             System.out.println ("Bad arguments ....");
	     System.out.println ("Arguments:  "+"\n"+"Farm: String "+"Cluster: String "+"Node: String  "+"ParameterName: "+"fromTime: long "+"toTime: long");
	     return;
	}

	// create the service
        Service  service = new Service();
	// create the call
        Call     call    = (Call) service.createCall();
        QName    qn      = new QName( "http://ws.lia", "Result" );

	//map types
        call.registerTypeMapping(Result.class, qn,
                      new org.apache.axis.encoding.ser.BeanSerializerFactory(Result.class, qn),        
                      new org.apache.axis.encoding.ser.BeanDeserializerFactory(Result.class, qn));        
        Result[] result;
	
        try {
            call.setTargetEndpointAddress( new java.net.URL(args[6]) );
	    
	    //the  function to be called	    
            call.setOperationName( new QName("MLWebService", "getValues") );
	    
	    //set the parameters for the called function
	    //in0 - the farm name - string
	    //in1 - the cluster name - string
	    //in2 - the node name - string
	    //in3 - the parameter name - string
	    //in4 - from time - long
	    //in5 - to time - long
            call.addParameter( "in0",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );
            call.addParameter( "in1",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );
            call.addParameter( "in2",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );
            call.addParameter( "in3",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );	    
            call.addParameter( "in4",org.apache.axis.encoding.XMLType.XSD_LONG , ParameterMode.IN );
            call.addParameter( "in5",org.apache.axis.encoding.XMLType.XSD_LONG , ParameterMode.IN );

	    // set the type of the returned result
            call.setReturnType( org.apache.axis.encoding.XMLType.SOAP_ARRAY );

	    //invoke the call
	    // args[0] - location of the service
	    // args[1] - farm name - string
	    // args[2] - cluster name - string
	    // args[3] - node name - string
	    // args[4] - parameter name - string
	    // args[5] - fromTime - long
	    // args[6] - toTime - long
            result = (Result[]) call.invoke( new Object[] { args[0], args[1],args[2],args[3],new Long(args[4]),new Long(args[5]) } );


	    if (result == null ){
		System.out.println ("Some error occured in accessing the database...");
		return;
	    }	
	
	    //show the results
           System.out.println ("\n      --------> Received values <--------\n");
	   
           for (int i=0;i<result.length;i++) {
               System.out.println ("\tFarmName:      "+result[i].getFarmName());
               System.out.println ("\tClusterName:   "+result[i].getClusterName());
               System.out.println ("\tNodeName:      "+result[i].getNodeName());
               System.out.println ("\tParameters: ");
	       for (int j=0;j<result[i].getParam_name().length;j++) {
	    	    System.out.println ("\t\t "+result[i].getParam_name()[j]+" : "+result[i].getParam()[j]);
	       }
               System.out.println ("\tTime:          "+result[i].getTime());
               System.out.println ("");
           } //for
	    
	} catch (AxisFault fault) {
	    System.out.println (fault.getFaultString());
        } catch (Exception e) {
	    e.printStackTrace ();
	}
    }
}
