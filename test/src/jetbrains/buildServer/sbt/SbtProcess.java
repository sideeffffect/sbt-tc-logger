/*
 * Copyright 2013-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 *
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

package jetbrains.buildServer.sbt;


import junit.framework.Assert;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SbtProcess {

    public static int runAndTest(String sbtCommands, String workingDir) throws IOException,
            InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome +
                File.separator + "bin" +
                File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        String sbtPath = new File("test" + File.separator + "sbt").getAbsolutePath();
        String sbtLauncherPath = new File(sbtPath, "bin" + File.separator + "sbt-launch.jar").getAbsolutePath();

        String sbtPathParam = "-Dsbt.global.base=" + sbtPath;
        String sbtParam = "-Dsbt.log.format=false";

        ProcessBuilder builder = new ProcessBuilder(
                javaBin, "-cp", classpath, "-jar", sbtLauncherPath, sbtPathParam, sbtParam, sbtCommands);
        Map<String, String> env = builder.environment();
        env.put("TEAMCITY_VERSION", "8.0.TEST");
        builder.directory(new File(workingDir));
        Process process = builder.start();
        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(process.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(process.getErrorStream()));

        File excludes = new File(workingDir + File.separator + "excludes.txt");
        BufferedReader brExcludes = null;
        if (excludes.exists()) {
            brExcludes = new BufferedReader(new FileReader(excludes));
        }

        checkOutputTest(new BufferedReader(new FileReader(workingDir + File.separator + "output.txt")), stdInput, brExcludes);


        process.waitFor();

        String s;
        // read any errors from the attempted command
        System.out.println("Here is the standard error of the command (if any):\n");
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }

        return process.exitValue();
    }


    public static void checkOutputTest(BufferedReader requiredOutput, BufferedReader stdInput, BufferedReader brExcludes) throws IOException {

        List<Pattern> required = getPatterns(requiredOutput);

        String s;
        int i = 0;
        int found = 0;

        List<Pattern> excludes = getPatterns(brExcludes);

        List<String> excludesFound = new ArrayList<String>();

        assert required.size() > 0;

        Pattern currentRequired = required.get(i++);
        while ((s = stdInput.readLine()) != null) {
            Matcher matcher = currentRequired.matcher(s);
            if (matcher.find()) {
                found++;
                if (i < required.size()) {
                    currentRequired = required.get(i++);
                }
            }
            System.out.println(s);

            //check for excludes
            for (Pattern exclude : excludes) {
                Matcher excludeMatcher = exclude.matcher(s);
                if (excludeMatcher.find()) {
                    excludesFound.add(s);
                }
            }

        }


        if (found != required.size()) {
            System.out.println("First failed line:");
            System.out.println(currentRequired);
        }
        Assert.assertEquals(required.size(), found);

        if (brExcludes != null && excludes.size() > 0 && excludesFound.size() > 0) {
            System.out.println("===================== ERROR ==========================");
            System.out.println("The following lines were found but should not be there:");
            for (String ef : excludesFound) {
                System.out.println(ef);
            }
            Assert.assertEquals(excludesFound.size(), 0);
        }

    }

    private static List<Pattern> getPatterns(BufferedReader requiredOutput) throws IOException {
        if (requiredOutput == null) {
            return Collections.emptyList();
        }
        List<Pattern> required = new ArrayList<Pattern>();
        String s;
        while ((s = requiredOutput.readLine()) != null) {
            required.add(Pattern.compile(s));
        }
        return required;
    }


}