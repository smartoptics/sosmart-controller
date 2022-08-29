/*
 * Copyright © 2023 Smartoptics.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.servicehandler.service;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.transportpce.common.OperationResult;
import org.opendaylight.transportpce.common.Timeouts;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev230123.ChangeRequestIdInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev230123.ChangeRequestIdOutput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev230123.ChangeRequestIdOutputBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev230123.RenameServiceInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev230123.RenameServiceOutput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev230123.RenameServiceOutputBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.editservice.rev230123.TransportpceEditserviceService;
import org.opendaylight.yang.gen.v1.http.org.openroadm.common.service.types.rev211210.sdnc.request.header.SdncRequestHeaderBuilder;
import org.opendaylight.yang.gen.v1.http.org.openroadm.common.state.types.rev191129.State;
import org.opendaylight.yang.gen.v1.http.org.openroadm.equipment.states.types.rev191129.AdminStates;
import org.opendaylight.yang.gen.v1.http.org.openroadm.service.rev211210.ServiceList;
import org.opendaylight.yang.gen.v1.http.org.openroadm.service.rev211210.service.list.Services;
import org.opendaylight.yang.gen.v1.http.org.openroadm.service.rev211210.service.list.ServicesBuilder;
import org.opendaylight.yang.gen.v1.http.org.openroadm.service.rev211210.service.list.ServicesKey;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.service.types.rev220118.service.handler.header.ServiceHandlerHeaderBuilder;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev171017.ServicePathList;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev171017.service.path.list.ServicePaths;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev171017.service.path.list.ServicePathsBuilder;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.servicepath.rev171017.service.path.list.ServicePathsKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditServiceImpl implements TransportpceEditserviceService {

    private static final Logger LOG = LoggerFactory.getLogger(EditServiceImpl.class);
    private final ServiceDataStoreOperations serviceDataStoreOperations;
    private DataBroker dataBroker;

    public EditServiceImpl(final ServiceDataStoreOperations serviceDataStoreOperations, DataBroker dataBroker) {
        this.serviceDataStoreOperations = serviceDataStoreOperations;
        this.dataBroker = dataBroker;
    }

    @Override
    public ListenableFuture<RpcResult<RenameServiceOutput>> renameService(RenameServiceInput input) {
        LOG.info("RPC rename service called for {}", input.getServiceName());
        Optional<Services> readServiceOpt = this.serviceDataStoreOperations.getService(input.getServiceName());
        Optional<ServicePaths> readServicePathOpt = this.serviceDataStoreOperations
            .getServicePath(input.getServiceName());
        if (!readServiceOpt.isPresent() || !readServicePathOpt.isPresent()) {
            LOG.warn("renameService: {} not found", input.getServiceName());
            return RpcResultBuilder.<RenameServiceOutput>failed()
                .withError(ErrorType.RPC, "Service not found")
                .buildFuture();
        }
        Services readService = readServiceOpt.get();
        ServicePaths readServicePath = readServicePathOpt.get();
        try {
            InstanceIdentifier<Services> iid = InstanceIdentifier.create(ServiceList.class)
                .child(Services.class, new ServicesKey(input.getNewServiceName()));
            Services service = new ServicesBuilder()
                .setServiceName(input.getNewServiceName())
                .setAdministrativeState(AdminStates.OutOfService)
                .setOperationalState(State.OutOfService)
                .setCommonId(readService.getCommonId())
                .setConnectionType(readService.getConnectionType())
                .setCustomer(readService.getCustomer())
                .setCustomerContact(readService.getCustomerContact())
                .setServiceResiliency(readService.getServiceResiliency())
                .setHardConstraints(readService.getHardConstraints())
                .setSoftConstraints(readService.getSoftConstraints())
                .setSdncRequestHeader(readService.getSdncRequestHeader())
                .setLifecycleState(readService.getLifecycleState())
                .setServiceAEnd(readService.getServiceAEnd())
                .setServiceZEnd(readService.getServiceZEnd())
                .build();
            WriteTransaction writeTx = this.dataBroker.newWriteOnlyTransaction();
            writeTx.put(LogicalDatastoreType.OPERATIONAL, iid, service);
            writeTx.commit().get(Timeouts.DATASTORE_WRITE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            LOG.warn("Failed to rename service: {}", input.getServiceName());
            return RpcResultBuilder.<RenameServiceOutput>failed()
                .withError(ErrorType.RPC, "Failed to rename service")
                .buildFuture();
        }
        OperationResult modifyServiceOperationResult = this.serviceDataStoreOperations.modifyService(
            input.getNewServiceName(), readService.getOperationalState(), readService.getAdministrativeState());
        if (!modifyServiceOperationResult.isSuccess()) {
            LOG.warn("Failed to update renamed service: {}", input.getNewServiceName());
            return RpcResultBuilder.<RenameServiceOutput>failed()
                .withError(ErrorType.RPC, "Failed update renamed service")
                .buildFuture();
        }
        try {
            InstanceIdentifier<ServicePaths> servicePathsIID = InstanceIdentifier.create(ServicePathList.class)
                .child(ServicePaths.class, new ServicePathsKey(input.getNewServiceName()));
            ServicePaths servicePath = new ServicePathsBuilder()
                .setServiceAEnd(readServicePath.getServiceAEnd())
                .setServiceZEnd(readServicePath.getServiceZEnd())
                .setServicePathName(input.getNewServiceName())
                .setServiceHandlerHeader(readServicePath.getServiceHandlerHeader())
                .setHardConstraints(readServicePath.getHardConstraints())
                .setSoftConstraints(readServicePath.getSoftConstraints())
                .setPathDescription(readServicePath.getPathDescription())
                .build();
            WriteTransaction writeTx = this.dataBroker.newWriteOnlyTransaction();
            writeTx.put(LogicalDatastoreType.OPERATIONAL, servicePathsIID, servicePath);
            writeTx.commit().get(Timeouts.DATASTORE_WRITE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            LOG.warn("Failed to rename service path: {}", input.getServiceName());
            return RpcResultBuilder.<RenameServiceOutput>failed()
                .withError(ErrorType.RPC, "Failed to rename service path")
                .buildFuture();
        }
        OperationResult deleteServiceOperationResult = this.serviceDataStoreOperations
            .deleteService(input.getServiceName());
        if (!deleteServiceOperationResult.isSuccess()) {
            LOG.warn("Failed to delete renamed service: {}", input.getServiceName());
            return RpcResultBuilder.<RenameServiceOutput>failed()
                .withError(ErrorType.RPC, "Failed to delete old service")
                .buildFuture();
        }
        OperationResult deleteServicePathOperationResult = this.serviceDataStoreOperations
            .deleteServicePath(input.getServiceName());
        if (!deleteServicePathOperationResult.isSuccess()) {
            LOG.warn("Failed to delete renamed service path: {}", input.getServiceName());
            return RpcResultBuilder.<RenameServiceOutput>failed()
                .withError(ErrorType.RPC, "Failed to delete old service path")
                .buildFuture();
        }
        LOG.info("Successfully renamed service {} to {}", input.getServiceName(), input.getNewServiceName());
        return RpcResultBuilder
            .success(new RenameServiceOutputBuilder()
                .setResult("Service renamed successfully")
                .build())
            .buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<ChangeRequestIdOutput>> changeRequestId(ChangeRequestIdInput input) {
        LOG.info("RPC change request-id called for {}", input.getServiceName());
        Optional<Services> readServiceOpt = this.serviceDataStoreOperations.getService(input.getServiceName());
        Optional<ServicePaths> readServicePathOpt = this.serviceDataStoreOperations
            .getServicePath(input.getServiceName());
        if (!readServiceOpt.isPresent() || !readServicePathOpt.isPresent()) {
            LOG.warn("changeRequestId: {} not found", input.getServiceName());
            return RpcResultBuilder.<ChangeRequestIdOutput>failed()
                .withError(ErrorType.RPC, "Service not found")
                .buildFuture();
        }
        Services readService = readServiceOpt.get();
        ServicePaths readServicePath = readServicePathOpt.get();
        try {
            InstanceIdentifier<Services> iid = InstanceIdentifier.create(ServiceList.class)
                .child(Services.class, new ServicesKey(input.getServiceName()));
            Services service = new ServicesBuilder(readService)
                .setSdncRequestHeader(new SdncRequestHeaderBuilder(readService.getSdncRequestHeader())
                    .setRequestId(input.getNewRequestId()).build())
                .build();
            WriteTransaction writeTx = this.dataBroker.newWriteOnlyTransaction();
            writeTx.merge(LogicalDatastoreType.OPERATIONAL, iid, service);
            writeTx.commit().get(Timeouts.DATASTORE_WRITE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            LOG.warn("Failed to change request-id of service: {}", input.getServiceName());
            return RpcResultBuilder.<ChangeRequestIdOutput>failed()
                .withError(ErrorType.RPC, "Failed to change request-id of service")
                .buildFuture();
        }
        try {
            InstanceIdentifier<ServicePaths> servicePathsIID = InstanceIdentifier.create(ServicePathList.class)
                .child(ServicePaths.class, new ServicePathsKey(input.getServiceName()));
            ServicePaths servicePath = new ServicePathsBuilder(readServicePath)
                .setServiceHandlerHeader(new ServiceHandlerHeaderBuilder(readServicePath.getServiceHandlerHeader())
                    .setRequestId(input.getNewRequestId()).build())
                .build();
            WriteTransaction writeTx = this.dataBroker.newWriteOnlyTransaction();
            writeTx.merge(LogicalDatastoreType.OPERATIONAL, servicePathsIID, servicePath);
            writeTx.commit().get(Timeouts.DATASTORE_WRITE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            LOG.warn("Failed to change request-id of service path: {}", input.getServiceName());
            return RpcResultBuilder.<ChangeRequestIdOutput>failed()
                .withError(ErrorType.RPC, "Failed to change request-id of service path")
                .buildFuture();
        }
        LOG.info("Successfully changed request-id for service {} to {}",
            input.getServiceName(), input.getNewRequestId());
        return RpcResultBuilder
            .success(new ChangeRequestIdOutputBuilder()
                .setResult("Changed request-id successfully")
                .build())
            .buildFuture();
    }
}
