/*
 * Copyright © 2017 Orange, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.servicehandler.impl;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.transportpce.servicehandler.listeners.NetworkModelListenerImpl;
import org.opendaylight.transportpce.servicehandler.listeners.PceListenerImpl;
import org.opendaylight.transportpce.servicehandler.listeners.RendererListenerImpl;
import org.opendaylight.transportpce.servicehandler.listeners.ServiceListener;
import org.opendaylight.transportpce.servicehandler.service.ServiceDataStoreOperations;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev231110.TransportpceEditserviceService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.networkmodel.rev201116.TransportpceNetworkmodelListener;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.pce.rev220615.TransportpcePceListener;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.renderer.rev210915.TransportpceRendererListener;
import org.opendaylight.yang.gen.v1.http.org.openroadm.service.rev211210.OrgOpenroadmServiceService;
import org.opendaylight.yang.gen.v1.http.org.openroadm.service.rev211210.ServiceList;
import org.opendaylight.yang.gen.v1.http.org.openroadm.service.rev211210.service.list.Services;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to register
 * Servicehandler Service and Notification.
 * @author Martial Coulibaly ( martial.coulibaly@gfi.com ) on behalf of Orange
 *
 */
public class ServicehandlerProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ServicehandlerProvider.class);
    private static final InstanceIdentifier<Services> SERVICE = InstanceIdentifier.builder(ServiceList.class)
            .child(Services.class).build();

    private final DataBroker dataBroker;
    private final RpcProviderService rpcService;
    private final NotificationService notificationService;
    private ListenerRegistration<TransportpcePceListener> pcelistenerRegistration;
    private ListenerRegistration<ServiceListener> serviceDataTreeChangeListenerRegistration;
    private ListenerRegistration<TransportpceRendererListener> rendererlistenerRegistration;
    private ListenerRegistration<TransportpceNetworkmodelListener> networkmodellistenerRegistration;
    private ObjectRegistration<OrgOpenroadmServiceService> rpcRegistration;
    private ObjectRegistration<TransportpceEditserviceService> editServiceRpcRegistration;
    private ServiceDataStoreOperations serviceDataStoreOperations;
    private PceListenerImpl pceListenerImpl;
    private ServiceListener serviceListener;
    private RendererListenerImpl rendererListenerImpl;
    private NetworkModelListenerImpl networkModelListenerImpl;
    private ServicehandlerImpl servicehandler;
    private TransportpceEditserviceService editserviceService;

    public ServicehandlerProvider(final DataBroker dataBroker, RpcProviderService rpcProviderService,
            NotificationService notificationService, ServiceDataStoreOperations serviceDataStoreOperations,
            PceListenerImpl pceListenerImpl, ServiceListener serviceListener, RendererListenerImpl rendererListenerImpl,
            NetworkModelListenerImpl networkModelListenerImpl, ServicehandlerImpl servicehandler,
            TransportpceEditserviceService editserviceService) {
        this.dataBroker = dataBroker;
        this.rpcService = rpcProviderService;
        this.notificationService = notificationService;
        this.serviceDataStoreOperations = serviceDataStoreOperations;
        this.serviceDataStoreOperations.initialize();
        this.pceListenerImpl = pceListenerImpl;
        this.serviceListener = serviceListener;
        this.rendererListenerImpl = rendererListenerImpl;
        this.networkModelListenerImpl = networkModelListenerImpl;
        this.servicehandler = servicehandler;
        this.editserviceService = editserviceService;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("ServicehandlerProvider Session Initiated");
        pcelistenerRegistration = notificationService.registerNotificationListener(pceListenerImpl);
        serviceDataTreeChangeListenerRegistration = dataBroker.registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, SERVICE), serviceListener);
        rendererlistenerRegistration = notificationService.registerNotificationListener(rendererListenerImpl);
        networkmodellistenerRegistration = notificationService.registerNotificationListener(networkModelListenerImpl);
        rpcRegistration = rpcService.registerRpcImplementation(OrgOpenroadmServiceService.class, servicehandler);
        editServiceRpcRegistration = rpcService
            .registerRpcImplementation(TransportpceEditserviceService.class, editserviceService);
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("ServicehandlerProvider Closed");
        pcelistenerRegistration.close();
        serviceDataTreeChangeListenerRegistration.close();
        rendererlistenerRegistration.close();
        networkmodellistenerRegistration.close();
        rpcRegistration.close();
        editServiceRpcRegistration.close();
    }

}
