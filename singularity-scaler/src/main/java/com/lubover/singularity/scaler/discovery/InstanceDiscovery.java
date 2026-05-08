package com.lubover.singularity.scaler.discovery;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InstanceDiscovery {
    private final NacosDiscoveryProperties nacosDiscoveryProperties;

    public List<Instance> getHealthyInstances(String serviceName) {
        try {
            NamingService namingService = nacosDiscoveryProperties.namingServiceInstance();
            return namingService.selectInstances(serviceName, true);
        } catch (NacosException e) {
            return Collections.emptyList();
        }
    }
}
