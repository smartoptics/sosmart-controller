/*
 * Copyright © 2017 Orange, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.common.device;

import static org.mockito.Matchers.any;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPoint;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkBuilder;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


@RunWith(MockitoJUnitRunner.class)
public class DeviceTransactionManagerTest {

    @Mock private MountPointService mountPointServiceMock;
    @Mock private MountPoint mountPointMock;
    @Mock private DataBroker dataBrokerMock;
    @Mock private ReadWriteTransaction rwTransactionMock;

    private DeviceTransactionManagerImpl transactionManager;
    private String defaultDeviceId = "device-id";
    private LogicalDatastoreType defaultDatastore = LogicalDatastoreType.OPERATIONAL;
    private InstanceIdentifier<Network> defaultIid = InstanceIdentifier.create(Network.class);
    private Network defaultData = new NetworkBuilder().build();
    private long defaultTimeout = 1000;
    private TimeUnit defaultTimeUnit = TimeUnit.MILLISECONDS;

    @Before
    public void before() {
        Mockito.when(mountPointServiceMock.getMountPoint(any())).thenReturn(Optional.of(mountPointMock));
        Mockito.when(mountPointMock.getService(any())).thenReturn(Optional.of(dataBrokerMock));
        Mockito.when(dataBrokerMock.newReadWriteTransaction()).thenReturn(rwTransactionMock);
        Mockito.doReturn(FluentFutures.immediateNullFluentFuture()).when(rwTransactionMock.submit());

        this.transactionManager = new DeviceTransactionManagerImpl(mountPointServiceMock, 3000);
    }

    @After
    public void after() {
        transactionManager.preDestroy();
    }

    @Test
    public void basicPositiveTransactionTest() {
        try {
            putAndSubmit(transactionManager, defaultDeviceId, defaultDatastore, defaultIid, defaultData);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
            return;
        }

        Mockito.verify(rwTransactionMock, Mockito.times(1)).put(defaultDatastore, defaultIid, defaultData);
        Mockito.verify(rwTransactionMock, Mockito.times(1)).submit();
    }

    @Test
    public void advancedPositiveTransactionTest() {
        try {
            Future<java.util.Optional<DeviceTransaction>> firstDeviceTxFuture =
                    transactionManager.getDeviceTransaction(defaultDeviceId);
            DeviceTransaction firstDeviceTx = firstDeviceTxFuture.get().get();

            Future<java.util.Optional<DeviceTransaction>> secondDeviceTxFuture =
                    transactionManager.getDeviceTransaction(defaultDeviceId);
            Assert.assertFalse(secondDeviceTxFuture.isDone());

            Future<java.util.Optional<DeviceTransaction>> thirdDeviceTxFuture =
                    transactionManager.getDeviceTransaction(defaultDeviceId);
            Assert.assertFalse(thirdDeviceTxFuture.isDone());

            firstDeviceTx.put(defaultDatastore, defaultIid, defaultData);
            Assert.assertFalse(secondDeviceTxFuture.isDone());
            Assert.assertFalse(thirdDeviceTxFuture.isDone());
            Thread.sleep(200);
            Assert.assertFalse(secondDeviceTxFuture.isDone());
            Assert.assertFalse(thirdDeviceTxFuture.isDone());

            Future<java.util.Optional<DeviceTransaction>> anotherDeviceTxFuture =
                    transactionManager.getDeviceTransaction("another-id");
            Assert.assertTrue(anotherDeviceTxFuture.isDone());
            anotherDeviceTxFuture.get().get().submit(defaultTimeout, defaultTimeUnit);

            firstDeviceTx.submit(defaultTimeout, defaultTimeUnit);
            Thread.sleep(200);
            Assert.assertTrue(secondDeviceTxFuture.isDone());
            Assert.assertFalse(thirdDeviceTxFuture.isDone());

            DeviceTransaction secondDeviceTx = secondDeviceTxFuture.get().get();
            secondDeviceTx.put(defaultDatastore, defaultIid, defaultData);
            Assert.assertFalse(thirdDeviceTxFuture.isDone());

            secondDeviceTx.submit(defaultTimeout, defaultTimeUnit);
            Thread.sleep(200);
            Assert.assertTrue(thirdDeviceTxFuture.isDone());

            DeviceTransaction thirdDeviceTx = thirdDeviceTxFuture.get().get();
            thirdDeviceTx.put(defaultDatastore, defaultIid, defaultData);
            thirdDeviceTx.submit(defaultTimeout, defaultTimeUnit);

            Mockito.verify(rwTransactionMock, Mockito.times(3)).put(defaultDatastore, defaultIid, defaultData);
            Mockito.verify(rwTransactionMock, Mockito.times(4)).submit();
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
        }
    }

    @Test
    public void bigAmountOfTransactionsOnSameDeviceTest() {
        int numberOfTxs = 100;
        List<Future<java.util.Optional<DeviceTransaction>>> deviceTransactionFutures = new LinkedList<>();
        List<DeviceTransaction> deviceTransactions = new LinkedList<>();

        for (int i = 0; i < numberOfTxs; i++) {
            deviceTransactionFutures.add(transactionManager.getDeviceTransaction(defaultDeviceId));
        }

        try {
            for (Future<java.util.Optional<DeviceTransaction>> futureTx : deviceTransactionFutures) {
                DeviceTransaction deviceTx = futureTx.get().get();
                deviceTx.submit(defaultTimeout, defaultTimeUnit);
                deviceTransactions.add(deviceTx);
            }
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
        }

        for (DeviceTransaction deviceTx : deviceTransactions) {
            Assert.assertTrue(deviceTx.wasSubmittedOrCancelled().get());
        }
    }

    @Test
    public void bigAmountOfTransactionsOnDifferentDevicesTest() {
        int numberOfTxs = 1000;
        List<DeviceTransaction> deviceTransactions = new LinkedList<>();

        try {
            for (int i = 0; i < numberOfTxs; i++) {
                deviceTransactions.add(transactionManager.getDeviceTransaction(defaultDeviceId + " " + i).get().get());
            }
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
        }

        deviceTransactions.parallelStream()
                .forEach(deviceTransaction -> deviceTransaction.submit(defaultTimeout, defaultTimeUnit));

        deviceTransactions.parallelStream()
                .forEach(deviceTransaction -> Assert.assertTrue(deviceTransaction.wasSubmittedOrCancelled().get()));
    }

    @Test
    public void bigAmountOfTransactionsOnDifferentDevicesWithoutSubmitTest() {
        int numberOfTxs = 1000;
        List<DeviceTransaction> deviceTransactions = new LinkedList<>();

        try {
            for (int i = 0; i < numberOfTxs; i++) {
                deviceTransactions.add(transactionManager.getDeviceTransaction(defaultDeviceId + " " + i).get().get());
            }
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
        }

        try {
            Thread.sleep(transactionManager.getMaxDurationToSubmitTransaction() + 1000);
        } catch (InterruptedException e) {
            Assert.fail("Exception catched! " + e);
        }
        deviceTransactions.parallelStream()
                .forEach(deviceTransaction -> Assert.assertTrue(deviceTransaction.wasSubmittedOrCancelled().get()));
    }

    @Test
    public void notSubmittedTransactionTest() {
        Future<java.util.Optional<DeviceTransaction>> deviceTxFuture =
                transactionManager.getDeviceTransaction(defaultDeviceId);
        try {
            deviceTxFuture.get();
            Thread.sleep(transactionManager.getMaxDurationToSubmitTransaction() + 1000);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
        }
        Mockito.verify(rwTransactionMock, Mockito.times(1)).cancel();

        try {
            putAndSubmit(transactionManager, defaultDeviceId, defaultDatastore, defaultIid, defaultData);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
            return;
        }

        Mockito.verify(rwTransactionMock, Mockito.times(1)).cancel();
        Mockito.verify(rwTransactionMock, Mockito.times(1)).put(defaultDatastore, defaultIid, defaultData);
        Mockito.verify(rwTransactionMock, Mockito.times(1)).submit();
    }

    @Test
    public void dataBrokerTimeoutTransactionTest() {
        Mockito.when(dataBrokerMock.newReadWriteTransaction()).then(invocation -> {
            Thread.sleep(transactionManager.getMaxDurationToSubmitTransaction() + 1000);
            return rwTransactionMock;
        });

        try {
            putAndSubmit(transactionManager, defaultDeviceId, defaultDatastore, defaultIid, defaultData);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
        }

        Mockito.verify(rwTransactionMock, Mockito.times(1)).submit();

        Mockito.when(dataBrokerMock.newReadWriteTransaction()).thenReturn(rwTransactionMock); // remove sleep

        try {
            putAndSubmit(transactionManager, defaultDeviceId, defaultDatastore, defaultIid, defaultData);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
            return;
        }

        Mockito.verify(rwTransactionMock, Mockito.times(2)).put(defaultDatastore, defaultIid, defaultData);
        Mockito.verify(rwTransactionMock, Mockito.times(2)).submit();
    }

    @Test
    public void getFutureTimeoutTransactionTest() {
        Mockito.when(dataBrokerMock.newReadWriteTransaction()).then(invocation -> {
            Thread.sleep(3000);
            return rwTransactionMock;
        });

        Exception throwedException = null;

        Future<java.util.Optional<DeviceTransaction>> deviceTxFuture =
                transactionManager.getDeviceTransaction(defaultDeviceId);
        try {
            deviceTxFuture.get(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
        } catch (TimeoutException e) {
            throwedException = e;
        }

        if (throwedException == null) {
            Assert.fail("TimeoutException should be thrown!");
            return;
        }

        Mockito.when(dataBrokerMock.newReadWriteTransaction()).thenReturn(rwTransactionMock); // remove sleep

        try {
            putAndSubmit(transactionManager, defaultDeviceId, defaultDatastore, defaultIid, defaultData);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
            return;
        }

        Mockito.verify(rwTransactionMock, Mockito.times(1)).put(defaultDatastore, defaultIid, defaultData);
        Mockito.verify(rwTransactionMock, Mockito.times(1)).submit();
    }

    @Test
    public void submitTxTimeoutTransactionTest() {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        Mockito.when(rwTransactionMock.submit()).then(invocation -> Futures.makeChecked(executor.submit(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Assert.fail("Exception catched in future! " + e);
            }
            return null;
        }), input -> input));

        Future<java.util.Optional<DeviceTransaction>> deviceTxFuture =
                transactionManager.getDeviceTransaction(defaultDeviceId);
        DeviceTransaction deviceTx;
        try {
            deviceTx = deviceTxFuture.get().get();
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
            return;
        }

        deviceTx.put(defaultDatastore, defaultIid, defaultData);

        Exception throwedException = null;

        ListenableFuture<Void> submitFuture = deviceTx.submit(200, defaultTimeUnit);
        try {
            submitFuture.get();
        } catch (InterruptedException e) {
            Assert.fail("Exception catched! " + e);
        } catch (ExecutionException e) {
            throwedException = e;
        }

        if (throwedException == null
                || !throwedException.getMessage().contains(TimeoutException.class.getName())) {
            Assert.fail("TimeoutException inside of should be thrown!");
            return;
        }


        Mockito.doReturn(FluentFutures.immediateNullFluentFuture()).when(rwTransactionMock.submit());

        try {
            putAndSubmit(transactionManager, defaultDeviceId, defaultDatastore, defaultIid, defaultData);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail("Exception catched! " + e);
            return;
        }

        Mockito.verify(rwTransactionMock, Mockito.times(2)).put(defaultDatastore, defaultIid, defaultData);
        Mockito.verify(rwTransactionMock, Mockito.times(2)).submit();

        executor.shutdown();
    }

    private <T extends DataObject> void putAndSubmit(DeviceTransactionManagerImpl deviceTxManager, String deviceId,
            LogicalDatastoreType store, InstanceIdentifier<T> path, T data)
            throws ExecutionException, InterruptedException {
        Future<java.util.Optional<DeviceTransaction>> deviceTxFuture = deviceTxManager.getDeviceTransaction(deviceId);
        DeviceTransaction deviceTx = deviceTxFuture.get().get();
        deviceTx.put(store, path, data);
        deviceTx.submit(defaultTimeout, defaultTimeUnit);
    }
}
