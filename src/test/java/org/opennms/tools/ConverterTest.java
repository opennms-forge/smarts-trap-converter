/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.netmgt.xml.eventconf.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ConverterTest {

    private SmartsTrapParser smartsTrapParser;

    @Before
    public void setUp() {
        ClassLoader classLoader = getClass().getClassLoader();
        File smarts = new File(classLoader.getResource("smarts_trap_defs").getFile());
        smartsTrapParser = new SmartsTrapParser(smarts);
    }

    @Test
    public void canConvertDefToEvent() throws IOException {
        SmartsTrapDef problemDef = smartsTrapParser.getTrapDefinition(".1.3.6.1.4.1.5528.100.10.2.3", "6", "3");
        SmartsTrapDef clearDef = smartsTrapParser.getTrapDefinition(".1.3.6.1.4.1.5528.100.10.2.3", "6", "103");

        Converter converter = new Converter();
        Event problemEvent = converter.convert(problemDef).get(0);
        Event clearEvent = converter.convert(clearDef).get(0);

        assertThat(problemEvent.getEventLabel(), is("SMARTS: DewpointTooLow"));
        assertThat(problemEvent.getUei(), is("uei.opennms.org/smarts/DewpointTooLow"));
        assertThat(problemEvent.getDescr(), is("The %parm[#5]% Dewpoint Sensor value is too low"));
        assertThat(problemEvent.getLogmsg().getContent(), is(problemEvent.getDescr()));
        assertThat(problemEvent.getSeverity(), is("Critical"));
        assertThat(problemEvent.getAlarmData().getAlarmType(), is(1));
        assertThat(problemEvent.getAlarmData().getAutoClean(), is(false));
        assertThat(problemEvent.getAlarmData().getReductionKey(), is("%uei%:%dpname%:%nodeid%:%interface%:%parm[#5]%"));

        Parameter classNameParm = new Parameter();
        classNameParm.setName("className");
        classNameParm.setValue("NetBotz");
        classNameParm.setExpand(false);
        assertThat(problemEvent.getParameters(), contains(classNameParm));

        assertThat(clearEvent.getEventLabel(), is("SMARTS: DewpointTooLowResolved"));
        assertThat(clearEvent.getUei(), is("uei.opennms.org/smarts/DewpointTooLowResolved"));
        assertThat(clearEvent.getDescr(), is("The %parm[#5]% Dewpoint Sensor value is no longer too low"));
        assertThat(clearEvent.getLogmsg().getContent(), is(clearEvent.getDescr()));
        assertThat(clearEvent.getSeverity(), is("Normal"));
        assertThat(clearEvent.getAlarmData().getAlarmType(), is(2));
        assertThat(clearEvent.getAlarmData().getAutoClean(), is(false));
        assertThat(clearEvent.getAlarmData().getReductionKey(), is("%uei%:%dpname%:%nodeid%:%interface%:%parm[#5]%"));
        assertThat(clearEvent.getAlarmData().getClearKey(), is("uei.opennms.org/smarts/DewpointTooLow:%dpname%:%nodeid%:%interface%:%parm[#5]%"));
        assertThat(clearEvent.getParameters(), contains(classNameParm));
    }


    @Test
    public void canConvertSingleDefToEvents() throws IOException {
        SmartsTrapDef def = smartsTrapParser.getTrapDefinition(".1.3.6.1.4.1.12148.9.8", "6", "7");
        Converter converter = new Converter();
        List<Event> events = converter.convert(def);
        assertThat(events, hasSize(2));

        Event clearEvent = events.get(0);
        Event problemEvent = events.get(1);

        assertThat(problemEvent.getEventLabel(), is("SMARTS: BatteryDisconnectOpen"));
        assertThat(problemEvent.getUei(), is("uei.opennms.org/smarts/BatteryDisconnectOpen"));
        assertThat(problemEvent.getDescr(), is("A Battery Disconnect Contactor alarm has happened %parm[#2]% times"));
        assertThat(problemEvent.getLogmsg().getContent(), is(problemEvent.getDescr()));
        assertThat(problemEvent.getSeverity(), is("Critical"));
        assertThat(problemEvent.getAlarmData().getAlarmType(), is(1));
        assertThat(problemEvent.getAlarmData().getAutoClean(), is(false));
        assertThat(problemEvent.getAlarmData().getReductionKey(), is("%uei%:%dpname%:%nodeid%:%interface%"));
        assertThat(problemEvent.getMask().getVarbinds().get(0).getVbnumber(), is(1));
        assertThat(problemEvent.getMask().getVarbinds().get(0).getVbvalues(), contains("1"));
        assertThat(problemEvent.getVarbindsdecodes(), hasSize(1));
        assertThat(problemEvent.getVarbindsdecodes().get(0).getParmid(), is("parm[#1]"));
        assertThat(problemEvent.getVarbindsdecodes().get(0).getDecodes(), hasSize(2));
        assertThat(problemEvent.getVarbindsdecodes().get(0).getDecodes().get(0).getVarbindvalue(), is("0"));
        assertThat(problemEvent.getVarbindsdecodes().get(0).getDecodes().get(0).getVarbinddecodedstring(), is("CLEAR"));
        assertThat(problemEvent.getVarbindsdecodes().get(0).getDecodes().get(1).getVarbindvalue(), is("1"));
        assertThat(problemEvent.getVarbindsdecodes().get(0).getDecodes().get(1).getVarbinddecodedstring(), is("NOTIFY"));

        assertThat(clearEvent.getEventLabel(), is("SMARTS: BatteryDisconnectOpenResolved"));
        assertThat(clearEvent.getUei(), is("uei.opennms.org/smarts/BatteryDisconnectOpenResolved"));
        assertThat(clearEvent.getDescr(), is("A Battery Disconnect Contactor alarm has happened %parm[#2]% times"));
        assertThat(clearEvent.getLogmsg().getContent(), is(clearEvent.getDescr()));
        assertThat(clearEvent.getSeverity(), is("Normal"));
        assertThat(clearEvent.getAlarmData().getAlarmType(), is(2));
        assertThat(clearEvent.getAlarmData().getAutoClean(), is(false));
        assertThat(clearEvent.getAlarmData().getReductionKey(), is("%uei%:%dpname%:%nodeid%:%interface%"));
        assertThat(clearEvent.getAlarmData().getClearKey(), is("uei.opennms.org/smarts/BatteryDisconnectOpen:%dpname%:%nodeid%:%interface%"));
        assertThat(clearEvent.getMask().getVarbinds().get(0).getVbnumber(), is(1));
        assertThat(clearEvent.getMask().getVarbinds().get(0).getVbvalues(), contains("0"));
        assertThat(clearEvent.getVarbindsdecodes(), hasSize(1));
        assertThat(clearEvent.getVarbindsdecodes().get(0).getParmid(), is("parm[#1]"));
        assertThat(clearEvent.getVarbindsdecodes().get(0).getDecodes(), hasSize(2));
        assertThat(clearEvent.getVarbindsdecodes().get(0).getDecodes().get(0).getVarbindvalue(), is("0"));
        assertThat(clearEvent.getVarbindsdecodes().get(0).getDecodes().get(0).getVarbinddecodedstring(), is("CLEAR"));
        assertThat(clearEvent.getVarbindsdecodes().get(0).getDecodes().get(1).getVarbindvalue(), is("1"));
        assertThat(clearEvent.getVarbindsdecodes().get(0).getDecodes().get(1).getVarbinddecodedstring(), is("NOTIFY"));
    }

    @Test
    public void canReplaceVariables() {
        assertThat(Converter.replaceVariables(null), nullValue());
        assertThat(Converter.replaceVariables(""), is(""));
        assertThat(Converter.replaceVariables(" "), is(" "));
        assertThat(Converter.replaceVariables("$V1$"), is("%parm[#1]%"));
        assertThat(Converter.replaceVariables(" $V1$ "), is(" %parm[#1]% "));

        assertThat(Converter.replaceVariables("$V1$"), is("%parm[#1]%"));
        assertThat(Converter.replaceVariables("$V2$"), is("%parm[#2]%"));
        assertThat(Converter.replaceVariables("$V99$"), is("%parm[#99]%"));
        assertThat(Converter.replaceVariables("$V*$"), is("%parm[all]%"));

        assertThat(Converter.replaceVariables("$OID1$"), is("%parm[name-#1]%"));
        assertThat(Converter.replaceVariables("$OID2$"), is("%parm[name-#2]%"));
        assertThat(Converter.replaceVariables("$OID99$"), is("%parm[name-#99]%"));
        assertThat(Converter.replaceVariables("$OID*$"), is("%parm[names-all]%"));

        assertThat(Converter.replaceVariables("$E$"), is("%id%"));
        assertThat(Converter.replaceVariables("$N$"), is("%generic%"));
        assertThat(Converter.replaceVariables("$S$"), is("%specific%"));

        assertThat(Converter.replaceVariables("$T$"), is("%time%"));

        assertThat(Converter.replaceVariables("$SYS$"), is("%interface%"));

        assertThat(Converter.replaceVariables("$C$"), is("%community%"));
        assertThat(Converter.replaceVariables("$A$"), is("%interface%"));
        assertThat(Converter.replaceVariables("$SRC$"), is("%interface%"));
    }
}
