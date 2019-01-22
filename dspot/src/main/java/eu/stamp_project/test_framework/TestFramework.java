package eu.stamp_project.test_framework;

import eu.stamp_project.test_framework.assertions.AssertEnum;
import eu.stamp_project.test_framework.junit.JUnit3Support;
import eu.stamp_project.test_framework.junit.JUnit4Support;
import eu.stamp_project.test_framework.junit.JUnit5Support;
import eu.stamp_project.testrunner.runner.Failure;
import eu.stamp_project.utils.program.InputConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 09/11/18
 * Singleton and Starting point of Chain of responsibility
 */
public class TestFramework implements TestFrameworkSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFramework.class);

    private static final TestFramework _instance = new TestFramework();

    private List<TestFrameworkSupport> testFrameworkSupportList;

    public static TestFramework get() {
        return _instance;
    }

    private TestFramework() {
        this.testFrameworkSupportList = new ArrayList<>();
        this.testFrameworkSupportList.add(new JUnit3Support());
        this.testFrameworkSupportList.add(new JUnit4Support());
        this.testFrameworkSupportList.add(new JUnit5Support());
        this.testFrameworkSupportList.add(new GoogleTruthTestFramework());
        this.testFrameworkSupportList.add(new AssertJTestFramework());
    }

    /**
     * This method says whether the given test method is JUnit 5 or not.
     *
     * For now, only JUnit5 needs to be checked because JUnit3 and JUnit4 can be run with the same test runner and do not required any
     * specific configuration (such as the pom for PIT, see TODO).
     *
     * @param ctMethod the test method to checkEnum
     * @return true if the given ctMethod is a JUnit5, false otherwise.
     */
    public static boolean isJUnit5(CtMethod<?> ctMethod) {
       InputConfiguration.get().setJUnit5(TestFramework.get().getTestFramework(ctMethod) instanceof JUnit5Support);
       return InputConfiguration.get().isJUnit5();
    }

    @Override
    public boolean isAssert(CtInvocation<?> invocation) {
        for (TestFrameworkSupport testFrameworkSupport : this.testFrameworkSupportList) {
            if (testFrameworkSupport.isAssert(invocation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAssert(CtStatement candidate) {
        if (candidate instanceof CtInvocation) {
            return this.isAssert(((CtInvocation<?>) candidate));
        } else {
            return false;
        }
    }

    @Override
    public boolean isInAssert(CtElement candidate) {
        if (candidate.getParent(CtInvocation.class) != null) {
            return isAssert(candidate.getParent(CtInvocation.class));
        } else {
            return false;
        }
    }

    @Override
    public boolean isTest(CtMethod<?> candidate) {
        for (TestFrameworkSupport testFrameworkSupport : this.testFrameworkSupportList) {
            if (testFrameworkSupport.isTest(candidate)) {
                return true;
            }
        }
        return false;
        /*
        LOGGER.error("Could not find any test framework support for {}",

                (candidate.getParent(CtType.class) != null ?
                        candidate.getParent(CtType.class).getQualifiedName() + "#" : "")
                        + candidate.getSimpleName());
        LOGGER.error("Current supported test framework are:");
        LOGGER.error(this.testFrameworkSupportList.stream().map(Object::toString).collect(Collectors.joining(AmplificationHelper.LINE_SEPARATOR)));
        throw new UnsupportedTestFrameworkException(candidate.toString());
        */
    }

    // This method identify the test framework support used in the given test method
    // The idea is to generate assertions that look like the original one,
    // i.e. if the developer used JUnit4, we should generate JUnit4 assertions
    // We determine is by taking the most common assertion type in the given test method.
    private TestFrameworkSupport getTestFramework(CtMethod<?> testMethod) {
        final Map<TestFrameworkSupport, Long> numberOfCallsToAssertionPerTestFramework = this.testFrameworkSupportList.stream()
                .collect(Collectors.toMap(Function.identity(),
                        testFrameworkSupport ->
                                testMethod.getElements(new TypeFilter<>(CtStatement.class))
                                        .stream()
                                        .filter(testFrameworkSupport::isAssert)
                                        .count()
                ));
        if (numberOfCallsToAssertionPerTestFramework.values().stream().allMatch(aLong -> aLong == 0L)) {
            for (TestFrameworkSupport testFrameworkSupport : testFrameworkSupportList) {
                if (testFrameworkSupport.isTest(testMethod)) {
                    return testFrameworkSupport;
                }
            }
            return this.testFrameworkSupportList.get(1);
        }
        return Collections.max(numberOfCallsToAssertionPerTestFramework.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    @Override
    public CtInvocation<?> buildInvocationToAssertion(CtMethod<?> testMethod, AssertEnum assertEnum, List<CtExpression> arguments) {
        return getTestFramework(testMethod).buildInvocationToAssertion(testMethod, assertEnum, arguments);
    }

    /**
     * @return the stream of all test classes of the test suite.
     * We consider a class as test class if at least one of its method match {@link TestFramework#isTest(CtMethod)}
     */
    public static Stream<CtType<?>> getAllTestClassesAsStream() {
        return InputConfiguration.get()
                .getFactory()
                .Type()
                .getAll()
                .stream()
                .filter(ctType ->
                        ctType.getMethods()
                                .stream()
                                .anyMatch(TestFramework.get()::isTest)
                );
    }

    /**
     * @return the list of all test classes of the test suite.
     * We consider a class as test class if at least one of its method match {@link TestFramework#isTest(CtMethod)}
     */
    public static List<CtType<?>> getAllTestClasses() {
        return TestFramework.getAllTestClassesAsStream().collect(Collectors.toList());
    }

    /**
     * @return the qualified name's array of all test classes of the test suite.
     * We consider a class as test class if at least one of its method match {@link TestFramework#isTest(CtMethod)}
     */
    public static String[] getAllTestClassesName() {
        return TestFramework.getAllTestClassesAsStream()
                .map(CtType::getQualifiedName)
                .toArray(String[]::new);
    }

    public static final TypeFilter<CtInvocation<?>> ASSERTIONS_FILTER = new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
        @Override
        public boolean matches(CtInvocation<?> element) {
            return TestFramework.get().isAssert(element);
        }
    };

    /**
     * return the list of method of the given test class.
     * We consider method as test method if the method matches {@link TestFramework#isTest(CtMethod)}
     *
     * @param classTest the class of which we want the list of test methods
     * @return the list of test methods of the given test class
     */
    public static List<CtMethod<?>> getAllTest(CtType<?> classTest) {
        Set<CtMethod<?>> methods = classTest.getMethods();
        return methods.stream()
                .filter(TestFramework.get()::isTest)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public CtMethod<?> prepareTestMethod(CtMethod<?> testMethod) {
        return getTestFramework(testMethod).prepareTestMethod(testMethod);
    }

    @Override
    public CtMethod<?> generateExpectedExceptionsBlock(CtMethod<?> test, Failure failure, int numberOfFail) {
        return this.getTestFramework(test).generateExpectedExceptionsBlock(test, failure, numberOfFail);
    }

    @Override
    public void generateAfterClassToSaveObservations(CtType<?> testClass, List<CtMethod<?>> testsToRun) {
        this.getTestFramework(testsToRun.get(0)).generateAfterClassToSaveObservations(testClass, testsToRun);
    }
}