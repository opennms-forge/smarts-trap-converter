/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.tools;

import com.google.common.collect.Sets;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.tools.smarts.config.SmartsExcludeConfig;
import org.opennms.tools.smarts.config.SmartsToolsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Handles loading the configuration and doing common tasks needed by
 * the different commands.
 */
public class SmartsTools {
    private static final Logger LOG = LoggerFactory.getLogger(SmartsTools.class);

    private final SmartsToolsConfig config;

    public SmartsTools(SmartsToolsConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public List<SmartsTrapDef> getSmartsTrapDefinitions() throws IOException {
        final File smartsTrapConfigFile = new File(config.getSmarts().getTraps());
        if (!smartsTrapConfigFile.canRead()) {
            throw new IOException("Cannot read Smarts trap definitions: "
                    + smartsTrapConfigFile.getAbsolutePath());
        }

        // Parse the trap definitions
        LOG.info("Parsing Smarts trap definitions...");
        SmartsTrapParser p = new SmartsTrapParser(smartsTrapConfigFile);
        List<SmartsTrapDef> trapDefs = p.getTrapDefinitions();

        // Sanitize
        LOG.info("Sanitizing Smarts trap definitions...");
        trapDefs = trapDefs.stream()
                .filter(def -> {
                    for (SmartsExcludeConfig exclude : config.getSmarts().getExcludes()) {
                        if (def.getEnterprise().startsWith(exclude.getEnterprisePrefix())) {
                            return false;
                        }
                    }
                    return true;

                })
                .collect(Collectors.toList());
        return trapDefs;
    }

    public OpenNMSEvents getOpenNMSEvents() {
        final File opennmsEventconfFile = new File( config.getOpennms().getEvents());
        LOG.info("Loading OpenNMS event definitions...");
        final OpenNMSEvents o = new OpenNMSEvents(opennmsEventconfFile);
        return o;
    }

    public Map<SmartsTrapDef, List<Event>> getDefinitionToEventMappings() throws IOException {
        // SMARTS Definitions
        final List<SmartsTrapDef> smartsTrapDefs = getSmartsTrapDefinitions();

        // Existing events in OpenNMS
        final OpenNMSEvents nmsEvents = getOpenNMSEvents();

        // Now try and match each of the SMARTS trap definitions against
        // corresponding event definitions
        return nmsEvents.mapDefinitionsToEventConfs(Sets.newLinkedHashSet(smartsTrapDefs));
    }

}
