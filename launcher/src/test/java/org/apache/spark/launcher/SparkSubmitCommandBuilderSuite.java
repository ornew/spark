/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.*;

import static org.junit.Assert.*;

public class SparkSubmitCommandBuilderSuite extends BaseSuite {

  private static File dummyPropsFile;
  private static SparkSubmitOptionParser parser;

  @BeforeClass
  public static void setUp() throws Exception {
    dummyPropsFile = File.createTempFile("spark", "properties");
    parser = new SparkSubmitOptionParser();
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    dummyPropsFile.delete();
  }

  @Test
  public void testDriverCmdBuilder() throws Exception {
    testCmdBuilder(true, true);
    testCmdBuilder(true, false);
  }

  @Test
  public void testClusterCmdBuilder() throws Exception {
    testCmdBuilder(false, true);
    testCmdBuilder(false, false);
  }

  @Test
  public void testCliHelpAndNoArg() throws Exception {
    List<String> helpArgs = Arrays.asList(parser.HELP);
    Map<String, String> env = new HashMap<>();
    List<String> cmd = buildCommand(helpArgs, env);
    assertTrue("--help should be contained in the final cmd.", cmd.contains(parser.HELP));

    List<String> sparkEmptyArgs = Collections.emptyList();
    cmd = buildCommand(sparkEmptyArgs, env);
    assertTrue(
      "org.apache.spark.deploy.SparkSubmit should be contained in the final cmd of empty input.",
      cmd.contains("org.apache.spark.deploy.SparkSubmit"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckJavaOptionsThrowException() throws Exception {
    Map<String, String> env = new HashMap<>();
    List<String> sparkSubmitArgs = Arrays.asList(
      parser.MASTER,
      "local",
      parser.DRIVER_CLASS_PATH,
      "/driverCp",
      parser.DRIVER_JAVA_OPTIONS,
      "-Xmx64g -Dprop=Other -Dprop1=\"-Xmx -Xmx\" -Dprop2=\"-Xmx '-Xmx\" " +
        "-Dprop3='-Xmx -Xmx' -Dprop4='-Xmx \"-Xmx'",
      SparkLauncher.NO_RESOURCE);
    buildCommand(sparkSubmitArgs, env);
  }

  @Test
  public void testCheckJavaOptions() throws Exception {
    Map<String, String> env = new HashMap<>();
    List<String> sparkSubmitArgs = Arrays.asList(
      parser.MASTER,
      "local",
      parser.DRIVER_CLASS_PATH,
      "/driverCp",
      parser.DRIVER_JAVA_OPTIONS,
      "-Dprop=-Xmx -Dprop1=\"-Xmx -Xmx\" -Dprop2=\"-Xmx '-Xmx\" " +
        "-Dprop3='-Xmx -Xmx' -Dprop4='-Xmx \"-Xmx'",
      SparkLauncher.NO_RESOURCE);
    buildCommand(sparkSubmitArgs, env);
  }

  @Test
  public void testCliKillAndStatus() throws Exception {
    List<String> params = Arrays.asList("driver-20160531171222-0000");
    testCLIOpts(null, parser.STATUS, params);
    testCLIOpts(null, parser.KILL_SUBMISSION, params);
    testCLIOpts(SparkSubmitCommandBuilder.RUN_EXAMPLE, parser.STATUS, params);
    testCLIOpts(SparkSubmitCommandBuilder.RUN_EXAMPLE, parser.KILL_SUBMISSION, params);
  }

  @Test
  public void testCliParser() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      parser.MASTER,
      "local",
      parser.DRIVER_MEMORY,
      "42g",
      parser.DRIVER_CLASS_PATH,
      "/driverCp",
      parser.DRIVER_JAVA_OPTIONS,
      "extraJavaOpt",
      parser.CONF,
      "spark.randomOption=foo",
      parser.CONF,
      SparkLauncher.DRIVER_EXTRA_LIBRARY_PATH + "=/driverLibPath",
      SparkLauncher.NO_RESOURCE);
    Map<String, String> env = new HashMap<>();
    List<String> cmd = buildCommand(sparkSubmitArgs, env);

    assertTrue(findInStringList(env.get(CommandBuilderUtils.getLibPathEnvName()),
        File.pathSeparator, "/driverLibPath"));
    assertTrue(findInStringList(findArgValue(cmd, "-cp"), File.pathSeparator, "/driverCp"));
    assertTrue("Driver -Xmx should be configured.", cmd.contains("-Xmx42g"));
    assertTrue("Command should contain user-defined conf.",
      Collections.indexOfSubList(cmd, Arrays.asList(parser.CONF, "spark.randomOption=foo")) > 0);
  }

  @Test
  public void testShellCliParser() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      parser.CLASS,
      "org.apache.spark.repl.Main",
      parser.MASTER,
      "foo",
      "--app-arg",
      "bar",
      "--app-switch",
      parser.FILES,
      "baz",
      parser.NAME,
      "appName");

    List<String> args = newCommandBuilder(sparkSubmitArgs).buildSparkSubmitArgs();
    List<String> expected = Arrays.asList("spark-shell", "--app-arg", "bar", "--app-switch");
    assertEquals(expected, args.subList(args.size() - expected.size(), args.size()));
  }

  @Test
  public void testAlternateSyntaxParsing() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      parser.CLASS + "=org.my.Class",
      parser.MASTER + "=foo",
      parser.DEPLOY_MODE + "=bar",
      SparkLauncher.NO_RESOURCE);

    List<String> cmd = newCommandBuilder(sparkSubmitArgs).buildSparkSubmitArgs();
    assertEquals("org.my.Class", findArgValue(cmd, parser.CLASS));
    assertEquals("foo", findArgValue(cmd, parser.MASTER));
    assertEquals("bar", findArgValue(cmd, parser.DEPLOY_MODE));
  }

  @Test
  public void testPySparkLauncher() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      SparkSubmitCommandBuilder.PYSPARK_SHELL,
      "--master=foo",
      "--deploy-mode=bar");

    Map<String, String> env = new HashMap<>();
    List<String> cmd = buildCommand(sparkSubmitArgs, env);
    assertTrue(Arrays.asList("python", "python2", "python3").contains(cmd.get(cmd.size() - 1)));
    assertEquals(
      String.format("\"%s\" \"foo\" \"%s\" \"bar\" \"%s\"",
        parser.MASTER, parser.DEPLOY_MODE, SparkSubmitCommandBuilder.PYSPARK_SHELL_RESOURCE),
      env.get("PYSPARK_SUBMIT_ARGS"));
  }

  @Test
  public void testPySparkFallback() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      "--master=foo",
      "--deploy-mode=bar",
      "script.py",
      "arg1");

    Map<String, String> env = new HashMap<>();
    List<String> cmd = buildCommand(sparkSubmitArgs, env);

    assertEquals("foo", findArgValue(cmd, "--master"));
    assertEquals("bar", findArgValue(cmd, "--deploy-mode"));
    assertEquals("script.py", cmd.get(cmd.size() - 2));
    assertEquals("arg1", cmd.get(cmd.size() - 1));
  }

  @Test
  public void testSparkRShell() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      SparkSubmitCommandBuilder.SPARKR_SHELL,
      "--master=foo",
      "--deploy-mode=bar",
      "--conf", "spark.r.shell.command=/usr/bin/R");

    Map<String, String> env = new HashMap<>();
    List<String> cmd = buildCommand(sparkSubmitArgs, env);
    assertEquals("/usr/bin/R", cmd.get(cmd.size() - 1));
    assertEquals(
      String.format(
        "\"%s\" \"foo\" \"%s\" \"bar\" \"--conf\" \"spark.r.shell.command=/usr/bin/R\" \"%s\"",
        parser.MASTER, parser.DEPLOY_MODE, SparkSubmitCommandBuilder.SPARKR_SHELL_RESOURCE),
      env.get("SPARKR_SUBMIT_ARGS"));
  }

  @Test
  public void testExamplesRunnerNoArg() {
    List<String> sparkSubmitArgs = Arrays.asList(SparkSubmitCommandBuilder.RUN_EXAMPLE);
    Map<String, String> env = new HashMap<>();
    assertThrows(IllegalArgumentException.class, () -> buildCommand(sparkSubmitArgs, env));
  }

  @Test
  public void testExamplesRunnerNoMainClass() throws Exception {
    testCLIOpts(SparkSubmitCommandBuilder.RUN_EXAMPLE, parser.HELP, null);
    testCLIOpts(SparkSubmitCommandBuilder.RUN_EXAMPLE, parser.USAGE_ERROR, null);
    testCLIOpts(SparkSubmitCommandBuilder.RUN_EXAMPLE, parser.VERSION, null);
  }

  @Test
  public void testExamplesRunnerWithMasterNoMainClass() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      SparkSubmitCommandBuilder.RUN_EXAMPLE,
      parser.MASTER + "=foo"
    );
    Map<String, String> env = new HashMap<>();
    Assert.assertThrows("Missing example class name.", IllegalArgumentException.class,
      () -> buildCommand(sparkSubmitArgs, env));
  }

  @Test
  public void testExamplesRunner() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
      SparkSubmitCommandBuilder.RUN_EXAMPLE,
      parser.MASTER + "=foo",
      parser.DEPLOY_MODE + "=bar",
      "SparkPi",
      "42");

    Map<String, String> env = new HashMap<>();
    List<String> cmd = buildCommand(sparkSubmitArgs, env);
    assertEquals("foo", findArgValue(cmd, parser.MASTER));
    assertEquals("bar", findArgValue(cmd, parser.DEPLOY_MODE));
    assertEquals(SparkSubmitCommandBuilder.EXAMPLE_CLASS_PREFIX + "SparkPi",
      findArgValue(cmd, parser.CLASS));
    assertEquals("42", cmd.get(cmd.size() - 1));
  }

  @Test
  public void testExamplesRunnerPrimaryResource() throws Exception {
    List<String> sparkSubmitArgs = Arrays.asList(
            SparkSubmitCommandBuilder.RUN_EXAMPLE,
            parser.MASTER + "=foo",
            parser.DEPLOY_MODE + "=cluster",
            "SparkPi",
            "100");

    List<String> cmd = newCommandBuilder(sparkSubmitArgs).buildSparkSubmitArgs();
    assertEquals(SparkSubmitCommandBuilder.EXAMPLE_CLASS_PREFIX + "SparkPi",
            findArgValue(cmd, parser.CLASS));
    assertEquals("cluster", findArgValue(cmd, parser.DEPLOY_MODE));
    String primaryResource = cmd.get(cmd.size() - 2);
    assertTrue(primaryResource.equals(SparkLauncher.NO_RESOURCE)
            || new File(primaryResource).getName().startsWith("spark-examples"));
  }

  @Test
  public void testMissingAppResource() {
    assertThrows(IllegalArgumentException.class,
      () -> new SparkSubmitCommandBuilder().buildSparkSubmitArgs());
  }

  @Test
  public void testIsClientMode() {
    // Default master is "local[*]"
    SparkSubmitCommandBuilder builder = newCommandBuilder(Collections.emptyList());
    assertTrue("By default application run in local mode",
      builder.isClientMode(Collections.emptyMap()));
    // --master yarn or it can be any RM
    List<String> sparkSubmitArgs = Arrays.asList(parser.MASTER, "yarn");
    builder = newCommandBuilder(sparkSubmitArgs);
    assertTrue("By default deploy mode is client", builder.isClientMode(Collections.emptyMap()));
    // --master yarn and set spark.submit.deployMode to client
    Map<String, String> userProps = new HashMap<>();
    userProps.put("spark.submit.deployMode", "client");
    assertTrue(builder.isClientMode(userProps));
    // --master mesos --deploy-mode cluster
    sparkSubmitArgs = Arrays.asList(parser.MASTER, "mesos", parser.DEPLOY_MODE, "cluster");
    builder = newCommandBuilder(sparkSubmitArgs);
    assertFalse(builder.isClientMode(Collections.emptyMap()));
  }

  private void testCmdBuilder(boolean isDriver, boolean useDefaultPropertyFile) throws Exception {
    final String DRIVER_DEFAULT_PARAM = "-Ddriver-default";
    final String DRIVER_EXTRA_PARAM = "-Ddriver-extra";
    String deployMode = isDriver ? "client" : "cluster";

    SparkSubmitCommandBuilder launcher =
      newCommandBuilder(Collections.emptyList());
    launcher.childEnv.put(CommandBuilderUtils.ENV_SPARK_HOME,
      System.getProperty("spark.test.home"));
    launcher.master = "yarn";
    launcher.deployMode = deployMode;
    launcher.appResource = "/foo";
    launcher.appName = "MyApp";
    launcher.mainClass = "my.Class";
    launcher.appArgs.add("foo");
    launcher.appArgs.add("bar");
    launcher.conf.put("spark.foo", "foo");
    // either set the property through "--conf" or through default property file
    if (!useDefaultPropertyFile) {
      launcher.setPropertiesFile(dummyPropsFile.getAbsolutePath());
      launcher.conf.put(SparkLauncher.DRIVER_MEMORY, "1g");
      launcher.conf.put(SparkLauncher.DRIVER_EXTRA_CLASSPATH, "/driver");
      launcher.conf.put(SparkLauncher.DRIVER_DEFAULT_JAVA_OPTIONS, DRIVER_DEFAULT_PARAM);
      launcher.conf.put(SparkLauncher.DRIVER_EXTRA_JAVA_OPTIONS, DRIVER_EXTRA_PARAM);
      launcher.conf.put(SparkLauncher.DRIVER_EXTRA_LIBRARY_PATH, "/native");
    } else {
      launcher.childEnv.put("SPARK_CONF_DIR", System.getProperty("spark.test.home")
          + "/launcher/src/test/resources");
    }

    Map<String, String> env = new HashMap<>();
    List<String> cmd = launcher.buildCommand(env);

    // Checks below are different for driver and non-driver mode.

    if (isDriver) {
      assertTrue("Driver -Xmx should be configured.", cmd.contains("-Xmx1g"));
      assertTrue("Driver default options should be configured.",
        cmd.contains(DRIVER_DEFAULT_PARAM));
      assertTrue("Driver extra options should be configured.", cmd.contains(DRIVER_EXTRA_PARAM));
    } else {
      boolean found = false;
      for (String arg : cmd) {
        if (arg.startsWith("-Xmx")) {
          found = true;
          break;
        }
      }
      assertFalse("Memory arguments should not be set.", found);
      assertFalse("Driver default options should not be configured.",
        cmd.contains(DRIVER_DEFAULT_PARAM));
      assertFalse("Driver extra options should not be configured.",
        cmd.contains(DRIVER_EXTRA_PARAM));
    }

    String[] cp = findArgValue(cmd, "-cp").split(Pattern.quote(File.pathSeparator));
    if (isDriver) {
      assertTrue("Driver classpath should contain provided entry.", contains("/driver", cp));
    } else {
      assertFalse("Driver classpath should not be in command.", contains("/driver", cp));
    }

    String libPath = env.get(CommandBuilderUtils.getLibPathEnvName());
    if (isDriver) {
      assertNotNull("Native library path should be set.", libPath);
      assertTrue("Native library path should contain provided entry.",
        contains("/native", libPath.split(Pattern.quote(File.pathSeparator))));
    } else {
      assertNull("Native library should not be set.", libPath);
    }

    // Checks below are the same for both driver and non-driver mode.
    if (!useDefaultPropertyFile) {
      assertEquals(dummyPropsFile.getAbsolutePath(), findArgValue(cmd, parser.PROPERTIES_FILE));
    }
    assertEquals("yarn", findArgValue(cmd, parser.MASTER));
    assertEquals(deployMode, findArgValue(cmd, parser.DEPLOY_MODE));
    assertEquals("my.Class", findArgValue(cmd, parser.CLASS));
    assertEquals("MyApp", findArgValue(cmd, parser.NAME));

    boolean appArgsOk = false;
    for (int i = 0; i < cmd.size(); i++) {
      if (cmd.get(i).equals("/foo")) {
        assertEquals("foo", cmd.get(i + 1));
        assertEquals("bar", cmd.get(i + 2));
        assertEquals(cmd.size(), i + 3);
        appArgsOk = true;
        break;
      }
    }
    assertTrue("App resource and args should be added to command.", appArgsOk);

    Map<String, String> conf = parseConf(cmd, parser);
    assertEquals("foo", conf.get("spark.foo"));
  }

  private boolean contains(String needle, String[] haystack) {
    for (String entry : haystack) {
      if (entry.equals(needle)) {
        return true;
      }
    }
    return false;
  }

  private Map<String, String> parseConf(List<String> cmd, SparkSubmitOptionParser parser) {
    Map<String, String> conf = new HashMap<>();
    for (int i = 0; i < cmd.size(); i++) {
      if (cmd.get(i).equals(parser.CONF)) {
        String[] val = cmd.get(i + 1).split("=", 2);
        conf.put(val[0], val[1]);
        i += 1;
      }
    }
    return conf;
  }

  private String findArgValue(List<String> cmd, String name) {
    for (int i = 0; i < cmd.size(); i++) {
      if (cmd.get(i).equals(name)) {
        return cmd.get(i + 1);
      }
    }
    fail(String.format("arg '%s' not found", name));
    return null;
  }

  private boolean findInStringList(String list, String sep, String needle) {
    return contains(needle, list.split(sep));
  }

  private SparkSubmitCommandBuilder newCommandBuilder(List<String> args) {
    SparkSubmitCommandBuilder builder = new SparkSubmitCommandBuilder(args);
    builder.childEnv.put(CommandBuilderUtils.ENV_SPARK_HOME, System.getProperty("spark.test.home"));
    return builder;
  }

  private List<String> buildCommand(List<String> args, Map<String, String> env) throws Exception {
    return newCommandBuilder(args).buildCommand(env);
  }

  private void testCLIOpts(String appResource, String opt, List<String> params) throws Exception {
    List<String> args = new ArrayList<>();
    if (appResource != null) {
      args.add(appResource);
    }
    args.add(opt);
    if (params != null) {
      args.addAll(params);
    }
    Map<String, String> env = new HashMap<>();
    List<String> cmd = buildCommand(args, env);
    assertTrue(opt + " should be contained in the final cmd.",
      cmd.contains(opt));
  }

}
