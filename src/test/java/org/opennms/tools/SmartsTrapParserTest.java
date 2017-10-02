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

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

public class SmartsTrapParserTest {

    private SmartsTrapParser smartsTrapParser;

    @Before
    public void setUp() {
        ClassLoader classLoader = getClass().getClassLoader();
        File smarts = new File(classLoader.getResource("smarts_trap_defs").getFile());
        smartsTrapParser = new SmartsTrapParser(smarts);
    }

    @Test
    public void canParseTraps() throws IOException {
        List<SmartsTrapDef> trapDefs = smartsTrapParser.getTrapDefinitions();
        assertThat(trapDefs, hasSize(greaterThanOrEqualTo(1)));
        assertThat(trapDefs, hasItem(hasEnterprise(".1.3.6.1.2.1.10.32")));
    }

    @Test
    public void canParseTrapDefinitions() throws IOException {
        final SmartsTrapDef def = smartsTrapParser.getTrapDefinition(".1.3.6.1.2.1.10.32", "6", "1");
        assertThat(def.getEnterprise(), is(".1.3.6.1.2.1.10.32"));
        assertThat(def.getTrapNumber(), is("6"));
        assertThat(def.getSpecific(), is("1"));
        assertThat(def.getClassName(), is("DLCI"));
        assertThat(def.getElementName(), is("$SYS$"));
        assertThat(def.getInstanceName(), is("$SYS$"));
        assertThat(def.getEventName(), is("frDLCIStatusChange"));
        assertThat(def.getSeverity(), is(3));
        assertThat(def.getEventText(), is("The virtual circuit ($V2$) has changed state"));
        assertThat(def.getState(), is("NOTIFY"));
        assertThat(def.getEventType(), is("MOMENTARY"));
        assertThat(def.getUnknownAgent(), is("CREATE"));
        assertThat(def.getClearOnAcknowledge(), is("FALSE"));
        assertThat(def.getExpiration(), is("240"));
        assertThat(def.getLogFile(), is("my.log"));
    }

    @Test
    public void canParseMap() throws IOException {
        final SmartsTrapDef def = smartsTrapParser.getTrapDefinition(".1.3.6.1.4.1.12148.9.8", "6", "7");
        assertThat(def.getEnterprise(), is(".1.3.6.1.4.1.12148.9.8"));
        assertThat(def.getTrapNumber(), is("6"));
        assertThat(def.getSpecific(), is("7"));
        assertThat(def.getState(), is("$V1$"));
        assertThat(def.getMap().size(), is(1));
        assertThat(def.getMap().get("V1").size(), is(2));
        assertThat(def.getMap().get("V1").get("0"), is("CLEAR"));
        assertThat(def.getMap().get("V1").get("1"), is("NOTIFY"));
    }

    private org.hamcrest.Matcher<SmartsTrapDef> hasEnterprise(final String enterprise) {
        return new BaseMatcher<SmartsTrapDef>() {
            public boolean matches(Object item) {
                final SmartsTrapDef def = (SmartsTrapDef) item;
                return enterprise.equals(def.getEnterprise());
            }

            public void describeTo(Description description) {
                description.appendText("getEnterprise should return ").appendValue(enterprise);
            }
        };
    }

    @Test
    public void canParseSpecificDef() {
        String definition = "ClassName:              Host\n" +
                "InstanceName:           $SYS$\n" +
                "EventName:              Catch_All_Trap\n" +
                "Severity:               1\n" +
                "Expiration:             20\n" +
                "State:                  CLEAR\n" +
                "EventText:              $V*$\n" +
                "EventType:              MOMENTARY\n" +
                "UnknownAgent:           CREATE\n" +
                "ClearOnAcknowledge:     FALSE\n" +
                "UserDefined1:           $V1$\n" +
                "UserDefined2:           $V2$\n" +
                "UserDefined3:           $V3$\n" +
                "UserDefined4:           $V4$\n" +
                "UserDefined5:           $V5$\n" +
                "UserDefined6:           $V6$\n" +
                "UserDefined7:           $V7$\n" +
                "UserDefined8:           $V8$\n" +
                "UserDefined9:           $V9$\n" +
                "UserDefined10:          $V10$\n" +
                "LogFile:                sbx-Trap_Catch_All.log";

        SmartsTrapDef def = new SmartsTrapDef();
        SmartsTrapParser.parseDefinition(def, definition);
        assertEquals(def.getClassName(), "Host");
        assertEquals(def.getInstanceName(), "$SYS$");
        assertEquals(def.getEventName(), "Catch_All_Trap");
        assertEquals(def.getSeverity(), 1);
        assertEquals(def.getExpiration(), "20");
        assertEquals(def.getState(), "CLEAR");
        assertEquals(def.getEventText(), "$V*$");
        assertEquals(def.getUnknownAgent(), "CREATE");
        assertEquals(def.getClearOnAcknowledge(), "FALSE");
        assertEquals(def.getLogFile(), "sbx-Trap_Catch_All.log");
    }

    @Test
    public void canParseSpecificDefWithColonInValue() {
        String definition = "EventName:  some_event\n" +
                "EventText:              a: b: c: $V*$\n";

        SmartsTrapDef def = new SmartsTrapDef();
        SmartsTrapParser.parseDefinition(def, definition);
        assertEquals(def.getEventName(), "some_event");
        assertEquals(def.getEventText(), "a: b: c: $V*$");
    }
}
