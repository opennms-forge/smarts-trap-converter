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

import org.junit.Ignore;
import org.junit.Test;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.snmp.SnmpInstId;
import org.opennms.netmgt.snmp.SnmpObjId;
import org.opennms.netmgt.snmp.SnmpTrapBuilder;
import org.opennms.netmgt.snmp.SnmpUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TrapGenerationTest {

    @Test
    @Ignore
    public void generateTraps() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File smarts = new File(classLoader.getResource("smarts_trap_defs").getFile());
        SmartsTrapParser smartsTrapParser = new SmartsTrapParser(smarts);

        List<SmartsTrapDef> trapDefs = smartsTrapParser.getTrapDefinitions();
        SmartsTrapDef def = trapDefs.get(0);
        sendTrap(def);
    }

    public static void sendTrap(SmartsTrapDef def) throws Exception {
        SnmpObjId enterpriseId = SnmpObjId.get(".1.3.6.1.3.95.3");
        SnmpObjId trapOID = SnmpObjId.get(enterpriseId, new SnmpInstId(2));
        SnmpTrapBuilder pdu = SnmpUtils.getV2TrapBuilder();
        pdu.addVarBind(SnmpObjId.get(".1.3.6.1.2.1.1.3.0"), SnmpUtils.getValueFactory().getTimeTicks(0));
        pdu.addVarBind(SnmpObjId.get(".1.3.6.1.6.3.1.1.4.1.0"), SnmpUtils.getValueFactory().getObjectId(trapOID));
        // Varbinds!
        pdu.addVarBind(SnmpObjId.get(".1.3.6.1.2.1.1.3.0"), SnmpUtils.getValueFactory().getInt32(1));
        pdu.addVarBind(SnmpObjId.get(".1.3.6.1.2.1.1.3.0"), SnmpUtils.getValueFactory().getInt32(1));

        pdu.send("127.0.0.1", 162, "public");
    }

}
