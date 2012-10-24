/**
 * Copyright (C) 2009-2012 enStratus Networks Inc
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


import org.dasein.cloud.network.AbstractNetworkServices;
import org.dasein.cloud.opsource.OpSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class OpSourceNetworkServices extends AbstractNetworkServices {
    private OpSource cloud;
    
    public OpSourceNetworkServices(@Nonnull OpSource cloud) { this.cloud = cloud; }
    
    @Override 
    public @Nullable SecurityGroup getFirewallSupport() {
       return new SecurityGroup(cloud);      
    }
    
    @Override
    public @Nonnull IpAddressImplement getIpAddressSupport() {
        return new IpAddressImplement(cloud);
    }
    
    @Override
    public @Nonnull LoadBalancers getLoadBalancerSupport() {
        return new LoadBalancers(cloud);
    }
    
    @Override
    public @Nullable Network getVlanSupport() {
    	return new Network(cloud);      
    }
}
