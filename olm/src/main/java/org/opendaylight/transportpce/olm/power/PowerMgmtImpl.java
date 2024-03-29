/*
 * Copyright © 2017 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.olm.power;
import java.math.BigDecimal;
// import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.transportpce.common.Timeouts;
import org.opendaylight.transportpce.common.crossconnect.CrossConnect;
import org.opendaylight.transportpce.common.device.DeviceTransactionManager;
import org.opendaylight.transportpce.common.fixedflex.GridConstant;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfaceException;
import org.opendaylight.transportpce.common.openroadminterfaces.OpenRoadmInterfaces;
import org.opendaylight.transportpce.olm.util.OlmUtils;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.olm.rev210618.ServicePowerSetupInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.olm.rev210618.ServicePowerTurndownInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.OpenroadmNodeVersion;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.cp.to.degree.CpToDegree;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.cp.to.degree.CpToDegreeKey;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.mapping.Mapping;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.mapping.MappingKey;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.portmapping.rev220316.network.Nodes;
import org.opendaylight.yang.gen.v1.http.org.openroadm.common.types.rev161014.OpticalControlMode;
import org.opendaylight.yang.gen.v1.http.org.openroadm.common.types.rev181019.LineAmplifierControlMode;
import org.opendaylight.yang.gen.v1.http.org.openroadm.device.rev170206.interfaces.grp.Interface;
import org.opendaylight.yang.gen.v1.http.org.openroadm.optical.transport.interfaces.rev161014.Interface1;
import org.opendaylight.yang.gen.v1.http.smartoptics.com.ns.com.smartoptics.roadm.rev231124.ComSmartopticsDevice;
import org.opendaylight.yang.gen.v1.http.smartoptics.com.ns.com.smartoptics.roadm.rev231124.com.smartoptics.device.Degree;
import org.opendaylight.yang.gen.v1.http.smartoptics.com.ns.com.smartoptics.roadm.rev231124.com.smartoptics.device.DegreeKey;
import org.opendaylight.yang.gen.v1.http.smartoptics.com.ns.com.smartoptics.roadm.rev231124.com.smartoptics.device.degree.Booster;
import org.opendaylight.yang.gen.v1.http.smartoptics.com.ns.com.smartoptics.roadm.rev231124.com.smartoptics.device.degree.booster.Channel;
import org.opendaylight.yang.gen.v1.http.smartoptics.com.ns.com.smartoptics.roadm.rev231124.com.smartoptics.device.degree.booster.ChannelKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowerMgmtImpl implements PowerMgmt {
    private static final Logger LOG = LoggerFactory.getLogger(PowerMgmtImpl.class);
    private final DataBroker db;
    private final OpenRoadmInterfaces openRoadmInterfaces;
    private final CrossConnect crossConnect;
    private final DeviceTransactionManager deviceTransactionManager;
    private static final BigDecimal DEFAULT_TPDR_PWR_100G = new BigDecimal(-5);
    private static final BigDecimal DEFAULT_TPDR_PWR_400G = new BigDecimal(0);
    private static final String INTERFACE_NOT_PRESENT = "Interface {} on node {} is not present!";
    private static final double MC_WIDTH_GRAN = 2 * GridConstant.GRANULARITY;

    private long timer1 = 35000;
    // openroadm spec value is 120000, functest value is 3000
    private long timer2 = 5000;
    // openroadm spec value is 20000, functest value is 2000
    private long timerIla = 2000;

    public PowerMgmtImpl(DataBroker db, OpenRoadmInterfaces openRoadmInterfaces,
                         CrossConnect crossConnect, DeviceTransactionManager deviceTransactionManager) {
        this.db = db;
        this.openRoadmInterfaces = openRoadmInterfaces;
        this.crossConnect = crossConnect;
        this.deviceTransactionManager = deviceTransactionManager;
    }

    public PowerMgmtImpl(DataBroker db, OpenRoadmInterfaces openRoadmInterfaces,
                         CrossConnect crossConnect, DeviceTransactionManager deviceTransactionManager,
                         String timer1, String timer2) {
        this.db = db;
        this.openRoadmInterfaces = openRoadmInterfaces;
        this.crossConnect = crossConnect;
        this.deviceTransactionManager = deviceTransactionManager;
        try {
            this.timer1 = Long.parseLong(timer1);
        } catch (NumberFormatException e) {
            this.timer1 = 35000;
            LOG.warn("Failed to retrieve Olm timer1 value from configuration - using default value {}",
                this.timer1, e);
        }
        try {
            this.timer2 = Long.parseLong(timer2);
        } catch (NumberFormatException e) {
            this.timer2 = 5000;
            LOG.warn("Failed to retrieve Olm timer2 value from configuration - using default value {}",
                this.timer2, e);
        }
    }

    /**
     * This methods measures power requirement for turning up a WL
     * from the Spanloss at OTS transmit direction and update
     * roadm-connection target-output-power.
     *
     * @param input
     *            Input parameter from the olm servicePowerSetup rpc
     *
     * @return true/false based on status of operation.
     */
    //TODO Need to Case Optical Power mode/NodeType in case of 2.2 devices
    public Boolean setPower(ServicePowerSetupInput input) {
        LOG.info("Olm-setPower initiated for input {}", input);
        String spectralSlotName = String.join(GridConstant.SPECTRAL_SLOT_SEPARATOR,
                input.getLowerSpectralSlotNumber().toString(),
                input.getHigherSpectralSlotNumber().toString());
        for (int i = 0; i < input.getNodes().size(); i++) {
            String nodeId = input.getNodes().get(i).getNodeId();
            String srcTpId = input.getNodes().get(i).getSrcTp();
            String destTpId = input.getNodes().get(i).getDestTp();
            Optional<Nodes> inputNodeOptional = OlmUtils.getNode(nodeId, this.db);
            if (inputNodeOptional.isEmpty()
                    || inputNodeOptional.get().getNodeInfo().getNodeType() == null) {
                LOG.error("OLM-PowerMgmtImpl : Error node type cannot be retrieved for node {}", nodeId);
                return false;
            }
            Nodes inputNode = inputNodeOptional.get();
            OpenroadmNodeVersion openroadmVersion = inputNode.getNodeInfo().getOpenroadmVersion();

            switch (inputNode.getNodeInfo().getNodeType()) {
                case Xpdr:
                    if (destTpId == null) {
                        continue;
                    }
                    LOG.info("Getting data from input node {}", inputNode.getNodeInfo().getNodeType());
                    LOG.info("Getting mapping data for node is {}",
                        inputNode.nonnullMapping().values().stream().filter(o -> o.key()
                         .equals(new MappingKey(destTpId))).findFirst().toString());
                    // If its not A-End transponder
                    if (!destTpId.toUpperCase(Locale.getDefault()).contains("NETWORK")) {
                        LOG.info("{} is a drop node. Net power settings needed", nodeId);
                        continue;
                    }

                    BigDecimal powerVal = getXpdrPowerValue(
                            inputNode, destTpId, nodeId, openroadmVersion.getIntValue(),
                            input.getNodes().get(i + 1).getSrcTp(), input.getNodes().get(i + 1).getNodeId());
                    if (powerVal == null) {
                        return false;
                    }

                    String interfaceName = String.join(GridConstant.NAME_PARAMETERS_SEPARATOR,
                        destTpId, spectralSlotName);
                    if (!callSetTransponderPower(nodeId, interfaceName, powerVal, openroadmVersion)) {
                        LOG.info("Transponder OCH connection: {} power update failed ", interfaceName);
                        continue;
                    }
                    LOG.info("Transponder OCH connection: {} power updated ", interfaceName);
                    try {
                        LOG.info("Now going in sleep mode");
                        Thread.sleep(timer1);
                    } catch (InterruptedException e) {
                        LOG.info("Transponder warmup failed for OCH connection: {}", interfaceName, e);
                        // FIXME shouldn't it be LOG.warn  or LOG.error?
                        // or maybe this try/catch block can simply be removed
                    }
                    break;
                case Rdm:
                    LOG.info("This is a roadm {} device", openroadmVersion.getName());
                    String connectionNumber = String.join(GridConstant.NAME_PARAMETERS_SEPARATOR,
                            input.getNodes().get(i).getSrcTp(), destTpId, spectralSlotName);
                    LOG.info("Connection number is {}", connectionNumber);

                    // If Drop node leave node is power mode
                    if (destTpId.toUpperCase(Locale.getDefault()).contains("SRG")) {
                        LOG.info("Setting power at drop node");
                        crossConnect.setPowerLevel(nodeId, OpticalControlMode.Power.getName(), null, connectionNumber);
                        continue;
                    }
                    if (!destTpId.toUpperCase(Locale.getDefault()).contains("DEG")) {
                        continue;
                    }
                    // If Degree is transmitting end then set power
                    Optional<Mapping> mappingObjectOptional = inputNode.nonnullMapping()
                            .values().stream().filter(o -> o.key()
                            .equals(new MappingKey(destTpId))).findFirst();
                    if (mappingObjectOptional.isEmpty()) {
                        continue;
                    }
                    // TODO can it be return false rather than continue?
                    // in that case, mappingObjectOptional could be moved inside method getSpanLossTx()
                    LOG.info("Dest point is Degree {}", mappingObjectOptional.get());
                    BigDecimal spanLossTx = getSpanLossTx(mappingObjectOptional.get().getSupportingOts(),
                        destTpId, nodeId, openroadmVersion.getIntValue());

                    LOG.info("Spanloss TX is {}", spanLossTx);
                    // TODO: The span-loss limits should be obtained from optical specifications
                    if (spanLossTx == null || spanLossTx.intValue() < 0) {
                        LOG.error("spanLossTx is null or negative {}",
                            spanLossTx);
                        return false;
                    }
                    Decimal64 powerValue = Decimal64.valueOf(getRdmPowerValue(spanLossTx, input));
                    try {
                        if (!crossConnect.setPowerLevel(nodeId, OpticalControlMode.Power.getName(), powerValue,
                                connectionNumber)) {
                            LOG.error("Set Power failed for Roadm-connection: {} on Node: {}",
                                    connectionNumber, nodeId);
                            // FIXME shouldn't it be LOG.error
                            return false;
                        }
                        LOG.info("Roadm-connection: {} updated ", connectionNumber);
                        Thread.sleep(timer1);
                        // TODO make this timer value configurable via OSGi blueprint
                        // although the value recommended by the white paper is 20 seconds.
                        // At least one vendor product needs 60 seconds
                        // because it is not supporting GainLoss with target-output-power.

                        // If source is SRG check if signal is detected
                        if (srcTpId.toUpperCase(Locale.getDefault()).contains("SRG") && input.getCenterFreq() != null) {
                            String cpName = mappingObjectOptional.get().getSupportingCircuitPackName();
                            Optional<CpToDegree> cpToDegreeObjectOptional = inputNode.nonnullCpToDegree()
                                .values().stream().filter(o -> o.key()
                                .equals(new CpToDegreeKey(cpName))).findFirst();
                            if (cpToDegreeObjectOptional.isPresent()) {
                                Uint8 degreeNumber = Uint8.valueOf(cpToDegreeObjectOptional.get().getDegreeNumber());
                                BigDecimal centerFrequencyTHz = input.getCenterFreq().getValue().decimalValue();
                                Uint64 centerFrequencyMHz = Uint64.valueOf(
                                    centerFrequencyTHz.multiply(BigDecimal.valueOf(1e6)).intValue());
                                InstanceIdentifier<Channel> chIID = InstanceIdentifier
                                    .builder(ComSmartopticsDevice.class)
                                    .child(Degree.class, new DegreeKey(Uint32.valueOf(degreeNumber)))
                                    .child(Booster.class)
                                    .child(Channel.class, new ChannelKey(centerFrequencyMHz))
                                    .build();
                                try {
                                    Optional<Channel> channelObject =
                                        deviceTransactionManager.getDataFromDevice(
                                            nodeId, LogicalDatastoreType.OPERATIONAL,
                                            chIID, Timeouts.DEVICE_READ_TIMEOUT, Timeouts.DEVICE_READ_TIMEOUT_UNIT);
                                    if (channelObject.isPresent() && channelObject.get().getPeak() != null) {
                                        Channel channel = channelObject.get();
                                        LOG.info("Channel info at freq={} for device {}: {}",
                                            centerFrequencyMHz, nodeId, channel);
                                        if (!channel.getPeak()) {
                                            LOG.error("Signal peak not detected in {}", nodeId);
                                            return false;
                                        }
                                    } else {
                                        LOG.error("Failed to get get signal peak from device {}", nodeId);
                                    }
                                } catch (IllegalArgumentException e) {
                                    LOG.info("Device {} does not support signal peak detecion", nodeId);
                                }
                            }
                        }

                        if (!crossConnect.setPowerLevel(nodeId, OpticalControlMode.GainLoss.getName(), powerValue,
                                connectionNumber)) {
                            LOG.error("Set GainLoss failed for Roadm-connection: {} on Node: {}",
                                    connectionNumber, nodeId);
                            // FIXME no return false in that case?
                            return false;
                        }
                    } catch (InterruptedException e) {
                        LOG.error("Olm-setPower wait failed :", e);
                        return false;
                    }
                    break;
                case Ila:
                    if (!destTpId.toUpperCase(Locale.getDefault()).contains("DEG")) {
                        continue;
                    }
                    if (!srcTpId.toUpperCase(Locale.getDefault()).contains("DEG")) {
                        continue;
                    }
                    Optional<Mapping> srcMappingObjectOptional = inputNode.nonnullMapping()
                            .values().stream().filter(o -> o.key()
                            .equals(new MappingKey(srcTpId))).findFirst();
                    if (srcMappingObjectOptional.isEmpty()) {
                        continue;
                    }
                    BigDecimal spanLossRx = getSpanLossRx(srcMappingObjectOptional.get().getSupportingOts(),
                        srcTpId, nodeId, openroadmVersion.getIntValue());
                    LOG.info("Spanloss RX is {}", spanLossRx);
                    if (spanLossRx == null || spanLossRx.intValue() < 0) {
                        LOG.error("spanLossRx is null or negative {}",
                            spanLossRx);
                        return false;
                    }
                    // If Degree is transmitting end then set power
                    mappingObjectOptional = inputNode.nonnullMapping()
                            .values().stream().filter(o -> o.key()
                            .equals(new MappingKey(destTpId))).findFirst();
                    if (mappingObjectOptional.isEmpty()) {
                        continue;
                    }
                    String cpName = mappingObjectOptional.get().getSupportingCircuitPackName();
                    Optional<CpToDegree> cpToDegreeObjectOptional = inputNode.nonnullCpToDegree()
                            .values().stream().filter(o -> o.key()
                            .equals(new CpToDegreeKey(cpName))).findFirst();
                    if (cpToDegreeObjectOptional.isEmpty()) {
                        continue;
                    }
                    // TODO can it be return false rather than continue?
                    // in that case, mappingObjectOptional could be moved inside method getSpanLossTx()
                    LOG.info("Dest point is Degree {}", mappingObjectOptional.get());
                    spanLossTx = getSpanLossTx(mappingObjectOptional.get().getSupportingOts(),
                        destTpId, nodeId, openroadmVersion.getIntValue());

                    LOG.info("Spanloss TX is {}", spanLossTx);
                    // TODO: The span-loss limits should be obtained from optical specifications
                    if (spanLossTx == null || spanLossTx.intValue() < 0) {
                        LOG.error("spanLossTx is null or negative {}",
                            spanLossTx);
                        return false;
                    }
                    Uint8 ampNumber = Uint8.valueOf(cpToDegreeObjectOptional.get().getDegreeNumber());
                    // Amp parameters, TODO make dynamic
                    BigDecimal optimalFlatGain = BigDecimal.valueOf(22.0);
                    BigDecimal voaInsertionLoss = BigDecimal.valueOf(1.0);
                    BigDecimal wdmAddDropLoss = BigDecimal.valueOf(0.6);
                    BigDecimal maxGain = BigDecimal.valueOf(26.0);

                    BigDecimal optimalSpanloss = optimalFlatGain.subtract(voaInsertionLoss).subtract(wdmAddDropLoss);
                    BigDecimal targetGain = spanLossRx.max(optimalSpanloss).subtract(
                        optimalSpanloss.subtract(spanLossTx).max(BigDecimal.valueOf(0.0)));
                    targetGain = targetGain.min(maxGain);
                    LOG.info("The target gain is {} dB for spanloss Rx {} and spanloss Tx {}",
                                targetGain, spanLossRx, spanLossTx);
                    try {
                        if (!PowerMgmtVersion221.setIlaTargetGain(nodeId, ampNumber, LineAmplifierControlMode.GainLoss,
                                Decimal64.valueOf(targetGain), deviceTransactionManager)) {
                            LOG.info("Set Target Gain failed on Node: {}", nodeId);
                            return false;
                        }
                        Thread.sleep(timerIla);
                    } catch (InterruptedException e) {
                        LOG.error("Olm-targetGain wait failed :", e);
                        return false;
                    }
                    break;
                default :
                    LOG.error("OLM-PowerMgmtImpl : Error with node type for node {}", nodeId);
                    break;
            }
        }
        return true;
    }

    private Map<String, Double> getTxPowerRangeMap(Nodes inputNode, String destTpId, String nodeId,
            Integer openroadmVersion) {

        Optional<Mapping> mappingObject = inputNode.nonnullMapping().values().stream()
                .filter(o -> o.key().equals(new MappingKey(destTpId))).findFirst();
        if (mappingObject.isEmpty()) {
            LOG.info("Mapping object not found for nodeId: {}", nodeId);
            // FIXME shouldn't it be LOG.error ?
            return null;
            // return null here means return false in setPower()
            // TODO Align protections with getSRGRxPowerRangeMap
        }

        String circuitPackName = mappingObject.get().getSupportingCircuitPackName();
        String portName = mappingObject.get().getSupportingPort();
        switch (openroadmVersion) {
            case 1:
                return PowerMgmtVersion121.getXponderPowerRange(circuitPackName, portName,
                    nodeId, deviceTransactionManager);
            case 2:
                return PowerMgmtVersion221.getXponderPowerRange(circuitPackName, portName,
                    nodeId, deviceTransactionManager);
            case 3:
                return PowerMgmtVersion710.getXponderPowerRange(circuitPackName, portName,
                    nodeId, deviceTransactionManager);
            default:
                LOG.error("Unrecognized OpenRoadm version");
                return new HashMap<>();
                // FIXME shouldn't it lead to a return false in setPower()?
        }
    }


    private Map<String, Double> getSRGRxPowerRangeMap(String srgId, String nodeId, Integer openroadmVersion) {

        Optional<Nodes> inputNode = OlmUtils.getNode(nodeId, this.db);
        int rdmOpenroadmVersion =
                inputNode.isPresent()
                    ? inputNode.get().getNodeInfo().getOpenroadmVersion().getIntValue()
                    : openroadmVersion;
        Optional<Mapping> mappingObject = inputNode
                .flatMap(node -> node.nonnullMapping().values().stream()
                    .filter(o -> o.key().equals(new MappingKey(srgId))).findFirst());

        if (mappingObject.isEmpty()) {
            return new HashMap<>();
            // FIXME shouldn't it lead to a return false in setPower() ?
        }

        String circuitPackName = mappingObject.get().getSupportingCircuitPackName();
        String portName = mappingObject.get().getSupportingPort();
        switch (rdmOpenroadmVersion) {
            case 1:
                return PowerMgmtVersion121.getSRGRxPowerRange(nodeId, srgId,
                        deviceTransactionManager, circuitPackName, portName);
            case 2:
                return PowerMgmtVersion221.getSRGRxPowerRange(nodeId, srgId,
                        deviceTransactionManager, circuitPackName, portName);
            case 3:
                return PowerMgmtVersion710.getSRGRxPowerRange(nodeId, srgId,
                        deviceTransactionManager, circuitPackName, portName);
            default:
                LOG.error("Unrecognized OpenRoadm version");
                return null;
                //return null here means return false in setPower()
                // TODO Align protections with getTxPowerRangeMap
        }
    }

    private BigDecimal getSpanLossTx(String supportingOts, String destTpId, String nodeId, Integer openroadmVersion) {
        try {
            switch (openroadmVersion) {
                case 1:
                    Optional<Interface> interfaceOpt =
                        this.openRoadmInterfaces.getInterface(nodeId, supportingOts);
                    if (interfaceOpt.isEmpty()) {
                        LOG.error(INTERFACE_NOT_PRESENT, supportingOts, nodeId);
                        return null;
                    }
                    if (interfaceOpt.get().augmentation(Interface1.class).getOts()
                            .getSpanLossTransmit() == null) {
                        LOG.error("interface {} has no spanloss value", interfaceOpt.get().getName());
                        return null;
                    }
                    return interfaceOpt.get()
                            .augmentation(Interface1.class)
                            .getOts().getSpanLossTransmit().getValue().decimalValue();
                case 2:
                    Optional<org.opendaylight.yang.gen.v1.http.org.openroadm.device.rev181019
                            .interfaces.grp.Interface> interfaceOpt1 =
                        this.openRoadmInterfaces.getInterface(nodeId, supportingOts);
                    if (interfaceOpt1.isEmpty()) {
                        LOG.error(INTERFACE_NOT_PRESENT, supportingOts, nodeId);
                        return null;
                    }
                    if (interfaceOpt1.get().augmentation(org.opendaylight.yang.gen.v1.http.org
                            .openroadm.optical.transport.interfaces.rev181019.Interface1.class).getOts()
                                .getSpanLossTransmit() == null) {
                        LOG.error("interface {} has no spanloss value", interfaceOpt1.get().getName());
                        return null;
                    }
                    return interfaceOpt1.get()
                            .augmentation(org.opendaylight.yang.gen.v1.http.org
                                .openroadm.optical.transport.interfaces.rev181019.Interface1.class)
                            .getOts().getSpanLossTransmit().getValue().decimalValue();
                // TODO no case 3 ?
                default:
                    return null;
            }
        } catch (OpenRoadmInterfaceException ex) {
            LOG.error("Failed to get interface {} from node {}!",
                supportingOts, nodeId, ex);
            return null;
        } catch (IllegalArgumentException ex) {
            LOG.error("Failed to get non existing interface {} from node {}!",
                supportingOts, nodeId);
            return null;
        }
    }

    private BigDecimal getSpanLossRx(String supportingOts, String srcTpId, String nodeId, Integer openroadmVersion) {
        try {
            switch (openroadmVersion) {
                case 1:
                    Optional<Interface> interfaceOpt =
                        this.openRoadmInterfaces.getInterface(nodeId, supportingOts);
                    if (interfaceOpt.isEmpty()) {
                        LOG.error(INTERFACE_NOT_PRESENT, supportingOts, nodeId);
                        return null;
                    }
                    if (interfaceOpt.get().augmentation(Interface1.class).getOts()
                            .getSpanLossReceive() == null) {
                        LOG.error("interface {} has no spanloss value", interfaceOpt.get().getName());
                        return null;
                    }
                    return interfaceOpt.get()
                            .augmentation(Interface1.class)
                            .getOts().getSpanLossReceive().getValue().decimalValue();
                case 2:
                    Optional<org.opendaylight.yang.gen.v1.http.org.openroadm.device.rev181019
                            .interfaces.grp.Interface> interfaceOpt1 =
                        this.openRoadmInterfaces.getInterface(nodeId, supportingOts);
                    if (interfaceOpt1.isEmpty()) {
                        LOG.error(INTERFACE_NOT_PRESENT, supportingOts, nodeId);
                        return null;
                    }
                    if (interfaceOpt1.get().augmentation(org.opendaylight.yang.gen.v1.http.org
                            .openroadm.optical.transport.interfaces.rev181019.Interface1.class).getOts()
                                .getSpanLossReceive() == null) {
                        LOG.error("interface {} has no spanloss value", interfaceOpt1.get().getName());
                        return null;
                    }
                    return interfaceOpt1.get()
                            .augmentation(org.opendaylight.yang.gen.v1.http.org
                                .openroadm.optical.transport.interfaces.rev181019.Interface1.class)
                            .getOts().getSpanLossReceive().getValue().decimalValue();
                // TODO no case 3 ?
                default:
                    return null;
            }
        } catch (OpenRoadmInterfaceException ex) {
            LOG.error("Failed to get interface {} from node {}!",
                supportingOts, nodeId, ex);
            return null;
        } catch (IllegalArgumentException ex) {
            LOG.error("Failed to get non existing interface {} from node {}!",
                supportingOts, nodeId);
            return null;
        }
    }

    private BigDecimal getXpdrPowerValue(Nodes inputNode, String destTpId, String nodeId, Integer openroadmVersion,
            String srgId, String nextNodeId) {

        Map<String, Double> txPowerRangeMap = getTxPowerRangeMap(inputNode, destTpId, nodeId, openroadmVersion);
        if (txPowerRangeMap == null) {
            return null;
            // return null here means return false in setPower()
        }
        BigDecimal powerVal =
            openroadmVersion == 3 ? DEFAULT_TPDR_PWR_400G : DEFAULT_TPDR_PWR_100G;
        if (txPowerRangeMap.isEmpty()) {
            LOG.info("Tranponder range not available setting to default power for nodeId: {}", nodeId);
            return powerVal;
        }

        Map<String, Double> rxSRGPowerRangeMap = getSRGRxPowerRangeMap(srgId, nextNodeId, openroadmVersion);
        if (rxSRGPowerRangeMap == null) {
            return null;
            // return null here means return false in setPower()
            // TODO empty txPowerRangeMap + null rxSRGPowerRangeMap is allowed
            // => confirm this behavior is OK
        }
        if (rxSRGPowerRangeMap.isEmpty()) {
            LOG.info("SRG Power Range not found, setting the Transponder range to default");
            return powerVal;
        }

        powerVal = new BigDecimal(txPowerRangeMap.get("MaxTx"))
            .min(new BigDecimal(rxSRGPowerRangeMap.get("MaxRx")));
        LOG.info("Calculated Transponder Power value is {}" , powerVal);
        return powerVal;
    }


    private BigDecimal getRdmPowerValue(BigDecimal spanLossTx, ServicePowerSetupInput input) {
        // TODO: These values will be obtained from the specifications
        // power-value here refers to the Pin[50GHz]
        BigDecimal powerValue;
        powerValue = spanLossTx.subtract(BigDecimal.valueOf(18.8));
        powerValue = powerValue.min(BigDecimal.valueOf(3.2));
        /* if (spanLossTx.doubleValue()  >= 23.0) {
            powerValue = BigDecimal.valueOf(2.0);
        } else if (spanLossTx.doubleValue()  >= 8.0) {
            powerValue = BigDecimal.valueOf(- (8.0 - spanLossTx.doubleValue()) / 3.0 - 3.0);
        } else if (spanLossTx.doubleValue() >= 6.0) {
            powerValue = BigDecimal.valueOf(-3.0);
        } else {
            powerValue = spanLossTx.subtract(BigDecimal.valueOf(9));
        }
        BigDecimal mcWidth = new BigDecimal(50);
        // we work at constant power spectral density (50 GHz channel width @-20dBm=37.5GHz)
        // 87.5 GHz channel width @-20dBm=75GHz
        if (input.getMcWidth() != null) {
            // Units of MC-width are in GHz, meaning it should be 40/50/87.5GHz
            // TODO: Should we validate this units before proceeding?
            LOG.debug("Input Grid size is {}", input.getMcWidth().getValue());

            // We round-off the mc-width to the nearest grid-value based on the granularity of 12.5 GHz
            double nbrMcSlots = Math.ceil(input.getMcWidth().getValue().doubleValue() / MC_WIDTH_GRAN);
            LOG.debug("Nearest (ceil) number of slots {}", nbrMcSlots);
            mcWidth = new BigDecimal(MC_WIDTH_GRAN * nbrMcSlots);
            LOG.debug("Given mc-width={}, Rounded mc-width={}", input.getMcWidth().getValue(), mcWidth);

            BigDecimal logVal = mcWidth.divide(new BigDecimal(50));
            double pdsVal = 10 * Math.log10(logVal.doubleValue());
            // Addition of PSD value will give Pin[87.5 GHz]
            powerValue = powerValue.add(new BigDecimal(pdsVal, new MathContext(3, RoundingMode.HALF_EVEN)));
        } */
        // FIXME compliancy with OpenROADM MSA and approximations used -- should be addressed with powermask update
        // cf JIRA ticket https://jira.opendaylight.org/browse/TRNSPRTPCE-494
        powerValue = powerValue.setScale(2, RoundingMode.CEILING);
        // target-output-power yang precision is 2, so we limit here to 2
        // LOG.info("The power value is P1[{}GHz]={} dB for spanloss {}", mcWidth, powerValue, spanLossTx);
        LOG.info("The power value is P1={} dB for spanloss {}", powerValue, spanLossTx);
        return powerValue;
    }

    /**
     * This methods turns down power a WL by performing
     * following steps:
     *
     * <p>
     * 1. Pull interfaces used in service and change
     * status to outOfService
     *
     * <p>
     * 2. For each of the ROADM node set target-output-power
     * to -60dbm, wait for 20 seconds, turn power mode to off
     *
     * <p>
     * 3. Turn down power in Z to A direction and A to Z
     *
     * @param input
     *            Input parameter from the olm servicePowerTurndown rpc
     *
     * @return true/false based on status of operation
     */
    public Boolean powerTurnDown(ServicePowerTurndownInput input) {
        LOG.info("Olm-powerTurnDown initiated for input {}", input);
        /*Starting with last element into the list Z -> A for
          turning down A -> Z */
        String spectralSlotName = String.join(GridConstant.SPECTRAL_SLOT_SEPARATOR,
                input.getLowerSpectralSlotNumber().toString(),
                input.getHigherSpectralSlotNumber().toString());
        Boolean success = true;
        for (int i = input.getNodes().size() - 1; i >= 0; i--) {
            String nodeId = input.getNodes().get(i).getNodeId();
            Optional<Nodes> inputNodeOptional = OlmUtils.getNode(nodeId, this.db);
            if (!inputNodeOptional.isEmpty()
                    && inputNodeOptional.get().getNodeInfo().getNodeType().getIntValue() == 3) {
                LOG.info("Node is ILA, nothing to do");
                continue;
            }
            String destTpId = input.getNodes().get(i).getDestTp();
            String connectionNumber =  String.join(GridConstant.NAME_PARAMETERS_SEPARATOR,
                    input.getNodes().get(i).getSrcTp(), destTpId, spectralSlotName);
            try {
                if (destTpId.toUpperCase(Locale.getDefault()).contains("DEG")) {
                    if (!crossConnect.setPowerLevel(nodeId, OpticalControlMode.Power.getName(),
                            Decimal64.valueOf("-60"), connectionNumber)) {
                        LOG.warn("Power down failed for Roadm-connection: {}", connectionNumber);
                        success = false;
                        continue;
                    }
                    Thread.sleep(timer2);
                    if (!crossConnect.setPowerLevel(nodeId, OpticalControlMode.Off.getName(), null, connectionNumber)) {
                        LOG.warn("Setting power-control mode off failed for Roadm-connection: {}", connectionNumber);
                        success = false;
                    }
                } else if (destTpId.toUpperCase(Locale.getDefault()).contains("SRG")) {
                    if (!crossConnect.setPowerLevel(nodeId, OpticalControlMode.Off.getName(), null, connectionNumber)) {
                        LOG.warn("Setting power-control mode off failed for Roadm-connection: {}", connectionNumber);
                        // FIXME a return false would allow sync with DEG case but makes current Unit tests fail
                    }
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                LOG.error("Olm-powerTurnDown wait failed: ",e);
                success = false;
            }
        }
        return success;
    }

    /**
     * This method retrieves transponder OCH interface and
     * sets power.
     *
     * @param nodeId
     *            Unique identifier for the mounted netconf- node
     * @param interfaceName
     *            OCH interface name carrying WL
     * @param txPower
     *            Calculated transmit power
     * @param openroadmVersion
     *            Version of openRoadm device software
     * @return true/false based on status of operation
     */
    private boolean callSetTransponderPower(String nodeId, String interfaceName, BigDecimal txPower,
                                            OpenroadmNodeVersion openroadmVersion) {

        boolean powerSetupResult = false;
        try {
            switch (openroadmVersion.getIntValue()) {
                case 1:
                    Optional<Interface> interfaceOptional121 =
                        openRoadmInterfaces.getInterface(nodeId, interfaceName);
                    if (interfaceOptional121.isEmpty()) {
                        LOG.error(INTERFACE_NOT_PRESENT, interfaceName, nodeId);
                        return false;
                    }
                    powerSetupResult = PowerMgmtVersion121.setTransponderPower(nodeId, interfaceName,
                            txPower, deviceTransactionManager, interfaceOptional121.get());
                    break;
                case 2:
                    Optional<org.opendaylight.yang.gen.v1.http.org.openroadm.device.rev181019.interfaces.grp
                            .Interface> interfaceOptional221 =
                        openRoadmInterfaces.getInterface(nodeId, interfaceName);
                    if (interfaceOptional221.isEmpty()) {
                        LOG.error(INTERFACE_NOT_PRESENT, interfaceName, nodeId);
                        return false;
                    }
                    powerSetupResult = PowerMgmtVersion221.setTransponderPower(nodeId, interfaceName,
                            txPower, deviceTransactionManager, interfaceOptional221.get());
                    break;
                case 3:
                    Optional<org.opendaylight.yang.gen.v1.http.org.openroadm.device.rev200529.interfaces.grp
                            .Interface> interfaceOptional710 =
                        openRoadmInterfaces.getInterface(nodeId, interfaceName);
                    if (interfaceOptional710.isEmpty()) {
                        LOG.error(INTERFACE_NOT_PRESENT, interfaceName, nodeId);
                        return false;
                    }
                    powerSetupResult = PowerMgmtVersion710.setTransponderPower(nodeId, interfaceName,
                            txPower, deviceTransactionManager, interfaceOptional710.get());
                    break;
                default:
                    LOG.error("Unrecognized OpenRoadm version");
                    return false;
            }
        } catch (OpenRoadmInterfaceException ex) {
            LOG.error("Failed to get interface {} from node {}!", interfaceName, nodeId, ex);
            return false;
        }
        if (!powerSetupResult) {
            LOG.debug("Transponder power setup failed for nodeId {} on interface {}",
                    nodeId, interfaceName);
            return false;
        }
        LOG.debug("Transponder power set up completed successfully for nodeId {} and interface {}",
                nodeId,interfaceName);
        return true;
    }

}
