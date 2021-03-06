/**
 * Copyright (C) 2009-2013 Dell, Inc.
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.opsource.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.*;

import org.dasein.cloud.network.AbstractVLANSupport;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.Networkable;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.SubnetCreateOptions;
import org.dasein.cloud.network.SubnetState;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.cloud.opsource.OpSource;
import org.dasein.cloud.opsource.OpSourceMethod;
import org.dasein.cloud.opsource.Param;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Minute;
import org.dasein.util.uom.time.TimePeriod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Network extends AbstractVLANSupport {
    static public final Logger logger = OpSource.getLogger(VLANSupport.class);

    private OpSource provider;
    
    Network(OpSource provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public boolean allowsNewSubnetCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsNewNetworkInterfaceCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsNewVlanCreation() throws CloudException, InternalException {
    	return true;
    }

    @Override
    public int getMaxVlanCount() throws CloudException, InternalException {
        return 5;
    }
    
    @Override
    public @Nullable VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.getVlan");
        try {
            return super.getVlan(vlanId);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean isNetworkInterfaceSupportEnabled() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    private @Nonnull Iterable<VLAN> fetchVlans() throws CloudException, InternalException {
        ArrayList<VLAN> list = new ArrayList<VLAN>();
        HashMap<Integer, Param>  parameters = new HashMap<Integer, Param>();
        Param param = new Param("networkWithLocation", null);
        parameters.put(0, param);

        //param = new Param(provider.getDefaultRegionId(), null);//Removed this as it appears to break when switching regions
        //parameters.put(1, param);

        OpSourceMethod method = new OpSourceMethod(provider,
                provider.buildUrl(null,true, parameters),
                provider.getBasicRequestParameters(OpSource.Content_Type_Value_Single_Para, "GET",null));
        Document doc = method.invoke();
        //Document doc = CallCache.getInstance().getAPICall("networkWithLocation", provider, parameters);

        String sNS = "";
        try{
            sNS = doc.getDocumentElement().getTagName().substring(0, doc.getDocumentElement().getTagName().indexOf(":") + 1);
        }
        catch(IndexOutOfBoundsException ignore){
            // ignore
        }
        NodeList matches = doc.getElementsByTagName(sNS + "network");

        if(matches != null){
            for( int i=0; i<matches.getLength(); i++ ) {
                Node node = matches.item(i);

                VLAN vlan = toVLAN(node);
                if( vlan != null ) {
                    list.add(vlan);
                }
            }
        }
        Cache<VLAN> cache = Cache.getInstance(getProvider(), "vlans", VLAN.class, CacheLevel.REGION_ACCOUNT, new TimePeriod<Minute>(3, TimePeriod.MINUTE));

        cache.put(getContext(), list);
        return list;
    }

    @Override
    public @Nonnull Iterable<VLAN> listVlans() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.listVlans");
        try {
            Cache<VLAN> cache = Cache.getInstance(getProvider(), "vlans", VLAN.class, CacheLevel.REGION_ACCOUNT, new TimePeriod<Minute>(3, TimePeriod.MINUTE));
            Iterable<VLAN> vlans = cache.get(getContext());

            if( vlans != null ) {
                return vlans;
            }
            return fetchVlans();
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull VLAN createVlan(@Nonnull String cidr, @Nonnull String name, @Nonnull String description, @Nonnull String domainName, @Nonnull String[] dnsServers, @Nonnull String[] ntpServers) throws CloudException, InternalException {
    	if( !allowsNewVlanCreation() ) {
            throw new OperationNotSupportedException("Dose not allow to create VLAN");
        }
        APITrace.begin(getProvider(), "VLAN.createVlan");
        try {
            HashMap<Integer, Param>  parameters = new HashMap<Integer, Param>();

            Param param;

            //Create post body
            Document doc = provider.createDoc();
            Element vlan ;
            Element location = null ;
            if(provider.getContext().getRegionId() == null){
                param = new Param(OpSource.NETWORK_BASE_PATH, null);
                vlan = doc.createElementNS("http://oec.api.opsource.net/schemas/network", "Network");

            }else{
                // Create a network under specific region
                param = new Param("networkWithLocation", null);
                vlan = doc.createElementNS("http://oec.api.opsource.net/schemas/network", "NewNetworkWithLocation");

                location = doc.createElement("location");

                location.setTextContent(provider.getContext().getRegionId());
            }
            parameters.put(0, param);

            Element nameElmt = doc.createElement("name");

            nameElmt.setTextContent(name);

            Element descriptionElmt = doc.createElement("description");

            descriptionElmt.setTextContent(description);

            vlan.appendChild(nameElmt);
            vlan.appendChild(descriptionElmt);
            if(location != null){
                vlan.appendChild(location);
            }
            doc.appendChild(vlan);

            OpSourceMethod method = new OpSourceMethod(provider,
                    provider.buildUrl(null,true, parameters),
                    provider.getBasicRequestParameters(OpSource.Content_Type_Value_Single_Para, "POST", provider.convertDomToString(doc)));

            String vlanId = method.getRequestResultId("Creating VLan", method.invoke(), "result", "resultDetail");
            if(vlanId != null){
                return this.getVlan(vlanId);
            }else{
                throw new CloudException("Creating VLan fails without explaination !!!");
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void removeVlan(String vlanId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.removeVlan");
        try {
            HashMap<Integer, Param>  parameters = new HashMap<Integer, Param>();

            Param param = new Param(OpSource.NETWORK_BASE_PATH, null);
            parameters.put(0, param);

            param = new Param(vlanId, null);
            parameters.put(1, param);

            OpSourceMethod method = new OpSourceMethod(provider,
                    provider.buildUrl("delete",true, parameters),
                    provider.getBasicRequestParameters(OpSource.Content_Type_Value_Single_Para, "GET", null));
            method.parseRequestResult("Removing Vlan",method.invoke(), "result", "resultDetail");
            try {
                fetchVlans(); // resets the cache
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
        finally {
            APITrace.end();
        }
    }

    public VLAN toVLAN(Node node) {
        if( node == null ) {
            return null;
        }
        String netmask = null;
        VLAN network = new VLAN();
        String sNS = "";
        try{
            sNS = node.getNodeName().substring(0, node.getNodeName().indexOf(":") + 1);
        }
        catch(IndexOutOfBoundsException ignore){
            // ignore
        }
        
        NodeList attributes = node.getChildNodes();

        network.setProviderOwnerId(provider.getContext().getAccountNumber());
        network.setCurrentState(VLANState.AVAILABLE);
        network.setProviderRegionId(provider.getContext().getRegionId());
        String gateway = null;

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName().toLowerCase();
            String value;
            
            if( attribute.getChildNodes().getLength() > 0 ) {
                value = attribute.getFirstChild().getNodeValue();                
            }
            else {
                continue;
            }
            if( name.equalsIgnoreCase(sNS + "id") ) {
                network.setProviderVlanId(value);
            }
            else if( name.equalsIgnoreCase(sNS + "name") ) {
                if( network.getName() == null ) {
                    network.setName(value);
                }
            }
            else if( name.equalsIgnoreCase(sNS + "description") ) {
                network.setDescription(value);
            }
            else if( name.equalsIgnoreCase(sNS + "location") && value != null ) {
                network.setProviderRegionId(value);
                if(! value.equals(provider.getContext().getRegionId())){
                	return null;                	
                }
            }
            //else if( name.equalsIgnoreCase(sNS + "privateNet") && value != null ) {
            //	network.setGateway(value);
            //}
            else if( name.equalsIgnoreCase(sNS + "multicast") && value != null ) {
            	//           	
            }
            //From here is the information for get specific network
            else if( name.equalsIgnoreCase(sNS + "network") && value != null ) {
         		NodeList networkAttributes  = attribute.getChildNodes();
           		for(int j=0;j<networkAttributes.getLength();j++ ){
	           		Node networkItem = networkAttributes.item(j);
	           		if( networkItem.getNodeName().equalsIgnoreCase(sNS + "id") && networkItem.getFirstChild().getNodeValue() != null ) {
	                    network.setProviderVlanId(networkItem.getFirstChild().getNodeValue());
	                }
	                else if( networkItem.getNodeName().equalsIgnoreCase(sNS + "name") && networkItem.getFirstChild().getNodeValue() != null ) {
	                    if( network.getName() == null ) {
	                        network.setName(networkItem.getFirstChild().getNodeValue());
	                    }
	                }
	                else if( networkItem.getNodeName().equalsIgnoreCase(sNS + "description") && networkItem.getFirstChild().getNodeValue() != null ) {
	                    network.setDescription(networkItem.getFirstChild().getNodeValue());
	                }	                       
           		}              	
            }
            else if( name.equalsIgnoreCase(sNS + "publicSnat") && value != null ) {
            	gateway = value;
            }
            else if( name.equalsIgnoreCase(sNS + "privateSnat") && value != null ) {
            	gateway = value;
            }
            else if( name.equalsIgnoreCase(sNS + "privateNet") && value != null ) {
                gateway = value;
            }
            else if( name.equalsIgnoreCase(sNS + "publicIps") && value != null ) {
         		NodeList publicIpAttributes  = attribute.getChildNodes();
           		for(int j=0;j<publicIpAttributes.getLength();j++ ){
	           		Node publicIpAttribute = publicIpAttributes.item(j);
	 	            if( publicIpAttribute.getNodeName().equals(sNS + "IpBlock") ){
	 	            	NodeList ipItems  = publicIpAttribute.getChildNodes();
	 		            for(int k=0;k<ipItems.getLength();k++ ){
	 		            	Node ipItem = ipItems.item(j);	 		            	
	 		                if( ipItem.getNodeName().equals(sNS + "id") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	
	 		                }else if( ipItem.getNodeName().equals(sNS + "baseIp") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	//	                       
	 		                }
	 		                        		     		 
	 		            }
	 	            }            
           		}                      	
            }
        }
        if( network.getProviderVlanId() == null ) {
            return null;
        }
        network.setProviderDataCenterId(network.getProviderRegionId());
        if( network.getName() == null ) {
            network.setName(network.getProviderVlanId());
        }
        if( network.getDescription() == null ) {
            network.setDescription(network.getName());
        }
        if( gateway != null ) {
            if( netmask == null ) {
                netmask = "255.255.255.0";
            }
            network.setCidr(netmask, gateway);
        }
        return network;
    }
    
    public VLAN toNetwork(Node node) {
        if( node == null ) {
            return null;
        }  
        VLAN network = new VLAN();
        String sNS = "";
        try{
            sNS = node.getNodeName().substring(0, node.getNodeName().indexOf(":") + 1);
        }
        catch(IndexOutOfBoundsException ignore){
            // ignore
        }
        
        network.setProviderRegionId(provider.getContext().getRegionId());
        network.setProviderDataCenterId(network.getProviderRegionId());
        
        NodeList attributes = node.getChildNodes();
        
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);
            if(attribute.getNodeType() == Node.TEXT_NODE) continue;
            String name = attribute.getNodeName().toLowerCase();
            String value;
            
            if( attribute.getChildNodes().getLength() > 0 ) {
                value = attribute.getFirstChild().getNodeValue();                
            }
            else {
               continue;
            }
            if( name.equalsIgnoreCase(sNS + "id") ) {
                network.setProviderVlanId(value);
            }
            else if( name.equalsIgnoreCase(sNS + "name") ) {
            	network.setName(value);               
            }
            else if( name.equalsIgnoreCase(sNS + "description") ) {
                network.setDescription(value);
            }
            else if( name.equalsIgnoreCase(sNS + "location") ) {
                if( !value.equals(provider.getContext().getRegionId())){
                	return null;
                }                
            }
            else if( name.equalsIgnoreCase(sNS + "multicast") && value != null ) {
            	//network.setGateway(value);            	
            }
        }
        
        return network;
    }
    public static boolean isNumeric(String str)
    {
      return str.matches("-?\\d+(.\\d+)?");
    }

    public Collection<VLAN> toVLan(Node node) {
    	
    	ArrayList<VLAN> list = new ArrayList<VLAN>();
        if( node == null ) {
            return null;
        }
  
        String regionId = null;
        String sNS = "";
        try{
            sNS = node.getNodeName().substring(0, node.getNodeName().indexOf(":") + 1);
        }
        catch(IndexOutOfBoundsException ignore){
            // ignore
        }
        
        NodeList attributes = node.getChildNodes();
        
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName().toLowerCase();
            String value;
            
            if( attribute.getChildNodes().getLength() > 0 ) {
                value = attribute.getFirstChild().getNodeValue();                
            }
            else {
                value = null;
            }
            if( name.equalsIgnoreCase(sNS + "id") ) {
         
            }            
            else if( name.equalsIgnoreCase(sNS + "location") && value != null ) {
                regionId = value;
            }          
            else if( name.equalsIgnoreCase(sNS + "publicIps") && value != null ) {
         		NodeList publicIpAttributes  = attribute.getChildNodes();
           		for(int j=0;j<publicIpAttributes.getLength();j++ ){
	           		Node publicIpAttribute = publicIpAttributes.item(j);
	 	            if( publicIpAttribute.getNodeName().equals(sNS + "IpBlock") ){
	 	            	VLAN  vlan = new VLAN();
	 	            	NodeList ipItems  = publicIpAttribute.getChildNodes();
	 		            for(int k=0;k<ipItems.getLength();k++ ){
	 		            	Node ipItem = ipItems.item(j);	
	 		            	String baseIp=null;
	 		            	int mask = -1;
	 		                if( ipItem.getNodeName().equals(sNS + "id") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	vlan.setProviderVlanId(ipItem.getFirstChild().getNodeValue());
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "baseIp") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	baseIp = ipItem.getFirstChild().getNodeValue();
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "subnetSize") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	String itemValue = ipItem.getFirstChild().getNodeValue();
	 		                	if(isNumeric(itemValue)){
	 		                		mask =  32 - (int) Math.log(Integer.valueOf(itemValue));
	 		                	} 		                	
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "networkDefault") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	//TODO
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "serverToVipConnectivity") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	//TODO
	 		                }
	 		                if(baseIp != null && mask != -1){
	 		                	vlan.setCidr(baseIp + "/" + mask);
	 		                }	 		                        		     		 
	 		            }

	 		           if(vlan.getProviderVlanId() == null)
	 		        	   continue;
	 		            
	 		           vlan.setProviderOwnerId(provider.getContext().getAccountNumber());
	 		          
	 		           vlan.setCurrentState(VLANState.AVAILABLE);
	 		           if(regionId != null){
	 		        	   vlan.setProviderRegionId(regionId);		            	
	 		           }
	 		           if( vlan.getName() == null ) {
	 		        	   vlan.setName(vlan.getProviderVlanId());
	 		           }
	 		           if( vlan.getDescription() == null ) {
	 		        	  vlan.setDescription(vlan.getName());
	 		           }
	 		           
	 		           list.add(vlan);
	 	            }
           		}                      	
            }
        }
  
        return list;
    }
    
   public @Nonnull Collection<Subnet> toSubnet(@Nonnull String vlanId, @Nonnull Node node) throws InternalException, CloudException {
    	
    	ArrayList<Subnet> list = new ArrayList<Subnet>();
        if( node == null ) {
            return null;
        }
  
        String regionId = null;
       String sNS = "";
       try{
           sNS = node.getNodeName().substring(0, node.getNodeName().indexOf(":") + 1);
       }
       catch(IndexOutOfBoundsException ignore){
           // ignore
       }
        
        NodeList attributes = node.getChildNodes();
                
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);
            if(attribute.getNodeType() == Node.TEXT_NODE) continue;
            String name = attribute.getNodeName();
            String value;
            
            if( attribute.getChildNodes().getLength() > 0 ) {
                value = attribute.getFirstChild().getNodeValue();                
            }
            else {
                continue;
            }
            if( name.equalsIgnoreCase(sNS + "id") ) {
            	if(vlanId != null){
            		continue;   // TODO: Andy: what is this supposed to do?
            	}            	
            }            
            else if( name.equalsIgnoreCase(sNS + "location")  ) {
                regionId = value;
            }          
            else if( name.equalsIgnoreCase(sNS + "publicIps") ) {
         		NodeList publicIpAttributes  = attribute.getChildNodes();
           		for(int j=0;j<publicIpAttributes.getLength();j++ ){
	           		Node publicIpAttribute = publicIpAttributes.item(j);
	           		if(publicIpAttribute.getNodeType() == Node.TEXT_NODE) continue;
	 	           
	           		if( publicIpAttribute.getNodeName().equals(sNS + "IpBlock") ){
	 	            	String baseIp=null;
		            	int mask = -1;
		            	int size = -1;
	 	            	NodeList ipItems  = publicIpAttribute.getChildNodes();

                        String subnetId = null;
                        String networkDefault = null;

	 		            for(int k=0;k<ipItems.getLength();k++ ){
	 		            	Node ipItem = ipItems.item(k);
	 		            	if(ipItem.getNodeType() == Node.TEXT_NODE) continue;
	 		            	
	 		            	if( ipItem.getNodeName().equals(sNS + "id") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	subnetId = ipItem.getFirstChild().getNodeValue().trim();
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "baseIp") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	baseIp = ipItem.getFirstChild().getNodeValue();
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "subnetSize") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	String itemValue = ipItem.getFirstChild().getNodeValue();
	 		                	if(isNumeric(itemValue)){
	 		                		size = Integer.valueOf(itemValue);
	 		                		// Size usually equal 8
	 		                		//TODO set the proper mask to reflect the size
	 		                		mask =  32 - (int) Math.log(size) - 1;
	 		                	} 		                	
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "networkDefault") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	networkDefault = ipItem.getFirstChild().getNodeValue().trim();
	 		                }
	 		                else if( ipItem.getNodeName().equals(sNS + "serverToVipConnectivity") && ipItem.getFirstChild().getNodeValue() != null ) {
	 		                	//TODO
	 		                }	 		               	                        		     		 
	 		            }
	 		            
	 		            if( baseIp == null){
                             logger.warn("Found subnet with null baseIp: " + subnetId);
	 		            	continue;	 		        	  
	 		            }

                        String cidr;

	 		            if(baseIp != null && !(mask == -1)){
		                	cidr = baseIp + "/" + mask;
	 		            }
                        else {
                             cidr = "0.0.0.0/0";
                        }
                        if(regionId == null){
	 		            	regionId = provider.getDefaultRegionId();	 		            	            	
	 		            }
                        if( subnetId != null ) {
                            Subnet subnet = Subnet.getInstance(getContext().getAccountNumber(), regionId, vlanId, subnetId, SubnetState.AVAILABLE, name, name, cidr).constrainedToDataCenter(provider.getDataCenterId(regionId));

                            subnet.setTag("baseIp", baseIp);
                            if( networkDefault != null ) {
                                subnet.setTag("networkDefault", networkDefault);
                            }
                            list.add(subnet);
                        }
                        else {
                            logger.warn("Bad subnet " + baseIp + " with null ID");
                        }
	 	            }
           		}                      	
            }
        }  
        return list;
    }

    @Override
    public @Nonnull Subnet createSubnet(@Nonnull SubnetCreateOptions options) throws CloudException, InternalException {
       return createSubnet(options.getProviderVlanId());
    }
    public Subnet createSubnet(String inProviderVlanId) throws CloudException, InternalException {
    	/*HashMap<Integer, Param>  parameters = new HashMap<Integer, Param>();
        Param param = new Param(OpSource.NETWORK_BASE_PATH, null);
        
       	parameters.put(0, param);
       	
       	param = new Param(inProviderVlanId, null);
        
       	parameters.put(1, param);
       	
       	param = new Param("publicip", null);
        
       	parameters.put(2, param);
        	
       	OpSourceMethod method = new OpSourceMethod(provider, 
       			provider.buildUrl("reserveNew",true, parameters),
       			provider.getBasicRequestParameters(OpSource.Content_Type_Value_Single_Para, "GET", null));
         	
        //Result would be something like: Public IP block with base IP 207.20.37.208 is reserved
       	String result = method.requestResult("Creating subnet", method.invoke(), "result", "resultDetail");
       	
       	return getSubnetResponseInfo(result);*/
        throw new OperationNotSupportedException("Subnets are not supported");
    }

    @Override
    public @Nonnull String getProviderTermForNetworkInterface(@Nonnull Locale locale) {
        return "NIC";
    }

    @Override
    public @Nonnull String getProviderTermForSubnet(@Nonnull Locale locale) {
        return "subnet";
    }

    @Override
    public @Nonnull String getProviderTermForVlan(@Nonnull Locale locale) {
        return "network";
    }

    @Override
    public @Nullable Subnet getSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.getSubnet");
        try {
            return super.getSubnet(subnetId);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Requirement getSubnetSupport() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    public @Nullable Subnet getSubnetResponseInfo( @Nonnull String continaBaseIpInfo) throws CloudException, InternalException {
        ArrayList<VLAN> vlanList = (ArrayList<VLAN>) listVlans();

        for(VLAN vlan : vlanList){
        	
        	ArrayList<Subnet> list = (ArrayList<Subnet>) this.listSubnets(vlan.getProviderVlanId());

        	for(Subnet subnet : list){
        		if(continaBaseIpInfo.contains(subnet.getTags().get("baseIp"))){
        			return subnet;        			
        		}        		
        	}
        }    	
        return null;    	
    }


    @Override
    public boolean isVlanDataCenterConstrained() throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull Iterable<Networkable> listResources(@Nonnull String inVlanId) throws CloudException, InternalException{
        return Collections.emptyList();//TODO: Implement for 2013.02
    }

    @Override
    public boolean isSubnetDataCenterConstrained() throws CloudException, InternalException {
        return true;
    }
    
    @Override
    public @Nonnull Iterable<Subnet> listSubnets(@Nonnull String inVlanId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.listSubnets");
        try {
            HashMap<Integer, Param>  parameters = new HashMap<Integer, Param>();
            Param param = new Param("networkWithLocation", null);
            parameters.put(0, param);

            param = new Param(inVlanId, null);
            parameters.put(1, param);

            param = new Param("config", null);
            parameters.put(2, param);

            ArrayList<Subnet> list = new ArrayList<Subnet>();

            OpSourceMethod method = new OpSourceMethod(provider,
                    provider.buildUrl(null,true, parameters),
                    provider.getBasicRequestParameters(OpSource.Content_Type_Value_Single_Para, "GET",null));
            Document doc = method.invoke();
            //Document doc = CallCache.getInstance().getAPICall("networkWithLocation", provider, parameters);

            String sNS = "";
            try{
                sNS = doc.getDocumentElement().getTagName().substring(0, doc.getDocumentElement().getTagName().indexOf(":") + 1);
            }
            catch(IndexOutOfBoundsException ignore){
                // ignore
            }
            NodeList matches = doc.getElementsByTagName(sNS + "NetworkConfigurationWithLocation");
            if(matches != null){
                for( int i=0; i<matches.getLength(); i++ ) {
                    Node node = matches.item(i);
                    ArrayList<Subnet> subnetList = (ArrayList<Subnet>) toSubnet(inVlanId, node);

                    if( subnetList != null ) {
                        list.addAll(subnetList);
                    }
                }
            }
            return list;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return Collections.singletonList(IPVersion.IPV4);
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.listVlanStatus");
        try {
            return super.listVlanStatus();
        }
        finally {
            APITrace.end();
        }
    }

    /**
     * https://<Cloud API URL>/oec/0.9/{org-id}/network/{netid}/
     *  publicip/{ipblock-id}?release
     */

    @Override
    public void removeSubnet(String providerSubnetId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.removeSubnet");
        try {
            Subnet subnet = getSubnet(providerSubnetId);

            if(subnet == null){
                throw new CloudException("Fail to remove the subnet because no vlan found for this subnet!!!");
            }

            HashMap<Integer, Param>  parameters = new HashMap<Integer, Param>();
            Param param = new Param(OpSource.NETWORK_BASE_PATH, null);
            parameters.put(0, param);

            param = new Param(subnet.getProviderVlanId(), null);
            parameters.put(1, param);

            param = new Param("publicip", null);
            parameters.put(2, param);

            param = new Param(providerSubnetId, null);
            parameters.put(3, param);

            OpSourceMethod method = new OpSourceMethod(provider,
                    provider.buildUrl("release",true, parameters),
                    provider.getBasicRequestParameters(OpSource.Content_Type_Value_Single_Para, "GET", null));

            method.parseRequestResult("Removing subnet",method.invoke(), "result", "resultDetail");
        }
        finally {
            APITrace.end();
        }
    }
}
