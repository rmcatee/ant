/*
 * Copyright  2005 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tools.ant.types.optional;

import org.apache.tools.ant.BuildFileTest;

/**
 */
public class ScriptConditionTest extends BuildFileTest {
    /**
     * Constructor for the BuildFileTest object
     *
     * @param name string to pass up to TestCase constructor
     */
    public ScriptConditionTest(String name) {
        super(name);
    }

    public void setUp() {
        configureProject("src/etc/testcases/types/scriptcondition.xml");
    }

    public void testNolanguage() {
        expectBuildExceptionContaining("testNolanguage",
                "Absence of language attribute not detected",
                "script language must be specified");
    }

    public void testMacro() {
        executeTarget("testMacro");
    }
    public void testClearByDefault() {
        executeTarget("testClearByDefault");
    }
    public void testValueWorks() {
        executeTarget("testValueWorks");
    }

    public void testSetWorks() {
        executeTarget("testSetWorks");
    }

    public void testClearWorks() {
        executeTarget("testClearWorks");
    }

}