/*
 * Copyright © 2020 Orange, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.networkmodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.FluentFuture;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.transportpce.common.network.NetworkTransactionService;
import org.opendaylight.transportpce.networkmodel.listeners.PortMappingListener;
import org.opendaylight.transportpce.networkmodel.service.FrequenciesService;
import org.opendaylight.transportpce.test.AbstractTest;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.createnode.rev221006.TransportpceCreatenodeService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.deletenode.rev230809.TransportpceDeletenodeService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.networkutils.rev220630.TransportpceNetworkutilsService;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetworkModelProviderTest extends AbstractTest {
    @Mock
    NetworkTransactionService networkTransactionService;
    @Mock
    RpcProviderService rpcProviderService;
    @Mock
    TransportpceNetworkutilsService networkutilsService;
    @Mock
    TransportpceCreatenodeService createnodeService;
    @Mock
    TransportpceDeletenodeService deletenodeService;
    @Mock
    NetConfTopologyListener topologyListener;
    @Mock
    private NotificationService notificationService;
    @Mock
    private FrequenciesService frequenciesService;
    @Mock
    private PortMappingListener portMappingListener;


    @Test
    public void networkmodelProviderInitTest() {
        NetworkModelProvider provider = new NetworkModelProvider(networkTransactionService, getDataBroker(),
            rpcProviderService, networkutilsService, createnodeService, deletenodeService, topologyListener,
            notificationService, frequenciesService, portMappingListener);
        Answer<FluentFuture<CommitInfo>> answer = new Answer<FluentFuture<CommitInfo>>() {

            @Override
            public FluentFuture<CommitInfo> answer(InvocationOnMock invocation) throws Throwable {
                return CommitInfo.emptyFluentFuture();
            }

        };
        when(networkTransactionService.commit()).then(answer);

        provider.init();

        verify(rpcProviderService, times(1))
            .registerRpcImplementation(any(), any(TransportpceNetworkutilsService.class));
    }

}
