/*
 * Copyright © 2018 Orange Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.renderer.provisiondevice;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.mdsal.binding.api.MountPoint;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.transportpce.common.StringConstants;
import org.opendaylight.transportpce.common.crossconnect.CrossConnect;
import org.opendaylight.transportpce.common.crossconnect.CrossConnectImpl;
import org.opendaylight.transportpce.common.crossconnect.CrossConnectImpl121;
import org.opendaylight.transportpce.common.crossconnect.CrossConnectImpl221;
import org.opendaylight.transportpce.common.crossconnect.CrossConnectImpl710;
import org.opendaylight.transportpce.common.device.DeviceTransactionManager;
import org.opendaylight.transportpce.common.device.DeviceTransactionManagerImpl;
import org.opendaylight.transportpce.common.mapping.MappingUtils;
import org.opendaylight.transportpce.common.mapping.MappingUtilsImpl;
import org.opendaylight.transportpce.common.mapping.PortMapping;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfaceException;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfaces;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfacesImpl;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfacesImpl121;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfacesImpl221;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfacesImpl710;
import org.opendaylight.transportpce.renderer.openroadminterface.OpenRoadmInterface121;
import org.opendaylight.transportpce.renderer.openroadminterface.OpenRoadmInterface221;
import org.opendaylight.transportpce.renderer.openroadminterface.OpenRoadmInterface710;
import org.opendaylight.transportpce.renderer.openroadminterface.OpenRoadmInterfaceFactory;
import org.opendaylight.transportpce.renderer.openroadminterface.OpenRoadmOtnInterface221;
import org.opendaylight.transportpce.renderer.openroadminterface.OpenRoadmOtnInterface710;
import org.opendaylight.transportpce.renderer.utils.CreateOtsOmsDataUtils;
import org.opendaylight.transportpce.renderer.utils.MountPointUtils;
import org.opendaylight.transportpce.test.AbstractTest;
import org.opendaylight.transportpce.test.stub.MountPointServiceStub;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.device.renderer.rev211004.CreateOtsOmsInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.device.renderer.rev211004.CreateOtsOmsOutput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.Network;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.OpenroadmNodeVersion;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.mapping.Mapping;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.Nodes;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.NodesBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.NodesKey;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.nodes.NodeInfo;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.nodes.NodeInfoBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class DeviceRendererServiceImplCreateOtsOmsTest extends AbstractTest {

    private DeviceRendererService deviceRendererService;
    private CrossConnect crossConnect;
    private OpenRoadmInterfaces openRoadmInterfaces;
    private OpenRoadmInterfaceFactory openRoadmInterfaceFactory;
    private DeviceTransactionManager deviceTransactionManager;
    private MappingUtils mappingUtils;
    private OpenRoadmInterfacesImpl121 openRoadmInterfacesImpl121;
    private OpenRoadmInterfacesImpl221 openRoadmInterfacesImpl221;
    private OpenRoadmInterfacesImpl710 openRoadmInterfacesImpl710;
    private CrossConnectImpl121 crossConnectImpl121;
    private CrossConnectImpl221 crossConnectImpl221;
    private CrossConnectImpl710 crossConnectImpl710;
    private final PortMapping portMapping = Mockito.mock(PortMapping.class);

    private void setMountPoint(MountPoint mountPoint) {
        MountPointService mountPointService = new MountPointServiceStub(mountPoint);
        this.deviceTransactionManager = new DeviceTransactionManagerImpl(mountPointService, 3000);
        this.mappingUtils = new MappingUtilsImpl(getDataBroker());
        this.mappingUtils = Mockito.spy(MappingUtils.class);

        Mockito.doReturn(StringConstants.OPENROADM_DEVICE_VERSION_1_2_1).when(mappingUtils)
                .getOpenRoadmVersion(Mockito.anyString());
        this.openRoadmInterfaces = new OpenRoadmInterfacesImpl(deviceTransactionManager, mappingUtils,
            openRoadmInterfacesImpl121, openRoadmInterfacesImpl221, openRoadmInterfacesImpl710);
        this.openRoadmInterfaces = Mockito.spy(this.openRoadmInterfaces);
        OpenRoadmInterface121 openRoadmInterface121 = new OpenRoadmInterface121(portMapping,openRoadmInterfaces);
        OpenRoadmInterface221 openRoadmInterface221 = new OpenRoadmInterface221(portMapping,openRoadmInterfaces);
        OpenRoadmInterface710 openRoadmInterface710 = new OpenRoadmInterface710(portMapping, openRoadmInterfaces);
        OpenRoadmOtnInterface221 openRoadmOTNInterface221 = new OpenRoadmOtnInterface221(portMapping,
            openRoadmInterfaces);
        OpenRoadmOtnInterface710 openRoadmOtnInterface710 = new OpenRoadmOtnInterface710(portMapping,
            openRoadmInterfaces);
        this.openRoadmInterfaceFactory = new OpenRoadmInterfaceFactory(this.mappingUtils,openRoadmInterface121,
            openRoadmInterface221, openRoadmInterface710, openRoadmOTNInterface221, openRoadmOtnInterface710);

        this.crossConnectImpl121 = new CrossConnectImpl121(this.deviceTransactionManager);
        this.crossConnectImpl221 = new CrossConnectImpl221(this.deviceTransactionManager);
        this.crossConnect = new CrossConnectImpl(this.deviceTransactionManager, this.mappingUtils,
            this.crossConnectImpl121, this.crossConnectImpl221, this.crossConnectImpl710);
        this.crossConnect = Mockito.spy(this.crossConnect);
        this.deviceRendererService = new DeviceRendererServiceImpl(getDataBroker(),
            this.deviceTransactionManager, this.openRoadmInterfaceFactory, this.openRoadmInterfaces,
            this.crossConnect, portMapping);
    }

    @Test
    public void testCreateOtsOmsWhenDeviceIsNotMounted() throws OpenRoadmInterfaceException {
        setMountPoint(null);
        CreateOtsOmsInput input = CreateOtsOmsDataUtils.buildCreateOtsOms();
        CreateOtsOmsOutput result = this.deviceRendererService.createOtsOms(input);
        Assert.assertFalse(result.getSuccess());
        Assert.assertEquals("node 1 is not mounted on the controller",
            result.getResult());
    }

    @Test
    public void testCreateOtsOmsWhenDeviceIsMountedWithNoMapping() throws OpenRoadmInterfaceException {
        setMountPoint(MountPointUtils.getMountPoint(new ArrayList<>(), getDataBroker()));
        CreateOtsOmsInput input = CreateOtsOmsDataUtils.buildCreateOtsOms();
        CreateOtsOmsOutput result = this.deviceRendererService.createOtsOms(input);
        Assert.assertFalse(result.getSuccess());
    }

    @Test
    public void testCreateOtsOmsWhenDeviceIsMountedWithMapping()
            throws OpenRoadmInterfaceException, InterruptedException, ExecutionException {
        InstanceIdentifier<NodeInfo> nodeInfoIID = InstanceIdentifier.builder(Network.class).child(Nodes.class,
                new NodesKey("node 1")).child(NodeInfo.class).build();
        InstanceIdentifier<Nodes> nodeIID = InstanceIdentifier.builder(Network.class).child(Nodes.class,
                new NodesKey("node 1")).build();
        final NodeInfo nodeInfo = new NodeInfoBuilder().setOpenroadmVersion(OpenroadmNodeVersion._221).build();
        Nodes nodes = new NodesBuilder().setNodeId("node 1").setNodeInfo(nodeInfo).build();
        WriteTransaction wr = getDataBroker().newWriteOnlyTransaction();
        wr.merge(LogicalDatastoreType.CONFIGURATION, nodeIID, nodes);
        wr.merge(LogicalDatastoreType.CONFIGURATION, nodeInfoIID, nodeInfo);
        wr.commit().get();
        setMountPoint(MountPointUtils.getMountPoint(new ArrayList<>(), getDataBroker()));
        CreateOtsOmsInput input = CreateOtsOmsDataUtils.buildCreateOtsOms();
        Mapping mapping = MountPointUtils.createMapping(input.getNodeId(), input.getLogicalConnectionPoint());
        Mockito.when(portMapping.getMapping(Mockito.anyString(), Mockito.anyString())).thenReturn(mapping);
        CreateOtsOmsOutput result = this.deviceRendererService.createOtsOms(input);
        Assert.assertTrue(result.getSuccess());
    }

}
