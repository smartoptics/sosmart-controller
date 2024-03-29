/*
 * Copyright © 2016 Orange and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.networkmodel;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.transportpce.common.InstanceIdentifiers;
import org.opendaylight.transportpce.common.NetworkUtils;
import org.opendaylight.transportpce.common.network.NetworkTransactionService;
import org.opendaylight.transportpce.networkmodel.listeners.PortMappingListener;
import org.opendaylight.transportpce.networkmodel.listeners.ServiceHandlerListener;
import org.opendaylight.transportpce.networkmodel.service.FrequenciesService;
import org.opendaylight.transportpce.networkmodel.util.TpceNetwork;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.createnode.rev221006.TransportpceCreatenodeService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.deletenode.rev230809.TransportpceDeletenodeService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.networkutils.rev220630.TransportpceNetworkutilsService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.Network;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.mapping.Mapping;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.servicehandler.rev201125.TransportpceServicehandlerListener;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkModelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkModelProvider.class);
    private static final InstanceIdentifier<Mapping> MAPPING_II = InstanceIdentifier.create(Network.class)
        .child(org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network
                .Nodes.class)
        .child(Mapping.class);

    private final DataBroker dataBroker;
    private final RpcProviderService rpcProviderService;
    private final TransportpceNetworkutilsService networkutilsService;
    private final TransportpceCreatenodeService createnodeService;
    private final TransportpceDeletenodeService deletenodeService;
    private final NetConfTopologyListener topologyListener;
    private ListenerRegistration<NetConfTopologyListener> dataTreeChangeListenerRegistration;
    private ListenerRegistration<PortMappingListener> mappingListenerRegistration;
    private ObjectRegistration<TransportpceNetworkutilsService> networkutilsServiceRpcRegistration;
    private ObjectRegistration<TransportpceCreatenodeService> createnodeServiceRpcRegistration;
    private ObjectRegistration<TransportpceDeletenodeService> deletenodeServiceRpcRegistration;
    private TpceNetwork tpceNetwork;
    private ListenerRegistration<TransportpceServicehandlerListener> serviceHandlerListenerRegistration;
    private NotificationService notificationService;
    private FrequenciesService frequenciesService;
    private PortMappingListener portMappingListener;

    public NetworkModelProvider(NetworkTransactionService networkTransactionService, final DataBroker dataBroker,
        final RpcProviderService rpcProviderService, final TransportpceNetworkutilsService networkutilsService,
        final TransportpceCreatenodeService createnodeService,
        final TransportpceDeletenodeService deletenodeService,
        final NetConfTopologyListener topologyListener, NotificationService notificationService,
        FrequenciesService frequenciesService, PortMappingListener portMappingListener) {
        this.dataBroker = dataBroker;
        this.rpcProviderService = rpcProviderService;
        this.networkutilsService = networkutilsService;
        this.createnodeService = createnodeService;
        this.deletenodeService = deletenodeService;
        this.topologyListener = topologyListener;
        this.tpceNetwork = new TpceNetwork(networkTransactionService);
        this.notificationService = notificationService;
        this.frequenciesService = frequenciesService;
        this.portMappingListener = portMappingListener;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("NetworkModelProvider Session Initiated");
        tpceNetwork.createLayer(NetworkUtils.CLLI_NETWORK_ID);
        tpceNetwork.createLayer(NetworkUtils.UNDERLAY_NETWORK_ID);
        tpceNetwork.createLayer(NetworkUtils.OVERLAY_NETWORK_ID);
        tpceNetwork.createLayer(NetworkUtils.OTN_NETWORK_ID);
        dataTreeChangeListenerRegistration = dataBroker.registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifiers.NETCONF_TOPOLOGY_II.child(Node.class)), topologyListener);
        mappingListenerRegistration = dataBroker.registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, MAPPING_II), portMappingListener);
        networkutilsServiceRpcRegistration =
            rpcProviderService.registerRpcImplementation(TransportpceNetworkutilsService.class, networkutilsService);
        createnodeServiceRpcRegistration =
            rpcProviderService.registerRpcImplementation(TransportpceCreatenodeService.class, createnodeService);
        deletenodeServiceRpcRegistration =
            rpcProviderService.registerRpcImplementation(TransportpceDeletenodeService.class, deletenodeService);
        TransportpceServicehandlerListener serviceHandlerListner =
                new ServiceHandlerListener(frequenciesService);
        serviceHandlerListenerRegistration = notificationService.registerNotificationListener(serviceHandlerListner);
    }

        /**
         * Method called when the blueprint container is destroyed.
         */
    public void close() {
        LOG.info("NetworkModelProvider Closed");
        if (dataTreeChangeListenerRegistration != null) {
            dataTreeChangeListenerRegistration.close();
        }
        if (mappingListenerRegistration != null) {
            mappingListenerRegistration.close();
        }
        if (networkutilsServiceRpcRegistration != null) {
            networkutilsServiceRpcRegistration.close();
        }
        if (createnodeServiceRpcRegistration != null) {
            createnodeServiceRpcRegistration.close();
        }
        if (deletenodeServiceRpcRegistration != null) {
            deletenodeServiceRpcRegistration.close();
        }
        serviceHandlerListenerRegistration.close();
    }
}
