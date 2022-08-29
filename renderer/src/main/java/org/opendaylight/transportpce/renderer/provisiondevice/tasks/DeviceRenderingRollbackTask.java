/*
 * Copyright © 2017 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.renderer.provisiondevice.tasks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.transportpce.renderer.provisiondevice.DeviceRendererService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.device.renderer.rev211004.RendererRollbackInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.device.renderer.rev211004.RendererRollbackInputBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.device.renderer.rev211004.RendererRollbackOutput;
import org.opendaylight.yang.gen.v1.http.org.transportpce.common.types.rev210930.node.interfaces.NodeInterface;
import org.opendaylight.yang.gen.v1.http.org.transportpce.common.types.rev210930.node.interfaces.NodeInterfaceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceRenderingRollbackTask extends RollbackTask {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRenderingRollbackTask.class);
    private final boolean isRollbackNecessary;
    private final DeviceRendererService rendererService;
    private final Map<NodeInterfaceKey,NodeInterface> renderedInterfaces;

    private final ResultMessage message;

    public DeviceRenderingRollbackTask(String id, boolean isRollbackNecessary, List<NodeInterface> renderedInterfaces,
            DeviceRendererService rendererService,
            ResultMessage message
    ) {
        super(id);
        this.isRollbackNecessary = isRollbackNecessary;
        this.rendererService = rendererService;
        this.renderedInterfaces = new HashMap<>();
        this.message = message;
        if (renderedInterfaces != null) {
            for (NodeInterface nodeInterface : renderedInterfaces) {
                if (nodeInterface != null) {
                    this.renderedInterfaces.put(nodeInterface.key(), nodeInterface);
                }
            }
        }
    }

    @Override
    public boolean isRollbackNecessary() {
        return isRollbackNecessary;
    }

    @Override
    public Void call() throws Exception {
        RendererRollbackInput rollbackInput = new RendererRollbackInputBuilder()
                .setNodeInterface(this.renderedInterfaces)
                .build();
        RendererRollbackOutput rollbackOutput = this.rendererService.rendererRollback(rollbackInput);
        if (! rollbackOutput.getSuccess()) {
            LOG.warn("Device rendering rollback of {} was not successful! Failed rollback on {}.", this.getId(),
                    message.createErrorMessage(rollbackOutput.nonnullFailedToRollback().values()));
        } else {
            LOG.info("Device rollback of {} successful.", this.getId());
        }
        return null;
    }

}
