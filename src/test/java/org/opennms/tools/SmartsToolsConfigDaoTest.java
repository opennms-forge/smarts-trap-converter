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

import org.junit.Test;
import org.opennms.tools.smarts.config.SmartsToolsConfig;
import org.opennms.tools.smarts.config.SmartsToolsConfigDao;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class SmartsToolsConfigDaoTest {

    @Test
    public void canParseConfig() {
        ClassLoader classLoader = getClass().getClassLoader();
        File configFile = new File(classLoader.getResource("config.yaml").getFile());
        SmartsToolsConfigDao dao = new SmartsToolsConfigDao(configFile);
        SmartsToolsConfig config = dao.getConfig();

        assertThat(config, notNullValue());
        assertThat(config.getMibs().getMappings().get(0).getUeiPrefix(), is("uei.opennms.org/IETF/RSVP/traps"));
    }
}
