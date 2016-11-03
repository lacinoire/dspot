package fr.inria.diversify.dspot.dynamic;

import fr.inria.diversify.dspot.value.MethodCall;
import fr.inria.diversify.dspot.value.MethodCallReader;
import fr.inria.diversify.dspot.value.ValueFactory;
import fr.inria.diversify.log.LogReader;
import fr.inria.diversify.log.TestCoverageParser;
import fr.inria.diversify.log.branch.Coverage;
import fr.inria.diversify.dspot.AssertGenerator;
import fr.inria.diversify.dspot.ClassWithLoggerBuilder;
import fr.inria.diversify.runner.InputProgram;
import fr.inria.diversify.testRunner.TestRunner;
import fr.inria.diversify.testRunner.JunitResult;
import fr.inria.diversify.util.FileUtils;
import fr.inria.diversify.util.Log;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * User: Simon
 * Date: 23/03/16
 * Time: 15:36
 */
public class TestGenerator {
    protected TestRunner testRunner;
    protected TestRunner testRunnerWithBranchLogger;
    protected ClassWithLoggerBuilder classWithLoggerBuilder;
    protected AssertGenerator assertGenerator;
    protected File branchDir;
    protected ValueFactory valueFactory;

    protected InputProgram inputProgram;
    protected Map<CtType, CtType> testClasses;

    public TestGenerator(InputProgram inputProgram, TestRunner testRunner, TestRunner testRunnerWithBranchLogger, AssertGenerator assertGenerator, String branchDir) {
        this.testRunner = testRunner;
        this.testRunnerWithBranchLogger = testRunnerWithBranchLogger;
        this.assertGenerator = assertGenerator;
        this.inputProgram = inputProgram;
        this.branchDir = new File(branchDir + "/log");
        this.testClasses = new HashMap<>();
        this.classWithLoggerBuilder = new ClassWithLoggerBuilder(inputProgram.getFactory());
    }

    public Collection<CtType> generateTestClasses(String logDir) throws IOException {
        valueFactory = new ValueFactory(inputProgram, logDir);

        LogReader logReader = new LogReader(logDir);
        MethodCallReader reader = new MethodCallReader(inputProgram.getFactory(), valueFactory);
        logReader.addParser(reader);
        logReader.readLogs();



        Map<CtMethod, List<MethodCall>> methodCalls = reader.getResult().stream()
                .collect(Collectors.groupingBy(mc -> mc.getMethod()));

        int maxSize = 50;
        Random r = new Random();
        methodCalls.values().stream()
                .forEach(set -> {
                    if(set.size() < maxSize) {
                        set.stream()
                                .forEach(mc -> generateTest(mc));
                    } else {
                        IntStream.range(0, maxSize)
                                .mapToObj(i -> set.remove(r.nextInt(set.size())))
                                .forEach(mc -> generateTest(mc));
                    }
                });

        List<CtType> tests = getTestClasses();

        Log.debug("nb tests: {}", tests.stream().mapToInt(test -> test.getMethods().size()).sum());
        return tests.stream()
                .map(test -> minimiseTests(test))
                .map(test -> {
                    try {
                        return assertGenerator.generateAsserts(test);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(test -> test != null)
                .collect(Collectors.toList());
    }

    protected CtType minimiseTests(CtType classTest) {
        inputProgram.getFactory().Type().get(Runnable.class);

        CtType cl = classWithLoggerBuilder.buildClassWithLogger(classTest, classTest.getMethods());
        try {
            fr.inria.diversify.logger.Logger.reset();
            fr.inria.diversify.logger.Logger.setLogDir(branchDir);
            JunitResult result = testRunnerWithBranchLogger.runTests(cl, cl.getMethods());
            List<Coverage> coverage = loadBranchCoverage(branchDir.getAbsolutePath());
            Set<String> mthsSubSet = coverage.stream()
                    .collect(Collectors.groupingBy(c -> c.getCoverageBranch()))
                    .values().stream()
                    .map(value -> value.stream().findAny().get())
                    .map(c -> c.getName())
                    .collect(Collectors.toSet());

            Set<CtMethod> mths = new HashSet<>(classTest.getMethods());
            mths.stream()
                    .filter(mth -> !mthsSubSet.contains(classTest.getQualifiedName() + "." + mth.getSimpleName()))
                    .forEach(mth -> classTest.removeMethod(mth));

        } catch (Exception e) {}

        return classTest;
    }
    protected List<CtType>  getTestClasses() {
        return testClasses.values().stream()
                .filter(testClass -> !testClass.getMethods().isEmpty())
                .map(testClass -> {
                    CtType cl = inputProgram.getFactory().Core().clone(testClass);
                    cl.setParent(testClass.getParent());
                    return cl;
                })
                .peek(testClass -> {
                    try {
                        JunitResult result = testRunner.runTests(testClass, testClass.getMethods());
                        Set<CtMethod> tests = new HashSet<CtMethod>(testClass.getMethods());
                                tests.stream()
                                .filter(test -> result.compileOrTimeOutTestName().contains(test.getSimpleName()))
                                .forEach(test -> {
                                    testClass.removeMethod(test);
                                });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .filter(testClass -> !testClass.getMethods().isEmpty())
                .collect(Collectors.toList());
    }
    static int count;
    static int tryTest;
    protected void generateTest(MethodCall methodCall) {
        tryTest++;
        TestMethodGenerator testMethodGenerator = new TestMethodGenerator(inputProgram.getFactory(), valueFactory);
        CtMethod method = methodCall.getMethod();
        CtType declaringClass = method.getDeclaringType();

        if(!testClasses.containsKey(declaringClass)) {
            testClasses.put(declaringClass, generateNewTestClass(declaringClass));
        }
        CtType testClass = testClasses.get(declaringClass);
        try {
            boolean result = false;
            if(!isPrivate(method)) {
                result = testMethodGenerator.generateTestFromInvocation(methodCall, testClass);
            }
            if(!result && !containsThis(method) && !containsReferenceToPrivatElement(method) && containsCall(methodCall.getMethod(), "com.caucho")) {
                result = testMethodGenerator.generateTestFromBody(methodCall, testClass);
            }
             if(result) {
                 count++;
             }
        } catch (Exception e) {
            e.printStackTrace();
            Log.debug("");
        }
        Log.debug("test count: {}/{}", count, tryTest);
    }

    protected CtType generateNewTestClass(CtType classToTest) {
        CtType test = inputProgram.getFactory().Class().create(classToTest.getPackage(), classToTest.getSimpleName()+"Test");
        Set<ModifierKind> modifierKinds = new HashSet<>(test.getModifiers());
        modifierKinds.add(ModifierKind.PUBLIC);
        test.setModifiers(modifierKinds);
        return test;
    }


    protected boolean isPrivate(CtMethod method) {
        return method.getModifiers().contains(ModifierKind.PRIVATE);
    }

    protected boolean containsThis(CtMethod method) {
        return !Query.getElements(method, new TypeFilter(CtThisAccess.class)).isEmpty();
    }

    protected boolean containsReferenceToPrivatElement(CtMethod method) {
        return Query.getElements(method.getBody(), new TypeFilter<CtModifiable>(CtModifiable.class)).stream()
                .anyMatch(modifiable -> modifiable.hasModifier(ModifierKind.PRIVATE));
    }

    protected boolean containsCall(CtMethod method, String filter) {
        List<CtInvocation> calls = Query.getElements(method, new TypeFilter(CtInvocation.class));
        return calls.stream()
                .map(call -> call.getType())
                .anyMatch(type -> type.getQualifiedName().startsWith(filter));
    }

    protected List<Coverage> loadBranchCoverage(String logDir) throws IOException {
        List<Coverage> branchCoverage = null;
        try {
            LogReader logReader = new LogReader(logDir);
            TestCoverageParser coverageParser = new TestCoverageParser();
            logReader.addParser(coverageParser);
            logReader.readLogs();

            branchCoverage = coverageParser.getResult();

        } catch (Throwable e) {}

        deleteLogFile(logDir);

        return branchCoverage;
    }

    protected void deleteLogFile(String logDir) throws IOException {
        File dir = new File(logDir);
        for(File file : dir.listFiles()) {
            if(!file.getName().equals("info")) {
                FileUtils.forceDelete(file);
            }
        }
    }
}
