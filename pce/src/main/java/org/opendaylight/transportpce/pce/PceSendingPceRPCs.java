/*
 * Copyright © 2017 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.pce;

import org.opendaylight.transportpce.common.mapping.PortMapping;
import org.opendaylight.transportpce.common.network.NetworkTransactionService;
import org.opendaylight.transportpce.pce.constraints.PceConstraints;
import org.opendaylight.transportpce.pce.constraints.PceConstraintsCalc;
import org.opendaylight.transportpce.pce.graph.PceGraph;
import org.opendaylight.transportpce.pce.networkanalyzer.PceCalculation;
import org.opendaylight.transportpce.pce.networkanalyzer.PceResult;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.pce.rev220615.PathComputationRequestInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.pce.rev220615.service.path.rpc.result.PathDescriptionBuilder;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.pathdescription.rev210705.path.description.AToZDirection;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.pathdescription.rev210705.path.description.ZToADirection;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.service.types.rev220118.PceMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Class for Sending
 * PCE requests :
 * - path-computation-request
 * - cancel-resource-reserve.
 * @author Martial Coulibaly ( martial.coulibaly@gfi.com ) on behalf of Orange
 *
 */
public class PceSendingPceRPCs {

    /* Logging. */
    private static final Logger LOG = LoggerFactory.getLogger(PceSendingPceRPCs.class);
    /* define procedure success (or not ). */
    private PceResult rc = new PceResult();

    /*
     * define type of request<br> <code>true</code> pathcomputation <br>
     * <code>false</code> cancelresourcereserve .
     */
    private PathDescriptionBuilder pathDescription;
    private PathComputationRequestInput input;
    private NetworkTransactionService networkTransaction;
    private PceConstraints pceHardConstraints = new PceConstraints();
    private PceConstraints pceSoftConstraints = new PceConstraints();
    private Boolean success;
    private String message;
    private String responseCode;
    private PortMapping portMapping;

    public PceSendingPceRPCs() {
        setPathDescription(null);
        this.input = null;
        this.networkTransaction = null;
    }

    public PceSendingPceRPCs(PathComputationRequestInput input,
        NetworkTransactionService networkTransaction, PortMapping portMapping) {
        setPathDescription(null);

        // TODO compliance check to check that input is not empty
        this.input = input;
        this.networkTransaction = networkTransaction;
        this.portMapping = portMapping;
    }

    public void cancelResourceReserve() {
        success = false;
        LOG.info("Wait for 10s til beginning the PCE cancelResourceReserve request");
        try {
            // sleep for 10s
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            LOG.error("in PCESendingPceRPC: ",e);
        }
        success = true;
        LOG.info("cancelResourceReserve ...");
    }

    public void pathComputationWithConstraints(PceConstraints hardConstraints, PceConstraints softConstraints) {

        PceCalculation nwAnalizer =
            new PceCalculation(input, networkTransaction, hardConstraints, softConstraints, rc, portMapping);
        nwAnalizer.retrievePceNetwork();
        rc = nwAnalizer.getReturnStructure();
        String serviceType = nwAnalizer.getServiceType();
        if (!rc.getStatus()) {
            LOG.error("In pathComputationWithConstraints, nwAnalizer: result = {}", rc);
            return;
        }
        LOG.info("PceGraph ...");
        PceGraph graph = new PceGraph(nwAnalizer.getaendPceNode(), nwAnalizer.getzendPceNode(),
                nwAnalizer.getAllPceNodes(), hardConstraints, softConstraints, rc, serviceType);
        graph.calcPath();
        rc = graph.getReturnStructure();
        if (!rc.getStatus()) {
            LOG.warn("In pathComputationWithConstraints : Graph return without Path ");
            // TODO fix. This is quick workaround for algorithm problem
            if ((rc.getLocalCause() == PceResult.LocalCause.TOO_HIGH_LATENCY)
                && (hardConstraints.getPceMetrics() == PceMetric.HopCount)
                && (hardConstraints.getMaxLatency() != -1)) {
                hardConstraints.setPceMetrics(PceMetric.PropagationDelay);
                graph = patchRerunGraph(graph);
            }

            if (rc.getLocalCause() == PceResult.LocalCause.HD_NODE_INCLUDE) {
                graph.setKpathsToBring(graph.getKpathsToBring() * 10);
                graph = patchRerunGraph(graph);
            }

            if (!rc.getStatus()) {
                LOG.error("In pathComputationWithConstraints, graph.calcPath: result = {}", rc);
                return;
            }
        }
        LOG.info("PcePathDescription ...");
        PcePathDescription description = new PcePathDescription(graph.getPathAtoZ(), nwAnalizer.getAllPceLinks(), rc);
        description.buildDescriptions();
        rc = description.getReturnStructure();
        if (!rc.getStatus()) {
            LOG.error("In pathComputationWithConstraints, description: result = {}", rc);
        }
    }

    public void pathComputation() throws Exception {

        PceConstraintsCalc constraints = new PceConstraintsCalc(input, networkTransaction);
        pceHardConstraints = constraints.getPceHardConstraints();
        pceSoftConstraints = constraints.getPceSoftConstraints();
        pathComputationWithConstraints(pceHardConstraints, pceSoftConstraints);
        this.success = rc.getStatus();
        this.message = rc.getMessage();
        this.responseCode = rc.getResponseCode();

        AToZDirection atoz = null;
        ZToADirection ztoa = null;
        if (rc.getStatus()) {
            atoz = rc.getAtoZDirection();
            ztoa = rc.getZtoADirection();
        }

        setPathDescription(new PathDescriptionBuilder().setAToZDirection(atoz).setZToADirection(ztoa));
    }

    private PceGraph patchRerunGraph(PceGraph graph) {
        LOG.info("In pathComputation patchRerunGraph : rerun Graph with metric = PROPAGATION-DELAY ");
        graph.setConstrains(pceHardConstraints, pceSoftConstraints);
        graph.calcPath();
        return graph;
    }

    public PathDescriptionBuilder getPathDescription() {
        return pathDescription;
    }

    private void setPathDescription(PathDescriptionBuilder pathDescription) {
        this.pathDescription = pathDescription;
    }

    public Boolean getSuccess() {
        return this.success;
    }

    public String getMessage() {
        return this.message;
    }

    public String getResponseCode() {
        return this.responseCode;
    }
}
