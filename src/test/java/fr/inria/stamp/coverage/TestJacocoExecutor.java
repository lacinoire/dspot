package fr.inria.stamp.coverage;

import fr.inria.diversify.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import spoon.reflect.declaration.CtClass;

import static org.junit.Assert.assertEquals;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 13/07/17
 */
public class TestJacocoExecutor {

	@Before
	public void setUp() throws Exception {
		Utils.reset();
	}

	@After
	public void tearDown() throws Exception {
		Utils.reset();
	}

	@Test
	public void testJacocoExecutorOnMocks() throws Exception {
		Utils.reset();
		Utils.init("src/test/resources/mock/mock.properties");

		JacocoExecutor jacocoExecutor = new JacocoExecutor(Utils.getInputProgram(), Utils.getInputConfiguration());
		final CtClass<?> easyMockTest = Utils.findClass("org.baeldung.mocks.easymock.LoginControllerIntegrationTest");
		final CtClass<?> jmockitTest = Utils.findClass("org.baeldung.mocks.jmockit.LoginControllerIntegrationTest");
		final CtClass<?> mockitoTest = Utils.findClass("org.baeldung.mocks.mockito.LoginControllerIntegrationTest");

		CoverageResults coverageResults = jacocoExecutor.executeJacoco(easyMockTest);
		assertEquals(0, coverageResults.instructionsCovered); // TODO not able to run mocked test
		assertEquals(78, coverageResults.instructionsTotal);
		coverageResults = jacocoExecutor.executeJacoco(jmockitTest);
		assertEquals(0, coverageResults.instructionsCovered); // TODO not able to run mocked test
		assertEquals(78, coverageResults.instructionsTotal);
		coverageResults = jacocoExecutor.executeJacoco(mockitoTest);
		assertEquals(3, coverageResults.instructionsCovered); // TODO not able to run mocked test
		assertEquals(78, coverageResults.instructionsTotal);
	}

	@Test
	public void testJacocoExecutor() throws Exception {
		Utils.reset();
		Utils.init("src/test/resources/test-projects/test-projects.properties");
		JacocoExecutor jacocoExecutor = new JacocoExecutor(Utils.getInputProgram(), Utils.getInputConfiguration());
		final CoverageResults coverageResults = jacocoExecutor.executeJacoco(Utils.findClass("example.TestSuiteExample"));
		assertEquals(33, coverageResults.instructionsCovered);
		assertEquals(37, coverageResults.instructionsTotal);
	}

	/**
	 * WARNING: The jacoco executor can not run mockito see: https://github.com/mockito/mockito/issues/969
	 * TODO: fixme
	 */
	@Test
	public void testJacocoExecutorOnMockito() throws Exception {
		Utils.reset();
		Utils.init("src/test/resources/mockito/mockito.properties");
		JacocoExecutor jacocoExecutor = new JacocoExecutor(Utils.getInputProgram(), Utils.getInputConfiguration());
		final CoverageResults coverageResults = jacocoExecutor.executeJacoco(Utils.findClass("info.sanaulla.dal.BookDALTest"));
		assertEquals(0, coverageResults.instructionsCovered); // TODO not able to run mockito test
		assertEquals(65, coverageResults.instructionsTotal);
	}

}