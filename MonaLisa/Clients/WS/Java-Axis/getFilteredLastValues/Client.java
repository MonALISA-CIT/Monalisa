
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
            call.setTargetEndpointAddress( new java.net.URL(args[4]) );
	    
	    //the  function to be called	    
            call.setOperationName( new QName("MLWebService", "getFilteredLastValues") );
	    
	    call.addParameter( "in0",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );
	    call.addParameter( "in1",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );
	    call.addParameter( "in2",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );
	    call.addParameter( "in3",org.apache.axis.encoding.XMLType.XSD_STRING , ParameterMode.IN );
	    
	    // set the type of the returned result
            call.setReturnType( org.apache.axis.encoding.XMLType.SOAP_ARRAY );

            result = (Result[]) call.invoke(new Object[] {args[0], args[1],args[2],args[3]});

	    if (result == null ){
		System.out.println ("Some error occured in accessing the database...");
		return;
	    }	
	
	    //show the results
           System.out.println ("\n      --------> Received values <--------\n");
	   
	   long lNow = System.currentTimeMillis();
	   
           for (int i=0;i<result.length;i++) {
               System.out.println (
	    	    "FarmName: "+result[i].getFarmName()+
	    	    "\tClusterName: "+result[i].getClusterName()+
		    "\tNodeName: "+result[i].getNodeName()
	       );
	       
	       for (int j=0;j<result[i].getParam_name().length;j++) {
	    	    System.out.println ("\t"+result[i].getParam_name()[j]+":\t"+result[i].getParam()[j]);
	       }
	       
               System.out.println (
	    	    "\tTime: "+result[i].getTime()+" ("+
	            (((double)(lNow-result[i].getTime()))/1000D)+" seconds old)"
	       );
	       
               System.out.println ("");
           } //for
	    
	} catch (AxisFault fault) {
	    System.out.println (fault.getFaultString());
        } catch (Exception e) {
	    e.printStackTrace ();
	}
    }
}
