/*
 * Copyright © 2020 Orange Labs, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.pce;


import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.codec.spi.BindingDOMCodecServices;
import org.opendaylight.transportpce.common.mapping.PortMapping;
import org.opendaylight.transportpce.common.network.NetworkTransactionImpl;
import org.opendaylight.transportpce.common.network.RequestProcessor;
import org.opendaylight.transportpce.pce.utils.PceTestData;
import org.opendaylight.transportpce.pce.utils.PceTestUtils;
import org.opendaylight.transportpce.test.AbstractTest;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.mapping.Mapping;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.mapping.MappingBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.Nodes;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.NodesBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.NodesKey;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.nodes.NodeInfo;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.nodes.NodeInfoBuilder;
import org.opendaylight.yang.gen.v1.http.org.openroadm.device.types.rev191129.NodeTypes;

@RunWith(MockitoJUnitRunner.class)
public class PceSendingPceRPCsTest extends AbstractTest {

    private PceSendingPceRPCs pceSendingPceRPCs;
    private NetworkTransactionImpl networkTransaction;
    private Mapping mapping;
    @Mock
    private BindingDOMCodecServices bindingDOMCodecServices;
    private DataBroker dataBroker;
    @Mock
    private PortMapping portMapping;


    @Before
    public void setUp() {
        this.dataBroker = getNewDataBroker();
        networkTransaction = new NetworkTransactionImpl(new RequestProcessor(this.dataBroker));
        PceTestUtils.writeNetworkInDataStore(this.dataBroker);
        pceSendingPceRPCs = new PceSendingPceRPCs(PceTestData.getPCE_test1_request_54(),
                        networkTransaction, portMapping);
        mapping = new MappingBuilder().setLogicalConnectionPoint("logicalConnectionPoint").setPortQual("xpdr-client")
            .build();
        NodeInfo info = new NodeInfoBuilder().setNodeType(NodeTypes.Xpdr).build();
        Nodes node = new NodesBuilder().withKey(new NodesKey("node")).setNodeId("node").setNodeInfo(info).build();
        when(portMapping.getMapping(anyString(), anyString())).thenReturn(mapping);
        when(portMapping.getNode(anyString())).thenReturn(node);
    }

    @Test
    public void cancelResourceReserve() {
        pceSendingPceRPCs.cancelResourceReserve();
        Assert.assertTrue("Success should equal to true", pceSendingPceRPCs.getSuccess());
    }

    @Test
    public void pathComputationTest() throws Exception {
        pceSendingPceRPCs =
                new PceSendingPceRPCs(PceTestData.getGnpyPCERequest("XPONDER-1", "XPONDER-2"),
                        networkTransaction, portMapping);
        when(portMapping.getMapping(anyString(), anyString())).thenReturn(mapping);
        pceSendingPceRPCs.pathComputation();

    }

    @Test
    public void checkMessage() {
        Assert.assertNull(pceSendingPceRPCs.getMessage());
    }

    @Test
    public void responseCodeTest() {
        Assert.assertNull(pceSendingPceRPCs.getResponseCode());
    }

}
